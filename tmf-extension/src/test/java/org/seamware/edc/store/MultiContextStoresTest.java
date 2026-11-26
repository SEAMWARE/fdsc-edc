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
package org.seamware.edc.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.seamware.edc.TMFConfig.ALL_PARTICIPANT_CONTEXTS;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.catalog.spi.Catalog;
import org.eclipse.edc.connector.controlplane.catalog.spi.CatalogRequestMessage;
import org.eclipse.edc.connector.controlplane.catalog.spi.Dataset;
import org.eclipse.edc.connector.controlplane.catalog.spi.DatasetRequestMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.ContractOfferId;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractDefinition;
import org.eclipse.edc.connector.controlplane.services.spi.protocol.ProtocolTokenValidator;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.query.CriterionOperatorRegistryImpl;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceFailure;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.types.domain.message.RemoteMessage;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.seamware.edc.SchemaBaseUriHolder;
import org.seamware.edc.domain.ExtendableProductOffering;
import org.seamware.edc.domain.ExtendableProductSpecification;
import org.seamware.edc.domain.ExtendableProductSpecificationRef;
import org.seamware.edc.tmf.DuplicateIdException;
import org.seamware.edc.tmf.ParticipantResolver;
import org.seamware.edc.tmf.ProductCatalogApiClient;

/**
 * Multi-dataspace behaviour of the read side over one shared TMForum (decisions 3 and 4): the real
 * mapper behind the asset index, the contract-definition store and the catalog service, with only
 * the TMForum client mocked.
 */
public class MultiContextStoresTest extends AbstractStoreTest {

  private static final String DEFAULT_CONTEXT = "urn:connector:fdsc-edc";
  private static final String CONTEXT_A = "did:web:dataspace-a";
  private static final String CONTEXT_B = "did:web:dataspace-b";
  private static final String CONTEXT_C = "did:web:dataspace-c";

  private static final String OFFER_ID = "offer";
  private static final String OFFER_ID_A = "offer-a";
  private static final String OFFER_ID_B = "offer-b";
  private static final String POLICY_UID = "policy-uid";
  private static final String SPEC_ID = "spec-tmf-id";
  private static final String OTHER_ASSET_ID = "other-asset-id";
  private static final String OTHER_SPEC_ID = "other-spec-tmf-id";
  private static final String PARTICIPANT_CONTEXT_ID_PROPERTY = "participantContextId";
  private static final String ID_PROPERTY = "id";
  private static final String EQUALS_OPERATOR = "=";
  private static final String OFFER_PART_SEPARATOR = "|";

  private static final List<String> A_AND_B = List.of(CONTEXT_A, CONTEXT_B);
  private static final List<String> ALL = List.of(ALL_PARTICIPANT_CONTEXTS);

  private Monitor monitor;
  private ProductCatalogApiClient productCatalogApi;
  private ProtocolTokenValidator protocolTokenValidator;
  private TMFEdcMapper tmfEdcMapper;
  private TMFBackedContractDefinitionStore contractDefinitionStore;
  private TMFBackedAssetIndex assetIndex;
  private TMForumBackedCatalogProtocolService catalogService;
  private final List<ExtendableProductOffering> offerings = new ArrayList<>();
  private final TokenRepresentation token =
      TokenRepresentation.Builder.newInstance().token("a-token").build();

