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
import java.util.concurrent.Executors;
import okhttp3.OkHttpClient;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationEvent;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessEvent;
import org.eclipse.edc.runtime.metamodel.annotation.Configuration;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.runtime.metamodel.annotation.Settings;
import org.eclipse.edc.spi.event.EventRouter;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.eclipse.edc.web.spi.configuration.PortMappingRegistry;

/**
 * Registers an {@link AuditEventSubscriber} that emits OpenTelemetry audit logs for every contract
 * negotiation and transfer process state transition and, with TMForum-backed stores, subscribes to
 * the TMForum product catalog events to audit asset and contract definition changes.
 */
@Extension(value = OtelAuditExtension.NAME)
public class OtelAuditExtension implements ServiceExtension {

  public static final String NAME = "OpenTelemetry Audit";

  private static final String DEFAULT_STATE_CACHE_SIZE = "10000";
  private static final String TMF_EVENTS_CONTEXT = "tmfevents";
  private static final int TMF_EVENTS_PORT = 8688;
  private static final String TMF_EVENTS_PATH = "/tmf-events";

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

  @Setting(
      key = "edc.otel.audit.catalog.enabled",
      description = "Enables audit logs for catalog changes notified by TMForum",
      defaultValue = "true")
  private boolean catalogEnabled;

  @Setting(
      key = "edc.otel.audit.catalog.callback-url",
      description =
          "URL TMForum uses to deliver catalog events. Defaults to"
              + " http://<edc.hostname>:<web.http.tmfevents.port><web.http.tmfevents.path>",
      required = false)
  private String catalogCallbackUrl;

  @Setting(
      key = "edc.otel.audit.catalog.retry-interval",
      description = "Seconds between retries when subscription to TMForum fails",
      defaultValue = "30")
  private long catalogRetryInterval;

  @Setting(
      key = "edc.otel.audit.catalog.dedup-cache.size",
      description = "Max number of TMForum events remembered to discard duplicated deliveries",
      defaultValue = DEFAULT_STATE_CACHE_SIZE)
  private int catalogDedupCacheSize;

  @Configuration private TmfEventsApiConfiguration tmfEventsApiConfiguration;

  @Inject private EventRouter eventRouter;

  @Inject private Logger otelLogger;

  @Inject private Clock clock;

  @Inject private OkHttpClient okHttpClient;

  @Inject private TypeManager typeManager;

  @Inject private WebService webService;

  @Inject private PortMappingRegistry portMappingRegistry;

  private TmfHubSubscriber hubSubscriber;

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

    if (catalogEnabled) {
      initializeCatalogAudit(context, monitor);
    }

    monitor.info("Otel Audit Extension initialized successfully");
  }

  @Override
  public void start() {
    if (hubSubscriber != null) {
      hubSubscriber.start();
    }
  }

  @Override
  public void shutdown() {
    if (hubSubscriber != null) {
      hubSubscriber.stop();
    }
  }

  private void initializeCatalogAudit(ServiceExtensionContext context, Monitor monitor) {
    var config = context.getConfig();
    if (!config.getBoolean("tmfExtension.enabled", false)) {
      monitor.info("Otel Audit: catalog audit skipped, it requires tmfExtension.enabled=true");
      return;
    }
    var productCatalogApi = config.getString("tmfExtension.productCatalogApi", null);
    if (productCatalogApi == null) {
      monitor.warning("Otel Audit: catalog audit skipped, tmfExtension.productCatalogApi not set");
      return;
    }
    var callbackUrl =
        catalogCallbackUrl != null
            ? catalogCallbackUrl
            : String.format(
                "http://%s:%d%s",
                config.getString("edc.hostname", "localhost"),
                tmfEventsApiConfiguration.port(),
                tmfEventsApiConfiguration.path());

    var auditor = new TmfCatalogEventAuditor(otelLogger, monitor, clock, catalogDedupCacheSize);
    portMappingRegistry.register(
        new PortMapping(
            TMF_EVENTS_CONTEXT,
            tmfEventsApiConfiguration.port(),
            tmfEventsApiConfiguration.path()));
    webService.registerResource(
        TMF_EVENTS_CONTEXT, new TmfCatalogEventController(auditor, monitor));

    var executor =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              var thread = new Thread(runnable, "otel-audit-tmf-hub");
              thread.setDaemon(true);
              return thread;
            });
    hubSubscriber =
        new TmfHubSubscriber(
            okHttpClient,
            typeManager.getMapper(),
            monitor,
            executor,
            productCatalogApi,
            callbackUrl,
            catalogRetryInterval);
    monitor.info("Otel Audit: catalog audit via TMForum notifications, callback " + callbackUrl);
  }

  @Settings
  record TmfEventsApiConfiguration(
      @Setting(
              key = "web.http." + TMF_EVENTS_CONTEXT + ".port",
              description = "Port for the TMForum catalog events context",
              defaultValue = TMF_EVENTS_PORT + "")
          int port,
      @Setting(
              key = "web.http." + TMF_EVENTS_CONTEXT + ".path",
              description = "Path for the TMForum catalog events context",
              defaultValue = TMF_EVENTS_PATH)
          String path) {}
}
