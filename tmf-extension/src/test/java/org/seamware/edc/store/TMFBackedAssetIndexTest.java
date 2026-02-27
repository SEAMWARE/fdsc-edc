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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.seamware.edc.domain.ExtendableProductSpecification;
import org.seamware.edc.tmf.ProductCatalogApiClient;

public class TMFBackedAssetIndexTest {

  private static final String TEST_ID = "test-id";
  private static final String TEST_CONTENT = "test-content";
  private static final DataAddress TEST_DATA_ADDRESS =
      DataAddress.Builder.newInstance().type("http").build();
  private static final Asset TEST_ASSET =
      Asset.Builder.newInstance().id(TEST_ID).dataAddress(TEST_DATA_ADDRESS).build();

  private ProductCatalogApiClient productCatalogApiClient;
  private TMFEdcMapper tmfEdcMapper;
  private CriterionOperatorRegistry criterionOperatorRegistry;
  private TMFBackedAssetIndex tmfBackedAssetIndex;

  @BeforeEach
  public void setup() {
    productCatalogApiClient = mock(ProductCatalogApiClient.class);
    tmfEdcMapper = mock(TMFEdcMapper.class);
    criterionOperatorRegistry = mock(CriterionOperatorRegistry.class);
    Monitor monitor = mock(Monitor.class);

    tmfBackedAssetIndex =
        new TMFBackedAssetIndex(
            monitor, productCatalogApiClient, tmfEdcMapper, criterionOperatorRegistry);
  }

  @Test
  public void testFindById_success() {
    when(productCatalogApiClient.getProductSpecByExternalId(eq(TEST_ID)))
        .thenReturn(Optional.of(getTestSpec()));
    when(tmfEdcMapper.assetFromProductSpec(eq(getTestSpec()))).thenReturn(Optional.of(TEST_ASSET));

    assertEquals(
        TEST_ASSET,
        tmfBackedAssetIndex.findById(TEST_ID),
        "The asset should successfully be returned.");
  }

  @Test
  public void testFindById_no_spec() {
    when(productCatalogApiClient.getProductSpecByExternalId(eq(TEST_ID)))
        .thenReturn(Optional.empty());

    assertNull(
        tmfBackedAssetIndex.findById(TEST_ID),
        "If no corresponding spec exists, null should be returned.");
  }

  @Test
  public void testFindById_api_error() {
    when(productCatalogApiClient.getProductSpecByExternalId(eq(TEST_ID)))
        .thenThrow(new RuntimeException("Something bad happened"));

    assertNull(
        tmfBackedAssetIndex.findById(TEST_ID),
        "If no corresponding spec exists, null should be returned.");
  }

  @Test
  public void testFindById_mapping_error() {
    when(productCatalogApiClient.getProductSpecByExternalId(eq(TEST_ID)))
        .thenReturn(Optional.of(getTestSpec()));
    when(tmfEdcMapper.assetFromProductSpec(eq(getTestSpec())))
        .thenThrow(new RuntimeException("Something bad happened"));

    assertNull(
        tmfBackedAssetIndex.findById(TEST_ID),
        "If no corresponding spec exists, null should be returned.");
  }

  @Test
  public void testFindById_unmappable_spec() {
    when(productCatalogApiClient.getProductSpecByExternalId(eq(TEST_ID)))
        .thenReturn(Optional.of(getTestSpec()));
    when(tmfEdcMapper.assetFromProductSpec(eq(getTestSpec()))).thenReturn(Optional.empty());

    assertNull(
        tmfBackedAssetIndex.findById(TEST_ID),
        "If no corresponding spec exists, null should be returned.");
  }

  @Test
  public void testFindById_null_asset_id() {
    assertThrows(
        NullPointerException.class,
        () -> tmfBackedAssetIndex.findById(null),
        "When no asset id is provided, an NPE should be thrown.");
  }

