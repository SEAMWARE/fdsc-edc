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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractDefinition;
import org.eclipse.edc.query.CriterionOperatorRegistryImpl;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.query.SortOrder;
import org.eclipse.edc.web.spi.exception.BadGatewayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.seamware.edc.domain.ExtendableProductOffering;
import org.seamware.edc.domain.ExtendableProductSpecification;
import org.seamware.edc.domain.ExtendableProductSpecificationRef;
import org.seamware.edc.tmf.ProductCatalogApiClient;

public class TMFBackedContractDefinitionStoreTest {

  private static final String TEST_ID = "test-id";
  private static final String TEST_CONTEXT_ID = "test-context";
  private static final String DEFAULT_CONTEXT_ID = "default-context";
  private static final String OTHER_CONTEXT_ID = "other-context";
  private static final String PARTICIPANT_CONTEXT_ID_FIELD = "participantContextId";
  private static final String ID_FIELD = "id";
  private static final String EQUALS = "=";
  private static final String REJECTED_PREFIX = "rejected-";
  private static final String BROKEN_PREFIX = "broken-";
  private static final String OFFERING_PREFIX = "offering-";
  private static final String UNREFERENCED_PREFIX = "unreferenced-";
  private static final String DANGLING_PREFIX = "dangling-";
  private static final String SPEC_PREFIX = "spec-";
  private static final String ASSET_PREFIX = "asset-";
  private static final int MULTI_PAGE_OFFERING_COUNT =
      TMFBackedContractDefinitionStore.PAGE_SIZE + TMFBackedContractDefinitionStore.PAGE_SIZE / 2;
  private static final int UNBOUNDED = Integer.MAX_VALUE;

  private ProductCatalogApiClient productCatalogApiClient;
  private TMFEdcMapper tmfEdcMapper;
  private TMFBackedContractDefinitionStore tmfBackedContractDefinitionStore;

