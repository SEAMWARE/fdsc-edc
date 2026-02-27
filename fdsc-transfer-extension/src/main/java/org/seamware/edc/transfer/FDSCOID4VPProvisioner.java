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

import static org.seamware.edc.FDSCTransferControlExtension.CONTEXT_SCOPE;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.DeprovisionedResource;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.ProvisionResponse;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.ProvisionedResource;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.ResourceDefinition;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.response.ResponseStatus;
import org.eclipse.edc.spi.response.StatusResult;
import org.seamware.credentials.model.MetaDataQueryVO;
import org.seamware.credentials.model.ServiceVO;
import org.seamware.edc.HttpClientException;
import org.seamware.edc.apisix.ApisixAdminClient;
import org.seamware.edc.apisix.Route;
import org.seamware.edc.ccs.CredentialsConfigServiceClient;
import org.seamware.edc.ccs.MetaQueryMixin;
import org.seamware.edc.domain.ExtendableProductSpecification;
import org.seamware.edc.pap.*;
import org.seamware.edc.store.TMFEdcMapper;
import org.seamware.edc.tmf.ProductCatalogApiClient;
import org.seamware.pap.model.PolicyPathVO;
import org.seamware.pap.model.ServiceCreateVO;
import org.seamware.tmforum.productcatalog.model.CharacteristicValueSpecificationVO;
import org.seamware.tmforum.productcatalog.model.ProductSpecificationCharacteristicVO;

