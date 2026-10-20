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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.dataplane.spi.edr.EndpointDataReferenceService;
import org.eclipse.edc.connector.dataplane.spi.provision.DeprovisionedResource;
import org.eclipse.edc.connector.dataplane.spi.provision.ProvisionResource;
import org.eclipse.edc.connector.dataplane.spi.provision.ProvisionedResource;
import org.eclipse.edc.connector.dataplane.spi.store.DataPlaneStore;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.response.ResponseStatus;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.seamware.credentials.model.MetaDataQueryVO;
import org.seamware.credentials.model.ServiceVO;
import org.seamware.edc.HttpClientException;
import org.seamware.edc.apisix.ApisixAdminClient;
import org.seamware.edc.apisix.Route;
import org.seamware.edc.ccs.CredentialsConfigServiceClient;
import org.seamware.edc.ccs.MetaQueryMixin;
import org.seamware.edc.domain.ExtendableProductSpecification;
import org.seamware.edc.pap.OdrlPapClient;
import org.seamware.edc.store.TMFEdcMapper;
import org.seamware.edc.tmf.ProductCatalogApiClient;
import org.seamware.pap.model.PolicyPathVO;
import org.seamware.pap.model.ServiceCreateVO;

/**
 * Provisions the APISIX route, PAP policy and credentials-config entry for an OID4VP-secured FDSC
 * data flow.
 *
 * <p>The usage policy is no longer handed to the provisioner: since EDC 0.16.0 provisioning runs in
 * the data plane, where only the flow's agreement id is known, so the policy is re-read from the
 * agreement store.
 */
public class FDSCOID4VPProvisioner extends FDSCProvisioner {

  /** Resource type emitted by {@link FDSCOID4VPProviderResourceDefinitionGenerator}. */
  public static final String PROVISION_TYPE = "fdsc-oid4vp-provider";

  private final CredentialsConfigServiceClient credentialsConfigServiceClient;
  private final OdrlPapClient odrlPapClient;
  private final ContractNegotiationStore contractNegotiationStore;
  private final TMFEdcMapper tmfEdcMapper;
  private final JsonLd jsonLd;

  public FDSCOID4VPProvisioner(
      Monitor monitor,
      ApisixAdminClient apisixAdminClient,
      CredentialsConfigServiceClient credentialsConfigServiceClient,
      OdrlPapClient odrlPapClient,
      ProductCatalogApiClient productCatalogApiClient,
      ContractNegotiationStore contractNegotiationStore,
      TransferMapper transferMapper,
      ObjectMapper objectMapper,
      TMFEdcMapper tmfEdcMapper,
      JsonLd jsonLd,
      DataPlaneStore dataPlaneStore,
      EndpointDataReferenceService endpointDataReferenceService) {
    super(
        monitor,
        apisixAdminClient,
        productCatalogApiClient,
        transferMapper,
        copy(objectMapper),
        dataPlaneStore,
        endpointDataReferenceService);
    this.credentialsConfigServiceClient = credentialsConfigServiceClient;
    this.odrlPapClient = odrlPapClient;
    this.contractNegotiationStore = contractNegotiationStore;
    this.tmfEdcMapper = tmfEdcMapper;
    this.jsonLd = jsonLd;
  }

