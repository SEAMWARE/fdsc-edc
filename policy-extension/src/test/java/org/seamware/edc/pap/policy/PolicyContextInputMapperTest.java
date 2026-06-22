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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.eclipse.edc.connector.controlplane.catalog.spi.policy.CatalogPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.ContractNegotiationPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.TransferProcessPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.policy.engine.spi.PolicyContext;
import org.eclipse.edc.policy.model.Policy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.seamware.pap.model.GenericJsonInputVO;

/**
 * Unit tests for {@link PolicyContextInputMapper}.
 *
 * <p>Verifies that Layer 2 {@link org.eclipse.edc.participant.spi.ParticipantAgentPolicyContext}
 * subtypes are mapped to {@link GenericJsonInputVO} with the authenticated participant's identity
 * and credential claims in the subject.
 */
class PolicyContextInputMapperTest {

  private static final String TEST_IDENTITY = "did:web:provider.example.com";
  private static final String TEST_SCOPE_CATALOG = "catalog";
  private static final String TEST_SCOPE_NEGOTIATION = "contract.negotiation";
  private static final String TEST_SCOPE_TRANSFER = "transfer.process";
  private static final String TEST_AGREEMENT_ID = "agreement-123";
  private static final String TEST_ASSET_ID = "asset-xyz";
  private static final String TEST_PROVIDER_ID = "urn:connector:provider";
  private static final String TEST_CONSUMER_ID = "urn:connector:consumer";
  private static final long TEST_SIGNING_DATE = 1700000000L;