/** Provisioner for Transfer Processes in the FIWARE Dataspace Connector */
public class FDSCOID4VPProvisioner
    extends FDSCProvisioner<FDSCOID4VPProviderResourceDefinition, FDSCProvisionedResource> {

  private static final String SERVICE_CONFIGURATION_KEY = "serviceConfiguration";
  private static final String TARGET_KEY = "targetSpecification";
  private static final String UPSTREAM_KEY = "upstreamAddress";
  private static final String ODRL_TARGET_KEY = "target";
  public static final String ODRL_UID = "odrl:uid";

  private final ObjectMapper objectMapper;
  private final CredentialsConfigServiceClient credentialsConfigServiceClient;
  private final OdrlPapClient odrlPapClient;
  private final TMFEdcMapper tmfEdcMapper;
  private final JsonLd jsonLd;

  public FDSCOID4VPProvisioner(
      Monitor monitor,
      ApisixAdminClient apisixAdminClient,
      CredentialsConfigServiceClient credentialsConfigServiceClient,
      OdrlPapClient odrlPapClient,
      ProductCatalogApiClient productCatalogApiClient,
      TransferMapper transferMapper,
      ObjectMapper objectMapper,
      TMFEdcMapper tmfEdcMapper,
      JsonLd jsonLd) {
    super(monitor, apisixAdminClient, productCatalogApiClient, transferMapper, objectMapper);
    this.credentialsConfigServiceClient = credentialsConfigServiceClient;
    this.odrlPapClient = odrlPapClient;
    this.objectMapper = objectMapper.copy();
    this.tmfEdcMapper = tmfEdcMapper;
    this.jsonLd = jsonLd;
    this.objectMapper.configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, false);
    this.objectMapper.addMixIn(MetaDataQueryVO.class, MetaQueryMixin.class);
  }

  @Override
  public boolean canProvision(ResourceDefinition resourceDefinition) {

    return resourceDefinition instanceof FDSCOID4VPProviderResourceDefinition;
  }

  @Override
  public boolean canDeprovision(ProvisionedResource resourceDefinition) {

    return resourceDefinition instanceof FDSCProvisionedResource;
  }

  /**
   * Policies and Trusted Issuers Entries are created by the contract-management -> create routes
   * for service and well-known -> conditionally: create credentials config entry -> create policies
   * at the pap
   *
   * @param resourceDefinition that contains metadata associated with the provision operation
   * @param policy the contract agreement usage policy for the asset being transferred
   * @return
   */
  @Override
  public CompletableFuture<StatusResult<ProvisionResponse>> provision(
      FDSCOID4VPProviderResourceDefinition resourceDefinition, Policy policy) {
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

            String serviceId = resourceDefinition.getTransferProcessId();

            PolicyPathVO policyPathVO =
                odrlPapClient.createService(new ServiceCreateVO().id(serviceId));

            // in order to not conflict in the pap, the policy should be identified by the process,
            // rather than its original id
            policy
                .getExtensibleProperties()
                .put(ODRL_UID, resourceDefinition.getTransferProcessId());
            // translate the policy into expanded odrl-jsonld
            Map<String, Object> odrlPolicy = tmfEdcMapper.fromEdcPolicy(policy);
            String policyString = objectMapper.writeValueAsString(odrlPolicy);
            JsonObject compactedPolicy = null;
            try (JsonReader reader = Json.createReader(new StringReader(policyString))) {
              compactedPolicy =
                  jsonLd
                      .compact(reader.readObject(), CONTEXT_SCOPE)
                      .orElseThrow(
                          f ->
                              new IllegalArgumentException(
                                  String.format(
                                      "Was not able to compact the policy. Failure: %s",
                                      f.getFailureDetail())));
            }

            Map<String, Object> policyMap =
                objectMapper.readValue(
                    compactedPolicy.toString(), new TypeReference<Map<String, Object>>() {});
            Optional<Map> targetSpec =
                optionalExtendableProductSpecification.flatMap(
                    eps -> getCharValue(eps, TARGET_KEY, Map.class));
            if (targetSpec.isPresent()) {
              monitor.debug("Replace target with the asset specific config.");
              policyMap.put(ODRL_TARGET_KEY, targetSpec.get());
              odrlPapClient.createPolicy(serviceId, policyMap);
            } else {
              odrlPapClient.createPolicy(serviceId, policyMap);
            }

            Route serviceRoute =
                transferMapper.toOid4VpServiceRoute(
                    resourceDefinition, upstreamAddress.get(), policyPathVO.getPolicyPath());
            Route wellKnownRoute = transferMapper.toWellknownRoute(resourceDefinition);
            apisixAdminClient.addRoute(serviceRoute);
            apisixAdminClient.addRoute(wellKnownRoute);

            // create service conf, if provided through the spec
            optionalExtendableProductSpecification
                .flatMap(eps -> getCharValue(eps, SERVICE_CONFIGURATION_KEY, ServiceVO.class))
                .ifPresent(
                    serviceVO -> {
                      serviceVO.id(resourceDefinition.getTransferProcessId());
                      credentialsConfigServiceClient.createService(serviceVO);
                    });

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

          } catch (HttpClientException e) {
            if (e.getStatusCode().isPresent() && e.getStatusCode().get() == 400) {
              return StatusResult.failure(
                  ResponseStatus.FATAL_ERROR,
                  "If no valid request is supplied, the provisioning should fail permanently.");
            }
            monitor.warning("Was not able to provision, remote server issue. Retry.", e);
            return StatusResult.failure(ResponseStatus.ERROR_RETRY, "Unable to provision");
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
            String wellKnownRouteId = transferMapper.toWellKnownRouteId(provisionedResource);

            executeDeletion(
                odrlPapClient::deleteService, provisionedResource.getTransferProcessId());

            executeDeletion(apisixAdminClient::deleteRoute, serviceRouteId);
            executeDeletion(apisixAdminClient::deleteRoute, wellKnownRouteId);

            executeDeletion(
                credentialsConfigServiceClient::deleteService,
                provisionedResource.getTransferProcessId());

            return StatusResult.success(
                DeprovisionedResource.Builder.newInstance()
                    .inProcess(false)
                    .provisionedResourceId(provisionedResource.getId())
                    .build());
          } catch (HttpClientException e) {
            monitor.warning("Was not able to deprovision, remote server error.", e);
            return StatusResult.failure(ResponseStatus.ERROR_RETRY, "Was not able to deprovision.");
          } catch (Exception e) {
            monitor.warning("Was not able to deprovision.", e);
            return StatusResult.failure(ResponseStatus.FATAL_ERROR, "Was not able to deprovision.");
          }
        });
  }

  private Optional<CharacteristicValueSpecificationVO> getCharValueSpec(
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
