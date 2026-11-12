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

import static org.seamware.edc.otel.audit.AuditEventSubscriber.ATTR_AGREEMENT_ID;
import static org.seamware.edc.otel.audit.AuditEventSubscriber.ATTR_ASSET_ID;
import static org.seamware.edc.otel.audit.AuditEventSubscriber.ATTR_CONSUMER;
import static org.seamware.edc.otel.audit.AuditEventSubscriber.ATTR_CONTRACT_ID;
import static org.seamware.edc.otel.audit.AuditEventSubscriber.ATTR_COUNTERPARTY_ID;
import static org.seamware.edc.otel.audit.AuditEventSubscriber.ATTR_EVENT_TYPE;
import static org.seamware.edc.otel.audit.AuditEventSubscriber.ATTR_NEGOTIATION_ID;
import static org.seamware.edc.otel.audit.AuditEventSubscriber.ATTR_OFFER_ID;
import static org.seamware.edc.otel.audit.AuditEventSubscriber.ATTR_PARTICIPANT_ID;
import static org.seamware.edc.otel.audit.AuditEventSubscriber.ATTR_PROVIDER;
import static org.seamware.edc.otel.audit.AuditEventSubscriber.ATTR_ROLE;
import static org.seamware.edc.otel.audit.AuditEventSubscriber.BODY_PREFIX;
import static org.seamware.edc.otel.audit.PolicyEvaluationAuditValidator.ATTR_DECISION;
import static org.seamware.edc.otel.audit.PolicyEvaluationAuditValidator.ATTR_SCOPE;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import java.time.Clock;
import java.util.regex.Pattern;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.TransferProcessPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractOffer;
import org.eclipse.edc.connector.controlplane.contract.spi.validation.ValidatableConsumerOffer;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.spi.monitor.Monitor;
import org.seamware.edc.otel.ParticipantRole;

/**
 * Emits {@code ContractNegotiationRejected} and {@code TransferProcessRejected} audit records for
 * the DSP requests denied by the {@code ContractValidationService}. Every value comes from the
 * arguments EDC passes to that service: nothing is read from the stores.
 *
 * <p>The deny reason is the EDC failure detail, truncated to {@value #MAX_REASON_LENGTH}
 * characters. It includes the ODRL-PAP explanation when the PAP denied the request.
 */
public class PolicyDenyAuditor {

  static final String NEGOTIATION_REJECTED = "ContractNegotiationRejected";
  static final String TRANSFER_REJECTED = "TransferProcessRejected";
  static final String DECISION_DENY = "deny";
  static final String ATTR_REASON = "policy.deny.reason";
  static final String ATTR_NEGOTIATION_CORRELATION_ID = "negotiation.correlation.id";
  static final int MAX_REASON_LENGTH = 1024;

  // EDC policy engine failure for unmet rules. Pre-validator denials (ODRL-PAP) do not name the
  // scope, so the scope is omitted for them
  private static final Pattern SCOPE_PATTERN =
      Pattern.compile("Policy in scope (\\S+) not fulfilled");
  // ContractValidationServiceImpl#validateAgreement prefixes every transfer.process evaluation
  // failure with this text, whatever validator failed
  private static final String AGREEMENT_POLICY_FAILURE_PREFIX =
      "Policy does not fulfill the agreement";

  private final Logger otelLogger;
  private final Monitor monitor;
  private final Clock clock;
  private final String participantId;

  /**
   * @param participantId id of this connector ({@code edc.participant.id}), may be {@code null}
   */
  public PolicyDenyAuditor(Logger otelLogger, Monitor monitor, Clock clock, String participantId) {
    this.otelLogger = otelLogger;
    this.monitor = monitor;
    this.clock = clock;
    this.participantId = participantId;
  }

  /** Provider denied an initial contract request: access or contract policy, or invalid offer. */
  public void initialOfferRejected(
      ParticipantAgent agent, ValidatableConsumerOffer offer, String failure) {
    var counterparty = identityOf(agent);
    var attributes = record(NEGOTIATION_REJECTED, failure, scopeOf(failure));
    attributes.put(ATTR_ROLE, ParticipantRole.PROVIDER);
    putIfPresent(attributes, ATTR_PROVIDER, participantId);
    putIfPresent(attributes, ATTR_CONSUMER, counterparty);
    putIfPresent(attributes, ATTR_COUNTERPARTY_ID, counterparty);
    if (offer != null && offer.getOfferId() != null) {
      putIfPresent(attributes, ATTR_OFFER_ID, offer.getOfferId().toString());
      putIfPresent(attributes, ATTR_ASSET_ID, offer.getOfferId().assetIdPart());
    }
    emit(attributes, NEGOTIATION_REJECTED);
  }

  /** Consumer denied the agreement sent by the provider. */
  public void agreementConfirmationRejected(
      ParticipantAgent agent,
      ContractAgreement agreement,
      ContractOffer latestOffer,
      String failure) {
    var attributes = record(NEGOTIATION_REJECTED, failure, null);
    attributes.put(ATTR_ROLE, ParticipantRole.CONSUMER);
    putIfPresent(attributes, ATTR_COUNTERPARTY_ID, identityOf(agent));
    putIfPresent(attributes, ATTR_OFFER_ID, latestOffer != null ? latestOffer.getId() : null);
    putAgreement(attributes, agreement);
    emit(attributes, NEGOTIATION_REJECTED);
  }

