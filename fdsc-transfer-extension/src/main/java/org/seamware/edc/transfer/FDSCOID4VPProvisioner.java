package org.seamware.edc.transfer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.connector.controlplane.transfer.spi.provision.Provisioner;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.DeprovisionedResource;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.ProvisionResponse;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.ProvisionedResource;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.ResourceDefinition;
import org.eclipse.edc.policy.model.Action;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.policy.model.PolicyType;
import org.eclipse.edc.policy.model.Rule;
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
import org.seamware.edc.pap.*;
import org.seamware.edc.tmf.ProductCatalogApiClient;
import org.seamware.pap.model.PolicyPathVO;
import org.seamware.pap.model.ServiceCreateVO;
import org.seamware.tmforum.productcatalog.model.CharacteristicValueSpecificationVO;
import org.seamware.tmforum.productcatalog.model.ProductSpecificationCharacteristicVO;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Provisioner for Transfer Processes in the FIWARE Dataspace Connector
 */
public class FDSCProvisioner implements Provisioner<FDSCProviderResourceDefinition, FDSCProvisionedResource> {

    private static final String SERVICE_CONFIGURATION_KEY = "serviceConfiguration";
    private static final String TARGET_KEY = "targetSpecification";
    private static final String UPSTREAM_KEY = "upstreamAddress";
    private static final String ODRL_TARGET_KEY = "target";
    public static final String ODRL_UID = "odrl:uid";

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
        this.objectMapper = objectMapper.copy();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, false);
        // required, so that the convertValue produces a proper map representation
        this.objectMapper.addMixIn(Policy.class, PolicyMixin.class);
        this.objectMapper.addMixIn(Rule.class, RuleMixin.class);
        this.objectMapper.addMixIn(Action.class, ActionMixin.class);
        this.objectMapper.addMixIn(PolicyType.class, PolicyTypeMixin.class);
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
        try {
            monitor.info("Provision " + resourceDefinition.getTransferProcessId() + " and policy " + objectMapper.writeValueAsString(policy));
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

            String serviceId = resourceDefinition.getTransferProcessId();

            PolicyPathVO policyPathVO = odrlPapClient.createService(new ServiceCreateVO().id(serviceId));

            // in order to not conflict in the pap, the policy should be identified by the process, rather than its orginal id
            policy.getExtensibleProperties().put(ODRL_UID, resourceDefinition.getTransferProcessId());

            Optional<Map> targetSpec = optionalExtendableProductSpecification
                    .flatMap(eps -> getCharValue(eps, TARGET_KEY, Map.class));
            if (targetSpec.isPresent()) {
                monitor.debug("Replace target with the asset specific config.");
                Map<String, Object> policyMap = objectMapper.convertValue(policy, new TypeReference<Map<String, Object>>() {
                });
                policyMap.put(ODRL_TARGET_KEY, targetSpec.get());
                odrlPapClient.createPolicy(serviceId, policyMap);
            } else {
                odrlPapClient.createPolicy(serviceId, policy);
            }

            Route serviceRoute = transferMapper.toServiceRoute(resourceDefinition, upstreamAddress.get(), policyPathVO.getPolicyPath());
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
            String wellKnownRouteId = transferMapper.toWellKnownRouteId(provisionedResource);

            odrlPapClient.deleteService(provisionedResource.getTransferProcessId());

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
