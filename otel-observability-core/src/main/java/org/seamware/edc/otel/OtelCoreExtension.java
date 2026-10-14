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
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

/**
 * Provides the OpenTelemetry {@link Logger} and {@link Meter} used by the audit and business
 * metrics extensions.
 *
 * <p>It honours the standard OpenTelemetry variables ({@code OTEL_EXPORTER_OTLP_ENDPOINT}, {@code
 * OTEL_EXPORTER_OTLP_PROTOCOL}, {@code OTEL_SERVICE_NAME}...), which EDC exposes as {@code otel.*}
 * settings. The SDK is not registered as global and ignores {@code OTEL_LOGS_EXPORTER} and {@code
 * OTEL_METRICS_EXPORTER}: those only configure the OpenTelemetry Java agent, and disablilng the
 * agent logs must not disable the audit logs.
 */
@Provides({Logger.class, Meter.class})
@Extension(value = OtelCoreExtension.NAME)
public class OtelCoreExtension implements ServiceExtension {

  public static final String NAME = "OpenTelemetry Core";

  @Setting(
      key = "edc.otel.logs.endpoint",
      description =
          "OTLP endpoint for log export. Overrides the standard OTEL_EXPORTER_OTLP_* variables",
      required = false)
  private String edcLogsEndpoint;

  @Setting(
      key = "edc.otel.metrics.endpoint",
      description =
          "OTLP endpoint for metric export. Overrides the standard OTEL_EXPORTER_OTLP_* variables",
      required = false)
  private String edcMetricsEndpoint;

  @Setting(
      key = "otel.exporter.otlp.endpoint",
      description = "OTEL_EXPORTER_OTLP_ENDPOINT: base OTLP endpoint for logs and metrics",
      required = false)
  private String otlpEndpoint;

  @Setting(
      key = "otel.exporter.otlp.logs.endpoint",
      description = "OTEL_EXPORTER_OTLP_LOGS_ENDPOINT: OTLP endpoint for logs",
      required = false)
  private String otlpLogsEndpoint;

  @Setting(
      key = "otel.exporter.otlp.metrics.endpoint",
      description = "OTEL_EXPORTER_OTLP_METRICS_ENDPOINT: OTLP endpoint for metrics",
      required = false)
  private String otlpMetricsEndpoint;

  @Setting(
      key = "otel.exporter.otlp.protocol",
      description = "OTEL_EXPORTER_OTLP_PROTOCOL: grpc or http/protobuf",
      defaultValue = "http/protobuf")
  private String otlpProtocol;

  @Setting(
      key = "otel.service.name",
      description = "OTEL_SERVICE_NAME: service.name of the exported logs and metrics",
      required = false)
  private String serviceName;

  @Setting(
      key = "otel.resource.attributes",
      description = "OTEL_RESOURCE_ATTRIBUTES: extra resource attributes as key=value,key=value",
      required = false)
  private String resourceAttributes;

  private SdkLoggerProvider sdkLoggerProvider;
  private SdkMeterProvider sdkMeterProvider;

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public void initialize(ServiceExtensionContext context) {
    var monitor = context.getMonitor();
    var config =
        OtlpExportConfig.resolve(
            otlpProtocol,
            otlpEndpoint,
            otlpLogsEndpoint,
            otlpMetricsEndpoint,
            edcLogsEndpoint,
            edcMetricsEndpoint);
    var resource = OtlpExportConfig.resource(serviceName, resourceAttributes);

    monitor.info(
        String.format(
            "Otel Core: exporting as service '%s' over %s, logs to %s, metrics to %s",
            resource.getAttribute(OtlpExportConfig.SERVICE_NAME),
            config.protocol(),
            config.logsEndpoint(),
            config.metricsEndpoint()));

    sdkLoggerProvider =
        SdkLoggerProvider.builder()
            .setResource(resource)
            .addLogRecordProcessor(BatchLogRecordProcessor.builder(logExporter(config)).build())
            .build();

    sdkMeterProvider =
        SdkMeterProvider.builder()
            .setResource(resource)
            .registerMetricReader(PeriodicMetricReader.builder(metricExporter(config)).build())
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

  private static LogRecordExporter logExporter(OtlpExportConfig config) {
    return switch (config.protocol()) {
      case GRPC -> OtlpGrpcLogRecordExporter.builder().setEndpoint(config.logsEndpoint()).build();
      case HTTP_PROTOBUF ->
          OtlpHttpLogRecordExporter.builder().setEndpoint(config.logsEndpoint()).build();
    };
  }

  private static MetricExporter metricExporter(OtlpExportConfig config) {
    return switch (config.protocol()) {
      case GRPC -> OtlpGrpcMetricExporter.builder().setEndpoint(config.metricsEndpoint()).build();
      case HTTP_PROTOBUF ->
          OtlpHttpMetricExporter.builder().setEndpoint(config.metricsEndpoint()).build();
    };
  }
}
