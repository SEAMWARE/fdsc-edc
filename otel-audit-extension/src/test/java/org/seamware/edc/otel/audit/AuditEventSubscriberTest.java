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
import java.util.List;
import java.util.UUID;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationAgreed;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationEvent;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationFinalized;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationRequested;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationTerminated;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractOffer;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessEvent;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessRequested;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessStarted;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessTerminated;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.event.Event;
import org.eclipse.edc.spi.event.EventEnvelope;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AuditEventSubscriberTest {

  private static final long EVENT_AT = 1_700_000_000_000L;
  // not a DID on purpose: participant ids are emitted as is
  private static final String SELF = "urn:connector:fdsc-edc";

  private InMemoryLogRecordExporter exporter;
  private AuditEventSubscriber subscriber;
  private Monitor monitor;
  private SdkLoggerProvider loggerProvider;

  @BeforeEach
  void setUp() {
    exporter = InMemoryLogRecordExporter.create();
    loggerProvider =
        SdkLoggerProvider.builder()
            .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
            .build();
    monitor = mock(Monitor.class);
    subscriber =
        new AuditEventSubscriber(
            loggerProvider.get("test"),
            monitor,
            Clock.systemUTC(),
            100,
            SELF,
            new AgreementPartiesCache(100));
  }

  @Test
  void negotiationEventsAreAuditedWithPreviousState() {
    var offer =
        ContractOffer.Builder.newInstance()
            .id("offer-1")
            .assetId("asset-1")
            .policy(Policy.Builder.newInstance().build())
            .build();

    subscriber.on(envelope(negotiation(ContractNegotiationRequested.Builder.newInstance(), offer)));
    subscriber.on(envelope(negotiation(ContractNegotiationAgreed.Builder.newInstance(), offer)));

    var records = exporter.getFinishedLogRecordItems();
    assertEquals(2, records.size());
    assertNull(attr(records.get(0), AuditEventSubscriber.ATTR_STATE_PREVIOUS));

    var agreed = records.get(1);
    assertEquals(Severity.INFO, agreed.getSeverity());
    assertEquals(EVENT_AT * 1_000_000, agreed.getTimestampEpochNanos());
    assertEquals("Audit: ContractNegotiationAgreed", agreed.getBodyValue().asString());
    assertEquals("ContractNegotiationAgreed", attr(agreed, AuditEventSubscriber.ATTR_EVENT_TYPE));
    assertEquals("contract_negotiation", attr(agreed, AuditEventSubscriber.ATTR_DOMAIN));
    assertEquals("cn-1", attr(agreed, AuditEventSubscriber.ATTR_NEGOTIATION_ID));
    assertEquals("REQUESTED", attr(agreed, AuditEventSubscriber.ATTR_STATE_PREVIOUS));
    assertEquals("AGREED", attr(agreed, AuditEventSubscriber.ATTR_STATE_NEW));
    assertEquals("provider", attr(agreed, AuditEventSubscriber.ATTR_COUNTERPARTY_ID));
    assertEquals(
        "http://provider/protocol", attr(agreed, AuditEventSubscriber.ATTR_COUNTERPARTY_ADDRESS));
    assertEquals("dataspace-protocol-http", attr(agreed, AuditEventSubscriber.ATTR_PROTOCOL));
    assertEquals("offer-1", attr(agreed, "contract.offer.id"));
    assertNull(attr(agreed, "offer.id"));
    assertEquals("asset-1", attr(agreed, AuditEventSubscriber.ATTR_ASSET_ID));
    assertEquals("ctx-1", attr(agreed, "participant.context.id"));
    assertEquals(SELF, attr(agreed, "participant.id"));
    // the offer has no assigner, so the role and the parties cannot be derived
    assertNull(attr(agreed, "participant.role"));
    assertNull(attr(agreed, AuditEventSubscriber.ATTR_PROVIDER));
    assertNull(attr(agreed, AuditEventSubscriber.ATTR_CONSUMER));
  }

  @Test
  void negotiationRoleIsProviderWhenThisConnectorAssignsTheOffer() {
    subscriber.on(
        envelope(
            negotiation(
                ContractNegotiationRequested.Builder.newInstance(),
                offerAssignedBy(SELF),
                "did:web:consumer")));

    var requested = exporter.getFinishedLogRecordItems().get(0);
    assertEquals("provider", attr(requested, "participant.role"));
    assertEquals(SELF, attr(requested, AuditEventSubscriber.ATTR_PROVIDER));
    assertEquals("did:web:consumer", attr(requested, AuditEventSubscriber.ATTR_CONSUMER));
  }

  @Test
  void negotiationRoleIsConsumerWhenTheCounterpartyAssignsTheOffer() {
    subscriber.on(
        envelope(
            negotiation(
                ContractNegotiationRequested.Builder.newInstance(),
                offerAssignedBy("did:web:provider"),
                "did:web:provider")));

    var requested = exporter.getFinishedLogRecordItems().get(0);
    assertEquals("consumer", attr(requested, "participant.role"));
    assertEquals("did:web:provider", attr(requested, AuditEventSubscriber.ATTR_PROVIDER));
    assertEquals(SELF, attr(requested, AuditEventSubscriber.ATTR_CONSUMER));
  }

  @Test
  void negotiationRoleIsOmittedWhenTheAssignerIsNeitherParty() {
    subscriber.on(
        envelope(
            negotiation(
                ContractNegotiationRequested.Builder.newInstance(),
                offerAssignedBy("did:web:third"),
                "did:web:provider")));

    var requested = exporter.getFinishedLogRecordItems().get(0);
    assertNull(attr(requested, "participant.role"));
    assertNull(attr(requested, AuditEventSubscriber.ATTR_PROVIDER));
    assertNull(attr(requested, AuditEventSubscriber.ATTR_CONSUMER));
  }

  @Test
  void withoutOwnIdOnlyTheCounterpartySideIsEmitted() {
    var anonymous =
        new AuditEventSubscriber(
            SdkLoggerProvider.builder()
                .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
                .build()
                .get("test"),
            monitor,
            Clock.systemUTC(),
            100,
            null,
            new AgreementPartiesCache(100));

    anonymous.on(
        envelope(
            negotiation(
                ContractNegotiationRequested.Builder.newInstance(),
                offerAssignedBy("did:web:provider"),
                "did:web:provider")));

    var requested = exporter.getFinishedLogRecordItems().get(0);
    assertNull(attr(requested, "participant.id"));
    assertEquals("consumer", attr(requested, "participant.role"));
    assertEquals("did:web:provider", attr(requested, AuditEventSubscriber.ATTR_PROVIDER));
    assertNull(attr(requested, AuditEventSubscriber.ATTR_CONSUMER));
  }

  @Test
  void finalizedNegotiationCarriesTheAgreement() {
    var agreement =
        ContractAgreement.Builder.newInstance()
            .id("agreement-1")
            .agreementId("dsp-agreement-1")
            .providerId("provider")
            .consumerId("consumer")
            .assetId("asset-1")
            .contractSigningDate(1_700_000_000L)
            .policy(Policy.Builder.newInstance().build())
            .build();
    var builder = ContractNegotiationFinalized.Builder.newInstance().contractAgreement(agreement);

    subscriber.on(envelope(negotiation(builder, null)));

    var finalized = exporter.getFinishedLogRecordItems().get(0);
    assertEquals("agreement-1", attr(finalized, AuditEventSubscriber.ATTR_CONTRACT_ID));
    assertEquals("dsp-agreement-1", attr(finalized, AuditEventSubscriber.ATTR_AGREEMENT_ID));
    assertEquals("provider", attr(finalized, AuditEventSubscriber.ATTR_PROVIDER));
    assertEquals("consumer", attr(finalized, AuditEventSubscriber.ATTR_CONSUMER));
    assertEquals("asset-1", attr(finalized, AuditEventSubscriber.ATTR_ASSET_ID));
    assertEquals(
        1_700_000_000L,
        finalized
            .getAttributes()
            .get(AttributeKey.longKey(AuditEventSubscriber.ATTR_SIGNING_DATE)));
    // this connector is neither party of the agreement
    assertNull(attr(finalized, "participant.role"));
  }

  @Test
  void finalizedNegotiationRoleComesFromTheAgreement() {
    var agreement =
        ContractAgreement.Builder.newInstance()
            .id("agreement-1")
            .providerId("did:web:provider")
            .consumerId(SELF)
            .assetId("asset-1")
            .contractSigningDate(1_700_000_000L)
            .policy(Policy.Builder.newInstance().build())
            .build();
    var builder = ContractNegotiationFinalized.Builder.newInstance().contractAgreement(agreement);

    subscriber.on(envelope(negotiation(builder, null)));

    var finalized = exporter.getFinishedLogRecordItems().get(0);
    assertEquals("consumer", attr(finalized, "participant.role"));
    assertEquals("did:web:provider", attr(finalized, AuditEventSubscriber.ATTR_PROVIDER));
    assertEquals(SELF, attr(finalized, AuditEventSubscriber.ATTR_CONSUMER));
  }

  @Test
  void stateIsForgottenAfterFinalState() {
    subscriber.on(envelope(negotiation(ContractNegotiationTerminated.Builder.newInstance(), null)));
    subscriber.on(envelope(negotiation(ContractNegotiationAgreed.Builder.newInstance(), null)));

    var records = exporter.getFinishedLogRecordItems();
    assertNull(attr(records.get(1), AuditEventSubscriber.ATTR_STATE_PREVIOUS));
  }

  @Test
  void transferTerminationIsAuditedAsWarningWithReason() {
    subscriber.on(
        envelope(
            TransferProcessRequested.Builder.newInstance()
                .transferProcessId("tp-1")
                .assetId("asset-1")
                .contractId("agreement-1")
                .type("PROVIDER")
                .build()));
    subscriber.on(
        envelope(
            TransferProcessTerminated.Builder.newInstance()
                .transferProcessId("tp-1")
                .assetId("asset-1")
                .contractId("agreement-1")
                .type("PROVIDER")
                .protocol("dataspace-protocol-http")
                .participantContextId("ctx-1")
                .reason("boom")
                .build()));

    var records = exporter.getFinishedLogRecordItems();
    assertEquals("REQUESTED", attr(records.get(0), AuditEventSubscriber.ATTR_STATE_NEW));

    var terminated = records.get(1);
    assertEquals(Severity.WARN, terminated.getSeverity());
    assertEquals("Audit: TransferProcessTerminated", terminated.getBodyValue().asString());
    assertEquals("transfer_process", attr(terminated, AuditEventSubscriber.ATTR_DOMAIN));
    assertEquals("tp-1", attr(terminated, AuditEventSubscriber.ATTR_TRANSFER_ID));
    assertEquals("REQUESTED", attr(terminated, AuditEventSubscriber.ATTR_STATE_PREVIOUS));
    assertEquals("TERMINATED", attr(terminated, AuditEventSubscriber.ATTR_STATE_NEW));
    assertEquals("asset-1", attr(terminated, AuditEventSubscriber.ATTR_ASSET_ID));
    assertEquals("agreement-1", attr(terminated, AuditEventSubscriber.ATTR_CONTRACT_ID));
    assertEquals("PROVIDER", attr(terminated, AuditEventSubscriber.ATTR_TRANSFER_TYPE));
    assertEquals("boom", attr(terminated, AuditEventSubscriber.ATTR_TRANSFER_REASON));
    assertEquals("dataspace-protocol-http", attr(terminated, AuditEventSubscriber.ATTR_PROTOCOL));
    assertEquals("ctx-1", attr(terminated, "participant.context.id"));
    // attributes not carried by the event are omitted, not emitted empty
    assertNull(attr(records.get(0), AuditEventSubscriber.ATTR_PROTOCOL));
    assertNull(attr(records.get(0), "participant.context.id"));
    assertEquals("provider", attr(terminated, "participant.role"));
    assertEquals(SELF, attr(terminated, "participant.id"));
  }

  @Test
  void transferRoleComesFromTheProcessType() {
    subscriber.on(
        envelope(
            TransferProcessRequested.Builder.newInstance()
                .transferProcessId("tp-1")
                .type("CONSUMER")
                .build()));
    subscriber.on(
        envelope(
            TransferProcessRequested.Builder.newInstance()
                .transferProcessId("tp-2")
                .type("SOMETHING_ELSE")
                .build()));

    var records = exporter.getFinishedLogRecordItems();
    assertEquals("consumer", attr(records.get(0), "participant.role"));
    assertNull(attr(records.get(1), "participant.role"));
  }

  @Test
  void startedTransferCarriesTheDestinationType() {
    subscriber.on(
        envelope(
            TransferProcessStarted.Builder.newInstance()
                .transferProcessId("rp-1")
                .dataAddress(DataAddress.Builder.newInstance().type("HttpData").build())
                .build()));
    subscriber.on(
        envelope(TransferProcessStarted.Builder.newInstance().transferProcessId("tp-2").build()));

    var records = exporter.getFinishedLogRecordItems();
    assertEquals("HttpData", attr(records.get(0), AuditEventSubscriber.ATTR_DESTINATION_TYPE));
    assertEquals(
        AuditEventSubscriber.UNKNOWN_TRANSFER_TYPE,
        attr(records.get(0), AuditEventSubscriber.ATTR_TRANSFER_TYPE));
    assertEquals(
        AuditEventSubscriber.UNSPECIFIED_DESTINATION,
        attr(records.get(1), AuditEventSubscriber.ATTR_DESTINATION_TYPE));
  }

  @Test
  void transferCarriesTheSharedAgreementIdAndPartiesOfAFinalizedNegotiation() {
    subscriber.on(envelope(finalizedNegotiation("agreement-1", "dsp-agreement-1")));
    subscriber.on(envelope(transfer("tp-1", "agreement-1")));

    var transfer = exporter.getFinishedLogRecordItems().get(1);
    assertEquals("agreement-1", attr(transfer, AuditEventSubscriber.ATTR_CONTRACT_ID));
    assertEquals("dsp-agreement-1", attr(transfer, AuditEventSubscriber.ATTR_AGREEMENT_ID));
    assertEquals("did:web:provider", attr(transfer, AuditEventSubscriber.ATTR_PROVIDER));
    assertEquals(SELF, attr(transfer, AuditEventSubscriber.ATTR_CONSUMER));
    // the role still comes from the process type, not from the cached agreement
    assertEquals("provider", attr(transfer, "participant.role"));
  }

  @Test
  void transferOfAnUnknownAgreementOmitsTheCachedAttributes() {
    // e.g. the negotiation was finalized before a restart, or by another replica
    subscriber.on(envelope(finalizedNegotiation("agreement-1", "dsp-agreement-1")));
    subscriber.on(envelope(transfer("tp-1", "agreement-2")));

    var transfer = exporter.getFinishedLogRecordItems().get(1);
    assertEquals("agreement-2", attr(transfer, AuditEventSubscriber.ATTR_CONTRACT_ID));
    assertNull(attr(transfer, AuditEventSubscriber.ATTR_AGREEMENT_ID));
    assertNull(attr(transfer, AuditEventSubscriber.ATTR_PROVIDER));
    assertNull(attr(transfer, AuditEventSubscriber.ATTR_CONSUMER));
  }

  @Test
  void evictedAgreementsAreNoLongerAddedToTransfers() {
    var smallCache =
        new AuditEventSubscriber(
            loggerProvider.get("test"),
            monitor,
            Clock.systemUTC(),
            100,
            SELF,
            new AgreementPartiesCache(1));

    smallCache.on(envelope(finalizedNegotiation("agreement-1", "dsp-agreement-1")));
    smallCache.on(envelope(finalizedNegotiation("agreement-2", "dsp-agreement-2")));
    smallCache.on(envelope(transfer("tp-1", "agreement-1")));
    smallCache.on(envelope(transfer("tp-2", "agreement-2")));

    var records = exporter.getFinishedLogRecordItems();
    assertNull(attr(records.get(2), AuditEventSubscriber.ATTR_AGREEMENT_ID));
    assertEquals("dsp-agreement-2", attr(records.get(3), AuditEventSubscriber.ATTR_AGREEMENT_ID));
  }

  @Test
  void loggerFailuresDoNotPropagate() {
    var failingLogger = mock(Logger.class);
    when(failingLogger.logRecordBuilder()).thenThrow(new IllegalStateException("exporter down"));
    var failingSubscriber =
        new AuditEventSubscriber(
            failingLogger, monitor, Clock.systemUTC(), 100, SELF, new AgreementPartiesCache(100));

    failingSubscriber.on(
        envelope(negotiation(ContractNegotiationRequested.Builder.newInstance(), null)));

    verify(monitor).warning(anyString(), any(Throwable.class));
  }

  private static <B extends ContractNegotiationEvent.Builder<?, B>>
      ContractNegotiationEvent negotiation(B builder, ContractOffer offer) {
    return negotiation(builder, offer, "provider");
  }

  private static <B extends ContractNegotiationEvent.Builder<?, B>>
      ContractNegotiationEvent negotiation(B builder, ContractOffer offer, String counterPartyId) {
    builder
        .contractNegotiationId("cn-1")
        .counterPartyId(counterPartyId)
        .counterPartyAddress("http://provider/protocol")
        .protocol("dataspace-protocol-http")
        .participantContextId("ctx-1");
    if (offer != null) {
      builder.contractOffers(List.of(offer));
    }
    return builder.build();
  }

  private static ContractNegotiationEvent finalizedNegotiation(
      String localAgreementId, String sharedAgreementId) {
    var agreement =
        ContractAgreement.Builder.newInstance()
            .id(localAgreementId)
            .agreementId(sharedAgreementId)
            .providerId("did:web:provider")
            .consumerId(SELF)
            .assetId("asset-1")
            .policy(Policy.Builder.newInstance().build())
            .build();
    return ContractNegotiationFinalized.Builder.newInstance()
        .contractNegotiationId("cn-" + localAgreementId)
        .counterPartyId("did:web:provider")
        .counterPartyAddress("http://provider/protocol")
        .protocol("dataspace-protocol-http")
        .contractAgreement(agreement)
        .build();
  }

  private static TransferProcessEvent transfer(String transferId, String localAgreementId) {
    return TransferProcessRequested.Builder.newInstance()
        .transferProcessId(transferId)
        .contractId(localAgreementId)
        .type("PROVIDER")
        .build();
  }

  private static ContractOffer offerAssignedBy(String assigner) {
    return ContractOffer.Builder.newInstance()
        .id("offer-1")
        .assetId("asset-1")
        .policy(Policy.Builder.newInstance().assigner(assigner).build())
        .build();
  }

  private static <E extends Event> EventEnvelope<E> envelope(E event) {
    return EventEnvelope.Builder.newInstance()
        .id(UUID.randomUUID().toString())
        .at(EVENT_AT)
        .payload(event)
        .build();
  }

  private static String attr(LogRecordData record, String key) {
    return record.getAttributes().get(AttributeKey.stringKey(key));
  }
}