  @BeforeEach
  public void setup() {
    SchemaBaseUriHolder.configure(URI.create("http://base.schema"));
    monitor = mock(Monitor.class);
    productCatalogApi = mock(ProductCatalogApiClient.class);
    protocolTokenValidator = mock(ProtocolTokenValidator.class);
    JsonLd jsonLd = mock(JsonLd.class);
    TypeTransformerRegistry typeTransformerRegistry = mock(TypeTransformerRegistry.class);
    PolicyEngine policyEngine = mock(PolicyEngine.class);

    when(jsonLd.expand(any())).thenReturn(Result.success(getEmptyOdrlContract()));
    when(typeTransformerRegistry.transform(any(), eq(Policy.class)))
        .thenReturn(Result.success(getTestPolicy(POLICY_UID)));
    when(policyEngine.evaluate(any(), any())).thenReturn(Result.success());
    when(protocolTokenValidator.verify(any(), eq(token), any(), any(RemoteMessage.class)))
        .thenReturn(ServiceResult.success(mock(ParticipantAgent.class)));
    givenTMForumServesOfferings();

    ObjectMapper objectMapper = new ObjectMapper();
    tmfEdcMapper =
        new TMFEdcMapper(
            monitor,
            objectMapper,
            mock(ParticipantResolver.class),
            typeTransformerRegistry,
            jsonLd,
            Clock.systemUTC(),
            DEFAULT_CONTEXT);
    contractDefinitionStore =
        new TMFBackedContractDefinitionStore(
            monitor,
            new ContractDefinitionIdResolver(monitor, productCatalogApi, tmfEdcMapper),
            tmfEdcMapper,
            productCatalogApi,
            CriterionOperatorRegistryImpl.ofDefaults());
    assetIndex =
        new TMFBackedAssetIndex(
            monitor, productCatalogApi, tmfEdcMapper, CriterionOperatorRegistryImpl.ofDefaults());
    catalogService =
        new TMForumBackedCatalogProtocolService(
            tmfEdcMapper,
            productCatalogApi,
            protocolTokenValidator,
            policyEngine,
            monitor,
            objectMapper);
  }

  // ---- CONTRACT DEFINITION STORE ----

