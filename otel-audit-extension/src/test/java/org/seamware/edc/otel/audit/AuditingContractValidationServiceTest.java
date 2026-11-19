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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import java.time.Clock;
import java.util.Map;
import org.eclipse.edc.connector.controlplane.contract.spi.ContractOfferId;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractDefinition;
import org.eclipse.edc.connector.controlplane.contract.spi.validation.ContractValidationService;
import org.eclipse.edc.connector.controlplane.contract.spi.validation.ValidatableConsumerOffer;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AuditingContractValidationServiceTest {

  // not a DID on purpose: participant ids are emitted as is
  private static final String SELF = "urn:connector:fdsc-edc";
  private static final String CONSUMER = "did:web:consumer";
  private static final String PROVIDER = "did:web:provider";
  // create() adds a random part, so the same instance is used for the offer and the assertion
  private static final ContractOfferId OFFER_ID = ContractOfferId.create("definition-1", "asset-1");

  private InMemoryLogRecordExporter exporter;
  private Monitor monitor;
  private ContractValidationService delegate;
  private AuditingContractValidationService service;

  @BeforeEach
  void setUp() {
    exporter = InMemoryLogRecordExporter.create();
    monitor = mock(Monitor.class);
    delegate = mock(ContractValidationService.class);
    var logger =
        SdkLoggerProvider.builder()
            .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
            .build()
            .get("test");
    service =
        new AuditingContractValidationService(
            delegate, new PolicyDenyAuditor(logger, monitor, Clock.systemUTC(), SELF), monitor);
  }

  @Test
  void permittedRequestsAreReturnedUnchangedWithoutRecord() {
    var agent = agent(CONSUMER);
    var offer = offer();
    Result<Void> success = Result.success();
    when(delegate.validateInitialOffer(agent, offer)).thenReturn(success);

    assertSame(success, service.validateInitialOffer(agent, offer));
    assertTrue(exporter.getFinishedLogRecordItems().isEmpty());
  }

  @Test
  void initialOfferDeniedByAnEdcRuleIsAuditedWithItsScope() {
    var agent = agent(CONSUMER);
    var offer = offer();
    var failure =
        "Policy in scope contract.negotiation not fulfilled: [Permission constraint not met]";
    Result<Void> denied = Result.failure(failure);
    when(delegate.validateInitialOffer(agent, offer)).thenReturn(denied);

    assertSame(denied, service.validateInitialOffer(agent, offer));

    var record = single();
    assertEquals(Severity.WARN, record.getSeverity());
    assertEquals("Audit: ContractNegotiationRejected", record.getBodyValue().asString());
    assertEquals("ContractNegotiationRejected", attr(record, "event.type"));
    assertEquals("deny", attr(record, "policy.decision"));
    assertEquals("contract.negotiation", attr(record, "policy.scope"));
    assertEquals(failure, attr(record, "policy.deny.reason"));
    assertEquals("provider", attr(record, "participant.role"));
    assertEquals(SELF, attr(record, "participant.id"));
    assertEquals(SELF, attr(record, "participant.provider"));
    assertEquals(CONSUMER, attr(record, "participant.consumer"));
    assertEquals(CONSUMER, attr(record, "participant.counterparty.id"));
    assertEquals(OFFER_ID.toString(), attr(record, "contract.offer.id"));
    assertEquals("asset-1", attr(record, "asset.id"));
  }

  @Test
  void initialOfferDeniedByOdrlPapKeepsTheExplanationWithoutScope() {
    var agent = agent(CONSUMER);
    var offer = offer();
    var failure = "OdrlPapPolicyValidator: PAP denied - missing MembershipCredential";
    when(delegate.validateInitialOffer(agent, offer)).thenReturn(Result.failure(failure));

    service.validateInitialOffer(agent, offer);

    var record = single();
    assertEquals(failure, attr(record, "policy.deny.reason"));
    // a pre-validator denial does not name the scope, and the call covers two scopes
    assertNull(attr(record, "policy.scope"));
  }

  @Test
  void deniedTransferIsAuditedInTheTransferScope() {
    var agent = agent(CONSUMER);
    var agreement = agreement();
    var failure =
        "Policy does not fulfill the agreement agreement-1, policy evaluation"
            + " OdrlPapPolicyValidator: PAP denied - outside business hours";
    Result<ContractAgreement> denied = Result.failure(failure);
    when(delegate.validateAgreement(agent, agreement)).thenReturn(denied);

    assertSame(denied, service.validateAgreement(agent, agreement));

    var record = single();
    assertEquals("TransferProcessRejected", attr(record, "event.type"));
    assertEquals("transfer.process", attr(record, "policy.scope"));
    assertEquals("provider", attr(record, "participant.role"));
    assertEquals("agreement-1", attr(record, "contract.id"));
    assertEquals("dsp-agreement-1", attr(record, "contract.agreement.id"));
    assertEquals(SELF, attr(record, "participant.provider"));
    assertEquals(CONSUMER, attr(record, "participant.consumer"));
  }

  @Test
  void transferFromAPartyOutsideTheAgreementIsAudited() {
    var agent = agent("did:web:intruder");
    var agreement = agreement();
    when(delegate.validateRequest(agent, agreement))
        .thenReturn(Result.failure("Invalid counter-party identity"));

    service.validateRequest(agent, agreement);

    var record = single();
    assertEquals("TransferProcessRejected", attr(record, "event.type"));
    assertEquals("provider", attr(record, "participant.role"));
    assertEquals("did:web:intruder", attr(record, "participant.counterparty.id"));
    assertNull(attr(record, "policy.scope"));
  }

  @Test
  void rejectedNegotiationMessageOnTheProviderCarriesTheConsumerPid() {
    var agent = agent("did:web:intruder");
    var negotiation = negotiation(ContractNegotiation.Type.PROVIDER, "consumer-pid-1");
    when(delegate.validateRequest(agent, negotiation))
        .thenReturn(Result.failure("Invalid counter-party identity"));

    service.validateRequest(agent, negotiation);

    var record = single();
    assertEquals("ContractNegotiationRejected", attr(record, "event.type"));
    assertEquals("cn-1", attr(record, "negotiation.id"));
    assertEquals("consumer-pid-1", attr(record, "negotiation.correlation.id"));
    assertEquals("provider", attr(record, "participant.role"));
  }

  @Test
  void rejectedNegotiationMessageOnTheConsumerCarriesTheProviderPid() {
    var agent = agent("did:web:intruder");
    var negotiation = negotiation(ContractNegotiation.Type.CONSUMER, "provider-pid-1");
    when(delegate.validateRequest(agent, negotiation))
        .thenReturn(Result.failure("Invalid counter-party identity"));

    service.validateRequest(agent, negotiation);

    var record = single();
    assertEquals("provider-pid-1", attr(record, "negotiation.correlation.id"));
    assertEquals("consumer", attr(record, "participant.role"));
  }

  @Test
  void rejectedAgreementConfirmationIsAuditedAsConsumer() {
    var agent = agent(PROVIDER);
    var agreement = agreement();
    when(delegate.validateConfirmed(agent, agreement, null))
        .thenReturn(Result.failure("Policy in the contract agreement is not equal"));

    service.validateConfirmed(agent, agreement, null);

    var record = single();
    assertEquals("ContractNegotiationRejected", attr(record, "event.type"));
    assertEquals("consumer", attr(record, "participant.role"));
    assertEquals(PROVIDER, attr(record, "participant.counterparty.id"));
  }

  @Test
  void longDenyReasonIsTruncated() {
    var agent = agent(CONSUMER);
    var offer = offer();
    when(delegate.validateInitialOffer(agent, offer))
        .thenReturn(Result.failure("x".repeat(PolicyDenyAuditor.MAX_REASON_LENGTH + 10)));

    service.validateInitialOffer(agent, offer);

    assertEquals(
        PolicyDenyAuditor.MAX_REASON_LENGTH, attr(single(), "policy.deny.reason").length());
  }

  @Test
  void auditFailureDoesNotChangeTheDspResult() {
    var auditor = mock(PolicyDenyAuditor.class);
    doThrow(new IllegalStateException("exporter down"))
        .when(auditor)
        .initialOfferRejected(any(), any(), any());
    var guarded = new AuditingContractValidationService(delegate, auditor, monitor);
    var agent = agent(CONSUMER);
    var offer = offer();
    Result<Void> denied = Result.failure("denied");
    when(delegate.validateInitialOffer(agent, offer)).thenReturn(denied);

    assertSame(denied, guarded.validateInitialOffer(agent, offer));
    verify(monitor).warning(anyString(), any(Throwable.class));
  }

  private static ParticipantAgent agent(String identity) {
    return new ParticipantAgent(identity, Map.of(), Map.of());
  }

  private static ValidatableConsumerOffer offer() {
    return ValidatableConsumerOffer.Builder.newInstance()
        .offerId(OFFER_ID)
        .contractDefinition(
            ContractDefinition.Builder.newInstance()
                .id("definition-1")
                .accessPolicyId("access-policy-1")
                .contractPolicyId("contract-policy-1")
                .build())
        .accessPolicy(Policy.Builder.newInstance().build())
        .contractPolicy(Policy.Builder.newInstance().build())
        .build();
  }

  private static ContractAgreement agreement() {
    return ContractAgreement.Builder.newInstance()
        .id("agreement-1")
        .agreementId("dsp-agreement-1")
        .providerId(SELF)
        .consumerId(CONSUMER)
        .assetId("asset-1")
        .policy(Policy.Builder.newInstance().build())
        .build();
  }

  private static ContractNegotiation negotiation(ContractNegotiation.Type type, String pid) {
    return ContractNegotiation.Builder.newInstance()
        .id("cn-1")
        .type(type)
        .correlationId(pid)
        .counterPartyId(CONSUMER)
        .counterPartyAddress("http://counterparty/protocol")
        .protocol("dataspace-protocol-http")
        .build();
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
