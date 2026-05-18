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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.JsonObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.policy.engine.spi.PolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyValidatorRule;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.seamware.edc.pap.OdrlPapClient;
import org.seamware.pap.model.TestRequestVO;
import org.seamware.pap.model.ValidationRequestVO;
import org.seamware.pap.model.ValidationResponseVO;

/**
 * A {@link PolicyValidatorRule} that delegates policy evaluation to an external ODRL-PAP service.
 *
 * <p>When registered as a pre-validator with the EDC {@code PolicyEngine}, this rule intercepts
 * policy evaluation before the built-in constraint functions run. It converts the EDC {@link
 * Policy} to expanded ODRL JSON-LD, maps the {@link PolicyContext} to an HTTP request
 * representation, and calls the PAP's {@code POST /validate} endpoint.
 *
 * <p>If the PAP returns {@code allow=false}, the validator reports the PAP's explanation messages
 * as problems on the context and returns {@code false}, causing the policy evaluation to fail
 * immediately. If the PAP returns {@code allow=true}, the validator returns {@code true} and EDC's
 * built-in constraint functions still run (layered evaluation).
 *
 * <p>Error handling is controlled by the {@code denyOnError} flag: when {@code true}, any exception
 * during PAP communication causes the validator to return {@code false} (fail-closed); when {@code
 * false}, exceptions are logged and the validator returns {@code true} (fail-open), allowing EDC's
 * built-in evaluators to handle the policy.
 */
public class OdrlPapPolicyValidator implements PolicyValidatorRule<PolicyContext> {

  /** Name identifying this validator in logs and policy engine diagnostics. */
  static final String VALIDATOR_NAME = "OdrlPapPolicyValidator";

  /** TypeReference for converting a JsonObject to a Map for the PAP API. */
  private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE =
      new TypeReference<>() {};

  private final OdrlPapClient odrlPapClient;
  private final TypeTransformerRegistry transformerRegistry;
  private final JsonLd jsonLd;
  private final PolicyContextRequestMapper requestMapper;
  private final Monitor monitor;
  private final ObjectMapper objectMapper;
  private final boolean denyOnError;

  /**
   * Creates a new ODRL-PAP policy validator.
   *
   * @param odrlPapClient the client for communicating with the ODRL-PAP service
   * @param transformerRegistry the EDC type transformer registry for converting {@link Policy} to
   *     {@link JsonObject}
   * @param jsonLd the JSON-LD processor for expanding compacted JSON-LD
   * @param requestMapper the mapper for converting {@link PolicyContext} to {@link TestRequestVO}
   * @param monitor the EDC monitor for logging
   * @param objectMapper the Jackson object mapper for JSON serialization
   * @param denyOnError when {@code true}, PAP communication errors cause policy denial (fail-
   *     closed); when {@code false}, errors are logged and the policy is allowed (fail-open)
   */
  public OdrlPapPolicyValidator(
      OdrlPapClient odrlPapClient,
      TypeTransformerRegistry transformerRegistry,
      JsonLd jsonLd,
      PolicyContextRequestMapper requestMapper,
      Monitor monitor,
      ObjectMapper objectMapper,
      boolean denyOnError) {
    this.odrlPapClient = odrlPapClient;
    this.transformerRegistry = transformerRegistry;
    this.jsonLd = jsonLd;
    this.requestMapper = requestMapper;
    this.monitor = monitor;
    this.objectMapper = objectMapper;
    this.denyOnError = denyOnError;
  }

