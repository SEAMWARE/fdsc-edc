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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.edc.connector.controlplane.catalog.spi.policy.CatalogPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.TransferProcessPolicyContext;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.participant.spi.ParticipantAgent;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.seamware.edc.pap.OdrlPapClient;
import org.seamware.pap.model.GenericJsonInputVO;
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

  private static final String TEST_SCOPE = "catalog";
  private static final String DEFAULT_PARTICIPANT_IDENTITY = "did:web:default-test.example.com";

  @Mock private OdrlPapClient odrlPapClient;
  @Mock private TypeTransformerRegistry transformerRegistry;
  @Mock private JsonLd jsonLd;
  @Mock private PolicyContextInputMapper inputMapper;
  @Mock private Monitor monitor;

  @Mock private ObjectMapper objectMapper;

  private Policy policy;
  private CatalogPolicyContext context;

  @BeforeEach
  void setUp() throws Exception {
    policy = Policy.Builder.newInstance().build();
    policy.getPermissions().add(Permission.Builder.newInstance().build());
    context = mock(CatalogPolicyContext.class);
    lenient().when(context.scope()).thenReturn(TEST_SCOPE);
    ParticipantAgent defaultAgent =
        new ParticipantAgent(DEFAULT_PARTICIPANT_IDENTITY, Map.of(), Map.of());
    lenient().when(context.participantAgent()).thenReturn(defaultAgent);
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
    when(inputMapper.toJsonInput(any(PolicyContext.class)))
        .thenReturn(new GenericJsonInputVO().payload(Map.of()));
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
              inputMapper,
              monitor,
              objectMapper,
              false,
              List.of(),
              List.of());

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
              inputMapper,
              monitor,
              objectMapper,
              false,
              List.of(),
              List.of());

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
              inputMapper,
              monitor,
              objectMapper,
              false,
              List.of(),
              List.of());

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
              inputMapper,
              monitor,
              objectMapper,
              true,
              List.of(),
              List.of());
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
              inputMapper,
              monitor,
              objectMapper,
              false,
              List.of(),
              List.of());
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
  @DisplayName("Context-dependent input mapping")
  class ContextDependentMapping {

    private OdrlPapPolicyValidator validator;

    @BeforeEach
    void setUp() {
      validator =
          new OdrlPapPolicyValidator(
              odrlPapClient,
              transformerRegistry,
              jsonLd,
              inputMapper,
              monitor,
              objectMapper,
              false,
              List.of(),
              List.of());
    }

    @Test
    @DisplayName("Uses jsonInput for policy context evaluation")
    void apply_usesJsonInput() throws Exception {
      setupSuccessfulPolicyConversion();
      ValidationResponseVO response = new ValidationResponseVO().allow(true);
      when(odrlPapClient.validate(any(ValidationRequestVO.class))).thenReturn(response);

      Boolean result = validator.apply(policy, context);

      assertTrue(result);
      verify(inputMapper).toJsonInput(context);
    }
  }

  @Nested
  @DisplayName("Additional contexts forwarding")
  class AdditionalContextsForwarding {

    @Test
    @DisplayName("includes additional contexts in validation request when configured")
    void apply_withAdditionalContexts_setsThemOnRequest() throws Exception {
      Map<String, Object> scopedContext =
          Map.of(
              "odrl:action",
              Map.of(
                  "@id", "http://www.w3.org/ns/odrl/2/action",
                  "@type", "@id",
                  "@context",
                      Map.of(
                          "dcp", Map.of("@id", "http://www.w3.org/ns/odrl/2/", "@prefix", true))));
      List<Map<String, Object>> contexts = List.of(scopedContext);

      OdrlPapPolicyValidator validator =
          new OdrlPapPolicyValidator(
              odrlPapClient,
              transformerRegistry,
              jsonLd,
              inputMapper,
              monitor,
              objectMapper,
              false,
              contexts,
              List.of());

      setupSuccessfulPolicyConversion();
      ValidationResponseVO response = new ValidationResponseVO().allow(true);
      when(odrlPapClient.validate(any(ValidationRequestVO.class))).thenReturn(response);

      validator.apply(policy, context);

      ArgumentCaptor<ValidationRequestVO> captor =
          ArgumentCaptor.forClass(ValidationRequestVO.class);
      verify(odrlPapClient).validate(captor.capture());
      assertEquals(contexts, captor.getValue().getAdditionalContexts());
    }

    @Test
    @DisplayName("does not set additional contexts on request when list is empty")
    void apply_withEmptyContexts_doesNotSetOnRequest() throws Exception {
      OdrlPapPolicyValidator validator =
          new OdrlPapPolicyValidator(
              odrlPapClient,
              transformerRegistry,
              jsonLd,
              inputMapper,
              monitor,
              objectMapper,
              false,
              List.of(),
              List.of());

      setupSuccessfulPolicyConversion();
      ValidationResponseVO response = new ValidationResponseVO().allow(true);
      when(odrlPapClient.validate(any(ValidationRequestVO.class))).thenReturn(response);

      validator.apply(policy, context);

      ArgumentCaptor<ValidationRequestVO> captor =
          ArgumentCaptor.forClass(ValidationRequestVO.class);
      verify(odrlPapClient).validate(captor.capture());
      assertEquals(new ArrayList<>(), captor.getValue().getAdditionalContexts());
    }
  }

  @Nested
  @DisplayName("ODRL target and assignee resolution")
  class OdrlFieldResolution {

    private static final String TEST_PARTICIPANT_IDENTITY = "did:web:consumer.example.com";
    private static final String TEST_ASSET_ID = "asset-from-agreement";
    private static final String TEST_CONSUMER_ID = "urn:connector:consumer";
    private static final String TEST_PROVIDER_ID = "urn:connector:provider";

    private OdrlPapPolicyValidator validator;

    @BeforeEach
    void setUp() {
      validator =
          new OdrlPapPolicyValidator(
              odrlPapClient,
              transformerRegistry,
              jsonLd,
              inputMapper,
              monitor,
              objectMapper,
              false,
              List.of(),
              List.of());
    }

    @Test
    @DisplayName(
        "Resolves assignee as PartyCollection with dcp:participant refinement in catalog scope")
    void apply_catalogContext_resolvesAssigneeAsPartyCollection() throws Exception {
      CatalogPolicyContext catalogContext = mock(CatalogPolicyContext.class);
      lenient().when(catalogContext.scope()).thenReturn(TEST_SCOPE);
      ParticipantAgent agent = new ParticipantAgent(TEST_PARTICIPANT_IDENTITY, Map.of(), Map.of());
      when(catalogContext.participantAgent()).thenReturn(agent);

      setupSuccessfulPolicyConversion();
      ValidationResponseVO response = new ValidationResponseVO().allow(true);
      when(odrlPapClient.validate(any(ValidationRequestVO.class))).thenReturn(response);

      validator.apply(policy, catalogContext);

      ArgumentCaptor<ValidationRequestVO> captor =
          ArgumentCaptor.forClass(ValidationRequestVO.class);
      verify(odrlPapClient).validate(captor.capture());
      Map<String, Object> sentPolicy = captor.getValue().getPolicy();
      Object assignee = sentPolicy.get(OdrlPapPolicyValidator.ODRL_ASSIGNEE_KEY);
      assertPartyCollectionWithParticipant(assignee, TEST_PARTICIPANT_IDENTITY);
    }

    @Test
    @DisplayName("Resolves target as scope-based AssetCollection in transfer scope")
    void apply_transferContext_resolvesTargetAsAssetCollection() throws Exception {
      TransferProcessPolicyContext transferContext = mock(TransferProcessPolicyContext.class);
      when(transferContext.scope()).thenReturn("transfer.process");
      ParticipantAgent agent = new ParticipantAgent(TEST_PARTICIPANT_IDENTITY, Map.of(), Map.of());
      when(transferContext.participantAgent()).thenReturn(agent);

      setupSuccessfulPolicyConversion();
      ValidationResponseVO response = new ValidationResponseVO().allow(true);
      when(odrlPapClient.validate(any(ValidationRequestVO.class))).thenReturn(response);

      validator.apply(policy, transferContext);

      ArgumentCaptor<ValidationRequestVO> captor =
          ArgumentCaptor.forClass(ValidationRequestVO.class);
      verify(odrlPapClient).validate(captor.capture());
      Map<String, Object> sentPolicy = captor.getValue().getPolicy();
      Object target = sentPolicy.get(OdrlPapPolicyValidator.ODRL_TARGET_KEY);
      assertAssetCollectionWithScope(target, "transfer.process");
      Object assignee = sentPolicy.get(OdrlPapPolicyValidator.ODRL_ASSIGNEE_KEY);
      assertPartyCollectionWithParticipant(assignee, TEST_PARTICIPANT_IDENTITY);
    }

    @Test
    @DisplayName(
        "Uses AssetCollection with dcp:Scope refinement as target in catalog scope (no asset ID"
            + " available)")
    void apply_catalogContext_assetCollectionAsTarget() throws Exception {
      CatalogPolicyContext catalogContext = mock(CatalogPolicyContext.class);
      when(catalogContext.scope()).thenReturn(TEST_SCOPE);
      ParticipantAgent agent = new ParticipantAgent(TEST_PARTICIPANT_IDENTITY, Map.of(), Map.of());
      when(catalogContext.participantAgent()).thenReturn(agent);

      setupSuccessfulPolicyConversion();
      ValidationResponseVO response = new ValidationResponseVO().allow(true);
      when(odrlPapClient.validate(any(ValidationRequestVO.class))).thenReturn(response);

      validator.apply(policy, catalogContext);

      ArgumentCaptor<ValidationRequestVO> captor =
          ArgumentCaptor.forClass(ValidationRequestVO.class);
      verify(odrlPapClient).validate(captor.capture());
      Map<String, Object> sentPolicy = captor.getValue().getPolicy();
      Object target = sentPolicy.get(OdrlPapPolicyValidator.ODRL_TARGET_KEY);
      assertAssetCollectionWithScope(target, TEST_SCOPE);
    }

    @Test
    @DisplayName("Always sets target to scope-based AssetCollection regardless of policy target")
    void apply_withTarget_setsAssetCollection() throws Exception {
      Policy policyWithTarget = Policy.Builder.newInstance().target("asset-123").build();
      policyWithTarget.getPermissions().add(Permission.Builder.newInstance().build());

      setupSuccessfulPolicyConversion();
      ValidationResponseVO response = new ValidationResponseVO().allow(true);
      when(odrlPapClient.validate(any(ValidationRequestVO.class))).thenReturn(response);

      validator.apply(policyWithTarget, context);

      ArgumentCaptor<ValidationRequestVO> captor =
          ArgumentCaptor.forClass(ValidationRequestVO.class);
      verify(odrlPapClient).validate(captor.capture());
      Map<String, Object> sentPolicy = captor.getValue().getPolicy();
      Object target = sentPolicy.get(OdrlPapPolicyValidator.ODRL_TARGET_KEY);
      assertAssetCollectionWithScope(target, TEST_SCOPE);
    }

    @Test
    @DisplayName("Falls back to context identity when expanded policy has no assignee in the map")
    void apply_withAssigneeOnPolicyObject_fallsBackToContextIdentity() throws Exception {
      Policy policyWithAssignee =
          Policy.Builder.newInstance().assignee("did:web:consumer.example.com").build();
      policyWithAssignee.getPermissions().add(Permission.Builder.newInstance().build());

      setupSuccessfulPolicyConversion();
      ValidationResponseVO response = new ValidationResponseVO().allow(true);
      when(odrlPapClient.validate(any(ValidationRequestVO.class))).thenReturn(response);

      CatalogPolicyContext catalogContext = mock(CatalogPolicyContext.class);
      lenient().when(catalogContext.scope()).thenReturn(TEST_SCOPE);
      ParticipantAgent agent = new ParticipantAgent(TEST_PARTICIPANT_IDENTITY, Map.of(), Map.of());
      when(catalogContext.participantAgent()).thenReturn(agent);

      validator.apply(policyWithAssignee, catalogContext);

      ArgumentCaptor<ValidationRequestVO> captor =
          ArgumentCaptor.forClass(ValidationRequestVO.class);
      verify(odrlPapClient).validate(captor.capture());
      Map<String, Object> sentPolicy = captor.getValue().getPolicy();
      Object assignee = sentPolicy.get(OdrlPapPolicyValidator.ODRL_ASSIGNEE_KEY);
      assertPartyCollectionWithParticipant(assignee, TEST_PARTICIPANT_IDENTITY);
    }

    @Test
    @DisplayName("Wraps @id-based assignee from expanded policy in PartyCollection")
    void apply_withIdAssigneeInExpandedPolicy_wrapsInPartyCollection() throws Exception {
      String assigneeDid = "did:web:existing-assignee.example.com";
      Map<String, Object> expandedPolicyWithAssignee =
          Map.of(OdrlPapPolicyValidator.ODRL_ASSIGNEE_KEY, List.of(Map.of("@id", assigneeDid)));

      JsonObject compactJson = Json.createObjectBuilder().add("@type", "odrl:Set").build();
      JsonObject expandedJson =
          Json.createObjectBuilder().add("http://www.w3.org/ns/odrl/2/type", "Set").build();
      when(transformerRegistry.transform(any(Policy.class), eq(JsonObject.class)))
          .thenReturn(Result.success(compactJson));
      when(jsonLd.expand(any(JsonObject.class))).thenReturn(Result.success(expandedJson));
      when(inputMapper.toJsonInput(any(PolicyContext.class)))
          .thenReturn(new GenericJsonInputVO().payload(Map.of()));
      when(objectMapper.readValue(anyString(), any(TypeReference.class)))
          .thenReturn(expandedPolicyWithAssignee);

      ValidationResponseVO response = new ValidationResponseVO().allow(true);
      when(odrlPapClient.validate(any(ValidationRequestVO.class))).thenReturn(response);

      validator.apply(policy, context);

      ArgumentCaptor<ValidationRequestVO> captor =
          ArgumentCaptor.forClass(ValidationRequestVO.class);
      verify(odrlPapClient).validate(captor.capture());
      Map<String, Object> sentPolicy = captor.getValue().getPolicy();
      Object assignee = sentPolicy.get(OdrlPapPolicyValidator.ODRL_ASSIGNEE_KEY);
      assertPartyCollectionWithParticipant(assignee, assigneeDid);
    }

    @Test
    @DisplayName("Preserves existing PartyCollection assignee without modification")
    void apply_withPartyCollectionAssignee_keepsUntouched() throws Exception {
      String assigneeDid = "did:web:party-collection-assignee.example.com";

      Map<String, Object> refinementConstraint = new LinkedHashMap<>();
      refinementConstraint.put("@type", List.of(OdrlPapPolicyValidator.ODRL_CONSTRAINT_TYPE));
      refinementConstraint.put(
          OdrlPapPolicyValidator.ODRL_LEFT_OPERAND_KEY,
          List.of(Map.of("@id", OdrlPapPolicyValidator.DCP_PARTICIPANT_OPERAND)));
      refinementConstraint.put(
          OdrlPapPolicyValidator.ODRL_OPERATOR_KEY,
          List.of(Map.of("@id", OdrlPapPolicyValidator.ODRL_EQ_OPERATOR)));
      refinementConstraint.put(
          OdrlPapPolicyValidator.ODRL_RIGHT_OPERAND_KEY, List.of(Map.of("@value", assigneeDid)));

      Map<String, Object> partyCollection = new LinkedHashMap<>();
      partyCollection.put("@type", List.of(OdrlPapPolicyValidator.ODRL_PARTY_COLLECTION_TYPE));
      partyCollection.put(
          OdrlPapPolicyValidator.ODRL_REFINEMENT_KEY, List.of(refinementConstraint));

      Map<String, Object> expandedPolicyWithPartyCollection =
          Map.of(OdrlPapPolicyValidator.ODRL_ASSIGNEE_KEY, partyCollection);

      JsonObject compactJson = Json.createObjectBuilder().add("@type", "odrl:Set").build();
      JsonObject expandedJson =
          Json.createObjectBuilder().add("http://www.w3.org/ns/odrl/2/type", "Set").build();
      when(transformerRegistry.transform(any(Policy.class), eq(JsonObject.class)))
          .thenReturn(Result.success(compactJson));
      when(jsonLd.expand(any(JsonObject.class))).thenReturn(Result.success(expandedJson));
      when(inputMapper.toJsonInput(any(PolicyContext.class)))
          .thenReturn(new GenericJsonInputVO().payload(Map.of()));
      when(objectMapper.readValue(anyString(), any(TypeReference.class)))
          .thenReturn(expandedPolicyWithPartyCollection);

      ValidationResponseVO response = new ValidationResponseVO().allow(true);
      when(odrlPapClient.validate(any(ValidationRequestVO.class))).thenReturn(response);

      validator.apply(policy, context);

      ArgumentCaptor<ValidationRequestVO> captor =
          ArgumentCaptor.forClass(ValidationRequestVO.class);
      verify(odrlPapClient).validate(captor.capture());
      Map<String, Object> sentPolicy = captor.getValue().getPolicy();
      Object assignee = sentPolicy.get(OdrlPapPolicyValidator.ODRL_ASSIGNEE_KEY);
      assertPartyCollectionWithParticipant(assignee, assigneeDid);
    }

    @Test
    @DisplayName("Uses AssetCollection with dcp:Scope refinement in transfer scope")
    void apply_transferContext_assetCollectionAsTarget() throws Exception {
      TransferProcessPolicyContext transferContext = mock(TransferProcessPolicyContext.class);
      when(transferContext.scope()).thenReturn("transfer.process");
      ParticipantAgent agent = new ParticipantAgent(TEST_PARTICIPANT_IDENTITY, Map.of(), Map.of());
      when(transferContext.participantAgent()).thenReturn(agent);

      setupSuccessfulPolicyConversion();
      ValidationResponseVO response = new ValidationResponseVO().allow(true);
      when(odrlPapClient.validate(any(ValidationRequestVO.class))).thenReturn(response);

      validator.apply(policy, transferContext);

      ArgumentCaptor<ValidationRequestVO> captor =
          ArgumentCaptor.forClass(ValidationRequestVO.class);
      verify(odrlPapClient).validate(captor.capture());
      Map<String, Object> sentPolicy = captor.getValue().getPolicy();
      Object target = sentPolicy.get(OdrlPapPolicyValidator.ODRL_TARGET_KEY);
      assertAssetCollectionWithScope(target, "transfer.process");
    }
  }

  @Nested
  @DisplayName("Scope-based permission filtering")
  class ScopeFiltering {

    private static final String ODRL_NS = "http://www.w3.org/ns/odrl/2/";
    private static final String DCP_SCOPE = "https://w3id.org/dspace/2024/1/scope";

    private static Map<String, Object> buildScopeTarget(String scope) {
      Map<String, Object> constraint = new LinkedHashMap<>();
      constraint.put("@type", List.of(ODRL_NS + "Constraint"));
      constraint.put(ODRL_NS + "leftOperand", List.of(Map.of("@id", DCP_SCOPE)));
      constraint.put(ODRL_NS + "operator", List.of(Map.of("@id", ODRL_NS + "eq")));
      constraint.put(ODRL_NS + "rightOperand", List.of(Map.of("@value", scope)));

      Map<String, Object> assetCollection = new LinkedHashMap<>();
      assetCollection.put("@type", List.of(ODRL_NS + "AssetCollection"));
      assetCollection.put(ODRL_NS + "refinement", List.of(constraint));
      return assetCollection;
    }

    private static Map<String, Object> buildConstraint(
        String leftOperand, String operator, String rightOperand) {
      Map<String, Object> constraint = new LinkedHashMap<>();
      constraint.put(ODRL_NS + "leftOperand", List.of(Map.of("@id", leftOperand)));
      constraint.put(ODRL_NS + "operator", List.of(Map.of("@id", operator)));
      constraint.put(ODRL_NS + "rightOperand", List.of(Map.of("@value", rightOperand)));
      return constraint;
    }

    private static Map<String, Object> buildPermission(
        String action, Map<String, Object> target, List<Map<String, Object>> constraints) {
      Map<String, Object> perm = new LinkedHashMap<>();
      perm.put(ODRL_NS + "action", List.of(Map.of("@id", action)));
      if (target != null) {
        perm.put(ODRL_NS + "target", List.of(target));
      }
      if (constraints != null && !constraints.isEmpty()) {
        perm.put(ODRL_NS + "constraint", new ArrayList<>(constraints));
      }
      return perm;
    }

    @Nested
    @DisplayName("Layer 1: Explicit scope targets")
    class ExplicitScopeTargets {

      @Test
      @DisplayName("includes permission whose scope target matches current scope")
      void includesMatchingScope() {
        OdrlPapPolicyValidator validator =
            new OdrlPapPolicyValidator(
                odrlPapClient,
                transformerRegistry,
                jsonLd,
                inputMapper,
                monitor,
                objectMapper,
                false,
                List.of(),
                List.of());

        Map<String, Object> perm =
            buildPermission(
                ODRL_NS + "use",
                buildScopeTarget("contract.negotiation"),
                List.of(buildConstraint("Membership", ODRL_NS + "eq", "gold")));

        Map<String, Object> policyMap = new HashMap<>();
        policyMap.put(ODRL_NS + "permission", new ArrayList<>(List.of(perm)));

        validator.filterPermissionsByScope(policyMap, "contract.negotiation");

        List<?> remaining = (List<?>) policyMap.get(ODRL_NS + "permission");
        assertEquals(1, remaining.size());
      }

      @Test
      @DisplayName("excludes permission whose scope target does not match current scope")
      void excludesNonMatchingScope() {
        OdrlPapPolicyValidator validator =
            new OdrlPapPolicyValidator(
                odrlPapClient,
                transformerRegistry,
                jsonLd,
                inputMapper,
                monitor,
                objectMapper,
                false,
                List.of(),
                List.of());

        Map<String, Object> perm =
            buildPermission(
                ODRL_NS + "use",
                buildScopeTarget("transfer.process"),
                List.of(buildConstraint(ODRL_NS + "dateTime", ODRL_NS + "gteq", "08:00")));

        Map<String, Object> policyMap = new HashMap<>();
        policyMap.put(ODRL_NS + "permission", new ArrayList<>(List.of(perm)));

        validator.filterPermissionsByScope(policyMap, "contract.negotiation");

        List<?> remaining = (List<?>) policyMap.get(ODRL_NS + "permission");
        assertTrue(remaining.isEmpty());
      }

      @Test
      @DisplayName("keeps permission without target in all scopes")
      void keepsUntargetedPermission() {
        OdrlPapPolicyValidator validator =
            new OdrlPapPolicyValidator(
                odrlPapClient,
                transformerRegistry,
                jsonLd,
                inputMapper,
                monitor,
                objectMapper,
                false,
                List.of(),
                List.of());

        Map<String, Object> perm =
            buildPermission(
                ODRL_NS + "use",
                null,
                List.of(buildConstraint(ODRL_NS + "count", ODRL_NS + "lteq", "1000")));

        Map<String, Object> policyMap = new HashMap<>();
        policyMap.put(ODRL_NS + "permission", new ArrayList<>(List.of(perm)));

        validator.filterPermissionsByScope(policyMap, "catalog");

        List<?> remaining = (List<?>) policyMap.get(ODRL_NS + "permission");
        assertEquals(1, remaining.size());
      }

      @Test
      @DisplayName("filters mixed permissions: keeps matching and unscoped, excludes non-matching")
      void filtersMixedPermissions() {
        OdrlPapPolicyValidator validator =
            new OdrlPapPolicyValidator(
                odrlPapClient,
                transformerRegistry,
                jsonLd,
                inputMapper,
                monitor,
                objectMapper,
                false,
                List.of(),
                List.of());

        Map<String, Object> negotiationPerm =
            buildPermission(
                ODRL_NS + "use",
                buildScopeTarget("contract.negotiation"),
                List.of(buildConstraint("Membership", ODRL_NS + "eq", "gold")));
        Map<String, Object> transferPerm =
            buildPermission(
                ODRL_NS + "use",
                buildScopeTarget("transfer.process"),
                List.of(buildConstraint(ODRL_NS + "dayOfWeek", ODRL_NS + "isNoneOf", "Saturday")));
        Map<String, Object> globalPerm =
            buildPermission(
                ODRL_NS + "use",
                null,
                List.of(buildConstraint(ODRL_NS + "count", ODRL_NS + "lteq", "1000")));

        Map<String, Object> policyMap = new HashMap<>();
        policyMap.put(
            ODRL_NS + "permission",
            new ArrayList<>(List.of(negotiationPerm, transferPerm, globalPerm)));

        validator.filterPermissionsByScope(policyMap, "contract.negotiation");

        List<?> remaining = (List<?>) policyMap.get(ODRL_NS + "permission");
        assertEquals(2, remaining.size());
        assertTrue(remaining.contains(negotiationPerm));
        assertTrue(remaining.contains(globalPerm));
      }
    }

    @Nested
    @DisplayName("Layer 2: Configuration-based scope mapping rules")
    class ConfigBasedRules {

      @Test
      @DisplayName("matches permission by constraint leftOperand rule")
      void matchesByLeftOperand() {
        List<ScopeMappingRule> rules =
            List.of(
                new ScopeMappingRule(
                    Map.of(ODRL_NS + "leftOperand", "Membership"),
                    List.of("contract.negotiation")));

        OdrlPapPolicyValidator validator =
            new OdrlPapPolicyValidator(
                odrlPapClient,
                transformerRegistry,
                jsonLd,
                inputMapper,
                monitor,
                objectMapper,
                false,
                List.of(),
                rules);

        Map<String, Object> perm =
            buildPermission(
                ODRL_NS + "use",
                null,
                List.of(buildConstraint("Membership", ODRL_NS + "eq", "gold")));

        Map<String, Object> policyMap = new HashMap<>();
        policyMap.put(ODRL_NS + "permission", new ArrayList<>(List.of(perm)));

        validator.filterPermissionsByScope(policyMap, "contract.negotiation");
        assertEquals(1, ((List<?>) policyMap.get(ODRL_NS + "permission")).size());

        policyMap.put(ODRL_NS + "permission", new ArrayList<>(List.of(perm)));
        validator.filterPermissionsByScope(policyMap, "transfer.process");
        assertTrue(((List<?>) policyMap.get(ODRL_NS + "permission")).isEmpty());
      }

      @Test
      @DisplayName("matches permission by action rule")
      void matchesByAction() {
        List<ScopeMappingRule> rules =
            List.of(
                new ScopeMappingRule(
                    Map.of(ODRL_NS + "action", ODRL_NS + "transfer"), List.of("transfer.process")));

        OdrlPapPolicyValidator validator =
            new OdrlPapPolicyValidator(
                odrlPapClient,
                transformerRegistry,
                jsonLd,
                inputMapper,
                monitor,
                objectMapper,
                false,
                List.of(),
                rules);

        Map<String, Object> perm =
            buildPermission(
                ODRL_NS + "transfer",
                null,
                List.of(buildConstraint(ODRL_NS + "dateTime", ODRL_NS + "gteq", "08:00")));

        Map<String, Object> policyMap = new HashMap<>();
        policyMap.put(ODRL_NS + "permission", new ArrayList<>(List.of(perm)));

        validator.filterPermissionsByScope(policyMap, "transfer.process");
        assertEquals(1, ((List<?>) policyMap.get(ODRL_NS + "permission")).size());

        policyMap.put(ODRL_NS + "permission", new ArrayList<>(List.of(perm)));
        validator.filterPermissionsByScope(policyMap, "catalog");
        assertTrue(((List<?>) policyMap.get(ODRL_NS + "permission")).isEmpty());
      }

      @Test
      @DisplayName("matches permission by rightOperand rule")
      void matchesByRightOperand() {
        List<ScopeMappingRule> rules =
            List.of(
                new ScopeMappingRule(
                    Map.of(ODRL_NS + "rightOperand", "gold"), List.of("contract.negotiation")));

        OdrlPapPolicyValidator validator =
            new OdrlPapPolicyValidator(
                odrlPapClient,
                transformerRegistry,
                jsonLd,
                inputMapper,
                monitor,
                objectMapper,
                false,
                List.of(),
                rules);

        Map<String, Object> perm =
            buildPermission(
                ODRL_NS + "use",
                null,
                List.of(buildConstraint("Membership", ODRL_NS + "eq", "gold")));

        Map<String, Object> policyMap = new HashMap<>();
        policyMap.put(ODRL_NS + "permission", new ArrayList<>(List.of(perm)));

        validator.filterPermissionsByScope(policyMap, "contract.negotiation");
        assertEquals(1, ((List<?>) policyMap.get(ODRL_NS + "permission")).size());
      }

      @Test
      @DisplayName("multi-condition rule requires all conditions on same constraint")
      void multiConditionSameConstraint() {
        List<ScopeMappingRule> rules =
            List.of(
                new ScopeMappingRule(
                    Map.of(
                        ODRL_NS + "leftOperand",
                        ODRL_NS + "dateTime",
                        ODRL_NS + "operator",
                        ODRL_NS + "gteq"),
                    List.of("transfer.process")));

        OdrlPapPolicyValidator validator =
            new OdrlPapPolicyValidator(
                odrlPapClient,
                transformerRegistry,
                jsonLd,
                inputMapper,
                monitor,
                objectMapper,
                false,
                List.of(),
                rules);

        // Constraint matches both conditions
        Map<String, Object> matchingPerm =
            buildPermission(
                ODRL_NS + "use",
                null,
                List.of(buildConstraint(ODRL_NS + "dateTime", ODRL_NS + "gteq", "08:00")));

        Map<String, Object> policyMap = new HashMap<>();
        policyMap.put(ODRL_NS + "permission", new ArrayList<>(List.of(matchingPerm)));
        validator.filterPermissionsByScope(policyMap, "transfer.process");
        assertEquals(1, ((List<?>) policyMap.get(ODRL_NS + "permission")).size());

        // Constraint has dateTime but different operator — should not match
        Map<String, Object> nonMatchingPerm =
            buildPermission(
                ODRL_NS + "use",
                null,
                List.of(buildConstraint(ODRL_NS + "dateTime", ODRL_NS + "lteq", "17:00")));

        policyMap.put(ODRL_NS + "permission", new ArrayList<>(List.of(nonMatchingPerm)));
        validator.filterPermissionsByScope(policyMap, "transfer.process");
        // No rule matched → kept in all scopes
        assertEquals(1, ((List<?>) policyMap.get(ODRL_NS + "permission")).size());
      }

      @Test
      @DisplayName("conflicting rules cause permission to be kept in all scopes")
      void conflictingRulesKeepInAllScopes() {
        List<ScopeMappingRule> rules =
            List.of(
                new ScopeMappingRule(
                    Map.of(ODRL_NS + "leftOperand", "Membership"), List.of("contract.negotiation")),
                new ScopeMappingRule(
                    Map.of(ODRL_NS + "leftOperand", ODRL_NS + "dayOfWeek"),
                    List.of("transfer.process")));

        OdrlPapPolicyValidator validator =
            new OdrlPapPolicyValidator(
                odrlPapClient,
                transformerRegistry,
                jsonLd,
                inputMapper,
                monitor,
                objectMapper,
                false,
                List.of(),
                rules);

        // Permission with constraints matching rules in different scopes
        Map<String, Object> perm =
            buildPermission(
                ODRL_NS + "use",
                null,
                List.of(
                    buildConstraint("Membership", ODRL_NS + "eq", "gold"),
                    buildConstraint(ODRL_NS + "dayOfWeek", ODRL_NS + "isNoneOf", "Saturday")));

        Map<String, Object> policyMap = new HashMap<>();
        policyMap.put(ODRL_NS + "permission", new ArrayList<>(List.of(perm)));

        // Kept in both scopes because of conflict
        validator.filterPermissionsByScope(policyMap, "contract.negotiation");
        assertEquals(1, ((List<?>) policyMap.get(ODRL_NS + "permission")).size());

        policyMap.put(ODRL_NS + "permission", new ArrayList<>(List.of(perm)));
        validator.filterPermissionsByScope(policyMap, "transfer.process");
        assertEquals(1, ((List<?>) policyMap.get(ODRL_NS + "permission")).size());

        verify(monitor, org.mockito.Mockito.atLeastOnce()).warning(contains("conflicting scopes"));
      }

      @Test
      @DisplayName("no matching rules keeps permission in all scopes")
      void noMatchingRulesKeepsInAllScopes() {
        List<ScopeMappingRule> rules =
            List.of(
                new ScopeMappingRule(
                    Map.of(ODRL_NS + "leftOperand", "Membership"),
                    List.of("contract.negotiation")));

        OdrlPapPolicyValidator validator =
            new OdrlPapPolicyValidator(
                odrlPapClient,
                transformerRegistry,
                jsonLd,
                inputMapper,
                monitor,
                objectMapper,
                false,
                List.of(),
                rules);

        Map<String, Object> perm =
            buildPermission(
                ODRL_NS + "use",
                null,
                List.of(buildConstraint("SomeCustom", ODRL_NS + "eq", "value")));

        Map<String, Object> policyMap = new HashMap<>();
        policyMap.put(ODRL_NS + "permission", new ArrayList<>(List.of(perm)));

        validator.filterPermissionsByScope(policyMap, "catalog");
        assertEquals(1, ((List<?>) policyMap.get(ODRL_NS + "permission")).size());
      }

      @Test
      @DisplayName("agreeing rules from multiple constraints assign correct scope")
      void agreeingRulesAssignScope() {
        List<ScopeMappingRule> rules =
            List.of(
                new ScopeMappingRule(
                    Map.of(ODRL_NS + "leftOperand", ODRL_NS + "dateTime"),
                    List.of("transfer.process")),
                new ScopeMappingRule(
                    Map.of(ODRL_NS + "leftOperand", ODRL_NS + "dayOfWeek"),
                    List.of("transfer.process")));

        OdrlPapPolicyValidator validator =
            new OdrlPapPolicyValidator(
                odrlPapClient,
                transformerRegistry,
                jsonLd,
                inputMapper,
                monitor,
                objectMapper,
                false,
                List.of(),
                rules);

        Map<String, Object> perm =
            buildPermission(
                ODRL_NS + "use",
                null,
                List.of(
                    buildConstraint(ODRL_NS + "dateTime", ODRL_NS + "gteq", "08:00"),
                    buildConstraint(ODRL_NS + "dayOfWeek", ODRL_NS + "isNoneOf", "Saturday")));

        Map<String, Object> policyMap = new HashMap<>();
        policyMap.put(ODRL_NS + "permission", new ArrayList<>(List.of(perm)));

        validator.filterPermissionsByScope(policyMap, "transfer.process");
        assertEquals(1, ((List<?>) policyMap.get(ODRL_NS + "permission")).size());

        policyMap.put(ODRL_NS + "permission", new ArrayList<>(List.of(perm)));
        validator.filterPermissionsByScope(policyMap, "contract.negotiation");
        assertTrue(((List<?>) policyMap.get(ODRL_NS + "permission")).isEmpty());
      }
    }

    @Nested
    @DisplayName("Layer precedence")
    class LayerPrecedence {

      @Test
      @DisplayName("explicit scope target takes precedence over config rules")
      void explicitTargetOverridesConfigRules() {
        List<ScopeMappingRule> rules =
            List.of(
                new ScopeMappingRule(
                    Map.of(ODRL_NS + "leftOperand", "Membership"), List.of("transfer.process")));

        OdrlPapPolicyValidator validator =
            new OdrlPapPolicyValidator(
                odrlPapClient,
                transformerRegistry,
                jsonLd,
                inputMapper,
                monitor,
                objectMapper,
                false,
                List.of(),
                rules);

        // Permission has explicit target for negotiation, but rule says transfer
        Map<String, Object> perm =
            buildPermission(
                ODRL_NS + "use",
                buildScopeTarget("contract.negotiation"),
                List.of(buildConstraint("Membership", ODRL_NS + "eq", "gold")));

        Map<String, Object> policyMap = new HashMap<>();
        policyMap.put(ODRL_NS + "permission", new ArrayList<>(List.of(perm)));

        // Layer 1 wins: included in negotiation
        validator.filterPermissionsByScope(policyMap, "contract.negotiation");
        assertEquals(1, ((List<?>) policyMap.get(ODRL_NS + "permission")).size());

        // Layer 1 wins: excluded from transfer (despite config rule)
        policyMap.put(ODRL_NS + "permission", new ArrayList<>(List.of(perm)));
        validator.filterPermissionsByScope(policyMap, "transfer.process");
        assertTrue(((List<?>) policyMap.get(ODRL_NS + "permission")).isEmpty());
      }
    }

    @Nested
    @DisplayName("Backward compatibility")
    class BackwardCompatibility {

      @Test
      @DisplayName("no-op when no permissions in policy")
      void noOpWhenNoPermissions() {
        OdrlPapPolicyValidator validator =
            new OdrlPapPolicyValidator(
                odrlPapClient,
                transformerRegistry,
                jsonLd,
                inputMapper,
                monitor,
                objectMapper,
                false,
                List.of(),
                List.of());

        Map<String, Object> policyMap = new HashMap<>();

        validator.filterPermissionsByScope(policyMap, "catalog");

        assertNull(policyMap.get(ODRL_NS + "permission"));
      }

      @Test
      @DisplayName("no-op when no scope rules and no scope targets")
      void noOpWithoutRulesOrTargets() {
        OdrlPapPolicyValidator validator =
            new OdrlPapPolicyValidator(
                odrlPapClient,
                transformerRegistry,
                jsonLd,
                inputMapper,
                monitor,
                objectMapper,
                false,
                List.of(),
                List.of());

        Map<String, Object> perm1 =
            buildPermission(
                ODRL_NS + "use",
                null,
                List.of(buildConstraint("Membership", ODRL_NS + "eq", "gold")));
        Map<String, Object> perm2 =
            buildPermission(
                ODRL_NS + "use",
                null,
                List.of(buildConstraint(ODRL_NS + "dayOfWeek", ODRL_NS + "isNoneOf", "Saturday")));

        Map<String, Object> policyMap = new HashMap<>();
        policyMap.put(ODRL_NS + "permission", new ArrayList<>(List.of(perm1, perm2)));

        validator.filterPermissionsByScope(policyMap, "catalog");

        List<?> remaining = (List<?>) policyMap.get(ODRL_NS + "permission");
        assertEquals(2, remaining.size());
      }
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
              inputMapper,
              monitor,
              objectMapper,
              false,
              List.of(),
              List.of());

      assertEquals(OdrlPapPolicyValidator.VALIDATOR_NAME, validator.name());
    }
  }

  @SuppressWarnings("unchecked")
  private static void assertAssetCollectionWithScope(Object target, String expectedScope) {
    assertInstanceOf(Map.class, target);
    Map<String, Object> targetMap = (Map<String, Object>) target;

    assertEquals(
        List.of(OdrlPapPolicyValidator.ODRL_ASSET_COLLECTION_TYPE), targetMap.get("@type"));

    List<Map<String, Object>> refinements =
        (List<Map<String, Object>>) targetMap.get(OdrlPapPolicyValidator.ODRL_REFINEMENT_KEY);
    assertEquals(1, refinements.size());

    Map<String, Object> constraint = refinements.get(0);
    assertEquals(List.of(OdrlPapPolicyValidator.ODRL_CONSTRAINT_TYPE), constraint.get("@type"));
    assertEquals(
        List.of(Map.of("@id", OdrlPapPolicyValidator.DCP_SCOPE_OPERAND)),
        constraint.get(OdrlPapPolicyValidator.ODRL_LEFT_OPERAND_KEY));
    assertEquals(
        List.of(Map.of("@id", OdrlPapPolicyValidator.ODRL_EQ_OPERATOR)),
        constraint.get(OdrlPapPolicyValidator.ODRL_OPERATOR_KEY));
    assertEquals(
        List.of(Map.of("@value", expectedScope)),
        constraint.get(OdrlPapPolicyValidator.ODRL_RIGHT_OPERAND_KEY));
  }

  @SuppressWarnings("unchecked")
  private static void assertPartyCollectionWithParticipant(
      Object assignee, String expectedIdentity) {
    assertInstanceOf(Map.class, assignee);
    Map<String, Object> assigneeMap = (Map<String, Object>) assignee;

    assertEquals(
        List.of(OdrlPapPolicyValidator.ODRL_PARTY_COLLECTION_TYPE), assigneeMap.get("@type"));

    List<Map<String, Object>> refinements =
        (List<Map<String, Object>>) assigneeMap.get(OdrlPapPolicyValidator.ODRL_REFINEMENT_KEY);
    assertEquals(1, refinements.size());

    Map<String, Object> constraint = refinements.get(0);
    assertEquals(List.of(OdrlPapPolicyValidator.ODRL_CONSTRAINT_TYPE), constraint.get("@type"));
    assertEquals(
        List.of(Map.of("@id", OdrlPapPolicyValidator.DCP_PARTICIPANT_OPERAND)),
        constraint.get(OdrlPapPolicyValidator.ODRL_LEFT_OPERAND_KEY));
    assertEquals(
        List.of(Map.of("@id", OdrlPapPolicyValidator.ODRL_EQ_OPERATOR)),
        constraint.get(OdrlPapPolicyValidator.ODRL_OPERATOR_KEY));
    assertEquals(
        List.of(Map.of("@value", expectedIdentity)),
        constraint.get(OdrlPapPolicyValidator.ODRL_RIGHT_OPERAND_KEY));
  }
}
