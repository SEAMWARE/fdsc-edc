package org.seamware.edc;

import org.eclipse.edc.connector.controlplane.contract.spi.offer.ConsumerOfferResolver;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Requires;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.seamware.edc.store.TMFEdcMapper;
import org.seamware.edc.store.TMForumConsumerOfferResolver;
import org.seamware.edc.tmf.ProductCatalogApiClient;

import java.util.logging.Logger;

/**
 * Extension to resolve offers through TMForum
 */
@Requires(ConsumerOfferResolver.class)
public class TMFOfferResolverExtension implements ServiceExtension {


    @Override
    public String name() {
        return "TMFOfferResolverExtension";
    }

    @Inject
    private ProductCatalogApiClient productCatalogApiClient;

    @Inject
    private TMFEdcMapper tmfEdcMapper;

    @Inject
    private Monitor monitor;

    @Override
    public void initialize(ServiceExtensionContext context) {
        if(TMFConfig.fromConfig(context.getConfig()).isEnabled()) {
            context.registerService(ConsumerOfferResolver.class, new TMForumConsumerOfferResolver(monitor, productCatalogApiClient, tmfEdcMapper));
        }
        monitor.info("TMFExtension is not enabled, TMForumConsumerOfferResolver will not be registered.");
    }
}
