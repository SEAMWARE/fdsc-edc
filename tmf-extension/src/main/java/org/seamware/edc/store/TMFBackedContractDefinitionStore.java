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

import java.util.stream.Stream;
import org.eclipse.edc.connector.controlplane.contract.spi.offer.store.ContractDefinitionStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractDefinition;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.jetbrains.annotations.NotNull;
import org.seamware.edc.tmf.ProductCatalogApiClient;

public class TMFBackedContractDefinitionStore implements ContractDefinitionStore {

  private final Monitor monitor;
  private final ProductCatalogApiClient productCatalogApi;
  private final TMFEdcMapper tmfEdcMapper;

  public TMFBackedContractDefinitionStore(
      Monitor monitor, ProductCatalogApiClient productCatalogApi, TMFEdcMapper tmfEdcMapper) {
    this.monitor = monitor;
    this.productCatalogApi = productCatalogApi;
    this.tmfEdcMapper = tmfEdcMapper;
  }

  @Override
  public @NotNull Stream<ContractDefinition> findAll(QuerySpec querySpec) {
    throw new UnsupportedOperationException(
        "Querying for contract definitions is currently not supported.");
  }

  @Override
  public ContractDefinition findById(String s) {
    try {
      return productCatalogApi
          .getProductOfferingByExternalId(s)
          .flatMap(tmfEdcMapper::fromProductOffer)
          .orElse(null);
    } catch (RuntimeException e) {
      monitor.warning("Was not able to successfully resolve the product offer.", e);
      return null;
    }
  }

  @Override
  public StoreResult<Void> save(ContractDefinition contractDefinition) {
    throw new UnsupportedOperationException(
        "Storing contract definitions is currently not supported.");
  }

  @Override
  public StoreResult<Void> update(ContractDefinition contractDefinition) {
    throw new UnsupportedOperationException(
        "Updating contract definitions is currently not supported.");
  }

  @Override
  public StoreResult<ContractDefinition> deleteById(String s) {
    throw new UnsupportedOperationException(
        "Deleting contract definitions is currently not supported.");
  }
}
