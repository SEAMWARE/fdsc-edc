/*
 * Copyright 2025 Seamless Middleware Technologies S.L and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.seamware.edc.otel.audit;

import static org.seamware.edc.otel.audit.AuditEventSubscriber.ATTR_DOMAIN;
import static org.seamware.edc.otel.audit.AuditEventSubscriber.ATTR_ENTITY_ID;
import static org.seamware.edc.otel.audit.AuditEventSubscriber.ATTR_EVENT_ID;
import static org.seamware.edc.otel.audit.AuditEventSubscriber.ATTR_EVENT_NAME;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.edc.spi.monitor.Monitor;

/**
 * Translates TMForum product catalog notifications (TMF620 hub) into OpenTelemetry audit log
 * records. With TMForum-backed stores all catalog writes happen directly in TMForum, so EDC never
 * raises asset or contract definition events and the notifications are the only source.
 *
 * <p>Product specifications are audited as assets and product offerings as contract definitions,
 * using the TMForum externalId as entity id. Deletions are not covered: the marketplace retires
 * entities through lifecycleStatus instead of deleting them.
 */
public class TmfCatalogEventAuditor {

  static final String SOURCE_TMFORUM = "tmforum";
  static final String DOMAIN_ASSET = "asset";
  static final String DOMAIN_CONTRACT_DEFINITION = "contract_definition";
  static final String ACTION_CREATED = "created";
  static final String ACTION_UPDATED = "updated";
  static final String ACTION_STATE_CHANGED = "state_changed";

  static final String ATTR_ACTION = "audit.action";
  static final String ATTR_SOURCE = "audit.source";
  static final String ATTR_TMF_ID = "tmf.id";
  static final String ATTR_LIFECYCLE_STATUS = "tmf.lifecycle_status";
  static final String ATTR_TMF_SPECIFICATION_ID = "tmf.product_specification.id";
  static final String ATTR_CONTRACT_POLICY_ID = "edc.contract_policy.id";
  static final String ATTR_ACCESS_POLICY_ID = "edc.access_policy.id";

  // same keys as TMFEdcMapper, duplicated to keep this module independent of tmf-extension
  static final String TERM_NAME_CONTRACT_DEFINITION = "edc:contractDefinition";
  static final String CONTRACT_POLICY_KEY = "contractPolicy";
  static final String ACCESS_POLICY_KEY = "accessPolicy";
  static final String UID_KEY = "http://www.w3.org/ns/odrl/2/uid";

  private static final Map<String, String> ENTITY_DOMAINS =
      Map.of("ProductSpecification", DOMAIN_ASSET, "ProductOffering", DOMAIN_CONTRACT_DEFINITION);

  private static final List<Map.Entry<String, String>> SUFFIX_ACTIONS =
      List.of(
          Map.entry("AttributeValueChangeEvent", ACTION_UPDATED),
          Map.entry("StateChangeEvent", ACTION_STATE_CHANGED),
          Map.entry("CreateEvent", ACTION_CREATED));

  private final Logger otelLogger;
  private final Monitor monitor;
  private final Clock clock;
  private final Map<String, Boolean> seenEvents;