  /**
   * Evaluates the given policy against the ODRL-PAP service.
   *
   * <p>The evaluation proceeds in the following steps:
   *
   * <ol>
   *   <li>Convert the EDC {@link Policy} to a {@link JsonObject} via the {@link
   *       TypeTransformerRegistry}
   *   <li>Expand the JSON-LD using {@link JsonLd#expand(JsonObject)}
   *   <li>Convert the expanded JSON-LD to a {@code Map<String, Object>} for the PAP API
   *   <li>Map the {@link PolicyContext} to a {@link TestRequestVO} via the {@link
   *       PolicyContextRequestMapper}
   *   <li>Call the PAP's {@code POST /validate} endpoint
   *   <li>Return {@code true} if allowed, {@code false} with reported problems if denied
   * </ol>
   *
   * @param policy the ODRL policy to evaluate
   * @param context the policy context providing request details
   * @return {@code true} if the PAP allows the request, {@code false} otherwise
   */
  @Override
  public Boolean apply(Policy policy, PolicyContext context) {
    try {
      monitor.info("The policy " + objectMapper.writeValueAsString(policy));
      monitor.info("The context " + objectMapper.writeValueAsString(context));

      if (policy.getPermissions() == null || policy.getPermissions().isEmpty()) {
        monitor.info(
            "When the policy does not contain permissions, it cannot be evaluated by the odrl-pap");
        return true;
      }
      Map<String, Object> policyMap = new HashMap<>(convertPolicyToMap(policy));
      policyMap.put("pap:evaluationContext", "json");
      TestRequestVO testRequest = requestMapper.toTestRequest(context);

      ValidationRequestVO validationRequest = new ValidationRequestVO();
      validationRequest.policy(policyMap);
      validationRequest.testRequest(testRequest);

      ValidationResponseVO response = odrlPapClient.validate(validationRequest);

      if (Boolean.TRUE.equals(response.getAllow())) {
        monitor.debug(
            () ->
                String.format(
                    "%s: PAP allowed request for scope '%s'", VALIDATOR_NAME, context.scope()));
        return true;
      }

      List<String> explanations = response.getExplanation();
      if (explanations != null) {
        for (String explanation : explanations) {
          context.reportProblem(String.format("%s: PAP denied - %s", VALIDATOR_NAME, explanation));
        }
      } else {
        context.reportProblem(
            String.format("%s: PAP denied request (no explanation provided)", VALIDATOR_NAME));
      }

      monitor.warning(
          String.format(
              "%s: PAP denied request for scope '%s'. Explanations: %s",
              VALIDATOR_NAME, context.scope(), explanations));
      return false;

    } catch (Exception e) {
      monitor.warning(
          String.format(
              "%s: Error communicating with PAP for scope '%s': %s",
              VALIDATOR_NAME, context.scope(), e.getMessage()),
          e);

      if (denyOnError) {
        context.reportProblem(
            String.format("%s: PAP communication error - %s", VALIDATOR_NAME, e.getMessage()));
        return false;
      }
      return true;
    }
  }

  @Override
  public String name() {
    return VALIDATOR_NAME;
  }

  /**
   * Converts an EDC {@link Policy} into a {@code Map<String, Object>} suitable for the PAP's
   * validation request.
   *
   * <p>The conversion pipeline: Policy &rarr; JsonObject (via TypeTransformerRegistry) &rarr;
   * expanded JSON-LD (via JsonLd) &rarr; Map (via ObjectMapper).
   *
   * @param policy the EDC policy to convert
   * @return the policy as a map of JSON-LD properties
   * @throws IllegalArgumentException if the policy cannot be transformed or expanded
   */
  private Map<String, Object> convertPolicyToMap(Policy policy) {
    Result<JsonObject> transformResult = transformerRegistry.transform(policy, JsonObject.class);
    if (transformResult.failed()) {
      throw new IllegalArgumentException(
          String.format(
              "Failed to transform Policy to JsonObject: %s", transformResult.getFailureDetail()));
    }

    JsonObject compactJsonObject = transformResult.getContent();
    Result<JsonObject> expandResult = jsonLd.expand(compactJsonObject);
    if (expandResult.failed()) {
      throw new IllegalArgumentException(
          String.format("Failed to expand JSON-LD policy: %s", expandResult.getFailureDetail()));
    }

    JsonObject expandedPolicy = expandResult.getContent();
    try {
      return objectMapper.readValue(expandedPolicy.toString(), MAP_TYPE_REFERENCE);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Failed to convert expanded JSON-LD policy to map", e);
    }
  }
}
