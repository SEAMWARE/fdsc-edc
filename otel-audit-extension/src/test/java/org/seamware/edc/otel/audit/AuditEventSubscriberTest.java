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
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationRequested;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationTerminated;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractOffer;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessProvisioningRequested;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessTerminated;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.event.Event;
import org.eclipse.edc.spi.event.EventEnvelope;
import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AuditEventSubscriberTest {

  private static final long EVENT_AT = 1_700_000_000_000L;

  private InMemoryLogRecordExporter exporter;
  private AuditEventSubscriber subscriber;
  private Monitor monitor;

  @BeforeEach
  void setUp() {
    exporter = InMemoryLogRecordExporter.create();
    var loggerProvider =
        SdkLoggerProvider.builder()
            .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
            .build();
    monitor = mock(Monitor.class);
    subscriber =
        new AuditEventSubscriber(loggerProvider.get("test"), monitor, Clock.systemUTC(), 100);
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
    assertEquals(
        "contract.negotiation.agreed id=cn-1 REQUESTED->AGREED", agreed.getBody().asString());
    assertEquals("contract_negotiation", attr(agreed, AuditEventSubscriber.ATTR_DOMAIN));
    assertEquals("cn-1", attr(agreed, AuditEventSubscriber.ATTR_ENTITY_ID));
    assertEquals("REQUESTED", attr(agreed, AuditEventSubscriber.ATTR_STATE_PREVIOUS));
    assertEquals("AGREED", attr(agreed, AuditEventSubscriber.ATTR_STATE_NEW));
    assertEquals("provider", attr(agreed, AuditEventSubscriber.ATTR_COUNTERPARTY_ID));
    assertEquals("offer-1", attr(agreed, AuditEventSubscriber.ATTR_OFFER_ID));
    assertEquals("asset-1", attr(agreed, AuditEventSubscriber.ATTR_ASSET_ID));
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
            TransferProcessProvisioningRequested.Builder.newInstance()
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
                .reason("boom")
                .build()));

    var records = exporter.getFinishedLogRecordItems();
    assertEquals(
        "PROVISIONING_REQUESTED", attr(records.get(0), AuditEventSubscriber.ATTR_STATE_NEW));

    var terminated = records.get(1);
    assertEquals(Severity.WARN, terminated.getSeverity());
    assertEquals("transfer_process", attr(terminated, AuditEventSubscriber.ATTR_DOMAIN));
    assertEquals(
        "PROVISIONING_REQUESTED", attr(terminated, AuditEventSubscriber.ATTR_STATE_PREVIOUS));
    assertEquals("TERMINATED", attr(terminated, AuditEventSubscriber.ATTR_STATE_NEW));
    assertEquals("agreement-1", attr(terminated, AuditEventSubscriber.ATTR_CONTRACT_ID));
    assertEquals("PROVIDER", attr(terminated, AuditEventSubscriber.ATTR_TRANSFER_TYPE));
    assertEquals("boom", attr(terminated, AuditEventSubscriber.ATTR_REASON));
  }

  @Test
  void loggerFailuresDoNotPropagate() {
    var failingLogger = mock(Logger.class);
    when(failingLogger.logRecordBuilder()).thenThrow(new IllegalStateException("exporter down"));
    var failingSubscriber =
        new AuditEventSubscriber(failingLogger, monitor, Clock.systemUTC(), 100);

    failingSubscriber.on(
        envelope(negotiation(ContractNegotiationRequested.Builder.newInstance(), null)));

    verify(monitor).warning(anyString(), any(Throwable.class));
  }

  private static <B extends ContractNegotiationEvent.Builder<?, B>>
      ContractNegotiationEvent negotiation(B builder, ContractOffer offer) {
    builder
        .contractNegotiationId("cn-1")
        .counterPartyId("provider")
        .counterPartyAddress("http://provider/protocol")
        .protocol("dataspace-protocol-http");
    if (offer != null) {
      builder.contractOffers(List.of(offer));
    }
    return builder.build();
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
