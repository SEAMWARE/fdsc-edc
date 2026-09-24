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

import io.opentelemetry.api.logs.Logger;
import java.time.Clock;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationEvent;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessEvent;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.event.EventRouter;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

/**
 * Registers an {@link AuditEventSubscriber} that emits OpenTelemetry audit logs for every contract
 * negotiation and transfer process state transition.
 */
@Extension(value = OtelAuditExtension.NAME)
public class OtelAuditExtension implements ServiceExtension {

  public static final String NAME = "OpenTelemetry Audit";

  private static final String DEFAULT_STATE_CACHE_SIZE = "10000";

  @Setting(
      key = "edc.otel.audit.enabled",
      description = "Enables OpenTelemetry audit logs for negotiation and transfer events",
      defaultValue = "true")
  private boolean enabled;

  @Setting(
      key = "edc.otel.audit.state-cache.size",
      description = "Max number of entities whose last state is kept to report the previous state",
      defaultValue = DEFAULT_STATE_CACHE_SIZE)
  private int stateCacheSize;

  @Inject private EventRouter eventRouter;

  @Inject private Logger otelLogger;

  @Inject private Clock clock;

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public void initialize(ServiceExtensionContext context) {
    var monitor = context.getMonitor();
    if (!enabled) {
      monitor.info("Otel Audit: disabled via edc.otel.audit.enabled");
      return;
    }

    var subscriber = new AuditEventSubscriber(otelLogger, monitor, clock, stateCacheSize);
    eventRouter.register(ContractNegotiationEvent.class, subscriber);
    eventRouter.register(TransferProcessEvent.class, subscriber);

    monitor.info("Otel Audit Extension initialized successfully");
  }
}
