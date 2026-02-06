package org.seamware.edc;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import org.eclipse.edc.iam.did.spi.resolution.DidPublicKeyResolver;
import org.eclipse.edc.iam.verifiablecredentials.spi.validation.TrustedIssuerRegistry;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.seamware.edc.tir.EbsiTrustedIssuersRegistry;
import org.seamware.edc.tir.TirClient;


@Provides({TrustedIssuerRegistry.class})
public class TirExtension implements ServiceExtension {

    private TirClient tirClient;
    private TrustedIssuerRegistry trustedIssuerRegistry;

    @Inject
    private Monitor monitor;

    @Inject
    private OkHttpClient okHttpClient;

    @Inject
    private ObjectMapper objectMapper;


    @Override
    public void initialize(ServiceExtensionContext context) {
        TirConfig tirConfig = TirConfig.fromConfig(context.getConfig());
        if (tirConfig.isEnabled()) {
            context.registerService(TrustedIssuerRegistry.class, trustedIssuerRegistry(context));
        }
    }

    public TrustedIssuerRegistry trustedIssuerRegistry(ServiceExtensionContext context) {
        if (trustedIssuerRegistry == null) {
            trustedIssuerRegistry = new EbsiTrustedIssuersRegistry(monitor, tirClient(context));
        }
        return trustedIssuerRegistry;
    }

    public TirClient tirClient(ServiceExtensionContext context) {
        if (tirClient == null) {
            TirConfig tirConfig = TirConfig.fromConfig(context.getConfig());
            tirClient = new TirClient(monitor, okHttpClient, tirConfig.getTilAddress(), objectMapper);
        }
        return tirClient;
    }
}
