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
package org.seamware.edc.pap.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.util.List;
import java.util.Map;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.policy.engine.spi.PolicyContext;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.seamware.edc.pap.OdrlPapClient;
import org.seamware.pap.model.TestRequestVO;
import org.seamware.pap.model.ValidationRequestVO;
import org.seamware.pap.model.ValidationResponseVO;

/**
 * Unit tests for {@link OdrlPapPolicyValidator}.
 *
 * <p>Verifies correct behavior for PAP allow/deny responses, error handling with both fail-open and
 * fail-closed configurations, and policy conversion failures.
 */
@ExtendWith(MockitoExtension.class)
class OdrlPapPolicyValidatorTest {

  private static final String TEST_SCOPE = "request.catalog";

  @Mock private OdrlPapClient odrlPapClient;
  @Mock private TypeTransformerRegistry transformerRegistry;
  @Mock private JsonLd jsonLd;
  @Mock private PolicyContextRequestMapper requestMapper;
  @Mock private Monitor monitor;

  @Mock private ObjectMapper objectMapper;

  private Policy policy;
  private PolicyContext context;

  @BeforeEach
  void setUp() throws Exception {
    policy = Policy.Builder.newInstance().build();
    policy.getPermissions().add(Permission.Builder.newInstance().build());
    context = mock(PolicyContext.class);
    lenient().when(context.scope()).thenReturn(TEST_SCOPE);
    lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
  }

  /**
   * Configures mocks so that policy-to-JSON-LD conversion succeeds, producing a minimal expanded
   * JSON-LD object.
   */
  private void setupSuccessfulPolicyConversion() throws Exception {
    JsonObject compactJson = Json.createObjectBuilder().add("@type", "odrl:Set").build();
    JsonObject expandedJson =
        Json.createObjectBuilder().add("http://www.w3.org/ns/odrl/2/type", "Set").build();

    when(transformerRegistry.transform(any(Policy.class), eq(JsonObject.class)))
        .thenReturn(Result.success(compactJson));
    when(jsonLd.expand(any(JsonObject.class))).thenReturn(Result.success(expandedJson));
    when(requestMapper.toTestRequest(any(PolicyContext.class)))
        .thenReturn(new TestRequestVO().method(TestRequestVO.MethodEnum.GET));
    when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(Map.of());
  }

  @Nested
  @DisplayName("PAP allows request")
  class PapAllows {

    @Test
    @DisplayName("Returns true when PAP responds with allow=true")
    void apply_papAllows_returnsTrue() throws Exception {
      OdrlPapPolicyValidator validator =
          new OdrlPapPolicyValidator(
              odrlPapClient,
              transformerRegistry,
              jsonLd,
              requestMapper,
              monitor,
              objectMapper,
              false);

      setupSuccessfulPolicyConversion();
      ValidationResponseVO response = new ValidationResponseVO().allow(true);
      when(odrlPapClient.validate(any(ValidationRequestVO.class))).thenReturn(response);

      Boolean result = validator.apply(policy, context);

      assertTrue(result);
      verify(context, never()).reportProblem(any());
    }
  }

  @Nested
  @DisplayName("PAP denies request")
  class PapDenies {

    @Test
    @DisplayName("Returns false when PAP responds with allow=false")
    void apply_papDenies_returnsFalse() throws Exception {
      OdrlPapPolicyValidator validator =
          new OdrlPapPolicyValidator(
              odrlPapClient,
              transformerRegistry,
              jsonLd,
              requestMapper,
              monitor,
              objectMapper,
              false);

      setupSuccessfulPolicyConversion();
      ValidationResponseVO response =
          new ValidationResponseVO()
              .allow(false)
              .explanation(List.of("Policy constraint violated", "Access denied for resource"));
      when(odrlPapClient.validate(any(ValidationRequestVO.class))).thenReturn(response);

      Boolean result = validator.apply(policy, context);

      assertFalse(result);
      verify(context).reportProblem(contains("Policy constraint violated"));
      verify(context).reportProblem(contains("Access denied for resource"));
    }