  private static Stream<Arguments> definitionLookups() {
    String qualifiedA = ContextQualifiedId.qualify(OFFER_ID, CONTEXT_A);
    String qualifiedB = ContextQualifiedId.qualify(OFFER_ID, CONTEXT_B);
    String qualifiedC = ContextQualifiedId.qualify(OFFER_ID, CONTEXT_C);
    return Stream.of(
        Arguments.of("legacy, plain id", null, OFFER_ID, DEFAULT_CONTEXT),
        Arguments.of("legacy, qualified B", null, qualifiedB, null),
        Arguments.of("context A, plain id", List.of(CONTEXT_A), OFFER_ID, CONTEXT_A),
        Arguments.of("context B, plain id", List.of(CONTEXT_B), OFFER_ID, CONTEXT_B),
        Arguments.of("single context, qualified id", List.of(CONTEXT_A), qualifiedA, null),
        Arguments.of("A and B, qualified A", A_AND_B, qualifiedA, CONTEXT_A),
        Arguments.of("A and B, qualified B", A_AND_B, qualifiedB, CONTEXT_B),
        Arguments.of("A and B, qualified C", A_AND_B, qualifiedC, null),
        Arguments.of("A and B, plain id", A_AND_B, OFFER_ID, null),
        Arguments.of("all, qualified A", ALL, qualifiedA, CONTEXT_A),
        Arguments.of("all, qualified C", ALL, qualifiedC, CONTEXT_C),
        Arguments.of("all, plain id", ALL, OFFER_ID, null));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("definitionLookups")
  public void testFindDefinition_resolvesTheIdToOneContext(
      String name, List<String> tags, String requestedId, String expectedContext) {
    offerings.add(offering(OFFER_ID, tags));

    ContractDefinition definition = contractDefinitionStore.findById(requestedId);

    if (expectedContext == null) {
      assertNull(definition, "The id must not resolve.");
      return;
    }
    assertNotNull(definition, "The id must resolve.");
    assertEquals(requestedId, definition.getId(), "The definition keeps the requested id.");
    assertEquals(expectedContext, definition.getParticipantContextId());
    String expectedPolicyId =
        OFFER_ID.equals(requestedId)
            ? POLICY_UID
            : ContextQualifiedId.qualify(POLICY_UID, expectedContext);
    assertEquals(expectedPolicyId, definition.getContractPolicyId());
    assertEquals(expectedPolicyId, definition.getAccessPolicyId());
  }

  @Test
  public void testFindDefinition_externalIdContainingTheSeparatorResolvesAsPlainId() {
    String externalId = ContextQualifiedId.qualify(OFFER_ID, "v2");
    offerings.add(offering(externalId, List.of(CONTEXT_A)));

    ContractDefinition definition = contractDefinitionStore.findById(externalId);

    assertNotNull(definition);
    assertEquals(externalId, definition.getId());
    assertEquals(CONTEXT_A, definition.getParticipantContextId());
  }

  @Test
  public void testFindDefinition_ambiguousIdResolvesToNullAndIsLoggedSevere() {
    String qualifiedA = ContextQualifiedId.qualify(OFFER_ID, CONTEXT_A);
    offerings.add(offering(qualifiedA, List.of(CONTEXT_A)));
    offerings.add(offering(OFFER_ID, A_AND_B));

    assertNull(contractDefinitionStore.findById(qualifiedA));
    verify(monitor).severe(contains("ambiguous"));
  }

  private static Stream<Arguments> unaddressablePlainHits() {
    return Stream.of(Arguments.of(A_AND_B), Arguments.of(ALL));
  }

  @ParameterizedTest(name = "plain hit tagged {0}")
  @MethodSource("unaddressablePlainHits")
  public void testFindDefinition_unaddressablePlainHitFallsBackToTheQualifiedId(
      List<String> plainHitTags) {
    String qualifiedA = ContextQualifiedId.qualify(OFFER_ID, CONTEXT_A);
    offerings.add(offering(qualifiedA, plainHitTags));
    offerings.add(offering(OFFER_ID, A_AND_B));

    ContractDefinition definition = contractDefinitionStore.findById(qualifiedA);

    assertNotNull(definition, "The qualified id of the multi-context offering must resolve.");
    assertEquals(qualifiedA, definition.getId());
    assertEquals(CONTEXT_A, definition.getParticipantContextId());
  }

  @Test
  public void testFindDefinition_externalIdUsedInTwoContextsIsNullAndLoggedSevere() {
    when(productCatalogApi.getProductOfferingByExternalId(OFFER_ID))
        .thenThrow(new DuplicateIdException("offer used in A and B"));

    assertNull(contractDefinitionStore.findById(OFFER_ID));
    verify(monitor).severe(anyString(), any(DuplicateIdException.class));
  }

  // ---- ASSET INDEX ----

  private static Stream<Arguments> queriedContexts() {
    return Stream.of(
        Arguments.of(CONTEXT_A, CONTEXT_A),
        Arguments.of(CONTEXT_B, CONTEXT_B),
        Arguments.of(null, DEFAULT_CONTEXT));
  }

  @ParameterizedTest(name = "queried {0} -> stamped {1}")
  @MethodSource("queriedContexts")
  public void testQueryAssets_listsEveryProductStampedWithTheQueriedContext(
      String queriedContext, String expectedContext) {
    givenSpecs();
    QuerySpec.Builder query = QuerySpec.Builder.newInstance();
    if (queriedContext != null) {
      query.filter(contextCriterion(queriedContext));
    }

    List<Asset> assets = assetIndex.queryAssets(query.build()).toList();

    assertEquals(
        Set.of(TEST_ASSET_ID, OTHER_ASSET_ID),
        assets.stream().map(Asset::getId).collect(Collectors.toSet()),
        "Products are shared, so every context lists all of them.");
    assets.forEach(a -> assertEquals(expectedContext, a.getParticipantContextId()));
  }

  @ParameterizedTest(name = "context {0}")
  @MethodSource("contextsAAndB")
  public void testQueryAssets_managementApiIdLookupReturnsTheAssetInTheQueriedContext(
      String context) {
    givenSpecs();
    QuerySpec query =
        QuerySpec.Builder.newInstance()
            .filter(
                List.of(
                    contextCriterion(context),
                    new Criterion(ID_PROPERTY, EQUALS_OPERATOR, OTHER_ASSET_ID)))
            .build();

    List<Asset> assets = assetIndex.queryAssets(query).toList();

    assertEquals(1, assets.size());
    assertEquals(OTHER_ASSET_ID, assets.getFirst().getId());
    assertEquals(context, assets.getFirst().getParticipantContextId());
  }

  private static Stream<String> contextsAAndB() {
    return Stream.of(CONTEXT_A, CONTEXT_B);
  }

  // ---- CATALOG ----

  private static Stream<Arguments> productOfferedInAAndB() {
    return Stream.of(
        Arguments.of(CONTEXT_A, Optional.of(OFFER_ID_A)),
        Arguments.of(CONTEXT_B, Optional.of(OFFER_ID_B)),
        Arguments.of(CONTEXT_C, Optional.empty()));
  }

  @ParameterizedTest(name = "requested in {0}")
  @MethodSource("productOfferedInAAndB")
  public void testCatalog_productOfferedInAAndBListsOnlyTheRequestingContextsOffer(
      String context, Optional<String> expectedDefinitionId) {
    givenSpecs();
    offerings.add(offering(OFFER_ID_A, List.of(CONTEXT_A)));
    offerings.add(offering(OFFER_ID_B, List.of(CONTEXT_B)));

    assertOffers(context, expectedDefinitionId);
  }

  private static Stream<Arguments> legacyOffering() {
    return Stream.of(
        Arguments.of(DEFAULT_CONTEXT, Optional.of(OFFER_ID)),
        Arguments.of(CONTEXT_A, Optional.empty()),
        Arguments.of(CONTEXT_B, Optional.empty()));
  }

  @ParameterizedTest(name = "requested in {0}")
  @MethodSource("legacyOffering")
  public void testCatalog_untaggedOfferingIsOfferedOnlyInTheDefaultContext(
      String context, Optional<String> expectedDefinitionId) {
    givenSpecs();
    offerings.add(offering(OFFER_ID, null));

    assertOffers(context, expectedDefinitionId);
  }

  @Test
  public void testCatalog_productOfferedOnlyInAIsMissingFromB() {
    givenSpecs();
    offerings.add(offering(OFFER_ID_A, List.of(CONTEXT_A)));

    assertOffers(CONTEXT_B, Optional.empty());
  }

  private static Stream<Arguments> offeringInSeveralDataspaces() {
    return Stream.of(A_AND_B, ALL)
        .flatMap(
            tags ->
                Stream.of(CONTEXT_A, CONTEXT_B, CONTEXT_C)
                    .map(
                        ctx ->
                            Arguments.of(
                                tags,
                                ctx,
                                tags.contains(ctx) || tags.equals(ALL)
                                    ? Optional.of(ContextQualifiedId.qualify(OFFER_ID, ctx))
                                    : Optional.empty())));
  }

  @ParameterizedTest(name = "offering in {0}, requested in {1}")
  @MethodSource("offeringInSeveralDataspaces")
  public void testCatalog_offeringInSeveralDataspacesIsQualifiedPerContext(
      List<String> tags, String context, Optional<String> expectedDefinitionId) {
    givenSpecs();
    offerings.add(offering(OFFER_ID, tags));

    assertOffers(context, expectedDefinitionId);
    expectedDefinitionId.ifPresent(
        id -> {
          ContractDefinition definition = contractDefinitionStore.findById(id);
          assertNotNull(definition, "The issued offer id must resolve in the store.");
          assertEquals(context, definition.getParticipantContextId());
        });
  }

  @Test
  public void testCatalog_defaultContextServesLegacyOfferingsWithoutAServerSideFilter() {
    givenSpecs();
    offerings.add(offering(OFFER_ID, null));
    offerings.add(offering(OFFER_ID_A, List.of(CONTEXT_A)));

    assertOffers(DEFAULT_CONTEXT, Optional.of(OFFER_ID));
    verify(productCatalogApi, never()).getProductOfferings(anyInt(), anyInt(), eq(DEFAULT_CONTEXT));
  }

  @Test
  public void testCatalog_namedContextIsFilteredServerSide() {
    givenSpecs();
    offerings.add(offering(OFFER_ID_A, List.of(CONTEXT_A)));

    assertOffers(CONTEXT_A, Optional.of(OFFER_ID_A));
    verify(productCatalogApi, never()).getProductOfferings(anyInt(), anyInt(), isNull());
  }

  /** Asserts the catalog and the dataset lookup in the context show exactly the expected offer. */
  private void assertOffers(String context, Optional<String> expectedDefinitionId) {
    ParticipantContext participantContext =
        ParticipantContext.Builder.newInstance()
            .participantContextId(context)
            .identity(context)
            .build();
    // the offer id's third part is random, so compare definition and asset parts only
    Set<String> expectedOfferIds =
        expectedDefinitionId
            .map(id -> Set.of(id + OFFER_PART_SEPARATOR + TEST_ASSET_ID))
            .orElse(Set.of());

    ServiceResult<Catalog> catalog =
        catalogService.getCatalog(participantContext, catalogRequest(), token);
    ServiceResult<Dataset> dataset =
        catalogService.getDataset(participantContext, datasetRequest(), token);

    assertTrue(catalog.succeeded());
    Set<String> catalogOfferIds =
        catalog.getContent().getDatasets().stream()
            .flatMap(d -> d.getOffers().keySet().stream())
            .map(MultiContextStoresTest::definitionAndAssetPart)
            .collect(Collectors.toSet());
    assertEquals(expectedOfferIds, catalogOfferIds, "The catalog lists only the context's offers.");
    if (expectedOfferIds.isEmpty()) {
      assertTrue(dataset.failed(), "A product without an offer in the context is not found.");
      assertEquals(ServiceFailure.Reason.NOT_FOUND, dataset.reason());
    } else {
      assertTrue(dataset.succeeded());
      assertEquals(
          expectedOfferIds,
          dataset.getContent().getOffers().keySet().stream()
              .map(MultiContextStoresTest::definitionAndAssetPart)
              .collect(Collectors.toSet()));
    }
  }

  private static String definitionAndAssetPart(String offerId) {
    ContractOfferId parsed = ContractOfferId.parseId(offerId).getContent();
    return parsed.definitionPart() + OFFER_PART_SEPARATOR + parsed.assetIdPart();
  }

  // ---- FIXTURES ----

  /** Emulates TMForum's {@code participantContextIds} filter: the context or {@code *}. */
  private static Stream<Arguments> definitionsPerContext() {
    return Stream.of(
        Arguments.of(DEFAULT_CONTEXT, Set.of(OFFER_ID)),
        Arguments.of(CONTEXT_A, Set.of(OFFER_ID_A)),
        Arguments.of(CONTEXT_B, Set.of(OFFER_ID_B)),
        Arguments.of(CONTEXT_C, Set.of()));
  }

  // the query ContractDefinitionResolverImpl sends for a catalog request
  @ParameterizedTest(name = "requested in {0}")
  @MethodSource("definitionsPerContext")
  public void testFindAllDefinitions_resolverQueryReturnsOnlyTheContextsOfferings(
      String context, Set<String> expectedDefinitionIds) {
    givenSpecs();
    offerings.add(offering(OFFER_ID, null));
    offerings.add(offering(OFFER_ID_A, List.of(CONTEXT_A)));
    offerings.add(offering(OFFER_ID_B, List.of(CONTEXT_B)));
    QuerySpec query =
        QuerySpec.Builder.newInstance()
            .filter(contextCriterion(context))
            .limit(Integer.MAX_VALUE)
            .build();

    List<ContractDefinition> definitions = contractDefinitionStore.findAll(query).toList();

    assertEquals(
        expectedDefinitionIds,
        definitions.stream().map(ContractDefinition::getId).collect(Collectors.toSet()));
    definitions.forEach(d -> assertEquals(context, d.getParticipantContextId()));
  }

  private void givenTMForumServesOfferings() {
    when(productCatalogApi.getProductOfferings(anyInt(), anyInt(), any()))
        .thenAnswer(
            invocation -> {
              String filter = invocation.getArgument(2);
              return offerings.stream()
                  .filter(
                      o ->
                          filter == null
                              || (o.getParticipantContextIds() != null
                                  && (o.getParticipantContextIds().contains(filter)
                                      || o.getParticipantContextIds()
                                          .contains(ALL_PARTICIPANT_CONTEXTS))))
                  .toList();
            });
    when(productCatalogApi.getProductOfferingByExternalId(anyString()))
        .thenAnswer(
            invocation ->
                offerings.stream()
                    .filter(o -> o.getExternalId().equals(invocation.getArgument(0)))
                    .findFirst());
  }

  /** One spec referenced by the offerings, and one no offering references. */
  private void givenSpecs() {
    ExtendableProductSpecification offered = spec(SPEC_ID, TEST_ASSET_ID);
    ExtendableProductSpecification other = spec(OTHER_SPEC_ID, OTHER_ASSET_ID);
    when(productCatalogApi.getProductSpecifications(anyInt(), anyInt()))
        .thenReturn(List.of(offered, other));
    when(productCatalogApi.getProductSpecByExternalId(TEST_ASSET_ID))
        .thenReturn(Optional.of(offered));
  }

  private static ExtendableProductSpecification spec(String tmfId, String externalId) {
    ExtendableProductSpecification spec =
        getTestProductSpec(
            List.of(new Endpoint("endpoint", "http://end.point")),
            Optional.of(externalId),
            Optional.of(TEST_VERSION),
            Optional.of(TEST_NAME),
            Optional.of(TEST_SPEC_DESCRIPTION),
            Optional.of(TEST_SPEC_DESCRIPTION));
    spec.setId(tmfId);
    return spec;
  }

  private static ExtendableProductOffering offering(String externalId, List<String> tags) {
    ExtendableProductOffering offering =
        getExtendableProductOffering(
            externalId, Optional.of(getTestOdrlPolicy()), Optional.of(getTestOdrlPolicy()));
    offering.setParticipantContextIds(tags);
    ExtendableProductSpecificationRef specRef = new ExtendableProductSpecificationRef();
    specRef.setId(SPEC_ID);
    offering.setExtendableProductSpecification(specRef);
    return offering;
  }

  private static Criterion contextCriterion(String context) {
    return new Criterion(PARTICIPANT_CONTEXT_ID_PROPERTY, EQUALS_OPERATOR, context);
  }

  private static CatalogRequestMessage catalogRequest() {
    return CatalogRequestMessage.Builder.newInstance()
        .protocol("dataspace-protocol-http")
        .counterPartyAddress(TEST_COUNTER_PARTY_ADDRESS)
        .counterPartyId(TEST_CONSUMER_DID)
        .querySpec(QuerySpec.Builder.newInstance().build())
        .build();
  }

  private static DatasetRequestMessage datasetRequest() {
    return DatasetRequestMessage.Builder.newInstance()
        .datasetId(TEST_ASSET_ID)
        .processId(TEST_NEGOTIATION_ID)
        .protocol("dataspace-protocol-http")
        .counterPartyAddress(TEST_COUNTER_PARTY_ADDRESS)
        .counterPartyId(TEST_CONSUMER_DID)
        .build();
  }
}