  public TmfCatalogEventAuditor(
      Logger otelLogger, Monitor monitor, Clock clock, int dedupCacheSize) {
    this.otelLogger = otelLogger;
    this.monitor = monitor;
    this.clock = clock;
    this.seenEvents =
        Collections.synchronizedMap(
            new LinkedHashMap<String, Boolean>(16, 0.75f, true) {
              @Override
              protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > dedupCacheSize;
              }
            });
  }

  public void audit(Map<String, Object> notification) {
    var eventType = asString(notification.get("eventType"));
    var kind = EventKind.of(eventType);
    if (kind == null) {
      monitor.debug("Otel Audit: ignoring TMForum event " + eventType);
      return;
    }

    var entity = asMap(asMap(notification.get("event")).get(kind.payloadName()));
    var tmfId = asString(entity.get("id"));
    if (tmfId == null) {
      monitor.warning("Otel Audit: TMForum event " + eventType + " without entity id");
      return;
    }

    // TMForum may deliver the same event more than once (retries, duplicated subscription)
    var lastUpdate = asString(entity.get("lastUpdate"));
    if (lastUpdate != null
        && seenEvents.put(eventType + "|" + tmfId + "|" + lastUpdate, Boolean.TRUE) != null) {
      return;
    }

    var externalId = asString(entity.get("externalId"));
    var attributes =
        Attributes.builder()
            .put(ATTR_DOMAIN, kind.domain())
            .put(ATTR_EVENT_NAME, eventType)
            .put(ATTR_ACTION, kind.action())
            .put(ATTR_SOURCE, SOURCE_TMFORUM)
            .put(ATTR_TMF_ID, tmfId);
    putIfPresent(attributes, ATTR_ENTITY_ID, externalId);
    putIfPresent(attributes, ATTR_EVENT_ID, asString(notification.get("eventId")));
    putIfPresent(attributes, ATTR_LIFECYCLE_STATUS, asString(entity.get("lifecycleStatus")));
    if (DOMAIN_CONTRACT_DEFINITION.equals(kind.domain())) {
      putIfPresent(
          attributes,
          ATTR_TMF_SPECIFICATION_ID,
          asString(asMap(entity.get("productSpecification")).get("id")));
      contractDefinitionTerm(entity)
          .ifPresent(
              term -> {
                putIfPresent(
                    attributes, ATTR_CONTRACT_POLICY_ID, policyUid(term, CONTRACT_POLICY_KEY));
                putIfPresent(attributes, ATTR_ACCESS_POLICY_ID, policyUid(term, ACCESS_POLICY_KEY));
              });
    }

    otelLogger
        .logRecordBuilder()
        .setTimestamp(eventTime(notification))
        .setObservedTimestamp(clock.instant())
        .setSeverity(Severity.INFO)
        .setSeverityText(Severity.INFO.name())
        .setBody(String.format("%s id=%s tmfId=%s", eventType, externalId, tmfId))
        .setAllAttributes(attributes.build())
        .emit();
  }

  private Instant eventTime(Map<String, Object> notification) {
    var value = asString(notification.get("eventTime"));
    if (value != null) {
      try {
        return Instant.parse(value);
      } catch (DateTimeParseException e) {
        monitor.debug("Otel Audit: unparseable TMForum eventTime " + value);
      }
    }
    return clock.instant();
  }

  private static Optional<Map<String, Object>> contractDefinitionTerm(
      Map<String, Object> offering) {
    if (!(offering.get("productOfferingTerm") instanceof List<?> terms)) {
      return Optional.empty();
    }
    return terms.stream()
        .map(TmfCatalogEventAuditor::asMap)
        .filter(term -> TERM_NAME_CONTRACT_DEFINITION.equals(term.get("name")))
        .findFirst();
  }

  private static String policyUid(Map<String, Object> term, String policyKey) {
    return asString(asMap(term.get(policyKey)).get(UID_KEY));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value) {
    return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
  }

  private static String asString(Object value) {
    return value instanceof String string ? string : null;
  }

  private static void putIfPresent(AttributesBuilder attributes, String key, String value) {
    if (value != null) {
      attributes.put(key, value);
    }
  }

  private record EventKind(String domain, String action, String payloadName) {

    static EventKind of(String eventType) {
      if (eventType == null) {
        return null;
      }
      for (var suffixAction : SUFFIX_ACTIONS) {
        if (eventType.endsWith(suffixAction.getKey())) {
          var entityType =
              eventType.substring(0, eventType.length() - suffixAction.getKey().length());
          var domain = ENTITY_DOMAINS.get(entityType);
          if (domain == null) {
            return null;
          }
          var payloadName = Character.toLowerCase(entityType.charAt(0)) + entityType.substring(1);
          return new EventKind(domain, suffixAction.getValue(), payloadName);
        }
      }
      return null;
    }
  }
}
