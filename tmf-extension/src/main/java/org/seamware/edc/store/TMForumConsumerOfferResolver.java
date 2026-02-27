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

import java.util.Optional;
import org.eclipse.edc.connector.controlplane.contract.spi.ContractOfferId;
import org.eclipse.edc.connector.controlplane.contract.spi.offer.ConsumerOfferResolver;
import org.eclipse.edc.connector.controlplane.contract.spi.validation.ValidatableConsumerOffer;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.ServiceResult;
import org.jetbrains.annotations.NotNull;
import org.seamware.edc.tmf.ProductCatalogApiClient;

/**
 * Offer resolver to extract the TMForum entities for a given ID and translates them to a {@link
 * ValidatableConsumerOffer}
 */
public class TMForumConsumerOfferResolver implements ConsumerOfferResolver {

  private final Monitor monitor;
  private final ProductCatalogApiClient productCatalogApiClient;
  private final TMFEdcMapper tmfEdcMapper;

  public TMForumConsumerOfferResolver(
      Monitor monitor, ProductCatalogApiClient productCatalogApiClient, TMFEdcMapper tmfEdcMapper) {
    this.monitor = monitor;
    this.productCatalogApiClient = productCatalogApiClient;
    this.tmfEdcMapper = tmfEdcMapper;
  }

  @Override
  public @NotNull ServiceResult<ValidatableConsumerOffer> resolveOffer(String offeringId) {
    monitor.debug("Resolve offer " + offeringId);
    try {
      Optional<ContractOfferId> optionalContractOfferId =
          ContractOfferId.parseId(offeringId).asOptional();
      return optionalContractOfferId
          .map(
              contractOfferId ->
                  productCatalogApiClient
                      .getProductOfferingByExternalId(offeringId)
                      .flatMap(
                          epo ->
                              tmfEdcMapper.consumerOfferFromProductOffering(epo, contractOfferId))
                      .map(ServiceResult::success)
                      .orElse(
                          ServiceResult.notFound(
                              String.format("Was not able to resolve offering %s.", offeringId))))
          .orElseGet(
              () ->
                  ServiceResult.badRequest(
                      String.format("Offering id %s is not valid.", offeringId)));
    } catch (RuntimeException e) {
      monitor.warning(String.format("Was not able to resolve the offering %s.", offeringId), e);
      return ServiceResult.unexpected(e.getMessage());
    }
  }
}
