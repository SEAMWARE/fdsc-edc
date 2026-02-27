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
package org.seamware.edc.transfer;

/*-
 * #%L
 * fdsc-transfer-extension
 * %%
 * Copyright (C) 2025 - 2026 Seamless Middleware Technologies S.L
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.DeprovisionedResource;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.ProvisionResponse;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.ProvisionedResource;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.ResourceDefinition;
import org.eclipse.edc.policy.model.*;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.response.ResponseStatus;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.web.spi.exception.BadGatewayException;
import org.seamware.edc.HttpClientException;
import org.seamware.edc.apisix.ApisixAdminClient;
import org.seamware.edc.apisix.Route;
import org.seamware.edc.domain.ExtendableProductSpecification;
import org.seamware.edc.pap.*;
import org.seamware.edc.tmf.ProductCatalogApiClient;
import org.seamware.tmforum.productcatalog.model.CharacteristicValueSpecificationVO;
import org.seamware.tmforum.productcatalog.model.ProductSpecificationCharacteristicVO;

/** Provisioner for Transfer Processes in the FIWARE Dataspace Connector */
public class FDSCDcpProvisioner
    extends FDSCProvisioner<FDSCDcpProviderResourceDefinition, FDSCProvisionedResource> {

  private final ObjectMapper objectMapper;

  public FDSCDcpProvisioner(
      Monitor monitor,
      ApisixAdminClient apisixAdminClient,
      ProductCatalogApiClient productCatalogApiClient,
      TransferMapper transferMapper,
      ObjectMapper objectMapper) {
    super(monitor, apisixAdminClient, productCatalogApiClient, transferMapper, objectMapper);
    this.objectMapper = objectMapper.copy();
    this.objectMapper.configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, false);
  }

  @Override
  public boolean canProvision(ResourceDefinition resourceDefinition) {

    return resourceDefinition instanceof FDSCDcpProviderResourceDefinition;
  }

  @Override
  public boolean canDeprovision(ProvisionedResource provisionedResource) {

    return provisionedResource instanceof FDSCProvisionedResource;
  }

  /**
   * -> create routes for service
   *
   * @param resourceDefinition that contains metadata associated with the provision operation
   * @param policy the contract agreement usage policy for the asset being transferred
   */
  @Override
  public CompletableFuture<StatusResult<ProvisionResponse>> provision(
      FDSCDcpProviderResourceDefinition resourceDefinition, Policy policy) {
    monitor.debug("Provision dcp resource " + resourceDefinition.getId());

    return CompletableFuture.supplyAsync(
        () -> {
          try {
            Optional<ExtendableProductSpecification> optionalExtendableProductSpecification =
                productCatalogApiClient.getProductSpecByExternalId(resourceDefinition.getAssetId());
            if (optionalExtendableProductSpecification.isEmpty()) {
              return StatusResult.failure(
                  ResponseStatus.FATAL_ERROR,
                  "Without a product specification, no FDSC Provider can be provisioned.");
            }

            Optional<String> upstreamAddress =
                optionalExtendableProductSpecification.flatMap(
                    eps -> getCharValue(eps, UPSTREAM_KEY, String.class));
            if (upstreamAddress.isEmpty()) {
              return StatusResult.failure(
                  ResponseStatus.FATAL_ERROR,
                  "Without an configured upstreamAddress, the service cannot be provisioned.");
            }

            Route serviceRoute =
                transferMapper.toDcpServiceRoute(resourceDefinition, upstreamAddress.get());
            apisixAdminClient.addRoute(serviceRoute);

            return StatusResult.success(
                ProvisionResponse.Builder.newInstance()
                    .inProcess(false)
                    .resource(
                        FDSCProvisionedResource.Builder.newInstance()
                            .id(UUID.randomUUID().toString())
                            .resourceDefinitionId(resourceDefinition.getId())
                            .transferProcessId(resourceDefinition.getTransferProcessId())
                            .build())
                    .build());
          } catch (BadGatewayException e) {
            monitor.warning("Was not able to provision, cannot reach remote server.", e);
            return StatusResult.failure(ResponseStatus.ERROR_RETRY);

          } catch (Exception e) {
            monitor.warning("Was not able to provision.", e);
            return StatusResult.failure(ResponseStatus.FATAL_ERROR, "Unable to provision");
          }
        });
  }

  @Override
  public CompletableFuture<StatusResult<DeprovisionedResource>> deprovision(
      FDSCProvisionedResource provisionedResource, Policy policy) {
    monitor.debug("Deprovision " + provisionedResource.getTransferProcessId());
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            String serviceRouteId = transferMapper.toServiceRouteId(provisionedResource);

            executeDeletion(apisixAdminClient::deleteRoute, serviceRouteId);

            return StatusResult.success(
                DeprovisionedResource.Builder.newInstance()
                    .inProcess(false)
                    .provisionedResourceId(provisionedResource.getId())
                    .build());
          } catch (HttpClientException e) {
            monitor.warning(
                "Was not able to deprovision, downstream server not reachable. Retry.", e);
            return StatusResult.failure(ResponseStatus.ERROR_RETRY, "Was not able to deprovision.");

          } catch (Exception e) {
            monitor.warning("Was not able to deprovision.", e);
            return StatusResult.failure(ResponseStatus.FATAL_ERROR, "Was not able to deprovision.");
          }
        });
  }

  public <T> Optional<T> getCharValue(
      ExtendableProductSpecification extendableProductSpecification,
      String valueKey,
      Class<T> targetClass) {
    List<CharacteristicValueSpecificationVO> cvsList =
        Optional.ofNullable(extendableProductSpecification.getProductSpecCharacteristic())
            .orElse(List.of())
            .stream()
            .filter(psc -> Optional.ofNullable(psc.getId()).orElse("").equals(valueKey))
            .map(ProductSpecificationCharacteristicVO::getProductSpecCharacteristicValue)
            .map(Optional::ofNullable)
            .map(ol -> ol.orElse(List.of()))
            .flatMap(List::stream)
            .toList();
    return Optional.ofNullable(
            cvsList.stream()
                .filter(cvs -> Optional.ofNullable(cvs.getIsDefault()).orElse(false))
                .findAny()
                .orElseGet(
                    () -> {
                      if (cvsList.isEmpty()) {
                        return null;
                      }
                      return cvsList.getFirst();
                    }))
        .map(CharacteristicValueSpecificationVO::getValue)
        .map(val -> objectMapper.convertValue(val, targetClass));
  }
}
