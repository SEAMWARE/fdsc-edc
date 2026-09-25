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
package org.seamware.edc.otel.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.seamware.edc.otel.metrics.BusinessMetricsEventSubscriber.ATTR_REASON;
import static org.seamware.edc.otel.metrics.BusinessMetricsEventSubscriber.ATTR_STATE;
import static org.seamware.edc.otel.metrics.BusinessMetricsEventSubscriber.ATTR_TRANSFER_TYPE;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationAgreed;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationEvent;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationFinalized;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationInitiated;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationRequested;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessDeprovisioned;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessEvent;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessInitiated;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessSuspended;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessTerminated;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.event.Event;
import org.eclipse.edc.spi.event.EventEnvelope;
import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BusinessMetricsEventSubscriberTest {

  private static final long START = 1_700_000_000_000L;

  private InMemoryMetricReader reader;
  private BusinessMetricsEventSubscriber subscriber;
  private Monitor monitor;

  @BeforeEach
  void setUp() {
    reader = InMemoryMetricReader.create();
    var meterProvider = SdkMeterProvider.builder().registerMetricReader(reader).build();
    monitor = mock(Monitor.class);
    subscriber = new BusinessMetricsEventSubscriber(meterProvider.get("test"), monitor, 100, 3);
  }

  @Test
  void negotiationTransitionsAreCountedAndDurationRecorded() {
    subscriber.on(
        envelope(negotiation("cn-1", ContractNegotiationInitiated.Builder.newInstance()), START));
    subscriber.on(
        envelope(
            negotiation("cn-1", ContractNegotiationRequested.Builder.newInstance()), START + 500));
    subscriber.on(
        envelope(
            negotiation("cn-1", ContractNegotiationAgreed.Builder.newInstance()), START + 1_000));
    subscriber.on(envelope(negotiation("cn-1", finalized()), START + 2_500));

    var transitions = BusinessMetricsEventSubscriber.METRIC_NEGOTIATION_TRANSITIONS;
    assertEquals(1, counter(transitions, Attributes.of(ATTR_STATE, "INITIATED")));
    assertEquals(1, counter(transitions, Attributes.of(ATTR_STATE, "REQUESTED")));
    assertEquals(1, counter(transitions, Attributes.of(ATTR_STATE, "FINALIZED")));

    var duration =
        histogram(
                BusinessMetricsEventSubscriber.METRIC_NEGOTIATION_DURATION,
                Attributes.of(ATTR_STATE, "FINALIZED"))
            .orElseThrow();
    assertEquals(1, duration.getCount());
    assertEquals(2.5, duration.getSum(), 1e-9);
  }

  @Test
  void durationIsNotRecordedWhenInitialStateWasNotObserved() {
    // e.g. connector restarted while the negotiation was in flight
    subscriber.on(
        envelope(negotiation("cn-2", ContractNegotiationAgreed.Builder.newInstance()), START));
    subscriber.on(envelope(negotiation("cn-2", finalized()), START + 1_000));

    assertTrue(
        histogram(
                BusinessMetricsEventSubscriber.METRIC_NEGOTIATION_DURATION,
                Attributes.of(ATTR_STATE, "FINALIZED"))
            .isEmpty());
    assertEquals(
        1,
        counter(
            BusinessMetricsEventSubscriber.METRIC_NEGOTIATION_TRANSITIONS,
            Attributes.of(ATTR_STATE, "FINALIZED")));
  }

  @Test
  void transferInterruptionsAreCountedByReasonAndDurationRecorded() {
    subscriber.on(
        envelope(transfer("tp-1", TransferProcessInitiated.Builder.newInstance()), START));
    subscriber.on(
        envelope(
            transfer("tp-1", TransferProcessSuspended.Builder.newInstance().reason("paused")),
            START + 1_000));
    subscriber.on(
        envelope(
            transfer("tp-1", TransferProcessTerminated.Builder.newInstance().reason("boom")),
            START + 2_000));
    subscriber.on(
        envelope(
            transfer("tp-1", TransferProcessDeprovisioned.Builder.newInstance()), START + 5_000));

    var interruptions = BusinessMetricsEventSubscriber.METRIC_TRANSFER_INTERRUPTIONS;
    assertEquals(1, counter(interruptions, interruption("SUSPENDED", "paused")));
    assertEquals(1, counter(interruptions, interruption("TERMINATED", "boom")));
    assertEquals(
        1,
        counter(
            BusinessMetricsEventSubscriber.METRIC_TRANSFER_TRANSITIONS,
            Attributes.of(ATTR_STATE, "DEPROVISIONED", ATTR_TRANSFER_TYPE, "PROVIDER")));

    var duration =
        histogram(
                BusinessMetricsEventSubscriber.METRIC_TRANSFER_DURATION,
                Attributes.of(ATTR_TRANSFER_TYPE, "PROVIDER"))
            .orElseThrow();
    assertEquals(1, duration.getCount());
    assertEquals(5.0, duration.getSum(), 1e-9);
  }

  @Test
  void reasonCardinalityIsBounded() {
    for (var i = 1; i <= 5; i++) {
      subscriber.on(
          envelope(
              transfer("tp-" + i, TransferProcessTerminated.Builder.newInstance().reason("r" + i)),
              START));
    }
    subscriber.on(
        envelope(transfer("tp-6", TransferProcessTerminated.Builder.newInstance()), START));

    var interruptions = BusinessMetricsEventSubscriber.METRIC_TRANSFER_INTERRUPTIONS;
    assertEquals(1, counter(interruptions, interruption("TERMINATED", "r1")));
    assertEquals(1, counter(interruptions, interruption("TERMINATED", "r3")));
    assertEquals(
        2,
        counter(
            interruptions,
            interruption("TERMINATED", BusinessMetricsEventSubscriber.REASON_OTHER)));
    assertEquals(
        1,
        counter(
            interruptions,
            interruption("TERMINATED", BusinessMetricsEventSubscriber.REASON_UNSPECIFIED)));
  }

  @Test
  void meterFailuresDoNotPropagate() {
    var meter = mock(Meter.class, RETURNS_DEEP_STUBS);
    var failingCounter = mock(LongCounter.class);
    when(meter.counterBuilder(anyString()).setDescription(anyString()).setUnit(anyString()).build())
        .thenReturn(failingCounter);
    doThrow(new IllegalStateException("exporter down"))
        .when(failingCounter)
        .add(anyLong(), any(Attributes.class));
    var failigSubscriber = new BusinessMetricsEventSubscriber(meter, monitor, 100, 3);

    failigSubscriber.on(
        envelope(negotiation("cn-1", ContractNegotiationRequested.Builder.newInstance()), START));

    verify(monitor).warning(anyString(), any(Throwable.class));
  }

  private static Attributes interruption(String state, String reason) {
    return Attributes.of(ATTR_STATE, state, ATTR_TRANSFER_TYPE, "PROVIDER", ATTR_REASON, reason);
  }

  private long counter(String name, Attributes attributes) {
    return metric(name)
        .map(
            m ->
                m.getLongSumData().getPoints().stream()
                    .filter(p -> p.getAttributes().equals(attributes))
                    .mapToLong(LongPointData::getValue)
                    .sum())
        .orElse(0L);
  }

  private Optional<HistogramPointData> histogram(String name, Attributes attributes) {
    return metric(name)
        .flatMap(
            m ->
                m.getHistogramData().getPoints().stream()
                    .filter(p -> p.getAttributes().equals(attributes))
                    .findFirst());
  }

  private Optional<MetricData> metric(String name) {
    return reader.collectAllMetrics().stream().filter(m -> m.getName().equals(name)).findFirst();
  }

  private static ContractNegotiationFinalized.Builder finalized() {
    return ContractNegotiationFinalized.Builder.newInstance()
        .contractAgreement(
            ContractAgreement.Builder.newInstance()
                .id("agreement-1")
                .providerId("provider")
                .consumerId("consumer")
                .assetId("asset-1")
                .policy(Policy.Builder.newInstance().build())
                .build());
  }

  private static <B extends ContractNegotiationEvent.Builder<?, B>>
      ContractNegotiationEvent negotiation(String id, B builder) {
    return builder
        .contractNegotiationId(id)
        .counterPartyId("provider")
        .counterPartyAddress("http://provider/protocol")
        .protocol("dataspace-protocol-http")
        .build();
  }

  private static <B extends TransferProcessEvent.Builder<?, B>> TransferProcessEvent transfer(
      String id, B builder) {
    return builder
        .transferProcessId(id)
        .assetId("asset-1")
        .contractId("agreement-1")
        .type("PROVIDER")
        .build();
  }

  private static <E extends Event> EventEnvelope<E> envelope(E event, long at) {
    return EventEnvelope.Builder.newInstance()
        .id(UUID.randomUUID().toString())
        .at(at)
        .payload(event)
        .build();
  }
}
