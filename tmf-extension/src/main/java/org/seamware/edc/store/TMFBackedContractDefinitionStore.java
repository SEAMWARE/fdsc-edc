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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.edc.connector.controlplane.contract.spi.offer.store.ContractDefinitionStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractDefinition;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.query.QueryResolver;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.store.ReflectionBasedQueryResolver;
import org.jetbrains.annotations.NotNull;
import org.seamware.edc.domain.ExtendableProductOffering;
import org.seamware.edc.domain.ExtendableProductSpecification;
import org.seamware.edc.domain.ExtendableProductSpecificationRef;
import org.seamware.edc.tmf.DuplicateIdException;
import org.seamware.edc.tmf.ProductCatalogApiClient;

/** Read-only {@link ContractDefinitionStore} that derives definitions from TMForum offerings. */
public class TMFBackedContractDefinitionStore implements ContractDefinitionStore {

  static final int PAGE_SIZE = 100;

  private final Monitor monitor;
  private final ContractDefinitionIdResolver contractDefinitionIdResolver;
  private final TMFEdcMapper tmfEdcMapper;
  private final ProductCatalogApiClient productCatalogApiClient;
  private final QueryResolver<ContractDefinition> queryResolver;

  public TMFBackedContractDefinitionStore(
      Monitor monitor,
      ContractDefinitionIdResolver contractDefinitionIdResolver,
      TMFEdcMapper tmfEdcMapper,
      ProductCatalogApiClient productCatalogApiClient,
      CriterionOperatorRegistry criterionOperatorRegistry) {
    this.monitor = monitor;
    this.contractDefinitionIdResolver = contractDefinitionIdResolver;
    this.tmfEdcMapper = tmfEdcMapper;
    this.productCatalogApiClient = productCatalogApiClient;
    this.queryResolver =
        new ReflectionBasedQueryResolver<>(ContractDefinition.class, criterionOperatorRegistry);
  }

  /**
   * Filters, sorts and pages the definitions of the queried context like EDC's in-memory store.
   * Each selects only its offering's asset; offerings without a resolvable specification are
   * skipped. TMForum failures propagate instead of yielding an empty catalog.
   */
  @Override
  public @NotNull Stream<ContractDefinition> findAll(QuerySpec querySpec) {
    String defaultContextId = tmfEdcMapper.getDefaultParticipantContextId();
    // stamped before the criteria are applied, so a context criterion matches shared offerings
    String participantContextId =
        ContextCriteria.requestedContext(querySpec.getFilterExpression()).orElse(defaultContextId);
    String serverSideContextId =
        ContextCriteria.serverSideContext(querySpec.getFilterExpression(), defaultContextId)
            .orElse(null);

    List<ExtendableProductOffering> offerings = new ArrayList<>();
    int offset = 0;
    List<ExtendableProductOffering> page;
    do {
      page = productCatalogApiClient.getProductOfferings(offset, PAGE_SIZE, serverSideContextId);
      offerings.addAll(page);
      offset += PAGE_SIZE;
    } while (page.size() == PAGE_SIZE);

    Map<String, String> assetIds = offerings.isEmpty() ? Map.of() : assetIdsBySpecificationId();
    Stream<ContractDefinition> definitions =
        offerings.stream()
            .map(offering -> toDefinition(offering, participantContextId, assetIds))
            .flatMap(Optional::stream);
    return queryResolver.query(definitions, querySpec);
  }

  /** Asset ids (specification {@code externalId}s) keyed by the TMForum specification id. */
  private Map<String, String> assetIdsBySpecificationId() {
    Map<String, String> assetIds = new HashMap<>();
    int offset = 0;
    List<ExtendableProductSpecification> page;
    do {
      page = productCatalogApiClient.getProductSpecifications(offset, PAGE_SIZE);
      page.stream()
          .filter(spec -> spec.getId() != null && spec.getExternalId() != null)
          .forEach(spec -> assetIds.put(spec.getId(), spec.getExternalId()));
      offset += PAGE_SIZE;
    } while (page.size() == PAGE_SIZE);
    return assetIds;
  }

  private Optional<ContractDefinition> toDefinition(
      ExtendableProductOffering offering,
      String participantContextId,
      Map<String, String> assetIds) {
    // without a selector the definition would offer every asset
    Optional<String> assetId =
        Optional.ofNullable(offering.getExtendableProductSpecification())
            .map(ExtendableProductSpecificationRef::getId)
            .map(assetIds::get);
    if (assetId.isEmpty()) {
      monitor.debug(
          String.format(
              "Skipping product offering %s: it references no product specification with an externalId.",
              offering.getExternalId()));
      return Optional.empty();
    }
    try {
      return tmfEdcMapper.fromProductOffer(offering, participantContextId, assetId.get());
    } catch (RuntimeException e) {
      monitor.debug(
          String.format(
              "Skipping product offering %s: %s", offering.getExternalId(), e.getMessage()));
      return Optional.empty();
    }
  }

  @Override
  public ContractDefinition findById(String s) {
    try {
      return contractDefinitionIdResolver
          .resolve(s)
          .flatMap(
              resolved ->
                  tmfEdcMapper.fromProductOffer(
                      resolved.offering(), resolved.participantContextId()))
          .orElse(null);
    } catch (DuplicateIdException e) {
      // hides the offering in every dataspace until the data is fixed
      monitor.severe(String.format("Contract definition %s cannot be resolved.", s), e);
      return null;
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