  @Test
  public void testFindById_empty_asset_id() {
    assertThrows(
        NullPointerException.class,
        () -> tmfBackedAssetIndex.findById(""),
        "When no asset id is provided, an NPE should be thrown.");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getAssetsToCount")
  public void testCountAssets_success(
      String name,
      List<AssetHolder> testSpecs,
      List<CriterionHolder> testPredicates,
      int expectedCount) {
    testPredicates.forEach(
        ch ->
            when(criterionOperatorRegistry.toPredicate(eq(ch.criterion())))
                .thenReturn(ch.predicate()));

    List<ExtendableProductSpecification> productSpecifications =
        testSpecs.stream().map(AssetHolder::productSpecification).toList();

    when(productCatalogApiClient.getProductSpecifications(anyInt(), anyInt()))
        .thenAnswer(
            invocation -> {
              int offset = invocation.getArgument(0);
              int limit = invocation.getArgument(1);

              int fromIndex = Math.min(offset, productSpecifications.size());
              int toIndex = Math.min(offset + limit, productSpecifications.size());

              return productSpecifications.subList(fromIndex, toIndex);
            });
    testSpecs.forEach(
        ah ->
            when(tmfEdcMapper.assetFromProductSpec(eq(ah.productSpecification())))
                .thenReturn(Optional.of(ah.asset())));

    List<Criterion> criteria = testPredicates.stream().map(CriterionHolder::criterion).toList();
    assertEquals(
        expectedCount,
        tmfBackedAssetIndex.countAssets(criteria),
        "The correct number of matching assets should be returned.");
  }

  @Test
  public void testCountAssets_zero_on_client_error() {
    when(productCatalogApiClient.getProductSpecifications(anyInt(), anyInt()))
        .thenThrow(new RuntimeException("Something failed."));

    assertEquals(0, tmfBackedAssetIndex.countAssets(List.of()), "0 should be returned on errors.");
  }

  @Test
  public void testCountAssets_zero_on_mapping_error() {
    List<AssetHolder> assetHolders = getTestAssets("pre", "v1", 50);
    when(productCatalogApiClient.getProductSpecifications(anyInt(), anyInt()))
        .thenReturn(assetHolders.stream().map(AssetHolder::productSpecification).toList());
    when(tmfEdcMapper.assetFromProductSpec(any()))
        .thenThrow(new RuntimeException("Something failed."));
    assertEquals(0, tmfBackedAssetIndex.countAssets(List.of()), "0 should be returned on errors.");
  }

  @Test
  public void testResolveForAsset_success() {
    when(productCatalogApiClient.getProductSpecByExternalId(eq(TEST_ID)))
        .thenReturn(Optional.of(getTestSpec()));
    when(tmfEdcMapper.assetFromProductSpec(eq(getTestSpec()))).thenReturn(Optional.of(TEST_ASSET));
    assertEquals(
        TEST_DATA_ADDRESS,
        tmfBackedAssetIndex.resolveForAsset(TEST_ID),
        "The correct address should be retrieved.");
  }

  @Test
  public void testResolveForAsset_no_spec() {
    when(productCatalogApiClient.getProductSpecByExternalId(eq(TEST_ID)))
        .thenReturn(Optional.empty());

    assertNull(
        tmfBackedAssetIndex.resolveForAsset(TEST_ID),
        "If no corresponding spec exists, null should be returned.");
  }

  @Test
  public void testResolveForAsset_api_error() {
    when(productCatalogApiClient.getProductSpecByExternalId(eq(TEST_ID)))
        .thenThrow(new RuntimeException("Something bad happened"));

    assertNull(
        tmfBackedAssetIndex.resolveForAsset(TEST_ID),
        "If no corresponding spec exists, null should be returned.");
  }

  @Test
  public void testResolveForAsset_mapping_error() {
    when(productCatalogApiClient.getProductSpecByExternalId(eq(TEST_ID)))
        .thenReturn(Optional.of(getTestSpec()));
    when(tmfEdcMapper.assetFromProductSpec(eq(getTestSpec())))
        .thenThrow(new RuntimeException("Something bad happened"));

    assertNull(
        tmfBackedAssetIndex.resolveForAsset(TEST_ID),
        "If no corresponding spec exists, null should be returned.");
  }

  @Test
  public void testResolveForAsset_unmappable_spec() {
    when(productCatalogApiClient.getProductSpecByExternalId(eq(TEST_ID)))
        .thenReturn(Optional.of(getTestSpec()));
    when(tmfEdcMapper.assetFromProductSpec(eq(getTestSpec()))).thenReturn(Optional.empty());

    assertNull(
        tmfBackedAssetIndex.resolveForAsset(TEST_ID),
        "If no corresponding spec exists, null should be returned.");
  }

  private static Stream<Arguments> getAssetsToCount() {
    return Stream.of(
        Arguments.of(
            "All v1 assets should be counted, using pagination.",
            Stream.concat(
                    getTestAssets("v1", "v1", 70).stream(), getTestAssets("v2", "v2", 80).stream())
                .toList(),
            List.of(
                new CriterionHolder(
                    Criterion.Builder.newInstance()
                        .operandLeft("version")
                        .operator("eq")
                        .operandRight("v1")
                        .build(),
                    getEqPredicate("https://w3id.org/edc/v0.0.1/ns/version", "v1"))),
            70),
        Arguments.of(
            "All v2 assets should be counted, using pagination.",
            Stream.concat(
                    getTestAssets("v1", "v1", 70).stream(), getTestAssets("v2", "v2", 80).stream())
                .toList(),
            List.of(
                new CriterionHolder(
                    Criterion.Builder.newInstance()
                        .operandLeft("version")
                        .operator("eq")
                        .operandRight("v2")
                        .build(),
                    getEqPredicate("https://w3id.org/edc/v0.0.1/ns/version", "v2"))),
            80),
        Arguments.of(
            "All assets should be counted if the content type matches, using pagination.",
            Stream.concat(
                    getTestAssets("v1", "v1", 70).stream(), getTestAssets("v2", "v2", 80).stream())
                .toList(),
            List.of(
                new CriterionHolder(
                    Criterion.Builder.newInstance()
                        .operandLeft("contenttype")
                        .operator("eq")
                        .operandRight(TEST_CONTENT)
                        .build(),
                    getEqPredicate("https://w3id.org/edc/v0.0.1/ns/contenttype", TEST_CONTENT))),
            150),
        Arguments.of(
            "All assets should be counted if no criteria is presented, using pagination.",
            Stream.concat(
                    getTestAssets("v1", "v1", 70).stream(), getTestAssets("v2", "v2", 80).stream())
                .toList(),
            List.of(
                new CriterionHolder(
                    Criterion.Builder.newInstance()
                        .operandLeft("contenttype")
                        .operator("eq")
                        .operandRight(TEST_CONTENT)
                        .build(),
                    getEqPredicate("https://w3id.org/edc/v0.0.1/ns/version", "v3"))),
            0),
        Arguments.of(
            "If no criteria is met, 0 should be returned, using pagination.",
            Stream.concat(
                    getTestAssets("v1", "v1", 70).stream(), getTestAssets("v2", "v2", 80).stream())
                .toList(),
            List.of(),
            150),
        Arguments.of(
            "All assets should be counted if no criteria is presented.",
            Stream.concat(
                    getTestAssets("v1", "v1", 7).stream(), getTestAssets("v2", "v2", 8).stream())
                .toList(),
            List.of(),
            15),
        Arguments.of("If no assets exist, nothing should be returned.", List.of(), List.of(), 0));
  }

  private static Predicate<Asset> getEqPredicate(String left, String right) {
    return o -> o.getProperties().containsKey(left) && o.getProperties().get(left).equals(right);
  }

  private static List<AssetHolder> getTestAssets(
      String idPrefix, String version, int numberOfAssets) {
    List<AssetHolder> assetHolders = new ArrayList<>();
    for (int i = 0; i < numberOfAssets; i++) {
      ExtendableProductSpecification extendableProductSpecification =
          new ExtendableProductSpecification().setExternalId(String.format("%s-%s", idPrefix, i));
      Asset asset =
          Asset.Builder.newInstance()
              .id(String.format("asset-%s-%s", idPrefix, i))
              .version(version)
              .contentType(TEST_CONTENT)
              .build();
      assetHolders.add(new AssetHolder(extendableProductSpecification, asset));
    }
    return assetHolders;
  }

  private record AssetHolder(ExtendableProductSpecification productSpecification, Asset asset) {}

  private record CriterionHolder(Criterion criterion, Predicate predicate) {}

  private ExtendableProductSpecification getTestSpec() {
    return new ExtendableProductSpecification().setExternalId(TEST_ID);
  }
}
