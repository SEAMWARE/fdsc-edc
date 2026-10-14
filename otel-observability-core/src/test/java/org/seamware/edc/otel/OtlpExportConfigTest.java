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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.opentelemetry.api.common.AttributeKey;
import org.junit.jupiter.api.Test;
import org.seamware.edc.otel.OtlpExportConfig.Protocol;

public class OtlpExportConfigTest {

  @Test
  void defaultsToHttpCollector() {
    var config = OtlpExportConfig.resolve(null, null, null, null, null, null);

    assertEquals(Protocol.HTTP_PROTOBUF, config.protocol());
    assertEquals("http://otel-collector:4318/v1/logs", config.logsEndpoint());
    assertEquals("http://otel-collector:4318/v1/metrics", config.metricsEndpoint());
  }

  @Test
  void defaultsToGrpcCollectorWhenOnlyProtocolIsSet() {
    var config = OtlpExportConfig.resolve("grpc", null, null, null, null, null);

    assertEquals(Protocol.GRPC, config.protocol());
    assertEquals("http://otel-collector:4317", config.logsEndpoint());
    assertEquals("http://otel-collector:4317", config.metricsEndpoint());
  }

  @Test
  void httpBaseEndpointGetsSignalPaths() {
    var config =
        OtlpExportConfig.resolve(
            "http/protobuf", "http://collector.monitoring:4318/", null, null, null, null);

    assertEquals("http://collector.monitoring:4318/v1/logs", config.logsEndpoint());
    assertEquals("http://collector.monitoring:4318/v1/metrics", config.metricsEndpoint());
  }

  @Test
  void grpcBaseEndpointIsUsedAsIs() {
    var config =
        OtlpExportConfig.resolve(
            "grpc", "http://collector.monitoring:4317", null, null, null, null);

    assertEquals("http://collector.monitoring:4317", config.logsEndpoint());
    assertEquals("http://collector.monitoring:4317", config.metricsEndpoint());
  }

  @Test
  void signalEndpointOverrideTheBaseEndpoint() {
    var config =
        OtlpExportConfig.resolve(
            null,
            "http://collector:4318",
            "http://logs-collector:4318/custom/logs",
            "http://metrics-collector:4318/custom/metrics",
            null,
            null);

    assertEquals("http://logs-collector:4318/custom/logs", config.logsEndpoint());
    assertEquals("http://metrics-collector:4318/custom/metrics", config.metricsEndpoint());
  }

  @Test
  void edcSettingsOverrideTheStandardVariables() {
    var config =
        OtlpExportConfig.resolve(
            null,
            "http://collector:4318",
            "http://logs-collector:4318/v1/logs",
            null,
            "http://edc-logs:4318/v1/logs",
            "http://edc-metrics:4318/v1/metrics");

    assertEquals("http://edc-logs:4318/v1/logs", config.logsEndpoint());
    assertEquals("http://edc-metrics:4318/v1/metrics", config.metricsEndpoint());
  }

  @Test
  void unknownProtocolIsRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> OtlpExportConfig.resolve("http/json", null, null, null, null, null));
  }

  @Test
  void serviceNameDefaultsWhenNotConfigured() {
    var resource = OtlpExportConfig.resource(null, null);

    assertEquals(
        OtlpExportConfig.DEFAULT_SERVICE_NAME,
        resource.getAttribute(OtlpExportConfig.SERVICE_NAME));
  }

  @Test
  void serviceNameTakesPrecedenceOverResourceAttributes() {
    var resource =
        OtlpExportConfig.resource(
            "fdsc-edc-controlplane", "service.name=ignored, deployment.environment = prod");

    assertEquals("fdsc-edc-controlplane", resource.getAttribute(OtlpExportConfig.SERVICE_NAME));
    assertEquals("prod", resource.getAttribute(AttributeKey.stringKey("deployment.environment")));
  }

  @Test
  void serviceNameIsTakenFromResourceAttribute() {
    var resource = OtlpExportConfig.resource(" ", "service.name=from-attributes,broken,=empty-key");

    assertEquals("from-attributes", resource.getAttribute(OtlpExportConfig.SERVICE_NAME));
  }
}