    @Test
    @DisplayName("Reports generic problem when PAP denies without explanation")
    void apply_papDeniesNoExplanation_reportsGenericProblem() throws Exception {
      OdrlPapPolicyValidator validator =
          new OdrlPapPolicyValidator(
              odrlPapClient,
              transformerRegistry,
              jsonLd,
              requestMapper,
              monitor,
              objectMapper,
              false);

      setupSuccessfulPolicyConversion();
      ValidationResponseVO response = new ValidationResponseVO().allow(false).explanation(null);
      when(odrlPapClient.validate(any(ValidationRequestVO.class))).thenReturn(response);

      Boolean result = validator.apply(policy, context);

      assertFalse(result);
      verify(context).reportProblem(contains("no explanation provided"));
    }
  }

  @Nested
  @DisplayName("Error handling with denyOnError=true (fail-closed)")
  class FailClosed {

    private OdrlPapPolicyValidator validator;

    @BeforeEach
    void setUp() {
      validator =
          new OdrlPapPolicyValidator(
              odrlPapClient,
              transformerRegistry,
              jsonLd,
              requestMapper,
              monitor,
              objectMapper,
              true);
    }

    @Test
    @DisplayName("Returns false when PAP throws exception and denyOnError is true")
    void apply_papException_denyOnError_returnsFalse() throws Exception {
      setupSuccessfulPolicyConversion();
      when(odrlPapClient.validate(any(ValidationRequestVO.class)))
          .thenThrow(new RuntimeException("Connection refused"));

      Boolean result = validator.apply(policy, context);

      assertFalse(result);
      verify(context).reportProblem(contains("PAP communication error"));
    }

    @Test
    @DisplayName("Returns false when policy transform fails and denyOnError is true")
    void apply_transformFails_denyOnError_returnsFalse() {
      when(transformerRegistry.transform(any(Policy.class), eq(JsonObject.class)))
          .thenReturn(Result.failure("Transform failed"));

      Boolean result = validator.apply(policy, context);

      assertFalse(result);
      verify(context).reportProblem(contains("PAP communication error"));
    }

    @Test
    @DisplayName("Returns false when JSON-LD expand fails and denyOnError is true")
    void apply_expandFails_denyOnError_returnsFalse() {
      JsonObject compactJson = Json.createObjectBuilder().add("@type", "odrl:Set").build();
      when(transformerRegistry.transform(any(Policy.class), eq(JsonObject.class)))
          .thenReturn(Result.success(compactJson));
      when(jsonLd.expand(any(JsonObject.class))).thenReturn(Result.failure("Expand failed"));

      Boolean result = validator.apply(policy, context);

      assertFalse(result);
      verify(context).reportProblem(contains("PAP communication error"));
    }
  }

  @Nested
  @DisplayName("Error handling with denyOnError=false (fail-open)")
  class FailOpen {

    private OdrlPapPolicyValidator validator;

    @BeforeEach
    void setUp() {
      validator =
          new OdrlPapPolicyValidator(
              odrlPapClient,
              transformerRegistry,
              jsonLd,
              requestMapper,
              monitor,
              objectMapper,
              false);
    }

    @Test
    @DisplayName("Returns true when PAP throws exception and denyOnError is false")
    void apply_papException_failOpen_returnsTrue() throws Exception {
      setupSuccessfulPolicyConversion();
      when(odrlPapClient.validate(any(ValidationRequestVO.class)))
          .thenThrow(new RuntimeException("Connection refused"));

      Boolean result = validator.apply(policy, context);

      assertTrue(result);
      verify(context, never()).reportProblem(any());
    }

    @Test
    @DisplayName("Returns true when policy transform fails and denyOnError is false")
    void apply_transformFails_failOpen_returnsTrue() {
      when(transformerRegistry.transform(any(Policy.class), eq(JsonObject.class)))
          .thenReturn(Result.failure("Transform failed"));

      Boolean result = validator.apply(policy, context);

      assertTrue(result);
      verify(context, never()).reportProblem(any());
    }
  }

  @Nested
  @DisplayName("Validator metadata")
  class Metadata {

    @Test
    @DisplayName("name() returns the expected validator name")
    void name_returnsExpectedName() {
      OdrlPapPolicyValidator validator =
          new OdrlPapPolicyValidator(
              odrlPapClient,
              transformerRegistry,
              jsonLd,
              requestMapper,
              monitor,
              objectMapper,
              false);

      assertEquals(OdrlPapPolicyValidator.VALIDATOR_NAME, validator.name());
    }
  }
}
