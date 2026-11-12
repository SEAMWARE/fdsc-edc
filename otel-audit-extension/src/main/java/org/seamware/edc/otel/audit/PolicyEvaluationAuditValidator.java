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
import static org.seamware.edc.otel.audit.AuditEventSubscriber.ATTR_CONTRACT_ID;
import static org.seamware.edc.otel.audit.AuditEventSubscriber.ATTR_COUNTERPARTY_ID;
import static org.seamware.edc.otel.audit.AuditEventSubscriber.ATTR_EVENT_TYPE;
import static org.seamware.edc.otel.audit.AuditEventSubscriber.ATTR_PARTICIPANT_ID;
import static org.seamware.edc.otel.audit.AuditEventSubscriber.ATTR_ROLE;
import static org.seamware.edc.otel.audit.AuditEventSubscriber.BODY_PREFIX;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import java.time.Clock;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.AgreementPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.ContractNegotiationPolicyContext;
import org.eclipse.edc.participant.spi.ParticipantAgentPolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyValidatorRule;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.monitor.Monitor;
import org.seamware.edc.otel.ParticipantRole;

/**
 * Policy engine post-validator that emits a {@code PolicyEvaluated} audit record for every
 * permitted evaluation outside the excluded scopes. Post-validators only run once the policy is
 * fulfilled, ODRL-PAP pre-validation included, so the decision is always {@code permit}.
 *
 * <p>It only observes: it always returns {@code true} and never propagates an exception, so it
 * cannot change the evaluation result.
 */
public class PolicyEvaluationAuditValidator implements PolicyValidatorRule<PolicyContext> {

  static final String EVENT_TYPE = "PolicyEvaluated";
  static final String DECISION_PERMIT = "permit";

  /**
   * Catalog and policy monitor evaluations, and every {@code request.*} scope, which only selects
   * the credentials presented on outgoing requests and is not an access decision.
   */
  static final String DEFAULT_EXCLUDED_SCOPES = "catalog,policy.monitor,request.*";

  static final String ATTR_DECISION = "policy.decision";
  static final String ATTR_SCOPE = "policy.scope";

  private static final String WILDCARD = "*";

  private final Logger otelLogger;
  private final Monitor monitor;
  private final Clock clock;
  private final String participantId;
  private final Set<String> excludedScopes;

  /**
   * @param participantId id of this connector ({@code edc.participant.id}), may be {@code null}
   * @param excludedScopes scopes not audited; an entry ending in {@code *} excludes every scope
   *     starting with the text before it
   */
  public PolicyEvaluationAuditValidator(
      Logger otelLogger,
      Monitor monitor,
      Clock clock,
      String participantId,
      Set<String> excludedScopes) {
    this.otelLogger = otelLogger;
    this.monitor = monitor;
    this.clock = clock;
    this.participantId = participantId;
    this.excludedScopes = Set.copyOf(excludedScopes);
  }

  /** Parses a comma-separated list of scopes, ignoring blank entries. */
  public static Set<String> parseScopes(String scopes) {
    if (scopes == null) {
      return Set.of();
    }
    return Arrays.stream(scopes.split(","))
        .map(String::trim)
        .filter(scope -> !scope.isEmpty())
        .collect(Collectors.toUnmodifiableSet());
  }

  static boolean isExcluded(Set<String> excludedScopes, String scope) {
    if (scope == null) {
      return false;
    }
    return excludedScopes.stream()
        .anyMatch(
            entry ->
                entry.endsWith(WILDCARD)
                    ? scope.startsWith(entry.substring(0, entry.length() - WILDCARD.length()))
                    : entry.equals(scope));
  }

  @Override
  public Boolean apply(Policy policy, PolicyContext context) {
    AuditGuard.run(
        () -> {
          if (context != null && !isExcluded(excludedScopes, context.scope())) {
            emit(policy, context);
          }
        },
        monitor,
        "emit " + EVENT_TYPE + " record");
    return true;
  }

  @Override
  public String name() {
    return "OtelPolicyEvaluationAudit";
  }

  private void emit(Policy policy, PolicyContext context) {
    var attributes =
        Attributes.builder().put(ATTR_EVENT_TYPE, EVENT_TYPE).put(ATTR_DECISION, DECISION_PERMIT);
    putIfPresent(attributes, ATTR_SCOPE, context.scope());
    putIfPresent(attributes, ATTR_PARTICIPANT_ID, participantId);

    if (context instanceof ParticipantAgentPolicyContext agentContext
        && agentContext.participantAgent() != null) {
      putIfPresent(attributes, ATTR_COUNTERPARTY_ID, agentContext.participantAgent().getIdentity());
    }

    var assetId = policy != null ? policy.getTarget() : null;
    if (context instanceof AgreementPolicyContext agreementContext
        && agreementContext.contractAgreement() != null) {
      var agreement = agreementContext.contractAgreement();
      putIfPresent(attributes, ATTR_CONTRACT_ID, agreement.getId());
      putIfPresent(attributes, ATTR_AGREEMENT_ID, agreement.getAgreementId());
      putIfPresent(
          attributes,
          ATTR_ROLE,
          ParticipantRole.fromParties(
              participantId, agreement.getProviderId(), agreement.getConsumerId()));
      if (agreement.getAssetId() != null) {
        assetId = agreement.getAssetId();
      }
    }
    if (context instanceof ContractNegotiationPolicyContext) {
      // EDC only evaluates contract.negotiation on the provider, for an initial contract request
      attributes.put(ATTR_ROLE, ParticipantRole.PROVIDER);
    }
    putIfPresent(attributes, ATTR_ASSET_ID, assetId);

    var now = clock.instant();
    otelLogger
        .logRecordBuilder()
        .setTimestamp(now)
        .setObservedTimestamp(now)
        .setSeverity(Severity.INFO)
        .setSeverityText(Severity.INFO.name())
        .setBody(BODY_PREFIX + EVENT_TYPE)
        .setAllAttributes(attributes.build())
        .emit();
  }

  private static void putIfPresent(AttributesBuilder attributes, String key, String value) {
    if (value != null && !value.isBlank()) {
      attributes.put(key, value);
    }
  }
}
