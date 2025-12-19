package org.seamware.edc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.connector.controlplane.services.spi.catalog.CatalogProtocolService;
import org.eclipse.edc.protocol.spi.DefaultParticipantIdExtractionFunction;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Requires;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.seamware.edc.store.TMFEdcMapper;
import org.seamware.edc.store.TMForumBackedCatalogProtocolService;
import org.seamware.edc.tmf.ProductCatalogApiClient;

/**
 * Extension to provide the catalog with contents from TMForum
 */
@Requires(CatalogProtocolService.class)
public class CatalogProtocolServiceExtension implements ServiceExtension {

    private static final String NAME = "Protocol Service Extension";

    @Inject
    public ProductCatalogApiClient productCatalogApi;

    @Inject
    public Monitor monitor;
    @Inject
    public ObjectMapper objectMapper;

    @Inject
    public TMFEdcMapper tmfEdcMapper;

    @Override
    public String name() {
        return NAME;
    }
    @Override
    public void initialize(ServiceExtensionContext context) {
        TMFConfig tmfConfig = TMFConfig.fromConfig(context.getConfig());
        if (tmfConfig.isEnabled() && tmfConfig.getCatalogConfig().enabled()) {
            context.registerService(CatalogProtocolService.class, new TMForumBackedCatalogProtocolService(tmfEdcMapper, productCatalogApi, context.getParticipantId()));
        } else {
            monitor.info("TMF Catalog Protocol Service is not enabled.");
        }
    }
}
