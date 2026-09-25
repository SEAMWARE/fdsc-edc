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

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationEvent;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessEvent;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessSuspended;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessTerminated;
import org.eclipse.edc.spi.event.Event;
import org.eclipse.edc.spi.event.EventEnvelope;
import org.eclipse.edc.spi.event.EventSubscriber;
import org.eclipse.edc.spi.monitor.Monitor;
import org.seamware.edc.otel.EntityLifecycleTracker;
import org.seamware.edc.otel.LifecycleStates;

/**
 * Translates contract negotiation and transfer process events into OpenTelemetry audit log records.
 * It only observes: entities are never read from or written to the stores.
 *
 * <p>EDC events do not carry the previous state, so the last state seen per entity is kept in a
 * bounded LRU cache. After a restart the first transition of an in-flight entity is reported
 * without previous state.
 */
public class AuditEventSubscriber implements EventSubscriber {

  static final String DOMAIN_NEGOTIATION = "contract_negotiation";
  static final String DOMAIN_TRANSFER = "transfer_process";

  static final String ATTR_DOMAIN = "audit.domain";
  static final String ATTR_EVENT_NAME = "event.name";
  static final String ATTR_EVENT_ID = "event.id";
  static final String ATTR_ENTITY_ID = "audit.entity.id";
  static final String ATTR_STATE_PREVIOUS = "audit.state.previous";
  static final String ATTR_STATE_NEW = "audit.state.new";
  static final String ATTR_REASON = "audit.reason";
  static final String ATTR_COUNTERPARTY_ID = "edc.counterparty.id";
  static final String ATTR_COUNTERPARTY_ADDRESS = "edc.counterparty.address";
  static final String ATTR_PROTOCOL = "edc.protocol";
  static final String ATTR_ASSET_ID = "edc.asset.id";
  static final String ATTR_OFFER_ID = "edc.offer.id";
  static final String ATTR_CONTRACT_ID = "edc.contract.id";
  static final String ATTR_TRANSFER_TYPE = "edc.transfer.type";

  private final Logger otelLogger;
  private final Monitor monitor;
  private final Clock clock;
  private final EntityLifecycleTracker negotiationTracker;
  private final EntityLifecycleTracker transferTracker;

  public AuditEventSubscriber(Logger otelLogger, Monitor monitor, Clock clock, int maxEntries) {
    this.otelLogger = otelLogger;
    this.monitor = monitor;
    this.clock = clock;
    this.negotiationTracker =
        new EntityLifecycleTracker(maxEntries, LifecycleStates.NEGOTIATION_FINAL_STATES);
    this.transferTracker =
        new EntityLifecycleTracker(maxEntries, LifecycleStates.TRANSFER_FINAL_STATES);
  }

  @Override
  public <E extends Event> void on(EventEnvelope<E> envelope) {
    try {
      var payload = envelope.getPayload();
      if (payload instanceof ContractNegotiationEvent negotiationEvent) {
        auditNegotiation(envelope, negotiationEvent);
      } else if (payload instanceof TransferProcessEvent transferEvent) {
        auditTransfer(envelope, transferEvent);
      }
    } catch (RuntimeException e) {
      // auditing must never break the state machine
      monitor.warning("Otel Audit: failed to emit audit record for " + envelope.getId(), e);
    }
  }

  private void auditNegotiation(EventEnvelope<?> envelope, ContractNegotiationEvent event) {
    var entityId = event.getContractNegotiationId();
    var newState = LifecycleStates.toState(event.name(), LifecycleStates.NEGOTIATION_EVENT_PREFIX);
    var previousState =
        negotiationTracker.track(entityId, newState, envelope.getAt()).previousState();

    var attributes =
        baseAttributes(envelope, DOMAIN_NEGOTIATION, entityId, previousState, newState);
    putIfPresent(attributes, ATTR_COUNTERPARTY_ID, event.getCounterPartyId());
    putIfPresent(attributes, ATTR_COUNTERPARTY_ADDRESS, event.getCounterPartyAddress());
    putIfPresent(attributes, ATTR_PROTOCOL, event.getProtocol());
    var offers = event.getContractOffers();
    if (offers != null && !offers.isEmpty()) {
      var lastOffer = offers.get(offers.size() - 1);
      putIfPresent(attributes, ATTR_OFFER_ID, lastOffer.getId());
      putIfPresent(attributes, ATTR_ASSET_ID, lastOffer.getAssetId());
    }

    emit(envelope, event.name(), entityId, previousState, newState, attributes.build());
  }

  private void auditTransfer(EventEnvelope<?> envelope, TransferProcessEvent event) {
    var entityId = event.getTransferProcessId();
    var newState = LifecycleStates.toState(event.name(), LifecycleStates.TRANSFER_EVENT_PREFIX);
    var previousState = transferTracker.track(entityId, newState, envelope.getAt()).previousState();

    var attributes = baseAttributes(envelope, DOMAIN_TRANSFER, entityId, previousState, newState);
    putIfPresent(attributes, ATTR_ASSET_ID, event.getAssetId());
    putIfPresent(attributes, ATTR_CONTRACT_ID, event.getContractId());
    putIfPresent(attributes, ATTR_TRANSFER_TYPE, event.getType());
    if (event instanceof TransferProcessTerminated terminated) {
      putIfPresent(attributes, ATTR_REASON, terminated.getReason());
    } else if (event instanceof TransferProcessSuspended suspended) {
      putIfPresent(attributes, ATTR_REASON, suspended.getReason());
    }

    emit(envelope, event.name(), entityId, previousState, newState, attributes.build());
  }

  private AttributesBuilder baseAttributes(
      EventEnvelope<?> envelope,
      String domain,
      String entityId,
      String previousState,
      String newState) {
    var attributes =
        Attributes.builder()
            .put(ATTR_DOMAIN, domain)
            .put(ATTR_EVENT_NAME, envelope.getPayload().name())
            .put(ATTR_STATE_NEW, newState);
    putIfPresent(attributes, ATTR_EVENT_ID, envelope.getId());
    putIfPresent(attributes, ATTR_ENTITY_ID, entityId);
    putIfPresent(attributes, ATTR_STATE_PREVIOUS, previousState);
    return attributes;
  }

  private void emit(
      EventEnvelope<?> envelope,
      String eventName,
      String entityId,
      String previousState,
      String newState,
      Attributes attributes) {
    var severity = "TERMINATED".equals(newState) ? Severity.WARN : Severity.INFO;
    var body =
        String.format(
            "%s id=%s %s->%s",
            eventName, entityId, previousState == null ? "?" : previousState, newState);

    otelLogger
        .logRecordBuilder()
        .setTimestamp(envelope.getAt(), TimeUnit.MILLISECONDS)
        .setObservedTimestamp(clock.instant())
        .setSeverity(severity)
        .setSeverityText(severity.name())
        .setBody(body)
        .setAllAttributes(attributes)
        .emit();
  }

  private static void putIfPresent(AttributesBuilder attributes, String key, String value) {
    if (value != null) {
      attributes.put(key, value);
    }
  }
}