  /**
   * A negotiation message came from a party other than the negotiation counterparty. The
   * correlation id is the counterparty process id kept by EDC in the negotiation it validates: the
   * consumerPid on the provider, the providerPid on the consumer.
   */
  public void negotiationRequestRejected(
      ParticipantAgent agent, ContractNegotiation negotiation, String failure) {
    var attributes = record(NEGOTIATION_REJECTED, failure, null);
    putIfPresent(attributes, ATTR_COUNTERPARTY_ID, identityOf(agent));
    if (negotiation != null) {
      putIfPresent(attributes, ATTR_NEGOTIATION_ID, negotiation.getId());
      putIfPresent(attributes, ATTR_NEGOTIATION_CORRELATION_ID, negotiation.getCorrelationId());
      if (negotiation.getType() != null) {
        putIfPresent(
            attributes, ATTR_ROLE, ParticipantRole.fromProcessType(negotiation.getType().name()));
      }
    }
    emit(attributes, NEGOTIATION_REJECTED);
  }

  /** Provider denied a transfer request: agreement policy not fulfilled or invalid consumer. */
  public void transferAgreementRejected(
      ParticipantAgent agent, ContractAgreement agreement, String failure) {
    var scope = scopeOf(failure);
    if (scope == null && failure != null && failure.startsWith(AGREEMENT_POLICY_FAILURE_PREFIX)) {
      scope = TransferProcessPolicyContext.TRANSFER_SCOPE;
    }
    var attributes = record(TRANSFER_REJECTED, failure, scope);
    attributes.put(ATTR_ROLE, ParticipantRole.PROVIDER);
    putIfPresent(attributes, ATTR_COUNTERPARTY_ID, identityOf(agent));
    putAgreement(attributes, agreement);
    emit(attributes, TRANSFER_REJECTED);
  }

  /** A transfer message came from a party that is not part of the agreement. */
  public void transferRequestRejected(
      ParticipantAgent agent, ContractAgreement agreement, String failure) {
    var attributes = record(TRANSFER_REJECTED, failure, null);
    if (agreement != null) {
      putIfPresent(
          attributes,
          ATTR_ROLE,
          ParticipantRole.fromParties(
              participantId, agreement.getProviderId(), agreement.getConsumerId()));
    }
    putIfPresent(attributes, ATTR_COUNTERPARTY_ID, identityOf(agent));
    putAgreement(attributes, agreement);
    emit(attributes, TRANSFER_REJECTED);
  }

  /** Scope named in an EDC policy evaluation failure, or {@code null}. */
  static String scopeOf(String failure) {
    if (failure == null) {
      return null;
    }
    var matcher = SCOPE_PATTERN.matcher(failure);
    return matcher.find() ? matcher.group(1) : null;
  }

  static String truncate(String reason) {
    if (reason == null || reason.length() <= MAX_REASON_LENGTH) {
      return reason;
    }
    return reason.substring(0, MAX_REASON_LENGTH);
  }

  private AttributesBuilder record(String eventType, String failure, String scope) {
    var attributes =
        Attributes.builder().put(ATTR_EVENT_TYPE, eventType).put(ATTR_DECISION, DECISION_DENY);
    putIfPresent(attributes, ATTR_SCOPE, scope);
    putIfPresent(attributes, ATTR_REASON, truncate(failure));
    putIfPresent(attributes, ATTR_PARTICIPANT_ID, participantId);
    return attributes;
  }

  private static void putAgreement(AttributesBuilder attributes, ContractAgreement agreement) {
    if (agreement == null) {
      return;
    }
    putIfPresent(attributes, ATTR_CONTRACT_ID, agreement.getId());
    putIfPresent(attributes, ATTR_AGREEMENT_ID, agreement.getAgreementId());
    putIfPresent(attributes, ATTR_ASSET_ID, agreement.getAssetId());
    putIfPresent(attributes, ATTR_PROVIDER, agreement.getProviderId());
    putIfPresent(attributes, ATTR_CONSUMER, agreement.getConsumerId());
  }

  private void emit(AttributesBuilder attributes, String eventType) {
    var now = clock.instant();
    otelLogger
        .logRecordBuilder()
        .setTimestamp(now)
        .setObservedTimestamp(now)
        .setSeverity(Severity.WARN)
        .setSeverityText(Severity.WARN.name())
        .setBody(BODY_PREFIX + eventType)
        .setAllAttributes(attributes.build())
        .emit();
    monitor.debug("Otel Audit: " + eventType + " recorded");
  }

  private static String identityOf(ParticipantAgent agent) {
    return agent == null ? null : agent.getIdentity();
  }

  private static void putIfPresent(AttributesBuilder attributes, String key, String value) {
    if (value != null && !value.isBlank()) {
      attributes.put(key, value);
    }
  }
}
