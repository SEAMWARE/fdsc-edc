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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.asset.spi.index.AssetIndex;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.seamware.edc.domain.ExtendableProductSpecification;
import org.seamware.edc.tmf.ProductCatalogApiClient;

public class TMFBackedAssetIndex implements AssetIndex {

  private final Monitor monitor;
  private final ProductCatalogApiClient productCatalogApiClient;
  private final TMFEdcMapper tmfEdcMapper;
  private final CriterionOperatorRegistry criterionOperatorRegistry;

  public TMFBackedAssetIndex(
      Monitor monitor,
      ProductCatalogApiClient productCatalogApiClient,
      TMFEdcMapper tmfEdcMapper,
      CriterionOperatorRegistry criterionOperatorRegistry) {
    this.monitor = monitor;
    this.productCatalogApiClient = productCatalogApiClient;
    this.tmfEdcMapper = tmfEdcMapper;
    this.criterionOperatorRegistry = criterionOperatorRegistry;
  }

  @Override
  public Stream<Asset> queryAssets(QuerySpec querySpec) {

    List<Asset> assets = queryAssets(querySpec.getFilterExpression());
    if (querySpec.getSortField() != null) {
      String sortField = querySpec.getSortField();

      assets.sort(
          new Comparator<Asset>() {
            @Override
            public int compare(Asset asset1, Asset asset2) {
              if (asset1.getProperties().get(sortField) instanceof String a1
                  && asset2.getProperties().get(sortField) instanceof String a2) {
                return Comparator.<String>naturalOrder().compare(a1, a2);
              }
              if (asset1.getProperties().get(sortField) instanceof Integer a1
                  && asset2.getProperties().get(sortField) instanceof Integer a2) {
                return Comparator.<Integer>naturalOrder().compare(a1, a2);
              }
              if (asset1.getProperties().get(sortField) instanceof Long a1
                  && asset2.getProperties().get(sortField) instanceof Long a2) {
                return Comparator.<Long>naturalOrder().compare(a1, a2);
              }
              if (asset1.getProperties().get(sortField) instanceof Double a1
                  && asset2.getProperties().get(sortField) instanceof Double a2) {
                return Comparator.<Double>naturalOrder().compare(a1, a2);
              }
              return 0;
            }
          });
    }
    int fromIndex = Math.min(querySpec.getOffset(), assets.size());
    int toIndex = Math.min(querySpec.getOffset() + querySpec.getLimit(), assets.size());
    return assets.subList(fromIndex, toIndex).stream();
  }

  @Override
  public Asset findById(String s) {
    // according to the interface, an NPE should be thrown if id is empty or null
    if (s == null || s.isEmpty()) {
      throw new NullPointerException("No asset id was provided.");
    }
    try {
      return productCatalogApiClient
          .getProductSpecByExternalId(s)
          .map(tmfEdcMapper::assetFromProductSpec)
          .filter(Optional::isPresent)
          .map(Optional::get)
          // according to the interface doc, null should be returned
          .orElse(null);
    } catch (RuntimeException e) {
      monitor.warning("Failed to get asset.", e);
      return null;
    }
  }

  @Override
  public StoreResult<Void> create(Asset asset) {
    throw new UnsupportedOperationException();
  }

  @Override
  public StoreResult<Asset> deleteById(String s) {
    throw new UnsupportedOperationException();
  }

  @Override
  public long countAssets(List<Criterion> criteria) {
    try {
      return queryAssets(criteria).size();
    } catch (RuntimeException e) {
      monitor.warning("Failed to count assets.", e);
      // according to the spec, 0 should be returned if no asset can be retrieved
      return 0;
    }
  }

  private List<Asset> queryAssets(List<Criterion> criteria) {
    Predicate<Asset> predicate =
        criteria.stream()
            .map(criterionOperatorRegistry::<Asset>toPredicate)
            .reduce(x -> true, Predicate::and);

    List<Asset> assets = new ArrayList<>();
    int offset = 0;
    boolean moreOfferingsAvailable = true;
    while (moreOfferingsAvailable) {
      List<ExtendableProductSpecification> productSpecifications =
          productCatalogApiClient.getProductSpecifications(offset, 100);

      productSpecifications.stream()
          .map(tmfEdcMapper::assetFromProductSpec)
          .filter(Optional::isPresent)
          .map(Optional::get)
          .filter(predicate)
          .forEach(assets::add);
      moreOfferingsAvailable = productSpecifications.size() == 100;
      offset += 100;
    }
    return assets;
  }

  @Override
  public StoreResult<Asset> updateAsset(Asset asset) {
    throw new UnsupportedOperationException();
  }

  @Override
  public DataAddress resolveForAsset(String s) {
    return Optional.ofNullable(findById(s)).map(Asset::getDataAddress).orElse(null);
  }
}
