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
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationFinalized;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessEvent;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessStarted;
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
 * <p>Body and attribute names follow the audit logs of partincipant-edc-connector, so the same
 * queries work for both connectors on a shared backend. On top of those, every record carries the
 * domain and the previous and new state of the entity.
 *
 * <p>EDC events do not carry the previous state, so the last state seen per entity is kept in a
 * bounded LRU cache. After a restart the first transition of an in-flight entity is reported
 * without previous state.
 */
public class AuditEventSubscriber implements EventSubscriber {

  static final String DOMAIN_NEGOTIATION = "contract_negotiation";
  static final String DOMAIN_TRANSFER = "transfer_process";

  // shared with participant-edc-connector
  static final String ATTR_EVENT_TYPE = "event.type";
  static final String ATTR_NEGOTIATION_ID = "negotiation.id";
  static final String ATTR_TRANSFER_ID = "transfer.id";
  static final String ATTR_COUNTERPARTY_ID = "participant.counterparty.id";
  static final String ATTR_COUNTERPARTY_ADDRESS = "participant.counterparty.address";
  static final String ATTR_PROTOCOL = "protocol";
  static final String ATTR_ASSET_ID = "asset.id";
  static final String ATTR_CONTRACT_ID = "contract.id";
  static final String ATTR_AGREEMENT_ID = "contract.agreement.id";
  static final String ATTR_PROVIDER = "participant.provider";
  static final String ATTR_CONSUMER = "participant.consumer";
  static final String ATTR_SIGNING_DATE = "contract.signing.date";
  static final String ATTR_TRANSFER_TYPE = "transfer.type";
  static final String ATTR_TRANSFER_REASON = "transfer.reason";
  static final String ATTR_DESTINATION_TYPE = "transfer.data.destination.type";

  // fdsc-edc additions
  static final String ATTR_DOMAIN = "audit.domain";
  static final String ATTR_EVENT_ID = "event.id";
  static final String ATTR_STATE_PREVIOUS = "audit.state.previous";
  static final String ATTR_STATE_NEW = "audit.state.new";
  static final String ATTR_OFFER_ID = "offer.id";

  static final String BODY_PREFIX = "Audit: ";
  static final String UNKNOWN_TRANSFER_TYPE = "UNKNOWN";
  static final String UNSPECIFIED_DESTINATION = "NOT_SPECIFIED";

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

    var attributes = baseAttributes(envelope, DOMAIN_NEGOTIATION, previousState, newState);
    putIfPresent(attributes, ATTR_NEGOTIATION_ID, entityId);
    putIfPresent(attributes, ATTR_COUNTERPARTY_ID, event.getCounterPartyId());
    putIfPresent(attributes, ATTR_COUNTERPARTY_ADDRESS, event.getCounterPartyAddress());
    putIfPresent(attributes, ATTR_PROTOCOL, event.getProtocol());
    var lastOffer = event.getLastContractOffer();
    if (lastOffer != null) {
      putIfPresent(attributes, ATTR_OFFER_ID, lastOffer.getId());
      putIfPresent(attributes, ATTR_ASSET_ID, lastOffer.getAssetId());
    }
    if (event instanceof ContractNegotiationFinalized finalized
        && finalized.getContractAgreement() != null) {
      var agreement = finalized.getContractAgreement();
      // EDC 0.14 has a single agreement id, used for both attributes
      putIfPresent(attributes, ATTR_CONTRACT_ID, agreement.getId());
      putIfPresent(attributes, ATTR_AGREEMENT_ID, agreement.getId());
      putIfPresent(attributes, ATTR_PROVIDER, agreement.getProviderId());
      putIfPresent(attributes, ATTR_CONSUMER, agreement.getConsumerId());
      putIfPresent(attributes, ATTR_ASSET_ID, agreement.getAssetId());
      attributes.put(ATTR_SIGNING_DATE, agreement.getContractSigningDate());
    }

    emit(envelope, newState, attributes.build());
  }

  private void auditTransfer(EventEnvelope<?> envelope, TransferProcessEvent event) {
    var entityId = event.getTransferProcessId();
    var newState = LifecycleStates.toState(event.name(), LifecycleStates.TRANSFER_EVENT_PREFIX);
    var previousState = transferTracker.track(entityId, newState, envelope.getAt()).previousState();

    var attributes = baseAttributes(envelope, DOMAIN_TRANSFER, previousState, newState);
    putIfPresent(attributes, ATTR_TRANSFER_ID, entityId);
    putIfPresent(attributes, ATTR_ASSET_ID, event.getAssetId());
    putIfPresent(attributes, ATTR_CONTRACT_ID, event.getContractId());
    attributes.put(
        ATTR_TRANSFER_TYPE, event.getType() != null ? event.getType() : UNKNOWN_TRANSFER_TYPE);
    if (event instanceof TransferProcessStarted started) {
      var dataAddress = started.getDataAddress();
      attributes.put(
          ATTR_DESTINATION_TYPE,
          dataAddress != null && dataAddress.getType() != null
              ? dataAddress.getType()
              : UNSPECIFIED_DESTINATION);
    } else if (event instanceof TransferProcessTerminated terminated) {
      putIfPresent(attributes, ATTR_TRANSFER_REASON, terminated.getReason());
    } else if (event instanceof TransferProcessSuspended suspended) {
      putIfPresent(attributes, ATTR_TRANSFER_REASON, suspended.getReason());
    }

    emit(envelope, newState, attributes.build());
  }

  private AttributesBuilder baseAttributes(
      EventEnvelope<?> envelope, String domain, String previousState, String newState) {
    var attributes =
        Attributes.builder()
            .put(ATTR_EVENT_TYPE, eventType(envelope))
            .put(ATTR_DOMAIN, domain)
            .put(ATTR_STATE_NEW, newState);
    putIfPresent(attributes, ATTR_EVENT_ID, envelope.getId());
    putIfPresent(attributes, ATTR_STATE_PREVIOUS, previousState);
    return attributes;
  }

  private void emit(EventEnvelope<?> envelope, String newState, Attributes attributes) {
    var severity = "TERMINATED".equals(newState) ? Severity.WARN : Severity.INFO;

    otelLogger
        .logRecordBuilder()
        .setTimestamp(envelope.getAt(), TimeUnit.MILLISECONDS)
        .setObservedTimestamp(clock.instant())
        .setSeverity(severity)
        .setSeverityText(severity.name())
        .setBody(BODY_PREFIX + eventType(envelope))
        .setAllAttributes(attributes)
        .emit();
  }

  private static String eventType(EventEnvelope<?> envelope) {
    return envelope.getPayload().getClass().getSimpleName();
  }

  private static void putIfPresent(AttributesBuilder attributes, String key, String value) {
    if (value != null) {
      attributes.put(key, value);
    }
  }
}
