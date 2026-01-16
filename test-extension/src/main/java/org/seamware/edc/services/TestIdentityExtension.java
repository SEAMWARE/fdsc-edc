package org.seamware.edc.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.protocol.spi.DefaultParticipantIdExtractionFunction;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.iam.AudienceResolver;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.seamware.edc.TestConfig;

@Provides({IdentityService.class, AudienceResolver.class, DefaultParticipantIdExtractionFunction.class})
public class TestIdentityExtension implements ServiceExtension {

    private static final String NAME = "Test Identity Extension";

    @Override
    public String name() {
        return NAME;
    }

    @Inject
    private Monitor monitor;

    @Inject
    private ObjectMapper objectMapper;

    @Override
    public void initialize(ServiceExtensionContext context) {
        TestConfig testConfig = TestConfig.fromConfig(context.getConfig());
        if (!testConfig.getIdentityConfig().enabled()) {
            monitor.info("Test identity services are not enabled.");
            return;
        }

        context.registerService(IdentityService.class, identityService(context));
        context.registerService(AudienceResolver.class, audienceResolver());
        context.registerService(DefaultParticipantIdExtractionFunction.class, defaultParticipantIdExtractionFunction());
    }

    private IdentityService identityService(ServiceExtensionContext context) {
        return new TestIdentityService(monitor, objectMapper, context.getParticipantId());
    }

    private AudienceResolver audienceResolver() {
        return new NoopAudienceResolver(monitor);
    }

    private DefaultParticipantIdExtractionFunction defaultParticipantIdExtractionFunction() {
        return new TestParticipantIdExtractionFunction(monitor);
    }
}
