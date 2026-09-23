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
package org.seamware.edc.otel;

import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

@Provides({Logger.class, Meter.class})
@Extension(value = OtelCoreExtension.NAME)
public class OtelCoreExtension implements ServiceExtension {

  public static final String NAME = "OpenTelemetry Core";

  private static final String DEFAULT_OTEL_ENDPOINT = "http://otel-collector:4318/v1/logs";
  private static final String DEFAULT_OTEL_METRICS_ENDPOINT =
      "http://otel-collector:4318/v1/metrics";

  @Setting(
      key = "edc.otel.logs.endpoint",
      description = "OTLP/HTTP endpoint for OpenTelemetry log export",
      defaultValue = DEFAULT_OTEL_ENDPOINT)
  private String otelEndpoint;

  @Setting(
      key = "edc.otel.metrics.endpoint",
      description = "OTLP/HTTP endpoint for OpenTelemetry metric export",
      defaultValue = DEFAULT_OTEL_METRICS_ENDPOINT)
  private String otelMetricsEndpoint;

  private SdkLoggerProvider sdkLoggerProvider;
  private SdkMeterProvider sdkMeterProvider;

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public void initialize(ServiceExtensionContext context) {
    var monitor = context.getMonitor();
    monitor.info("Otel Core: sending logs to " + otelEndpoint);

    var logExporter = OtlpHttpLogRecordExporter.builder().setEndpoint(otelEndpoint).build();

    sdkLoggerProvider =
        SdkLoggerProvider.builder()
            .addLogRecordProcessor(BatchLogRecordProcessor.builder(logExporter).build())
            .build();

    monitor.info("Otel Core: sending metrics to " + otelMetricsEndpoint);

    var metricExporter = OtlpHttpMetricExporter.builder().setEndpoint(otelMetricsEndpoint).build();

    sdkMeterProvider =
        SdkMeterProvider.builder()
            .registerMetricReader(PeriodicMetricReader.builder(metricExporter).build())
            .build();

    var openTelemetry =
        OpenTelemetrySdk.builder()
            .setLoggerProvider(sdkLoggerProvider)
            .setMeterProvider(sdkMeterProvider)
            .build();

    Logger otelLogger = openTelemetry.getLogsBridge().get("edc-otel");
    context.registerService(Logger.class, otelLogger);

    Meter otelMeter = openTelemetry.getMeter("edc-otel");
    context.registerService(Meter.class, otelMeter);

    monitor.info("Otel Core Extension initialized successfully");
  }

  @Override
  public void shutdown() {
    if (sdkLoggerProvider != null) {
      sdkLoggerProvider.forceFlush();
      sdkLoggerProvider.shutdown();
    }
    if (sdkMeterProvider != null) {
      sdkMeterProvider.forceFlush();
      sdkMeterProvider.shutdown();
    }
  }
}
