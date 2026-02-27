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

import java.util.List;
import java.util.Optional;
import org.eclipse.edc.connector.controlplane.catalog.spi.Catalog;
import org.eclipse.edc.connector.controlplane.catalog.spi.CatalogRequestMessage;
import org.eclipse.edc.connector.controlplane.catalog.spi.Dataset;
import org.eclipse.edc.connector.controlplane.services.spi.catalog.CatalogProtocolService;
import org.eclipse.edc.connector.controlplane.services.spi.protocol.ProtocolTokenValidator;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.policy.context.request.spi.RequestCatalogPolicyContext;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.ServiceResult;
import org.jetbrains.annotations.NotNull;
import org.seamware.edc.domain.ExtendableProductOffering;
import org.seamware.edc.domain.ExtendableProductSpecification;
import org.seamware.edc.tmf.ProductCatalogApiClient;

public class TMForumBackedCatalogProtocolService implements CatalogProtocolService {

  private final TMFEdcMapper tmfEdcMapper;
  private final ProductCatalogApiClient productCatalogApi;
  private final String participantId;
  private final Monitor monitor;
  private final ProtocolTokenValidator protocolTokenValidator;

  public TMForumBackedCatalogProtocolService(
      TMFEdcMapper tmfEdcMapper,
      ProductCatalogApiClient productCatalogApi,
      String participantId,
      Monitor monitor,
      ProtocolTokenValidator protocolTokenValidator) {
    this.tmfEdcMapper = tmfEdcMapper;
    this.productCatalogApi = productCatalogApi;
    this.participantId = participantId;
    this.monitor = monitor;
    this.protocolTokenValidator = protocolTokenValidator;
  }

  @Override
  public @NotNull ServiceResult<Catalog> getCatalog(
      CatalogRequestMessage catalogRequestMessage, TokenRepresentation tokenRepresentation) {
    ServiceResult<ParticipantAgent> validatedToken =
        protocolTokenValidator.verify(
            tokenRepresentation, RequestCatalogPolicyContext::new, catalogRequestMessage);
    if (validatedToken.failed()) {
      return ServiceResult.unauthorized("Request not authorized.");
    }
    List<ExtendableProductOffering> productOfferingVOList =
        productCatalogApi.getProductOfferings(
            catalogRequestMessage.getQuerySpec().getOffset(),
            catalogRequestMessage.getQuerySpec().getLimit());
    Catalog.Builder catalogBuilder = Catalog.Builder.newInstance();
    catalogBuilder.participantId(participantId);

    productOfferingVOList.stream()
        .map(this::getProductSpec)
        .map(tmfEdcMapper::getDataService)
        .flatMap(List::stream)
        .forEach(catalogBuilder::dataService);
    productOfferingVOList.stream()
        .map(po -> tmfEdcMapper.datasetFromProductOffering(po, getProductSpec(po)))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .forEach(catalogBuilder::dataset);
    return ServiceResult.success(catalogBuilder.build());
  }

  @Override
  public @NotNull ServiceResult<Dataset> getDataset(
      String datasetId, TokenRepresentation tokenRepresentation, String protocol) {

    return productCatalogApi
        .getProductOfferingByExternalId(datasetId)
        .flatMap(po -> tmfEdcMapper.datasetFromProductOffering(po, getProductSpec(po)))
        .map(ServiceResult::success)
        .orElse(ServiceResult.notFound(String.format("No dataset with id %s exists.", datasetId)));
  }

  private Optional<ExtendableProductSpecification> getProductSpec(
      ExtendableProductOffering offeringVO) {
    if (offeringVO.getExtendableProductSpecification() == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(
        productCatalogApi.getProductSpecification(
            offeringVO.getExtendableProductSpecification().getId()));
  }
}
