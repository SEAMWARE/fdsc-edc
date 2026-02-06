package org.seamware.edc.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.connector.controlplane.transfer.spi.provision.Provisioner;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.ProvisionedResource;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.ResourceDefinition;
import org.eclipse.edc.spi.monitor.Monitor;
import org.seamware.edc.apisix.ApisixAdminClient;
import org.seamware.edc.tmf.ProductCatalogApiClient;

public abstract class FDSCProvisioner<RD extends ResourceDefinition, PR extends ProvisionedResource> implements Provisioner<RD, PR> {

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

    protected FDSCProvisioner(Monitor monitor, ApisixAdminClient apisixAdminClient, ProductCatalogApiClient productCatalogApiClient, TransferMapper transferMapper, ObjectMapper objectMapper) {
        this.monitor = monitor;
        this.apisixAdminClient = apisixAdminClient;
        this.productCatalogApiClient = productCatalogApiClient;
        this.transferMapper = transferMapper;
        this.objectMapper = objectMapper;
    }

}
