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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.eclipse.edc.connector.controlplane.catalog.spi.Catalog;
import org.eclipse.edc.connector.controlplane.catalog.spi.CatalogRequestMessage;
import org.eclipse.edc.connector.controlplane.catalog.spi.Dataset;
import org.eclipse.edc.connector.controlplane.catalog.spi.DatasetRequestMessage;
import org.eclipse.edc.connector.controlplane.services.spi.catalog.CatalogProtocolService;
import org.eclipse.edc.connector.controlplane.services.spi.protocol.ProtocolTokenValidator;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.policy.context.request.spi.RequestCatalogPolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.ServiceResult;
import org.jetbrains.annotations.NotNull;
import org.seamware.edc.domain.ExtendableProductOffering;
import org.seamware.edc.domain.ExtendableProductSpecification;
import org.seamware.edc.tmf.ProductCatalogApiClient;

/**
 * TMForum-backed implementation of the EDC CatalogProtocolService. Fetches product specifications
 * and offerings from the TMForum API and maps them to EDC catalog datasets.
 *
 * <p>Access policies from each offering's contract definition are evaluated against the
 * authenticated participant agent to filter catalog visibility.
 */
public class TMForumBackedCatalogProtocolService implements CatalogProtocolService {

  private final TMFEdcMapper tmfEdcMapper;
  private final ProductCatalogApiClient productCatalogApi;
  private final ProtocolTokenValidator protocolTokenValidator;
  private final PolicyEngine policyEngine;
  private final Monitor monitor;
  private final ObjectMapper objectMapper;

  /**
   * Creates a new TMForum-backed catalog protocol service.
   *
   * @param tmfEdcMapper mapper between TMForum and EDC entities
   * @param productCatalogApi client for the TMForum Product Catalog API
   * @param protocolTokenValidator validator for incoming protocol tokens
   * @param policyEngine the EDC policy engine for evaluating access policies
   */
  public TMForumBackedCatalogProtocolService(
      TMFEdcMapper tmfEdcMapper,
      ProductCatalogApiClient productCatalogApi,
      ProtocolTokenValidator protocolTokenValidator,
      PolicyEngine policyEngine,
      Monitor monitor,
      ObjectMapper objectMapper) {
    this.tmfEdcMapper = tmfEdcMapper;
    this.productCatalogApi = productCatalogApi;
    this.protocolTokenValidator = protocolTokenValidator;
    this.policyEngine = policyEngine;
    this.monitor = monitor;
    this.objectMapper = objectMapper;
  }

  /** Page size when fetching ProductOfferings (contract definitions). */
  private static final int OFFERINGS_PAGE_SIZE = 100;

  @Override
  public @NotNull ServiceResult<Catalog> getCatalog(
      ParticipantContext participantContext,
      CatalogRequestMessage catalogRequestMessage,
      TokenRepresentation tokenRepresentation) {
    ServiceResult<ParticipantAgent> validatedToken =
        protocolTokenValidator.verify(
            participantContext,
            tokenRepresentation,
            RequestCatalogPolicyContext::new,
            catalogRequestMessage);
    if (validatedToken.failed()) {
      return ServiceResult.unauthorized("Request not authorized.");
    }

    ParticipantAgent participantAgent = validatedToken.getContent();

    List<ExtendableProductSpecification> specs =
        productCatalogApi.getProductSpecifications(
            catalogRequestMessage.getQuerySpec().getOffset(),
            catalogRequestMessage.getQuerySpec().getLimit());
    String participantContextId = participantContext.getParticipantContextId();
    List<ExtendableProductOffering> accessibleOfferings =
        tmfEdcMapper.filterByAccessPolicy(
            offeringsServing(participantContextId), policyEngine, participantAgent);

    Catalog.Builder catalogBuilder = Catalog.Builder.newInstance();
    catalogBuilder.participantId(participantContext.getIdentity());

    specs.stream()
        .map(Optional::ofNullable)
        .map(tmfEdcMapper::getDataService)
        .flatMap(List::stream)
        .forEach(catalogBuilder::dataService);

    specs.stream()
        .map(
            spec ->
                tmfEdcMapper.datasetFromProductSpecification(
                    spec, accessibleOfferings, participantContextId))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .forEach(catalogBuilder::dataset);
    return ServiceResult.success(catalogBuilder.build());
  }

  @Override
  public @NotNull ServiceResult<Dataset> getDataset(
      ParticipantContext participantContext,
      DatasetRequestMessage datasetRequestMessage,
      TokenRepresentation tokenRepresentation) {

    // The DSP layer only null-checks the bearer token before dispatching here, so verifying it is
    // the service's job - upstream's CatalogProtocolServiceImpl.getDataset does the same.
    ServiceResult<ParticipantAgent> validatedToken =
        protocolTokenValidator.verify(
            participantContext,
            tokenRepresentation,
            RequestCatalogPolicyContext::new,
            datasetRequestMessage);
    if (validatedToken.failed()) {
      return ServiceResult.unauthorized("Request not authorized.");
    }

    String datasetId = datasetRequestMessage.getDatasetId();
    Optional<ExtendableProductSpecification> spec =
        productCatalogApi.getProductSpecByExternalId(datasetId);
    if (spec.isEmpty()) {
      return ServiceResult.notFound(String.format("No dataset with id %s exists.", datasetId));
    }

    String participantContextId = participantContext.getParticipantContextId();
    List<ExtendableProductOffering> accessibleOfferings =
        tmfEdcMapper.filterByAccessPolicy(
            offeringsServing(participantContextId), policyEngine, validatedToken.getContent());

    return tmfEdcMapper
        .datasetFromProductSpecification(spec.get(), accessibleOfferings, participantContextId)
        .map(ServiceResult::success)
        .orElse(ServiceResult.notFound(String.format("No dataset with id %s exists.", datasetId)));
  }

  /**
   * The offerings made in the context. Fetched unfiltered and filtered in memory: untagged
   * offerings serve every context, and TMForum cannot express "tagged with it or untagged".
   */
  private List<ExtendableProductOffering> offeringsServing(String participantContextId) {
    List<ExtendableProductOffering> offerings = new ArrayList<>();
    int offset = 0;
    List<ExtendableProductOffering> page;
    do {
      page = productCatalogApi.getProductOfferings(offset, OFFERINGS_PAGE_SIZE);
      page.stream()
          .filter(
              o -> tmfEdcMapper.servesContext(o.getParticipantContextIds(), participantContextId))
          .forEach(offerings::add);
      offset += OFFERINGS_PAGE_SIZE;
    } while (page.size() == OFFERINGS_PAGE_SIZE);
    return offerings;
  }
}
