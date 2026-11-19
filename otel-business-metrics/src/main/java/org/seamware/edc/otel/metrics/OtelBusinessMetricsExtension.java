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

import io.opentelemetry.api.metrics.Meter;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationEvent;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessEvent;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.event.EventRouter;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

/**
 * Registers a {@link BusinessMetricsEventSubscriber} that records OpenTelemetry business metrics
 * for contract negotiation and transfer process state transitions.
 */
@Extension(value = OtelBusinessMetricsExtension.NAME)
public class OtelBusinessMetricsExtension implements ServiceExtension {

  public static final String NAME = "OpenTelemetry Business Metrics";

  private static final String DEFAULT_STATE_CACHE_SIZE = "10000";
  private static final String DEFAULT_MAX_DISTINCT_REASONS = "50";

  @Setting(
      key = "edc.otel.metrics.business.enabled",
      description = "Enables OpenTelemetry business metrics for negotiation and transfer events",
      defaultValue = "true")
  private boolean enabled;

  @Setting(
      key = "edc.otel.metrics.business.state-cache.size",
      description = "Max number of entities per domain whose lifecycle is tracked for durations",
      defaultValue = DEFAULT_STATE_CACHE_SIZE)
  private int stateCacheSize;

  @Setting(
      key = "edc.otel.metrics.business.reason.max-distinct",
      description = "Max distinct transfer termination/suspension reasons before using 'other'",
      defaultValue = DEFAULT_MAX_DISTINCT_REASONS)
  private int maxDistinctReasons;

  @Inject private EventRouter eventRouter;

  @Inject private Meter meter;

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public void initialize(ServiceExtensionContext context) {
    var monitor = context.getMonitor();
    if (!enabled) {
      monitor.info("Otel Metrics: disabled via edc.otel.metrics.business.enabled");
      return;
    }

    var subscriber =
        new BusinessMetricsEventSubscriber(meter, monitor, stateCacheSize, maxDistinctReasons);
    eventRouter.register(ContractNegotiationEvent.class, subscriber);
    eventRouter.register(TransferProcessEvent.class, subscriber);

    monitor.info("Otel Business Metrics Extension initialized successfully");
  }
}