  @BeforeEach
  public void setup() {
    productCatalogApiClient = mock(ProductCatalogApiClient.class);
    tmfEdcMapper = mock(TMFEdcMapper.class);
    when(tmfEdcMapper.contextForId(any(), isNull())).thenReturn(Optional.of(TEST_CONTEXT_ID));
    when(tmfEdcMapper.getDefaultParticipantContextId()).thenReturn(DEFAULT_CONTEXT_ID);
    Monitor monitor = mock(Monitor.class);

    tmfBackedContractDefinitionStore =
        new TMFBackedContractDefinitionStore(
            monitor,
            new ContractDefinitionIdResolver(monitor, productCatalogApiClient, tmfEdcMapper),
            tmfEdcMapper,
            productCatalogApiClient,
            CriterionOperatorRegistryImpl.ofDefaults());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("findAllCases")
  public void testFindAll(
      String description,
      List<String> offeringIds,
      QuerySpec querySpec,
      String expectedServerSideContext,
      String expectedStampedContext,
      List<String> expectedIds) {
    serveOfferings(offeringIds);

    List<ContractDefinition> definitions =
        tmfBackedContractDefinitionStore.findAll(querySpec).toList();

    assertEquals(
        expectedIds,
        definitions.stream().map(ContractDefinition::getId).toList(),
        "The matching definitions should be returned in the requested window and order.");
    definitions.forEach(
        definition ->
            assertEquals(
                expectedStampedContext,
                definition.getParticipantContextId(),
                "Definitions should be built for the requested context."));
    definitions.forEach(
        definition ->
            assertEquals(
                List.of(
                    Criterion.criterion(
                        Asset.PROPERTY_ID, EQUALS, ASSET_PREFIX + definition.getId())),
                definition.getAssetsSelector(),
                "Each definition should select only its offering's asset."));
    int pageSize = TMFBackedContractDefinitionStore.PAGE_SIZE;
    for (int offset = 0; offset <= offeringIds.size(); offset += pageSize) {
      verify(productCatalogApiClient)
          .getProductOfferings(eq(offset), eq(pageSize), eq(expectedServerSideContext));
    }
    for (int offset = 0; offset <= servedSpecifications(offeringIds).size(); offset += pageSize) {
      verify(productCatalogApiClient).getProductSpecifications(eq(offset), eq(pageSize));
    }
    verifyNoMoreInteractions(productCatalogApiClient);
  }

  private static Stream<Arguments> findAllCases() {
    List<String> abc = List.of(offeringId("b"), offeringId("c"), offeringId("a"));
    List<String> multiPage =
        IntStream.range(0, MULTI_PAGE_OFFERING_COUNT)
            .mapToObj(i -> offeringId(String.format("%03d", i)))
            .toList();
    return Stream.of(
        Arguments.of(
            "no criteria uses the default context, unfiltered",
            abc,
            QuerySpec.max(),
            null,
            DEFAULT_CONTEXT_ID,
            abc),
        Arguments.of(
            "explicit default context is fetched unfiltered",
            abc,
            contextQuery(DEFAULT_CONTEXT_ID).build(),
            null,
            DEFAULT_CONTEXT_ID,
            abc),
        Arguments.of(
            "non-default context is filtered server-side",
            abc,
            contextQuery(OTHER_CONTEXT_ID).build(),
            OTHER_CONTEXT_ID,
            OTHER_CONTEXT_ID,
            abc),
        Arguments.of(
            "rejected and unmappable offerings are skipped",
            List.of(offeringId("a"), REJECTED_PREFIX + "x", offeringId("b"), BROKEN_PREFIX + "y"),
            contextQuery(OTHER_CONTEXT_ID).build(),
            OTHER_CONTEXT_ID,
            OTHER_CONTEXT_ID,
            List.of(offeringId("a"), offeringId("b"))),
        Arguments.of(
            "offerings without a resolvable specification are skipped",
            List.of(UNREFERENCED_PREFIX + "x", offeringId("a"), DANGLING_PREFIX + "y"),
            QuerySpec.max(),
            null,
            DEFAULT_CONTEXT_ID,
            List.of(offeringId("a"))),
        Arguments.of(
            "further criteria are applied",
            abc,
            contextQuery(DEFAULT_CONTEXT_ID)
                .filter(new Criterion(ID_FIELD, EQUALS, offeringId("c")))
                .build(),
            null,
            DEFAULT_CONTEXT_ID,
            List.of(offeringId("c"))),
        Arguments.of(
            "offset and limit select a window",
            abc,
            QuerySpec.Builder.newInstance().offset(1).limit(1).build(),
            null,
            DEFAULT_CONTEXT_ID,
            List.of(offeringId("c"))),
        Arguments.of(
            "offset beyond size returns nothing",
            abc,
            QuerySpec.Builder.newInstance().offset(abc.size() + 1).limit(1).build(),
            null,
            DEFAULT_CONTEXT_ID,
            List.of()),
        Arguments.of(
            "offset with an unbounded limit returns the tail without overflow",
            abc,
            contextQuery(DEFAULT_CONTEXT_ID).offset(1).limit(UNBOUNDED).build(),
            null,
            DEFAULT_CONTEXT_ID,
            List.of(offeringId("c"), offeringId("a"))),
        Arguments.of(
            "offerings are fetched across pages",
            multiPage,
            contextQuery(DEFAULT_CONTEXT_ID).limit(UNBOUNDED).build(),
            null,
            DEFAULT_CONTEXT_ID,
            multiPage),
        Arguments.of(
            "sort by id ascending",
            abc,
            QuerySpec.Builder.newInstance().sortField(ID_FIELD).sortOrder(SortOrder.ASC).build(),
            null,
            DEFAULT_CONTEXT_ID,
            List.of(offeringId("a"), offeringId("b"), offeringId("c"))),
        Arguments.of(
            "sort by id descending",
            abc,
            QuerySpec.Builder.newInstance().sortField(ID_FIELD).sortOrder(SortOrder.DESC).build(),
            null,
            DEFAULT_CONTEXT_ID,
            List.of(offeringId("c"), offeringId("b"), offeringId("a"))));
  }

  @Test
  public void testFindAll_no_offerings_skips_specification_fetch() {
    serveOfferings(List.of());

    assertEquals(
        List.of(),
        tmfBackedContractDefinitionStore.findAll(QuerySpec.max()).toList(),
        "Without offerings there is nothing to define.");
    verify(productCatalogApiClient, never()).getProductSpecifications(anyInt(), anyInt());
  }

  @Test
  public void testFindAll_api_error_propagates() {
    when(productCatalogApiClient.getProductOfferings(anyInt(), anyInt(), any()))
        .thenThrow(new BadGatewayException("TMForum is down"));

    assertThrows(
        BadGatewayException.class,
        () -> tmfBackedContractDefinitionStore.findAll(QuerySpec.max()),
        "TMForum failures should not be hidden as an empty catalog.");
  }

  @Test
  public void testFindAll_specification_api_error_propagates() {
    serveOfferings(List.of(offeringId("a")));
    when(productCatalogApiClient.getProductSpecifications(anyInt(), anyInt()))
        .thenThrow(new BadGatewayException("TMForum is down"));

    assertThrows(
        BadGatewayException.class,
        () -> tmfBackedContractDefinitionStore.findAll(QuerySpec.max()),
        "TMForum failures should not be hidden as an empty catalog.");
  }

  @Test
  public void testFindById_success() {
    ContractDefinition testDefinition =
        ContractDefinition.Builder.newInstance()
            .accessPolicyId("access-policy-id")
            .contractPolicyId("contract-policy-id")
            .build();

    when(productCatalogApiClient.getProductOfferingByExternalId(eq(TEST_ID)))
        .thenReturn(Optional.of(getTestOffering()));
    when(tmfEdcMapper.fromProductOffer(eq(getTestOffering()), eq(TEST_CONTEXT_ID)))
        .thenReturn(Optional.of(testDefinition));

    assertEquals(
        testDefinition,
        tmfBackedContractDefinitionStore.findById(TEST_ID),
        "The correct contract should have been returned.");
  }

  @Test
  public void testFindById_no_spec() {
    when(productCatalogApiClient.getProductOfferingByExternalId(eq(TEST_ID)))
        .thenReturn(Optional.empty());

    assertNull(
        tmfBackedContractDefinitionStore.findById(TEST_ID),
        "If no corresponding offer exists, null should be returned.");
  }

  @Test
  public void testFindById_api_error() {
    when(productCatalogApiClient.getProductOfferingByExternalId(eq(TEST_ID)))
        .thenThrow(new RuntimeException("Something bad happened"));

    assertNull(
        tmfBackedContractDefinitionStore.findById(TEST_ID),
        "If no corresponding offer exists, null should be returned.");
  }

  @Test
  public void testFindById_mapping_error() {
    when(productCatalogApiClient.getProductOfferingByExternalId(eq(TEST_ID)))
        .thenReturn(Optional.of(getTestOffering()));
    when(tmfEdcMapper.fromProductOffer(eq(getTestOffering()), eq(TEST_CONTEXT_ID)))
        .thenThrow(new RuntimeException("Something bad happened"));

    assertNull(
        tmfBackedContractDefinitionStore.findById(TEST_ID),
        "If no corresponding offer exists, null should be returned.");
  }

  @Test
  public void testFindById_unmappable_spec() {
    when(productCatalogApiClient.getProductOfferingByExternalId(eq(TEST_ID)))
        .thenReturn(Optional.of(getTestOffering()));
    when(tmfEdcMapper.fromProductOffer(eq(getTestOffering()), eq(TEST_CONTEXT_ID)))
        .thenReturn(Optional.empty());

    assertNull(
        tmfBackedContractDefinitionStore.findById(TEST_ID),
        "If no corresponding offer exists, null should be returned.");
  }

  /**
   * Serves offerings and their specifications page-wise and maps offerings like the real mapper,
   * stamping the context and selecting the given asset.
   */
  private void serveOfferings(List<String> offeringIds) {
    when(productCatalogApiClient.getProductOfferings(anyInt(), anyInt(), any()))
        .thenAnswer(
            invocation ->
                page(offeringIds, invocation.getArgument(0), invocation.getArgument(1)).stream()
                    .map(TMFBackedContractDefinitionStoreTest::offering)
                    .toList());
    List<String> specifiedIds = servedSpecifications(offeringIds);
    when(productCatalogApiClient.getProductSpecifications(anyInt(), anyInt()))
        .thenAnswer(
            invocation ->
                page(specifiedIds, invocation.getArgument(0), invocation.getArgument(1)).stream()
                    .map(TMFBackedContractDefinitionStoreTest::specification)
                    .toList());
    when(tmfEdcMapper.fromProductOffer(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              ExtendableProductOffering offering = invocation.getArgument(0);
              String id = offering.getExternalId();
              if (id.startsWith(BROKEN_PREFIX)) {
                throw new IllegalArgumentException("Unmappable offering");
              }
              if (id.startsWith(REJECTED_PREFIX)) {
                return Optional.empty();
              }
              return Optional.of(
                  ContractDefinition.Builder.newInstance()
                      .id(id)
                      .participantContextId(invocation.getArgument(1))
                      .accessPolicyId(id)
                      .contractPolicyId(id)
                      .assetsSelectorCriterion(
                          Criterion.criterion(
                              Asset.PROPERTY_ID, EQUALS, invocation.<String>getArgument(2)))
                      .build());
            });
  }

  private static <T> List<T> page(List<T> items, int offset, int limit) {
    return items.subList(Math.min(offset, items.size()), Math.min(offset + limit, items.size()));
  }

  /** Offering ids whose specification TMForum serves: all but unreferenced and dangling ones. */
  private static List<String> servedSpecifications(List<String> offeringIds) {
    return offeringIds.stream()
        .filter(id -> !id.startsWith(UNREFERENCED_PREFIX) && !id.startsWith(DANGLING_PREFIX))
        .toList();
  }

  private static ExtendableProductOffering offering(String id) {
    ExtendableProductOffering offering = new ExtendableProductOffering().setExternalId(id);
    if (!id.startsWith(UNREFERENCED_PREFIX)) {
      ExtendableProductSpecificationRef specRef = new ExtendableProductSpecificationRef();
      specRef.setId(SPEC_PREFIX + id);
      offering.setExtendableProductSpecification(specRef);
    }
    return offering;
  }

  private static ExtendableProductSpecification specification(String offeringId) {
    ExtendableProductSpecification specification = new ExtendableProductSpecification();
    specification.setId(SPEC_PREFIX + offeringId);
    specification.setExternalId(ASSET_PREFIX + offeringId);
    return specification;
  }

  private static QuerySpec.Builder contextQuery(String participantContextId) {
    return QuerySpec.Builder.newInstance()
        .filter(new Criterion(PARTICIPANT_CONTEXT_ID_FIELD, EQUALS, participantContextId));
  }

  private static String offeringId(String suffix) {
    return OFFERING_PREFIX + suffix;
  }

  private ExtendableProductOffering getTestOffering() {
    return new ExtendableProductOffering().setExternalId(TEST_ID);
  }
}
