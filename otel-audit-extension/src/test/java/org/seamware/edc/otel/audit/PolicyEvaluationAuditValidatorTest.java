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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.ContractNegotiationPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.TransferProcessPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.policy.engine.spi.PolicyContext;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PolicyEvaluationAuditValidatorTest {

  // not a DID on purpose: participant ids are emitted as is
  private static final String SELF = "urn:connector:fdsc-edc";
  private static final String CONSUMER = "did:web:consumer";

  private InMemoryLogRecordExporter exporter;
  private Monitor monitor;
  private PolicyEvaluationAuditValidator validator;

  @BeforeEach
  void setUp() {
    exporter = InMemoryLogRecordExporter.create();
    monitor = mock(Monitor.class);
    validator =
        new PolicyEvaluationAuditValidator(
            logger(),
            monitor,
            Clock.systemUTC(),
            SELF,
            PolicyEvaluationAuditValidator.parseScopes(
                PolicyEvaluationAuditValidator.DEFAULT_EXCLUDED_SCOPES));
  }

  @Test
  void permittedTransferEvaluationIsAudited() {
    var agreement =
        ContractAgreement.Builder.newInstance()
            .id("agreement-1")
            .agreementId("dsp-agreement-1")
            .providerId(SELF)
            .consumerId(CONSUMER)
            .assetId("asset-1")
            .policy(Policy.Builder.newInstance().build())
            .build();
    var context = new TransferProcessPolicyContext(agent(CONSUMER), agreement, Instant.now());

    assertTrue(validator.apply(Policy.Builder.newInstance().build(), context));

    var record = single();
    assertEquals(Severity.INFO, record.getSeverity());
    assertEquals("Audit: PolicyEvaluated", record.getBodyValue().asString());
    assertEquals("PolicyEvaluated", attr(record, "event.type"));
    assertEquals("permit", attr(record, "policy.decision"));
    assertEquals("transfer.process", attr(record, "policy.scope"));
    assertEquals(SELF, attr(record, "participant.id"));
    assertEquals(CONSUMER, attr(record, "participant.counterparty.id"));
    assertEquals("agreement-1", attr(record, "contract.id"));
    assertEquals("dsp-agreement-1", attr(record, "contract.agreement.id"));
    assertEquals("provider", attr(record, "participant.role"));
    assertEquals("asset-1", attr(record, "asset.id"));
  }

  @Test
  void contractNegotiationEvaluationIsAuditedAsProvider() {
    var context = new ContractNegotiationPolicyContext(agent(CONSUMER));

    validator.apply(Policy.Builder.newInstance().target("asset-1").build(), context);

    var record = single();
    assertEquals("contract.negotiation", attr(record, "policy.scope"));
    assertEquals("provider", attr(record, "participant.role"));
    assertEquals("asset-1", attr(record, "asset.id"));
    assertEquals(CONSUMER, attr(record, "participant.counterparty.id"));
  }

  @Test
  void excludedScopesAreNotAudited() {
    for (var scope : Set.of("catalog", "policy.monitor", "request.catalog")) {
      assertTrue(validator.apply(Policy.Builder.newInstance().build(), context(scope)));
    }
    assertTrue(exporter.getFinishedLogRecordItems().isEmpty());

    // "request.*" is a prefix match on "request.", not on "request"
    validator.apply(Policy.Builder.newInstance().build(), context("requests"));
    assertEquals("requests", attr(single(), "policy.scope"));
  }

  @Test
  void auditFailureNeverChangesTheEvaluationResult() {
    var failingLogger = mock(Logger.class);
    when(failingLogger.logRecordBuilder()).thenThrow(new IllegalStateException("exporter down"));
    var failing =
        new PolicyEvaluationAuditValidator(
            failingLogger, monitor, Clock.systemUTC(), SELF, Set.of());

    assertTrue(failing.apply(Policy.Builder.newInstance().build(), context("transfer.process")));
    verify(monitor).warning(anyString(), any(Throwable.class));
  }

  private Logger logger() {
    return SdkLoggerProvider.builder()
        .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
        .build()
        .get("test");
  }

  private static ParticipantAgent agent(String identity) {
    return new ParticipantAgent(identity, Map.of(), Map.of());
  }

  private static PolicyContext context(String scope) {
    var context = mock(PolicyContext.class);
    when(context.scope()).thenReturn(scope);
    return context;
  }

  private LogRecordData single() {
    var records = exporter.getFinishedLogRecordItems();
    assertEquals(1, records.size());
    return records.get(0);
  }

  private static String attr(LogRecordData record, String key) {
    return record.getAttributes().get(AttributeKey.stringKey(key));
  }
}
