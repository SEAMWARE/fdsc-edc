package org.seamware.edc.transfer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.connector.controlplane.transfer.spi.provision.Provisioner;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.DeprovisionedResource;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.ProvisionResponse;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.ProvisionedResource;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.ResourceDefinition;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.response.ResponseStatus;
import org.eclipse.edc.spi.response.StatusResult;
import org.seamware.credentials.model.ServiceVO;
import org.seamware.edc.apisix.ApisixAdminClient;
import org.seamware.edc.apisix.Route;
import org.seamware.edc.ccs.CredentialsConfigServiceClient;
import org.seamware.edc.domain.ExtendableProductOffering;
import org.seamware.edc.domain.ExtendableProductSpecification;
import org.seamware.edc.domain.ExtendableProductSpecificationRef;
import org.seamware.edc.pap.OdrlPapClient;
import org.seamware.edc.tmf.ProductCatalogApiClient;
import org.seamware.pap.model.PolicyPathVO;
import org.seamware.pap.model.ServiceCreateVO;
import org.seamware.tmforum.productcatalog.model.CharacteristicValueSpecificationVO;
import org.seamware.tmforum.productcatalog.model.ProductSpecificationCharacteristicVO;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Provisioner for Transfer Processes in the FIWARE Dataspace Connector
 */
public class FDSCProvisioner implements Provisioner<FDSCProviderResourceDefinition, FDSCProvisionedResource> {

    private static final String SERVICE_CONFIGURATION_KEY = "serviceConfiguration";
    private static final String ENDPOINT_URL_KEY = "endpointUrl";

    private final Monitor monitor;
    private final ApisixAdminClient apisixAdminClient;
    private final CredentialsConfigServiceClient credentialsConfigServiceClient;
    private final OdrlPapClient odrlPapClient;
    private final ProductCatalogApiClient productCatalogApiClient;
    private final TransferMapper transferMapper;
    private final ObjectMapper objectMapper;

    public FDSCProvisioner(Monitor monitor, ApisixAdminClient apisixAdminClient, CredentialsConfigServiceClient credentialsConfigServiceClient, OdrlPapClient odrlPapClient, ProductCatalogApiClient productCatalogApiClient, TransferMapper transferMapper, ObjectMapper objectMapper) {
        this.monitor = monitor;
        this.apisixAdminClient = apisixAdminClient;
        this.credentialsConfigServiceClient = credentialsConfigServiceClient;
        this.odrlPapClient = odrlPapClient;
        this.productCatalogApiClient = productCatalogApiClient;
        this.transferMapper = transferMapper;
        this.objectMapper = objectMapper.copy().configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, false);
    }

    @Override
    public boolean canProvision(ResourceDefinition resourceDefinition) {

        return resourceDefinition instanceof FDSCProviderResourceDefinition;
    }

    @Override
    public boolean canDeprovision(ProvisionedResource resourceDefinition) {

        return resourceDefinition instanceof FDSCProvisionedResource;
    }

    /**
     * Policies and Trusted Issuers Entries are created by the contract-management
     * -> create routes for service and well-known
     * -> conditionally: create credentials config entry
     * -> create policies at the pap
     *
     * @param resourceDefinition that contains metadata associated with the provision operation
     * @param policy             the contract agreement usage policy for the asset being transferred
     * @return
     */
    @Override
    public CompletableFuture<StatusResult<ProvisionResponse>> provision(FDSCProviderResourceDefinition resourceDefinition, Policy policy) {
        monitor.info("Provision " + resourceDefinition.getTransferProcessId());

        Optional<ExtendableProductSpecification> optionalExtendableProductSpecification = productCatalogApiClient.getProductOfferingByAssetId(resourceDefinition.getAssetId())
                .map(ExtendableProductOffering::getExtendableProductSpecification)
                .map(ExtendableProductSpecificationRef::getId)
                .map(productCatalogApiClient::getProductSpecification);
        if (optionalExtendableProductSpecification.isEmpty()) {
            return CompletableFuture.completedFuture(StatusResult.failure(ResponseStatus.FATAL_ERROR,
                    "Without a product specification, no FDSC Provider can be provisioned."));
        }

        Optional<String> serviceAddress = optionalExtendableProductSpecification
                .flatMap(eps -> getCharValue(eps, ENDPOINT_URL_KEY, String.class));
        if (serviceAddress.isEmpty()) {
            return CompletableFuture.completedFuture(StatusResult.failure(ResponseStatus.FATAL_ERROR,
                    "Without an configured endpoint, the service cannot be provisioned."));
        }

        String serviceId = resourceDefinition.getTransferProcessId();


        PolicyPathVO policyPathVO = odrlPapClient.createService(new ServiceCreateVO().id(serviceId));

        policy.getExtensibleProperties()
                .put("@context", "http://www.w3.org/ns/odrl.jsonld");
        odrlPapClient.createPolicy(serviceId, policy);

        Route serviceRoute = transferMapper.toServiceRoute(resourceDefinition, serviceAddress.get(), policyPathVO.getPolicyPath());
        Route wellKnownRoute = transferMapper.toWellknownRouteRoute(resourceDefinition);
        apisixAdminClient.addRoute(serviceRoute);
        apisixAdminClient.addRoute(wellKnownRoute);

        // create service conf, if provided through the spec
        optionalExtendableProductSpecification
                .flatMap(eps -> getCharValue(eps, SERVICE_CONFIGURATION_KEY, ServiceVO.class))
                .ifPresent(serviceVO -> {
                    serviceVO.id(resourceDefinition.getTransferProcessId());
                    credentialsConfigServiceClient.createService(serviceVO);
                });

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
    }

    @Override
    public CompletableFuture<StatusResult<DeprovisionedResource>> deprovision(FDSCProvisionedResource provisionedResource, Policy policy) {
        monitor.info("Deprovision " + provisionedResource.getTransferProcessId());

        String serviceRouteId = transferMapper.toServiceRouteId(provisionedResource);
        String wellKnownRouteId = transferMapper.toWellKnownRouteId(provisionedResource);
        String serviceId = provisionedResource.getTransferProcessId().replace("-", "");

        odrlPapClient.deleteService(serviceId);

        apisixAdminClient.deleteRoute(serviceRouteId);
        apisixAdminClient.deleteRoute(wellKnownRouteId);

        // Delete it. If the request fails because no such service exists, we dont care
        try {
            credentialsConfigServiceClient.deleteService(provisionedResource.getTransferProcessId());
        } catch (RuntimeException e) {
            monitor.info(String.format("Was not able to delete service config for %s", provisionedResource.getTransferProcessId()), e);
        }

        return CompletableFuture.completedFuture(StatusResult.success(DeprovisionedResource.Builder.newInstance()
                .inProcess(false)
                .provisionedResourceId(provisionedResource.getId())
                .build()));
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
                .map(val -> {
                    return objectMapper.convertValue(val, targetClass);
                });
    }
}
