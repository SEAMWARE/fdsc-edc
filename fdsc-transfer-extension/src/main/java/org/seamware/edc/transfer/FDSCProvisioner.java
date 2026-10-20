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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.eclipse.edc.connector.dataplane.spi.DataFlow;
import org.eclipse.edc.connector.dataplane.spi.edr.EndpointDataReferenceService;
import org.eclipse.edc.connector.dataplane.spi.provision.Deprovisioner;
import org.eclipse.edc.connector.dataplane.spi.provision.ProvisionResource;
import org.eclipse.edc.connector.dataplane.spi.provision.Provisioner;
import org.eclipse.edc.connector.dataplane.spi.store.DataPlaneStore;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.seamware.edc.HttpClientException;
import org.seamware.edc.apisix.ApisixAdminClient;
import org.seamware.edc.domain.ExtendableProductSpecification;
import org.seamware.edc.tmf.ProductCatalogApiClient;
import org.seamware.tmforum.productcatalog.model.CharacteristicValueSpecificationVO;
import org.seamware.tmforum.productcatalog.model.ProductSpecificationCharacteristicVO;

/**
 * Shared state and helpers for the data-plane provisioners that configure the FDSC gateway.
 *
 * <p>Since EDC 0.16.0 provisioning is a data-plane concern: a {@link ProvisionResource} carries the
 * flow id and the properties stashed by the matching {@code ResourceDefinitionGenerator} instead of
 * the control-plane {@code ResourceDefinition} that used to be passed in.
 */
public abstract class FDSCProvisioner implements Provisioner, Deprovisioner {

  /**
   * Property key under which the generators stash the asset id on the {@link ProvisionResource}.
   */
  public static final String ASSET_ID_PROPERTY = "assetId";

  /**
   * Property key under which the generators stash the agreement id on the {@link
   * ProvisionResource}.
   */
  public static final String AGREEMENT_ID_PROPERTY = "agreementId";

  protected static final String SERVICE_CONFIGURATION_KEY = "serviceConfiguration";
  protected static final String TARGET_KEY = "targetSpecification";
  protected static final String UPSTREAM_KEY = "upstreamAddress";
  protected static final String ODRL_TARGET_KEY = "target";
  protected static final String ODRL_UID = "odrl:uid";

  protected final Monitor monitor;
  protected final ApisixAdminClient apisixAdminClient;
  protected final ProductCatalogApiClient productCatalogApiClient;
  protected final TransferMapper transferMapper;
  protected final ObjectMapper objectMapper;
  protected final DataPlaneStore dataPlaneStore;
  protected final EndpointDataReferenceService endpointDataReferenceService;

  protected FDSCProvisioner(
      Monitor monitor,
      ApisixAdminClient apisixAdminClient,
      ProductCatalogApiClient productCatalogApiClient,
      TransferMapper transferMapper,
      ObjectMapper objectMapper,
      DataPlaneStore dataPlaneStore,
      EndpointDataReferenceService endpointDataReferenceService) {
    this.monitor = monitor;
    this.apisixAdminClient = apisixAdminClient;
    this.productCatalogApiClient = productCatalogApiClient;
    this.transferMapper = transferMapper;
    this.objectMapper = objectMapper;
    this.dataPlaneStore = dataPlaneStore;
    this.endpointDataReferenceService = endpointDataReferenceService;
  }

  /**
   * Creates the EDR for the data flow the resource belongs to.
   *
   * <p>When a flow is provisioned, the data plane reports only the data address of the provisioned
   * resource to the control plane, which sends it to the consumer in the TransferStartMessage. The
   * EDR the data plane creates once the flow starts is not sent anywhere, so the provisioned
   * resource has to carry it.
   */
  protected Result<DataAddress> endpointDataReference(ProvisionResource provisionResource) {
    DataFlow dataFlow = dataPlaneStore.findById(provisionResource.getFlowId());
    if (dataFlow == null) {
      return Result.failure("No data flow found for " + provisionResource.getFlowId());
    }
    return endpointDataReferenceService.createEndpointDataReference(dataFlow);
  }

  /** Reads a string property stashed on the resource by the generator; empty when absent. */
  protected static Optional<String> stringProperty(ProvisionResource resource, String key) {
    return Optional.ofNullable(resource.getProperty(key)).map(Object::toString);
  }

  protected void executeDeletion(Consumer<String> deletionMethod, String idToDelete) {

    try {
      deletionMethod.accept(idToDelete);
    } catch (HttpClientException e) {
      if (e.getStatusCode().isPresent() && e.getStatusCode().get() == 404) {
        // If the request fails because no such service exists, we don't care
        monitor.info(
            String.format("Was not able to delete entity %s because it does not exist", idToDelete),
            e);
      } else {
        // bubble the exception
        throw e;
      }
    }
  }

  /** Resolves a product-spec characteristic, preferring the one flagged as default. */
  protected Optional<CharacteristicValueSpecificationVO> getCharValueSpec(
      ExtendableProductSpecification extendableProductSpecification, String valueKey) {
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
                }));
  }

  public <T> Optional<T> getCharValue(
      ExtendableProductSpecification extendableProductSpecification,
      String valueKey,
      Class<T> targetClass) {
    return getCharValueSpec(extendableProductSpecification, valueKey)
        .map(CharacteristicValueSpecificationVO::getValue)
        .map(val -> objectMapper.convertValue(val, targetClass));
  }
}
