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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.TransferProcessPolicyContext;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.participant.spi.ParticipantAgentPolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyValidatorRule;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.seamware.edc.pap.OdrlPapClient;
import org.seamware.pap.model.ValidationRequestVO;
import org.seamware.pap.model.ValidationResponseVO;

/**
 * A {@link PolicyValidatorRule} that delegates policy evaluation to an external ODRL-PAP service.
 *
 * <p>When registered as a pre-validator with the EDC {@code PolicyEngine}, this rule intercepts
 * policy evaluation before the built-in constraint functions run. It converts the EDC {@link
 * Policy} to expanded ODRL JSON-LD, maps the {@link PolicyContext} to a JSON payload input via
 * {@link PolicyContextInputMapper}, and calls the PAP's {@code POST /validate} endpoint.
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

  /** ODRL namespace IRI prefix. */
  static final String ODRL_NAMESPACE = "http://www.w3.org/ns/odrl/2/";

  /**
   * ODRL target key used when the policy has no target set. Ensures the PAP always receives a
   * target field, even for catalog-scope evaluations where EDC does not set one on the Policy
   * object.
   */
  static final String ODRL_TARGET_KEY = ODRL_NAMESPACE + "target";

  /** ODRL assignee key in expanded JSON-LD form. */
  static final String ODRL_ASSIGNEE_KEY = ODRL_NAMESPACE + "assignee";

  /** ODRL AssetCollection type IRI in expanded JSON-LD form. */
  static final String ODRL_ASSET_COLLECTION_TYPE = ODRL_NAMESPACE + "AssetCollection";

  /** ODRL refinement property IRI in expanded JSON-LD form. */
  static final String ODRL_REFINEMENT_KEY = ODRL_NAMESPACE + "refinement";

  /** ODRL Constraint type IRI in expanded JSON-LD form. */
  static final String ODRL_CONSTRAINT_TYPE = ODRL_NAMESPACE + "Constraint";

  /** ODRL leftOperand property IRI in expanded JSON-LD form. */
  static final String ODRL_LEFT_OPERAND_KEY = ODRL_NAMESPACE + "leftOperand";

  /** ODRL operator property IRI in expanded JSON-LD form. */
  static final String ODRL_OPERATOR_KEY = ODRL_NAMESPACE + "operator";

  /** ODRL eq operator IRI in expanded JSON-LD form. */
  static final String ODRL_EQ_OPERATOR = ODRL_NAMESPACE + "eq";

  /** ODRL rightOperand property IRI in expanded JSON-LD form. */
  static final String ODRL_RIGHT_OPERAND_KEY = ODRL_NAMESPACE + "rightOperand";

  /** ODRL PartyCollection type IRI in expanded JSON-LD form. */
  static final String ODRL_PARTY_COLLECTION_TYPE = ODRL_NAMESPACE + "PartyCollection";

  /** DCP Scope left operand IRI used in AssetCollection refinement constraints. */
  static final String DCP_SCOPE_OPERAND = "https://w3id.org/dspace/2024/1/scope";

  /** DCP participant left operand IRI used in PartyCollection refinement constraints. */
  static final String DCP_PARTICIPANT_OPERAND = "https://w3id.org/dspace/2024/1/participant";

  /** ODRL permission property IRI in expanded JSON-LD form. */
  static final String ODRL_PERMISSION_KEY = ODRL_NAMESPACE + "permission";

  /** ODRL constraint property IRI in expanded JSON-LD form (used within permissions). */
  static final String ODRL_CONSTRAINT_PROP_KEY = ODRL_NAMESPACE + "constraint";

  /**
   * Set of ODRL property IRIs that are constraint-level (appear inside constraint objects rather
   * than on the permission itself). Used to classify match conditions in scope mapping rules.
   */
  static final Set<String> CONSTRAINT_LEVEL_PROPERTIES =
      Set.of(
          ODRL_NAMESPACE + "leftOperand",
          ODRL_NAMESPACE + "operator",
          ODRL_NAMESPACE + "rightOperand",
          ODRL_NAMESPACE + "dataType",
          ODRL_NAMESPACE + "unit",
          ODRL_NAMESPACE + "status");

  private final OdrlPapClient odrlPapClient;
  private final TypeTransformerRegistry transformerRegistry;
  private final JsonLd jsonLd;
  private final PolicyContextInputMapper inputMapper;
  private final Monitor monitor;
  private final ObjectMapper objectMapper;
  private final boolean denyOnError;
  private final List<Map<String, Object>> additionalContexts;
  private final List<ScopeMappingRule> scopeMappingRules;

  /**
   * Creates a new ODRL-PAP policy validator.
   *
   * @param odrlPapClient the client for communicating with the ODRL-PAP service
   * @param transformerRegistry the EDC type transformer registry for converting {@link Policy} to
   *     {@link JsonObject}
   * @param jsonLd the JSON-LD processor for expanding compacted JSON-LD
   * @param inputMapper the mapper for converting {@link PolicyContext} to the appropriate PAP input
   *     format
   * @param monitor the EDC monitor for logging
   * @param objectMapper the Jackson object mapper for JSON serialization
   * @param denyOnError when {@code true}, PAP communication errors cause policy denial (fail-
   *     closed); when {@code false}, errors are logged and the policy is allowed (fail-open)
   * @param additionalContexts additional JSON-LD context objects to include in PAP validation
   *     requests for controlling term compaction (e.g., remapping {@code odrl:use} to {@code
   *     dcp:use}); may be empty but not {@code null}
   * @param scopeMappingRules rules that assign permissions to evaluation scopes based on their ODRL
   *     properties; used as a fallback when permissions lack explicit scope targets; may be empty
   *     but not {@code null}
   */
  public OdrlPapPolicyValidator(
      OdrlPapClient odrlPapClient,
      TypeTransformerRegistry transformerRegistry,
      JsonLd jsonLd,
      PolicyContextInputMapper inputMapper,
      Monitor monitor,
      ObjectMapper objectMapper,
      boolean denyOnError,
      List<Map<String, Object>> additionalContexts,
      List<ScopeMappingRule> scopeMappingRules) {
    this.odrlPapClient = odrlPapClient;
    this.transformerRegistry = transformerRegistry;
    this.jsonLd = jsonLd;
    this.inputMapper = inputMapper;
    this.monitor = monitor;
    this.objectMapper = objectMapper;
    this.denyOnError = denyOnError;
    this.additionalContexts = Collections.unmodifiableList(additionalContexts);
    this.scopeMappingRules = List.copyOf(scopeMappingRules);
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
   *   <li>Map the {@link PolicyContext} to a JSON input via the {@link PolicyContextInputMapper}
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
      if (policy.getPermissions() == null || policy.getPermissions().isEmpty()) {
        monitor.info(
            "When the policy does not contain permissions, there is nothing to be evaluated by the odrl-pap");
        return true;
      }
      Map<String, Object> policyMap = new HashMap<>(convertPolicyToMap(policy));
      filterPermissionsByScope(policyMap, context.scope());
      ensureOdrlDefaults(policyMap, policy, context);

      ValidationRequestVO validationRequest = new ValidationRequestVO();
      validationRequest.jsonInput(inputMapper.toJsonInput(context));
      validationRequest.policy(policyMap);
      if (!additionalContexts.isEmpty()) {
        validationRequest.additionalContexts(additionalContexts);
      }

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
          String.format("%s: PAP denied request for scope '%s'.", VALIDATOR_NAME, context.scope()));
      // monitor.debug(String.format("Explanations: %s", explanations));
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
   * Ensures the policy map contains properly structured {@code odrl:target} and {@code
   * odrl:assignee} fields.
   *
   * <ul>
   *   <li><b>Target</b>: always set to an {@code odrl:AssetCollection} with a {@code dcp:Scope}
   *       refinement constraint for the current evaluation scope.
   *   <li><b>Assignee</b>: if already an {@code odrl:PartyCollection}, left untouched. If a plain
   *       {@code @id} or string, the identity is extracted and wrapped in a PartyCollection with a
   *       {@code dcp:participant} refinement. If absent, resolved from the {@link
   *       ParticipantAgentPolicyContext}.
   * </ul>
   *
   * @param policyMap the mutable policy map to enrich
   * @param policy the source EDC policy (retained for signature compatibility)
   * @param context the policy context, used to resolve target scope and fallback assignee identity
   */
  private void ensureOdrlDefaults(
      Map<String, Object> policyMap, Policy policy, PolicyContext context) {
    policyMap.put(ODRL_TARGET_KEY, resolveTarget(context));
    policyMap.put(ODRL_ASSIGNEE_KEY, resolveAssignee(policyMap, context));
  }

  /**
   * Resolves the ODRL target from the policy context. For {@link TransferProcessPolicyContext}, the
   * target is the asset ID from the contract agreement. For other scopes where no specific asset ID
   * is available, an expanded JSON-LD {@code odrl:AssetCollection} with a {@code dcp:Scope}
   * refinement constraint is returned so the PAP can evaluate scope-based access rules.
   *
   * @param context the policy context
   * @return the resolved asset ID as a string, or an AssetCollection map for scope-based targets
   */
  private Object resolveTarget(PolicyContext context) {

    return buildScopeAssetCollection(context.scope());
  }

  /**
   * Builds an expanded JSON-LD {@code odrl:AssetCollection} with a refinement constraint that binds
   * the target to the given scope via {@code dcp:Scope}.
   *
   * <p>The returned structure in expanded JSON-LD form:
   *
   * <pre>{@code
   * {
   *   "@type": ["http://www.w3.org/ns/odrl/2/AssetCollection"],
   *   "http://www.w3.org/ns/odrl/2/refinement": [{
   *     "@type": ["http://www.w3.org/ns/odrl/2/Constraint"],
   *     "http://www.w3.org/ns/odrl/2/leftOperand": [{"@id": "https://w3id.org/dspace/2024/1/scope"}],
   *     "http://www.w3.org/ns/odrl/2/operator": [{"@id": "http://www.w3.org/ns/odrl/2/eq"}],
   *     "http://www.w3.org/ns/odrl/2/rightOperand": [{"@value": "<scope>"}]
   *   }]
   * }
   * }</pre>
   *
   * @param scope the policy context scope value (e.g. {@code "catalog"}, {@code
   *     "contract.negotiation"})
   * @return a map representing the expanded JSON-LD AssetCollection
   */
  private Map<String, Object> buildScopeAssetCollection(String scope) {
    Map<String, Object> constraint = new LinkedHashMap<>();
    constraint.put("@type", List.of(ODRL_CONSTRAINT_TYPE));
    constraint.put(ODRL_LEFT_OPERAND_KEY, List.of(Map.of("@id", DCP_SCOPE_OPERAND)));
    constraint.put(ODRL_OPERATOR_KEY, List.of(Map.of("@id", ODRL_EQ_OPERATOR)));
    constraint.put(ODRL_RIGHT_OPERAND_KEY, List.of(Map.of("@value", scope)));

    Map<String, Object> assetCollection = new LinkedHashMap<>();
    assetCollection.put("@type", List.of(ODRL_ASSET_COLLECTION_TYPE));
    assetCollection.put(ODRL_REFINEMENT_KEY, List.of(constraint));

    return assetCollection;
  }

  /**
   * Resolves the ODRL assignee to a properly structured value for the PAP. Three cases:
   *
   * <ol>
   *   <li>If the expanded policy already contains an {@code odrl:PartyCollection} assignee, it is
   *       returned as-is.
   *   <li>If the expanded policy contains a plain assignee (a bare {@code @id} string, e.g., from
   *       {@code "odrl:assignee": "did:web:consumer.example.com"}), that identity is extracted and
   *       wrapped in a {@code odrl:PartyCollection} with a {@code dcp:participant} refinement.
   *   <li>If no assignee is present, the identity is resolved from the {@link
   *       ParticipantAgentPolicyContext} and wrapped in a PartyCollection.
   * </ol>
   *
   * @param policyMap the expanded JSON-LD policy map, checked for an existing assignee value
   * @param context the policy context, used as fallback identity source
   * @return a PartyCollection map wrapping the participant identity, or the existing
   *     PartyCollection
   */
  private Object resolveAssignee(Map<String, Object> policyMap, PolicyContext context) {
    Object existing = policyMap.get(ODRL_ASSIGNEE_KEY);

    if (isPartyCollection(existing)) {
      return existing;
    }

    String existingIdentity = extractPlainAssigneeIdentity(existing);
    if (existingIdentity != null && !existingIdentity.isBlank()) {
      return buildParticipantPartyCollection(existingIdentity);
    }

    if (context instanceof ParticipantAgentPolicyContext agentContext) {
      String identity = agentContext.participantAgent().getIdentity();
      if (identity != null && !identity.isBlank()) {
        return buildParticipantPartyCollection(identity);
      }
    }
    throw new IllegalArgumentException("Requests without a participant identity cannot exist.");
  }

  /**
   * Checks whether a JSON-LD value is an {@code odrl:PartyCollection}. Handles both a direct map
   * and a single-element list wrapping a map (the expanded JSON-LD form).
   *
   * @param value the assignee value from the expanded policy map
   * @return {@code true} if the value is or contains a PartyCollection
   */
  private boolean isPartyCollection(Object value) {
    Map<?, ?> candidate = null;
    if (value instanceof Map<?, ?> map) {
      candidate = map;
    } else if (value instanceof List<?> list
        && !list.isEmpty()
        && list.get(0) instanceof Map<?, ?> map) {
      candidate = map;
    }
    if (candidate == null) {
      return false;
    }
    Object type = candidate.get("@type");
    if (type instanceof List<?> typeList) {
      return typeList.contains(ODRL_PARTY_COLLECTION_TYPE);
    }
    return ODRL_PARTY_COLLECTION_TYPE.equals(type);
  }

  /**
   * Extracts the assignee identity string from a plain expanded JSON-LD assignee value. Handles
   * bare strings, {@code @id} maps, and JSON-LD value arrays (e.g., {@code [{"@id":
   * "did:web:example.com"}]}). Does not extract from structured types like PartyCollection — use
   * {@link #isPartyCollection(Object)} to detect those first.
   *
   * @param assignee the raw assignee value from the expanded policy map
   * @return the identity string, or {@code null} if not extractable
   */
  private String extractPlainAssigneeIdentity(Object assignee) {
    if (assignee instanceof String s) {
      return s;
    }
    if (assignee instanceof List<?> list && !list.isEmpty()) {
      Object first = list.get(0);
      if (first instanceof Map<?, ?> map) {
        Object id = map.get("@id");
        if (id instanceof String s) {
          return s;
        }
      }
      if (first instanceof String s) {
        return s;
      }
    }
    if (assignee instanceof Map<?, ?> map) {
      Object id = map.get("@id");
      if (id instanceof String s) {
        return s;
      }
    }
    return null;
  }

  /**
   * Builds an expanded JSON-LD {@code odrl:PartyCollection} with a refinement constraint that binds
   * the assignee to the given participant identity via {@code dcp:participant}.
   *
   * <p>The returned structure in expanded JSON-LD form:
   *
   * <pre>{@code
   * {
   *   "@type": ["http://www.w3.org/ns/odrl/2/PartyCollection"],
   *   "http://www.w3.org/ns/odrl/2/refinement": [{
   *     "@type": ["http://www.w3.org/ns/odrl/2/Constraint"],
   *     "http://www.w3.org/ns/odrl/2/leftOperand": [{"@id": "https://w3id.org/dspace/2024/1/participant"}],
   *     "http://www.w3.org/ns/odrl/2/operator": [{"@id": "http://www.w3.org/ns/odrl/2/eq"}],
   *     "http://www.w3.org/ns/odrl/2/rightOperand": [{"@value": "<identity>"}]
   *   }]
   * }
   * }</pre>
   *
   * @param identity the participant identity (e.g. a DID like {@code
   *     "did:web:consumer.example.com"})
   * @return a map representing the expanded JSON-LD PartyCollection
   */
  private Map<String, Object> buildParticipantPartyCollection(String identity) {
    Map<String, Object> constraint = new LinkedHashMap<>();
    constraint.put("@type", List.of(ODRL_CONSTRAINT_TYPE));
    constraint.put(ODRL_LEFT_OPERAND_KEY, List.of(Map.of("@id", DCP_PARTICIPANT_OPERAND)));
    constraint.put(ODRL_OPERATOR_KEY, List.of(Map.of("@id", ODRL_EQ_OPERATOR)));
    constraint.put(ODRL_RIGHT_OPERAND_KEY, List.of(Map.of("@value", identity)));

    Map<String, Object> partyCollection = new LinkedHashMap<>();
    partyCollection.put("@type", List.of(ODRL_PARTY_COLLECTION_TYPE));
    partyCollection.put(ODRL_REFINEMENT_KEY, List.of(constraint));

    return partyCollection;
  }

  /**
   * Filters the policy's permissions to include only those relevant to the current evaluation
   * scope, using two-layer resolution: (1) explicit scope target on the permission, (2)
   * configuration-based scope mapping rules.
   *
   * <p>Permissions that do not match any layer are kept (evaluated in all scopes).
   *
   * @param policyMap the mutable expanded JSON-LD policy map
   * @param currentScope the current evaluation scope (e.g., {@code "catalog"}, {@code
   *     "contract.negotiation"})
   */
  @SuppressWarnings("unchecked")
  void filterPermissionsByScope(Map<String, Object> policyMap, String currentScope) {

    try {
      monitor.warning(
          "Scope: "
              + currentScope
              + " Filter the map: "
              + objectMapper.writeValueAsString(policyMap));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
    Object permissions = policyMap.get(ODRL_PERMISSION_KEY);
    if (!(permissions instanceof List<?> permList) || permList.isEmpty()) {
      return;
    }

    int originalCount = permList.size();
    List<?> filtered =
        permList.stream().filter(p -> isPermissionRelevantForScope(p, currentScope)).toList();

    if (filtered.size() < originalCount) {
      monitor.debug(
          () ->
              String.format(
                  "%s: Filtered %d permission(s) not relevant for scope '%s'. "
                      + "Evaluating %d of %d total permissions.",
                  VALIDATOR_NAME,
                  originalCount - filtered.size(),
                  currentScope,
                  filtered.size(),
                  originalCount));
    }

    policyMap.put(ODRL_PERMISSION_KEY, filtered);
  }

  /**
   * Determines whether a permission is relevant for the current scope using two-layer resolution.
   *
   * @param permission the permission object from the expanded JSON-LD policy
   * @param currentScope the current evaluation scope
   * @return {@code true} if the permission should be evaluated in this scope
   */
  private boolean isPermissionRelevantForScope(Object permission, String currentScope) {
    if (!(permission instanceof Map<?, ?> permMap)) {
      return true;
    }

    // Layer 1: explicit scope target on the permission
    Object target = permMap.get(ODRL_TARGET_KEY);
    String scopeFromTarget = extractScopeFromTarget(target);
    if (scopeFromTarget != null) {
      return currentScope.equals(scopeFromTarget);
    }

    // Layer 2: configuration-based scope mapping rules
    if (!scopeMappingRules.isEmpty()) {
      Set<String> resolvedScopes = resolveScopesFromRules(permMap);
      if (resolvedScopes != null) {
        return resolvedScopes.contains(currentScope);
      }
    }

    return true;
  }

  /**
   * Extracts the {@code dcp:Scope} value from a permission's {@code odrl:target} when it is an
   * {@code odrl:AssetCollection} with a scope refinement. Returns {@code null} if the target is not
   * a scope-refined AssetCollection.
   *
   * @param target the target value from the expanded JSON-LD permission
   * @return the scope string, or {@code null} if not a scope-refined target
   */
  @SuppressWarnings("unchecked")
  private String extractScopeFromTarget(Object target) {
    if (!(target instanceof Map<?, ?> targetMap)) {
      return extractScopeFromTargetList(target);
    }
    return extractScopeFromAssetCollection(targetMap);
  }

  @SuppressWarnings("unchecked")
  private String extractScopeFromTargetList(Object target) {
    if (!(target instanceof List<?> targetList) || targetList.isEmpty()) {
      return null;
    }
    Object first = targetList.get(0);
    if (first instanceof Map<?, ?> firstMap) {
      return extractScopeFromAssetCollection(firstMap);
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private String extractScopeFromAssetCollection(Map<?, ?> targetMap) {
    Object type = targetMap.get("@type");
    boolean isAssetCollection = false;
    if (type instanceof List<?> typeList) {
      isAssetCollection = typeList.contains(ODRL_ASSET_COLLECTION_TYPE);
    } else if (type instanceof String typeStr) {
      isAssetCollection = ODRL_ASSET_COLLECTION_TYPE.equals(typeStr);
    }
    if (!isAssetCollection) {
      return null;
    }

    Object refinements = targetMap.get(ODRL_REFINEMENT_KEY);
    if (!(refinements instanceof List<?> refinementList)) {
      return null;
    }

    for (Object refinement : refinementList) {
      if (!(refinement instanceof Map<?, ?> constraintMap)) {
        continue;
      }
      if (hasIdValue(constraintMap.get(ODRL_LEFT_OPERAND_KEY), DCP_SCOPE_OPERAND)) {
        return extractStringValue(constraintMap.get(ODRL_RIGHT_OPERAND_KEY));
      }
    }
    return null;
  }

  /**
   * Resolves scopes for a permission by evaluating all scope mapping rules and intersecting the
   * scopes of all matching rules. Returns {@code null} if no rules match.
   *
   * @param permMap the expanded JSON-LD permission map
   * @return the intersection of matching rule scopes, or {@code null} if no rules matched
   */
  private Set<String> resolveScopesFromRules(Map<?, ?> permMap) {
    Set<String> intersection = null;

    for (ScopeMappingRule rule : scopeMappingRules) {
      if (!ruleMatchesPermission(rule, permMap)) {
        continue;
      }
      if (intersection == null) {
        intersection = new HashSet<>(rule.scopes());
      } else {
        intersection.retainAll(rule.scopes());
      }
    }

    if (intersection != null && intersection.isEmpty()) {
      monitor.warning(
          String.format(
              "%s: Permission matched scope mapping rules with conflicting scopes. "
                  + "Evaluating in all scopes.",
              VALIDATOR_NAME));
      return null;
    }

    return intersection;
  }

  /**
   * Checks whether a scope mapping rule matches a permission. Permission-level conditions are
   * matched against the permission map. Constraint-level conditions must all be satisfied by the
   * same constraint within the permission.
   *
   * @param rule the scope mapping rule
   * @param permMap the expanded JSON-LD permission map
   * @return {@code true} if all conditions in the rule are satisfied
   */
  private boolean ruleMatchesPermission(ScopeMappingRule rule, Map<?, ?> permMap) {
    Map<String, String> permissionConditions = new LinkedHashMap<>();
    Map<String, String> constraintConditions = new LinkedHashMap<>();

    for (Map.Entry<String, String> condition : rule.match().entrySet()) {
      if (CONSTRAINT_LEVEL_PROPERTIES.contains(condition.getKey())) {
        constraintConditions.put(condition.getKey(), condition.getValue());
      } else {
        permissionConditions.put(condition.getKey(), condition.getValue());
      }
    }

    for (Map.Entry<String, String> cond : permissionConditions.entrySet()) {
      if (!propertyValueMatches(permMap, cond.getKey(), cond.getValue())) {
        return false;
      }
    }

    if (!constraintConditions.isEmpty()) {
      List<Map<?, ?>> constraints = extractConstraints(permMap);
      return constraints.stream()
          .anyMatch(
              constraint ->
                  constraintConditions.entrySet().stream()
                      .allMatch(
                          cond ->
                              propertyValueMatches(constraint, cond.getKey(), cond.getValue())));
    }

    return true;
  }

  /**
   * Checks whether a property in an expanded JSON-LD map matches the expected value. Handles both
   * {@code @id} and {@code @value} structures in the expanded form.
   *
   * @param map the expanded JSON-LD map (permission or constraint)
   * @param propertyIri the property IRI to look up
   * @param expectedValue the expected value to match against
   * @return {@code true} if the property has a matching value
   */
  private boolean propertyValueMatches(Map<?, ?> map, String propertyIri, String expectedValue) {
    Object value = map.get(propertyIri);
    if (value instanceof List<?> valueList) {
      return valueList.stream().anyMatch(item -> matchesItem(item, expectedValue));
    }
    return matchesItem(value, expectedValue);
  }

  private boolean matchesItem(Object item, String expectedValue) {
    if (item instanceof Map<?, ?> itemMap) {
      return expectedValue.equals(itemMap.get("@id"))
          || expectedValue.equals(itemMap.get("@value"));
    }
    return expectedValue.equals(item);
  }

  /**
   * Extracts constraint objects from a permission's {@code odrl:constraint} property.
   *
   * @param permMap the expanded JSON-LD permission map
   * @return the list of constraint maps, or an empty list if none
   */
  private List<Map<?, ?>> extractConstraints(Map<?, ?> permMap) {
    Object constraints = permMap.get(ODRL_CONSTRAINT_PROP_KEY);
    if (constraints instanceof List<?> constraintList) {
      List<Map<?, ?>> result = new ArrayList<>();
      for (Object c : constraintList) {
        if (c instanceof Map<?, ?> cMap) {
          result.add(cMap);
        }
      }
      return result;
    }
    return List.of();
  }

  /**
   * Checks whether a JSON-LD value list contains an entry with an {@code @id} matching the given
   * IRI.
   */
  private boolean hasIdValue(Object value, String expectedId) {
    if (value instanceof List<?> valueList) {
      return valueList.stream()
          .anyMatch(
              item -> item instanceof Map<?, ?> itemMap && expectedId.equals(itemMap.get("@id")));
    }
    return false;
  }

  /** Extracts the {@code @value} string from the first entry in a JSON-LD value list. */
  private String extractStringValue(Object value) {
    if (value instanceof List<?> valueList) {
      for (Object item : valueList) {
        if (item instanceof Map<?, ?> itemMap) {
          Object v = itemMap.get("@value");
          if (v instanceof String s) {
            return s;
          }
        }
      }
    }
    return null;
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