  private PolicyContextInputMapper mapper;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    mapper = new PolicyContextInputMapper(objectMapper);
  }

  @Nested
  @DisplayName("Participant agent context mapping (Layer 2)")
  class ParticipantAgentContextMapping {

    /** Provides Layer 2 context types with their expected scopes for parameterized tests. */
    static Stream<Arguments> layer2ContextTypes() {
      return Stream.of(
          Arguments.of("Catalog", CatalogPolicyContext.class, TEST_SCOPE_CATALOG),
          Arguments.of(
              "ContractNegotiation",
              ContractNegotiationPolicyContext.class,
              TEST_SCOPE_NEGOTIATION),
          Arguments.of("TransferProcess", TransferProcessPolicyContext.class, TEST_SCOPE_TRANSFER));
    }

    @ParameterizedTest(name = "{0} context includes scope in payload")
    @MethodSource("layer2ContextTypes")
    @DisplayName("Layer 2 context types include scope in payload")
    void toJsonInput_includesScopeInPayload(
        String label, Class<? extends PolicyContext> contextClass, String expectedScope) {
      PolicyContext context = createMockLayer2Context(contextClass, expectedScope);

      GenericJsonInputVO result = mapper.toJsonInput(context);

      assertNotNull(result);
      assertNotNull(result.getPayload());
      assertEquals(expectedScope, result.getPayload().get(PolicyContextInputMapper.KEY_SCOPE));
    }

    @ParameterizedTest(name = "{0} context extracts identity into subject")
    @MethodSource("layer2ContextTypes")
    @DisplayName("Layer 2 context types extract participant identity into subject")
    void toJsonInput_extractsIdentity(
        String label, Class<? extends PolicyContext> contextClass, String expectedScope) {
      PolicyContext context = createMockLayer2Context(contextClass, expectedScope);

      GenericJsonInputVO result = mapper.toJsonInput(context);

      assertNotNull(result.getSubject());
      assertEquals(TEST_IDENTITY, result.getSubject().get(PolicyContextInputMapper.KEY_IDENTITY));
    }

    @ParameterizedTest(name = "{0} context extracts claims into subject")
    @MethodSource("layer2ContextTypes")
    @DisplayName("Layer 2 context types extract participant claims into subject")
    @SuppressWarnings("unchecked")
    void toJsonInput_extractsClaims(
        String label, Class<? extends PolicyContext> contextClass, String expectedScope) {
      PolicyContext context = createMockLayer2Context(contextClass, expectedScope);

      GenericJsonInputVO result = mapper.toJsonInput(context);

      assertNotNull(result.getSubject());
      Map<String, Object> claims =
          (Map<String, Object>) result.getSubject().get(PolicyContextInputMapper.KEY_CLAIMS);
      assertNotNull(claims);
      assertEquals("value1", claims.get("claim1"));
    }

    @Test
    @DisplayName("Null identity is omitted from subject")
    void toJsonInput_nullIdentity_omittedFromSubject() {
      CatalogPolicyContext context = mock(CatalogPolicyContext.class);
      ParticipantAgent agent =
          new ParticipantAgent(null, Map.of("claim1", (Object) "value1"), Map.of());
      when(context.scope()).thenReturn(TEST_SCOPE_CATALOG);
      when(context.participantAgent()).thenReturn(agent);

      GenericJsonInputVO result = mapper.toJsonInput(context);

      assertNotNull(result.getSubject());
      assertNull(result.getSubject().get(PolicyContextInputMapper.KEY_IDENTITY));
    }

    @Test
    @DisplayName("Empty claims are omitted from subject")
    void toJsonInput_emptyClaims_omittedFromSubject() {
      CatalogPolicyContext context = mock(CatalogPolicyContext.class);
      ParticipantAgent agent = new ParticipantAgent(TEST_IDENTITY, Map.of(), Map.of());
      when(context.scope()).thenReturn(TEST_SCOPE_CATALOG);
      when(context.participantAgent()).thenReturn(agent);

      GenericJsonInputVO result = mapper.toJsonInput(context);

      assertNotNull(result.getSubject());
      assertEquals(TEST_IDENTITY, result.getSubject().get(PolicyContextInputMapper.KEY_IDENTITY));
      assertNull(result.getSubject().get(PolicyContextInputMapper.KEY_CLAIMS));
    }

    @Test
    @DisplayName("VerifiableCredential list in claims is serialized to JSON maps")
    @SuppressWarnings("unchecked")
    void toJsonInput_vcClaimsSerializedAsJsonMaps() {
      CatalogPolicyContext context = mock(CatalogPolicyContext.class);
      Map<String, Object> vcData =
          Map.of("type", List.of("VerifiableCredential", "MembershipCredential"));
      Map<String, Object> inputClaims = new java.util.HashMap<>();
      inputClaims.put("vc", List.of(vcData));
      inputClaims.put("other", "value");
      ParticipantAgent agent = new ParticipantAgent(TEST_IDENTITY, inputClaims, Map.of());
      when(context.scope()).thenReturn(TEST_SCOPE_CATALOG);
      when(context.participantAgent()).thenReturn(agent);

      GenericJsonInputVO result = mapper.toJsonInput(context);

      Map<String, Object> claims =
          (Map<String, Object>) result.getSubject().get(PolicyContextInputMapper.KEY_CLAIMS);
      assertNotNull(claims);
      List<Map<String, Object>> vcList = (List<Map<String, Object>>) claims.get("vc");
      assertNotNull(vcList);
      assertEquals(1, vcList.size());
      List<String> types = (List<String>) vcList.get(0).get("type");
      assertTrue(types.contains("MembershipCredential"));
      assertEquals("value", claims.get("other"));
    }
  }

  @Nested
  @DisplayName("Transfer process context enrichment")
  class TransferProcessEnrichment {

    @Test
    @DisplayName("Transfer context includes contract agreement in payload")
    @SuppressWarnings("unchecked")
    void toJsonInput_transferContext_includesContractAgreement() {
      TransferProcessPolicyContext context = createMockTransferContext();

      GenericJsonInputVO result = mapper.toJsonInput(context);

      Map<String, Object> payload = result.getPayload();
      Map<String, Object> agreementMap =
          (Map<String, Object>) payload.get(PolicyContextInputMapper.KEY_CONTRACT_AGREEMENT);
      assertNotNull(agreementMap);
      assertEquals(TEST_AGREEMENT_ID, agreementMap.get(PolicyContextInputMapper.KEY_AGREEMENT_ID));
      assertEquals(
          TEST_ASSET_ID, agreementMap.get(PolicyContextInputMapper.KEY_AGREEMENT_ASSET_ID));
      assertEquals(
          TEST_PROVIDER_ID, agreementMap.get(PolicyContextInputMapper.KEY_AGREEMENT_PROVIDER_ID));
      assertEquals(
          TEST_CONSUMER_ID, agreementMap.get(PolicyContextInputMapper.KEY_AGREEMENT_CONSUMER_ID));
      assertEquals(
          TEST_SIGNING_DATE, agreementMap.get(PolicyContextInputMapper.KEY_AGREEMENT_SIGNING_DATE));
    }

    @Test
    @DisplayName("Transfer context includes evaluation timestamp in payload")
    void toJsonInput_transferContext_includesNow() {
      TransferProcessPolicyContext context = createMockTransferContext();

      GenericJsonInputVO result = mapper.toJsonInput(context);

      assertNotNull(result.getPayload().get(PolicyContextInputMapper.KEY_NOW));
    }

    @Test
    @DisplayName("Transfer context with null agreement omits agreement from payload")
    void toJsonInput_transferContext_nullAgreement_omitted() {
      TransferProcessPolicyContext context = mock(TransferProcessPolicyContext.class);
      ParticipantAgent agent = new ParticipantAgent(TEST_IDENTITY, Map.of(), Map.of());
      when(context.scope()).thenReturn(TEST_SCOPE_TRANSFER);

      when(context.participantAgent()).thenReturn(agent);
      when(context.contractAgreement()).thenReturn(null);
      when(context.now()).thenReturn(null);

      GenericJsonInputVO result = mapper.toJsonInput(context);

      assertNull(result.getPayload().get(PolicyContextInputMapper.KEY_CONTRACT_AGREEMENT));
      assertNull(result.getPayload().get(PolicyContextInputMapper.KEY_NOW));
    }

    /**
     * Creates a mock transfer process context with a contract agreement and evaluation timestamp.
     */
    private TransferProcessPolicyContext createMockTransferContext() {
      TransferProcessPolicyContext context = mock(TransferProcessPolicyContext.class);
      ParticipantAgent agent =
          new ParticipantAgent(TEST_IDENTITY, Map.of("claim1", (Object) "value1"), Map.of());
      ContractAgreement agreement =
          ContractAgreement.Builder.newInstance()
              .id(TEST_AGREEMENT_ID)
              .assetId(TEST_ASSET_ID)
              .providerId(TEST_PROVIDER_ID)
              .consumerId(TEST_CONSUMER_ID)
              .contractSigningDate(TEST_SIGNING_DATE)
              .policy(Policy.Builder.newInstance().build())
              .build();

      when(context.scope()).thenReturn(TEST_SCOPE_TRANSFER);
      when(context.participantAgent()).thenReturn(agent);
      when(context.contractAgreement()).thenReturn(agreement);
      when(context.now()).thenReturn(Instant.ofEpochSecond(TEST_SIGNING_DATE));
      return context;
    }
  }

  @Nested
  @DisplayName("Non-participant-agent context mapping")
  class GenericContextMapping {

    @Test
    @DisplayName("Non-ParticipantAgentPolicyContext includes only scope in payload")
    void toJsonInput_genericContext_onlyScope() {
      String customScope = "custom.scope";
      PolicyContext context = mock(PolicyContext.class);
      when(context.scope()).thenReturn(customScope);

      GenericJsonInputVO result = mapper.toJsonInput(context);

      assertNotNull(result);
      assertEquals(customScope, result.getPayload().get(PolicyContextInputMapper.KEY_SCOPE));
    }

    @Test
    @DisplayName("Non-ParticipantAgentPolicyContext has no subject")
    void toJsonInput_genericContext_noSubject() {
      PolicyContext context = mock(PolicyContext.class);
      when(context.scope()).thenReturn("custom.scope");

      GenericJsonInputVO result = mapper.toJsonInput(context);

      assertTrue(
          result.getSubject() == null || result.getSubject().isEmpty(),
          "Subject should be absent for non-participant-agent context types");
    }
  }

  /** Creates a mock Layer 2 context of the given type with a standard {@link ParticipantAgent}. */
  private PolicyContext createMockLayer2Context(
      Class<? extends PolicyContext> contextClass, String scope) {
    if (contextClass == CatalogPolicyContext.class) {
      CatalogPolicyContext ctx = mock(CatalogPolicyContext.class);
      when(ctx.scope()).thenReturn(scope);
      when(ctx.participantAgent()).thenReturn(createTestAgent());
      return ctx;
    } else if (contextClass == ContractNegotiationPolicyContext.class) {
      ContractNegotiationPolicyContext ctx = mock(ContractNegotiationPolicyContext.class);
      when(ctx.scope()).thenReturn(scope);
      when(ctx.participantAgent()).thenReturn(createTestAgent());
      return ctx;
    } else if (contextClass == TransferProcessPolicyContext.class) {
      TransferProcessPolicyContext ctx = mock(TransferProcessPolicyContext.class);
      when(ctx.scope()).thenReturn(scope);
      when(ctx.participantAgent()).thenReturn(createTestAgent());
      when(ctx.contractAgreement()).thenReturn(null);
      when(ctx.now()).thenReturn(null);
      return ctx;
    }
    throw new IllegalArgumentException("Unknown context class: " + contextClass);
  }

  /** Creates a {@link ParticipantAgent} with standard test identity and claims. */
  private ParticipantAgent createTestAgent() {
    return new ParticipantAgent(TEST_IDENTITY, Map.of("claim1", (Object) "value1"), Map.of());
  }
}