  private static ObjectMapper copy(ObjectMapper objectMapper) {
    ObjectMapper copy = objectMapper.copy();
    copy.configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, false);
    copy.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
    copy.addMixIn(MetaDataQueryVO.class, MetaQueryMixin.class);
    return copy;
  }

  @Override
  public String supportedType() {
    return PROVISION_TYPE;
  }

  /**
   * Creates the service and well-known routes, the PAP policy and - when the spec provides one -
   * the credentials-config entry. Trusted-issuer entries are created by the contract management.
   */
  @Override
  public CompletableFuture<StatusResult<ProvisionedResource>> provision(
      ProvisionResource provisionResource) {
    String serviceId = provisionResource.getFlowId();

    return CompletableFuture.supplyAsync(
        () -> {
          try {
            Optional<String> assetId = stringProperty(provisionResource, ASSET_ID_PROPERTY);
            if (assetId.isEmpty()) {
              return StatusResult.failure(
                  ResponseStatus.FATAL_ERROR,
                  "Without an asset id, no FDSC Provider can be provisioned.");
            }

            Optional<Policy> agreementPolicy =
                stringProperty(provisionResource, AGREEMENT_ID_PROPERTY)
                    .map(contractNegotiationStore::findContractAgreement)
                    .map(ContractAgreement::getPolicy);
            if (agreementPolicy.isEmpty()) {
              return StatusResult.failure(
                  ResponseStatus.FATAL_ERROR,
                  "Without the agreement policy, no FDSC Provider can be provisioned.");
            }

            Optional<ExtendableProductSpecification> optionalExtendableProductSpecification =
                productCatalogApiClient.getProductSpecByExternalId(assetId.get());
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

            Result<DataAddress> endpointDataReference = endpointDataReference(provisionResource);
            if (endpointDataReference.failed()) {
              return StatusResult.failure(
                  ResponseStatus.ERROR_RETRY,
                  "Unable to create the endpoint data reference: "
                      + endpointDataReference.getFailureDetail());
            }

            PolicyPathVO policyPathVO =
                odrlPapClient.createService(new ServiceCreateVO().id(serviceId));

            // in order to not conflict in the pap, the policy should be identified by the flow,
            // rather than its original id
            Policy policy =
                agreementPolicy.get().toBuilder().extensibleProperty(ODRL_UID, serviceId).build();
            // translate the policy into expanded odrl-jsonld
            Map<String, Object> odrlPolicy = tmfEdcMapper.fromEdcPolicy(policy);
            String policyString = objectMapper.writeValueAsString(odrlPolicy);
            JsonObject compactedPolicy;
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
            }
            odrlPapClient.createPolicy(serviceId, policyMap);

            Route serviceRoute =
                transferMapper.toOid4VpServiceRoute(
                    serviceId, upstreamAddress.get(), policyPathVO.getPolicyPath());
            Route wellKnownRoute = transferMapper.toWellknownRoute(serviceId);
            apisixAdminClient.addRoute(serviceRoute);
            apisixAdminClient.addRoute(wellKnownRoute);

            // create service conf, if provided through the spec
            optionalExtendableProductSpecification
                .flatMap(eps -> getCharValue(eps, SERVICE_CONFIGURATION_KEY, ServiceVO.class))
                .ifPresent(
                    serviceVO -> {
                      serviceVO.id(serviceId);
                      credentialsConfigServiceClient.createService(serviceVO);
                    });

            return StatusResult.success(
                ProvisionedResource.Builder.from(provisionResource)
                    .dataAddress(endpointDataReference.getContent())
                    .build());

          } catch (HttpClientException e) {
            // Best-effort cleanup of any partial state left in the PAP so the
            // next retry doesn't fail with "Service ... already exists". The
            // PAP service is created first, so any later failure may leave it
            // dangling and turn every retry into a permanent error.
            rollbackPapService(serviceId);
            if (e.getStatusCode().isPresent() && e.getStatusCode().get() == 400) {
              return StatusResult.failure(
                  ResponseStatus.FATAL_ERROR,
                  "If no valid request is supplied, the provisioning should fail permanently.");
            }
            monitor.warning("Was not able to provision, remote server issue. Retry.", e);
            return StatusResult.failure(ResponseStatus.ERROR_RETRY, "Unable to provision");
          } catch (Exception e) {
            rollbackPapService(serviceId);
            monitor.warning("Was not able to provision.", e);
            return StatusResult.failure(ResponseStatus.FATAL_ERROR, "Unable to provision");
          }
        });
  }

  /**
   * Best-effort delete of the PAP service for this flow. Swallows any error: if the service was
   * never created (or already gone) the delete is a no-op, and if the PAP itself is down the
   * original provision failure already surfaces the same condition. Without this, a single
   * transient failure between createService and a later step leaves an orphan that turns every
   * subsequent retry into "already exists" until the transfer is permanently terminated.
   */
  private void rollbackPapService(String serviceId) {
    try {
      odrlPapClient.deleteService(serviceId);
    } catch (Exception cleanupFailure) {
      monitor.debug(
          "Rollback of PAP service "
              + serviceId
              + " failed (ignored): "
              + cleanupFailure.getMessage());
    }
  }

  @Override
  public CompletableFuture<StatusResult<DeprovisionedResource>> deprovision(
      ProvisionResource provisionResource) {
    String serviceId = provisionResource.getFlowId();
    monitor.debug("Deprovision " + serviceId);

    return CompletableFuture.supplyAsync(
        () -> {
          try {

            String serviceRouteId = transferMapper.toServiceRouteId(serviceId);
            String wellKnownRouteId = transferMapper.toWellKnownRouteId(serviceId);

            executeDeletion(odrlPapClient::deleteService, serviceId);

            executeDeletion(apisixAdminClient::deleteRoute, serviceRouteId);
            executeDeletion(apisixAdminClient::deleteRoute, wellKnownRouteId);

            executeDeletion(credentialsConfigServiceClient::deleteService, serviceId);

            return StatusResult.success(
                DeprovisionedResource.Builder.from(provisionResource).build());
          } catch (HttpClientException e) {
            monitor.warning("Was not able to deprovision, remote server error.", e);
            return StatusResult.failure(ResponseStatus.ERROR_RETRY, "Was not able to deprovision.");
          } catch (Exception e) {
            monitor.warning("Was not able to deprovision.", e);
            return StatusResult.failure(ResponseStatus.FATAL_ERROR, "Was not able to deprovision.");
          }
        });
  }
}
