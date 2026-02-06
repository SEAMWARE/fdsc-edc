package org.seamware.edc.transfer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.DeprovisionedResource;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.ProvisionResponse;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.ProvisionedResource;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.ResourceDefinition;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.model.*;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.response.ResponseStatus;
import org.eclipse.edc.spi.response.StatusResult;
import org.seamware.edc.apisix.ApisixAdminClient;
import org.seamware.edc.apisix.Route;
import org.seamware.edc.domain.ExtendableProductSpecification;
import org.seamware.edc.pap.*;
import org.seamware.edc.tmf.ProductCatalogApiClient;
import org.seamware.tmforum.productcatalog.model.CharacteristicValueSpecificationVO;
import org.seamware.tmforum.productcatalog.model.ProductSpecificationCharacteristicVO;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Provisioner for Transfer Processes in the FIWARE Dataspace Connector
 */
public class FDSCDcpProvisioner extends FDSCProvisioner<FDSCDcpProviderResourceDefinition, FDSCProvisionedResource> {

    private final ObjectMapper objectMapper;
    private final PolicyEngine policyEngine;

    public FDSCDcpProvisioner(Monitor monitor, ApisixAdminClient apisixAdminClient, ProductCatalogApiClient productCatalogApiClient, TransferMapper transferMapper, ObjectMapper objectMapper, PolicyEngine policyEngine) {
        super(monitor, apisixAdminClient, productCatalogApiClient, transferMapper, objectMapper);
        this.objectMapper = objectMapper.copy();
        this.policyEngine = policyEngine;
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
     * @param policy             the contract agreement usage policy for the asset being transferred
     */
    @Override
    public CompletableFuture<StatusResult<ProvisionResponse>> provision(FDSCDcpProviderResourceDefinition resourceDefinition, Policy policy) {
        try {
            monitor.info("Received policy " + objectMapper.writeValueAsString(policy));
            monitor.info("Received definition " + objectMapper.writeValueAsString(resourceDefinition));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        try {
            Optional<ExtendableProductSpecification> optionalExtendableProductSpecification = productCatalogApiClient
                    .getProductSpecByExternalId(resourceDefinition.getAssetId());
            if (optionalExtendableProductSpecification.isEmpty()) {
                return CompletableFuture.completedFuture(StatusResult.failure(ResponseStatus.FATAL_ERROR,
                        "Without a product specification, no FDSC Provider can be provisioned."));
            }

            Optional<String> upstreamAddress = optionalExtendableProductSpecification
                    .flatMap(eps -> getCharValue(eps, UPSTREAM_KEY, String.class));
            if (upstreamAddress.isEmpty()) {
                return CompletableFuture.completedFuture(StatusResult.failure(ResponseStatus.FATAL_ERROR,
                        "Without an configured upstreamAddress, the service cannot be provisioned."));
            }

            Route serviceRoute = transferMapper.toDcpServiceRoute(resourceDefinition, upstreamAddress.get());
            apisixAdminClient.addRoute(serviceRoute);

            return CompletableFuture.completedFuture(StatusResult.success(
                            ProvisionResponse.Builder.newInstance()
                                    .inProcess(false)
                                    .resource(FDSCProvisionedResource.Builder.newInstance()
                                            .id(UUID.randomUUID().toString())
                                            .resourceDefinitionId(resourceDefinition.getId())
                                            .transferProcessId(resourceDefinition.getTransferProcessId())
                                            .build())
                                    .build()
                    )
            );

        } catch (Exception e) {
            monitor.warning("Was not able to provision.", e);
            return CompletableFuture.completedFuture(StatusResult.failure(ResponseStatus.FATAL_ERROR, "Unable to provision"));
        }
    }

    @Override
    public CompletableFuture<StatusResult<DeprovisionedResource>> deprovision(FDSCProvisionedResource provisionedResource, Policy policy) {
        monitor.info("Deprovision " + provisionedResource.getTransferProcessId());
        try {

            String serviceRouteId = transferMapper.toServiceRouteId(provisionedResource);

            apisixAdminClient.deleteRoute(serviceRouteId);

            return CompletableFuture.completedFuture(StatusResult.success(DeprovisionedResource.Builder.newInstance()
                    .inProcess(false)
                    .provisionedResourceId(provisionedResource.getId())
                    .build()));
        } catch (Exception e) {
            monitor.warning("Was not able to deprovision.", e);
            return CompletableFuture.completedFuture(StatusResult.failure(ResponseStatus.FATAL_ERROR, "Was not able to deprovision."));
        }
    }

    public <T> Optional<T> getCharValue(ExtendableProductSpecification extendableProductSpecification, String valueKey, Class<T> targetClass) {
        List<CharacteristicValueSpecificationVO> cvsList = Optional.ofNullable(extendableProductSpecification
                        .getProductSpecCharacteristic())
                .orElse(List.of())
                .stream()
                .filter(psc -> Optional.ofNullable(psc.getId()).orElse("").equals(valueKey))
                .map(ProductSpecificationCharacteristicVO::getProductSpecCharacteristicValue)
                .map(Optional::ofNullable)
                .map(ol -> ol.orElse(List.of()))
                .flatMap(List::stream)
                .toList();
        return Optional.ofNullable(cvsList
                        .stream()
                        .filter(cvs -> Optional.ofNullable(cvs.getIsDefault()).orElse(false))
                        .findAny()
                        .orElseGet(() -> {
                            if (cvsList.isEmpty()) {
                                return null;
                            }
                            return cvsList.getFirst();
                        }))
                .map(CharacteristicValueSpecificationVO::getValue)
                .map(val -> objectMapper.convertValue(val, targetClass));
    }
}
