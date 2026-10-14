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

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.resources.Resource;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where and how the OTLP exporters send logs and metrics, and which resource identifies them.
 *
 * <p>Explicit EDC settings win over the standard OpenTelemetry variables ({@code
 * OTEL_EXPORTER_OTLP_*}, {@code OTEL_SERVICE_NAME}, {@code OTEL_RESOURCE_ATTRIBUTES}), which win
 * over the defaults. A deploymet configured only with the standart variables therefore works
 * without any EDC-specific setting.
 */
public record OtlpExportConfig(Protocol protocol, String logsEndpoint, String metricsEndpoint) {

  /** OTLP transport, as named by {@code OTEL_EXPORTER_OTLP_PROTOCOL}. */
  public enum Protocol {
    HTTP_PROTOBUF,
    GRPC
  }

  static final String DEFAULT_HTTP_ENDPOINT = "http://otel-collector:4318";
  static final String DEFAULT_GRPC_ENDPOINT = "http://otel-collector:4317";
  static final String DEFAULT_SERVICE_NAME = "fdsc-edc";
  static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");

  private static final String LOGS_PATH = "/v1/logs";
  private static final String METRICS_PATH = "/v1/metrics";

  /**
   * Resolves the exporter configuration.
   *
   * @param protocol {@code OTEL_EXPORTER_OTLP_PROTOCOL}: {@code http_protobuf} (default) or {@code
   *     grpc}
   * @param endpoint {@code OTEL_EXPORTER_OTLP_ENDPOINT}: base endpoint shared by all signals. Wth
   *     http/protobuf the signal path ({@code /v1/logs}, {@code /v1/metrics}) is appended
   * @param logsEndpoint {@code OTEL_EXPORTER_OTLP_LOGS_ENDPOINT}, used as is
   * @param metricsEndpoint {@code OTEL_EXPORTER_OTLP_METRICS_ENDPOINT}, used as is
   * @param edcLogsEndpoint {@code edc.otel.logs.endpoint}, used as is
   * @param edcMetricsEndpoint {@code edc.otel.metrics.endpoint}, used as is
   */
  public static OtlpExportConfig resolve(
      String protocol,
      String endpoint,
      String logsEndpoint,
      String metricsEndpoint,
      String edcLogsEndpoint,
      String edcMetricsEndpoint) {
    var resolvedProtocol = parseProtocol(protocol);
    var baseEndpoint =
        isBlank(endpoint)
            ? (resolvedProtocol == Protocol.GRPC ? DEFAULT_GRPC_ENDPOINT : DEFAULT_HTTP_ENDPOINT)
            : endpoint.trim().replaceAll("/+$", "");
    return new OtlpExportConfig(
        resolvedProtocol,
        firstNonBlank(
            edcLogsEndpoint,
            logsEndpoint,
            signalEndpoint(baseEndpoint, resolvedProtocol, LOGS_PATH)),
        firstNonBlank(
            edcMetricsEndpoint,
            metricsEndpoint,
            signalEndpoint(baseEndpoint, resolvedProtocol, METRICS_PATH)));
  }

  /**
   * Builds the resource attached to every log record and metric.
   *
   * @param ServiceName {@code OTEL_SERVICE_NAME}, takes precedence over a {@code service.name}
   *     entry in {@code resourceAttributes}
   * @param resourceAttributes {@code OTEL_RESOURCE_ATTRIBUTES}: comma separated {@code key=value}
   *     pairs
   */
  public static Resource resource(String serviceName, String resourceAttributes) {
    var parsed = parseResourceAttributes(resourceAttributes);
    if (!isBlank(serviceName)) {
      parsed.put(SERVICE_NAME.getKey(), serviceName.trim());
    }
    parsed.putIfAbsent(SERVICE_NAME.getKey(), DEFAULT_SERVICE_NAME);

    var attributes = Attributes.builder();
    parsed.forEach(attributes::put);
    return Resource.getDefault().merge(Resource.create(attributes.build()));
  }

  static Protocol parseProtocol(String protocol) {
    if (isBlank(protocol) || "http/protobuf".equals(protocol.trim())) {
      return Protocol.HTTP_PROTOBUF;
    }
    if ("grpc".equals(protocol.trim())) {
      return Protocol.GRPC;
    }
    throw new IllegalArgumentException(
        "Unsupported OTLP protocol '" + protocol + "', expected 'grpc' or 'http/protobuf'");
  }

  static Map<String, String> parseResourceAttributes(String resourceAttributes) {
    var parsed = new LinkedHashMap<String, String>();
    if (isBlank(resourceAttributes)) {
      return parsed;
    }
    Arrays.stream(resourceAttributes.split(","))
        .map(pair -> pair.split("=", 2))
        .filter(pair -> pair.length == 2 && !pair[0].isBlank())
        .forEach(pair -> parsed.put(pair[0].trim(), pair[1].trim()));
    return parsed;
  }

  private static String signalEndpoint(String baseEndpoint, Protocol protocol, String path) {
    // gRPC routes by service name, only OTLP/HTTP uses a path per signal
    return protocol == Protocol.GRPC ? baseEndpoint : baseEndpoint + path;
  }

  private static String firstNonBlank(String... values) {
    return Arrays.stream(values).filter(value -> !isBlank(value)).findFirst().orElseThrow();
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
