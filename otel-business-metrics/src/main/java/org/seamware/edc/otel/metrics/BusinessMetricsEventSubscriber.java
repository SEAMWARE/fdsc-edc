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

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
 * Translates contract negotiation and transfer process events into OpenTelemetry business metrics.
 * It only observes: entities are never read from or written to the stores.
 *
 * <p>Durations are measured between event timestamps and only recorded when the entity was observed
 * from its initial state, so in-flight entities after a restart do not skew them.
 */
public class BusinessMetricsEventSubscriber implements EventSubscriber {

  static final String METRIC_NEGOTIATION_TRANSITIONS = "contract_negotiation.transitions";
  static final String METRIC_NEGOTIATION_DURATION = "contract_negotiation.duration";
  static final String METRIC_TRANSFER_TRANSITIONS = "transfer_process.transitions";
  static final String METRIC_TRANSFER_DURATION = "transfer_process.duration";
  static final String METRIC_TRANSFER_INTERRUPTIONS = "transfer_process.interruptions";

  static final AttributeKey<String> ATTR_STATE = AttributeKey.stringKey("state");
  static final AttributeKey<String> ATTR_TRANSFER_TYPE = AttributeKey.stringKey("type");
  static final AttributeKey<String> ATTR_REASON = AttributeKey.stringKey("reason");

  static final String REASON_UNSPECIFIED = "unspecified";
  static final String REASON_OTHER = "other";

  private static final int MAX_REASON_LENGTH = 128;

  private static final List<Double> DURATION_BUCKETS_SECONDS =
      List.of(0.1, 0.5, 1.0, 2.5, 5.0, 10.0, 30.0, 60.0, 120.0, 300.0, 600.0, 1800.0, 3600.0);

  private final Monitor monitor;
  private final EntityLifecycleTracker negotiationTracker;
  private final EntityLifecycleTracker transferTracker;
  private final int maxDistinctReasons;
  private final Set<String> knownReasons = new HashSet<>();

  private final LongCounter negotiationTransitions;
  private final DoubleHistogram negotiationDuration;
  private final LongCounter transferTransitions;
  private final DoubleHistogram transferDuration;
  private final LongCounter transferInterruptions;

  public BusinessMetricsEventSubscriber(
      Meter meter, Monitor monitor, int maxEntries, int maxDistinctReasons) {
    this.monitor = monitor;
    this.maxDistinctReasons = maxDistinctReasons;
    this.negotiationTracker =
        new EntityLifecycleTracker(maxEntries, LifecycleStates.NEGOTIATION_FINAL_STATES);
    this.transferTracker =
        new EntityLifecycleTracker(maxEntries, LifecycleStates.TRANSFER_FINAL_STATES);

    this.negotiationTransitions =
        counter(meter, METRIC_NEGOTIATION_TRANSITIONS, "Contract negotiation state transition");
    this.negotiationDuration =
        histogram(
            meter,
            METRIC_NEGOTIATION_DURATION,
            "Contract negotiation duration until FINALIZED or TERMINATED");
    this.transferTransitions =
        counter(meter, METRIC_TRANSFER_TRANSITIONS, "Transfer process state transitions");
    this.transferDuration =
        histogram(meter, METRIC_TRANSFER_DURATION, "Transfer process duration until DEPROVISIONED");
    this.transferInterruptions =
        counter(
            meter,
            METRIC_TRANSFER_INTERRUPTIONS,
            "Transfer process terminations and suspensions by reason");
  }

  @Override
  public <E extends Event> void on(EventEnvelope<E> envelope) {
    try {
      var payload = envelope.getPayload();
      if (payload instanceof ContractNegotiationEvent negotiationEvent) {
        recordNegotiation(envelope, negotiationEvent);
      } else if (payload instanceof TransferProcessEvent transferEvent) {
        recordTransfer(envelope, transferEvent);
      }
    } catch (RuntimeException e) {
      // metrics must never break the state machine
      monitor.warning("Otel Metrics: failed to record metrics for " + envelope.getId(), e);
    }
  }

  private void recordNegotiation(EventEnvelope<?> envelope, ContractNegotiationEvent event) {
    var state = LifecycleStates.toState(event.name(), LifecycleStates.NEGOTIATION_EVENT_PREFIX);
    var transition =
        negotiationTracker.track(event.getContractNegotiationId(), state, envelope.getAt());

    var attributes = Attributes.of(ATTR_STATE, state);
    negotiationTransitions.add(1, attributes);
    recordDuration(
        negotiationDuration,
        transition,
        LifecycleStates.NEGOTIATION_INITIAL_STATES,
        envelope.getAt(),
        attributes);
  }

  private void recordTransfer(EventEnvelope<?> envelope, TransferProcessEvent event) {
    var state = LifecycleStates.toState(event.name(), LifecycleStates.TRANSFER_EVENT_PREFIX);
    var transition = transferTracker.track(event.getTransferProcessId(), state, envelope.getAt());

    transferTransitions.add(1, withType(Attributes.builder().put(ATTR_STATE, state), event));
    // the final state is always DEPROVISIONED, so only the transfer type is worth splitting by
    recordDuration(
        transferDuration,
        transition,
        LifecycleStates.TRANSFER_INITIAL_STATES,
        envelope.getAt(),
        withType(Attributes.builder(), event));

    String reason;
    if (event instanceof TransferProcessTerminated terminated) {
      reason = terminated.getReason();
    } else if (event instanceof TransferProcessSuspended suspended) {
      reason = suspended.getReason();
    } else {
      return;
    }
    var builder =
        Attributes.builder().put(ATTR_STATE, state).put(ATTR_REASON, normalizeReason(reason));
    transferInterruptions.add(1, withType(builder, event));
  }

  private static void recordDuration(
      DoubleHistogram histogram,
      EntityLifecycleTracker.Transition transition,
      Set<String> initialStates,
      long at,
      Attributes attributes) {
    // previousState == null: single-event lifecycle, nothing to measure
    if (!transition.isFinal()
        || transition.previousState() == null
        || !initialStates.contains(transition.firstState())) {
      return;
    }
    var seconds = Math.max(0, at - transition.firstSeenAt()) / 1000.0;
    histogram.record(seconds, attributes);
  }

  /**
   * EDC reasons are free text and often embed ids: cap length and the number or distinct values so
   * the metric cardinality stays bounded.
   */
  synchronized String normalizeReason(String reason) {
    if (reason == null || reason.isBlank()) {
      return REASON_UNSPECIFIED;
    }
    var normalized = reason.strip();
    if (normalized.length() > MAX_REASON_LENGTH) {
      normalized = normalized.substring(0, MAX_REASON_LENGTH);
    }
    if (knownReasons.contains(normalized)) {
      return normalized;
    }
    if (knownReasons.size() < maxDistinctReasons) {
      knownReasons.add(normalized);
      return normalized;
    }
    return REASON_OTHER;
  }

  private static Attributes withType(AttributesBuilder builder, TransferProcessEvent event) {
    if (event.getType() != null) {
      builder.put(ATTR_TRANSFER_TYPE, event.getType());
    }
    return builder.build();
  }

  private static LongCounter counter(Meter meter, String name, String description) {
    return meter.counterBuilder(name).setDescription(description).setUnit("{transition}").build();
  }

  private static DoubleHistogram histogram(Meter meter, String name, String description) {
    return meter
        .histogramBuilder(name)
        .setDescription(description)
        .setUnit("s")
        .setExplicitBucketBoundariesAdvice(DURATION_BUCKETS_SECONDS)
        .build();
  }
}
