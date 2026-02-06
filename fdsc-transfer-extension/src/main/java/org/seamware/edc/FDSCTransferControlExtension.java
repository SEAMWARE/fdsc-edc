package org.seamware.edc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import okhttp3.OkHttpClient;
import org.eclipse.edc.connector.controlplane.transfer.spi.provision.ProvisionManager;
import org.eclipse.edc.connector.controlplane.transfer.spi.provision.ResourceManifestGenerator;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance;
import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstanceStates;
import org.eclipse.edc.connector.dataplane.selector.spi.store.DataPlaneInstanceStore;
import org.eclipse.edc.connector.dataplane.spi.edr.EndpointDataReferenceService;
import org.eclipse.edc.connector.dataplane.spi.edr.EndpointDataReferenceServiceRegistry;
import org.eclipse.edc.connector.dataplane.spi.iam.PublicEndpointGeneratorService;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.WebService;
import org.seamware.edc.apisix.ApisixAdminClient;
import org.seamware.edc.ccs.CredentialsConfigServiceClient;
import org.seamware.edc.dcp.JwksController;
import org.seamware.edc.dcp.OidConfigController;
import org.seamware.edc.pap.OdrlPapClient;
import org.seamware.edc.store.TMFEdcMapper;
import org.seamware.edc.tmf.ProductCatalogApiClient;
import org.seamware.edc.transfer.*;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Optional;


public class FDSCTransferControlExtension implements ServiceExtension {

    private static final String NAME = "FDSC Transfer Extension";
    private static final String KEY_ID = "sig";

    public static final String KEY_NAME = "fdsc-dcp-signing-key";
    public static final String CONTEXT_SCOPE = "odrl";
    public static final String DATAPLANE_OID4VP_ID = "FDSC-OID4VC";
    public static final String DATAPLANE_DCP_ID = "FDSC-DCP";
    public static final String FDSC_TYPE = "FDSC";
    public static final String TYPE_HTTP_DATA = "HttpData";
    public static final String TRANSFER_TYPE_HTTP_PULL = "HttpData-PULL";

    @Inject
    public ProvisionManager provisionManager;

    @Inject
    public Monitor monitor;

    @Inject
    public OkHttpClient okHttpClient;

    @Inject
    public ObjectMapper objectMapper;

    @Inject
    public ProductCatalogApiClient productCatalogApiClient;

    @Inject
    public CriterionOperatorRegistry criterionOperatorRegistry;

    @Inject
    public ResourceManifestGenerator resourceManifestGenerator;

    @Inject
    public DataPlaneInstanceStore dataPlaneInstanceStore;

    @Inject
    private EndpointDataReferenceServiceRegistry endpointDataReferenceServiceRegistry;

    @Inject
    private PublicEndpointGeneratorService endpointGenerator;

    @Inject
    private WebService webService;

    @Inject
    public Vault vault;

    @Inject
    public Clock clock;

    @Inject
    public TMFEdcMapper tmfEdcMapper;

    @Inject
    public JsonLd jsonLd;

    private ApisixAdminClient apisixAdminClient;
    private CredentialsConfigServiceClient credentialsConfigServiceClient;
    private OdrlPapClient odrlPapClient;
    private TransferConfig transferConfig;
    private TransferMapper transferMapper;
    private TransferProcessStore transferProcessStore;
    private PolicyEngine policyEngine;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {

        TransferConfig transferConfig = transferConfig(context);

        try {
            monitor.info("Transfer config: " + new ObjectMapper().writeValueAsString(transferConfig));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        if (transferConfig.isEnabled()) {

            if (transferConfig.getOid4Vc().enabled()) {
                monitor.info("Enable OID4VC provisioning");
                enableOid4Vc(context, transferConfig.getOid4Vc());
            }
            if (transferConfig.getDcp().enabled()) {
                monitor.info("Enable DCP provisioning");
                enableDcp(context, transferConfig.getDcp());
            }

        } else {
            monitor.info("TMF TransferControl is not enabled.");
        }
    }

    private void enableDcp(ServiceExtensionContext context, TransferConfig.Dcp dcp) {
        // generate private key or get it
        if (Optional.ofNullable(vault.resolveSecret(KEY_NAME)).isEmpty()) {
            KeyPairGenerator kpg = null;
            try {
                kpg = KeyPairGenerator.getInstance("RSA");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalArgumentException("RSA is not available.", e);
            }
            kpg.initialize(2048);
            KeyPair keyPair = kpg.generateKeyPair();

            RSAKey rsaJWK = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) keyPair.getPublic())
                    .privateKey((java.security.interfaces.RSAPrivateKey) keyPair.getPrivate())
                    .keyID(KEY_ID)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .build();
            String keyString = rsaJWK.toJSONString();
            vault.storeSecret(KEY_NAME, keyString);
        }
        webService.registerResource(new JwksController(vault, monitor));
        webService.registerResource(new OidConfigController(monitor, transferConfig, context.getParticipantId()));

        FDSCDcpProviderResourceDefinitionGenerator fdscProviderResourceDefinitionGenerator = new FDSCDcpProviderResourceDefinitionGenerator(monitor);
        resourceManifestGenerator.registerGenerator(fdscProviderResourceDefinitionGenerator);

        FDSCDcpProvisioner fdscDcpProvisioner = new FDSCDcpProvisioner(
                monitor,
                apisixAdminClient(context),
                productCatalogApiClient,
                transferMapper(context),
                objectMapper,
                policyEngine);
        provisionManager.register(fdscDcpProvisioner);
        monitor.info("Registered DCP Provisioner.");
        dataPlaneInstanceStore.save(DataPlaneInstance.Builder.newInstance()
                .id(DATAPLANE_DCP_ID)
                .url(transferConfig.getApisix().address())
                .state(DataPlaneInstanceStates.AVAILABLE.code())
                .allowedSourceType(FDSC_TYPE)
                .allowedTransferType(TRANSFER_TYPE_HTTP_PULL)
                .build());
        EndpointDataReferenceService endpointDataReferenceService = new FDSCDcpEndpointDataReferenceService(transferConfig, vault, context.getParticipantId(), clock);

        endpointDataReferenceServiceRegistry.register(TYPE_HTTP_DATA, endpointDataReferenceService);
        endpointDataReferenceServiceRegistry.register(FDSC_TYPE, endpointDataReferenceService);
    }

    private void enableOid4Vc(ServiceExtensionContext context, TransferConfig.Oid4Vc oid4Vc) {
        FDSCOID4VPProviderResourceDefinitionGenerator fdscProviderResourceDefinitionGenerator = new FDSCOID4VPProviderResourceDefinitionGenerator(monitor);
        resourceManifestGenerator.registerGenerator(fdscProviderResourceDefinitionGenerator);

        jsonLd.registerContext("http://www.w3.org/ns/odrl.jsonld", CONTEXT_SCOPE);

        FDSCOID4VPProvisioner FDSCOID4VPProvisioner = new FDSCOID4VPProvisioner(
                monitor,
                apisixAdminClient(context),
                credentialsConfigServiceClient(transferConfig.getOid4Vc()),
                odrlPapClient(transferConfig.getOid4Vc()),
                productCatalogApiClient,
                transferMapper(context),
                objectMapper,
                tmfEdcMapper,
                jsonLd);
        provisionManager.register(FDSCOID4VPProvisioner);

        dataPlaneInstanceStore.save(DataPlaneInstance.Builder.newInstance()
                .id(DATAPLANE_OID4VP_ID)
                .url(transferConfig.getApisix().address())
                .state(DataPlaneInstanceStates.AVAILABLE.code())
                .allowedSourceType(FDSC_TYPE)
                .allowedTransferType(TRANSFER_TYPE_HTTP_PULL)
                .build());

        EndpointDataReferenceService endpointDataReferenceService = new FDSCOid4VpEndpointDataReferenceService(transferConfig);

        endpointDataReferenceServiceRegistry.register(TYPE_HTTP_DATA, endpointDataReferenceService);
        endpointDataReferenceServiceRegistry.register(FDSC_TYPE, endpointDataReferenceService);
    }

    @Provider
    public TransferConfig transferConfig(ServiceExtensionContext context) {
        if (transferConfig == null) {
            transferConfig = TransferConfig.fromConfig(context.getConfig());
        }
        return transferConfig;
    }

    @Provider
    public ApisixAdminClient apisixAdminClient(ServiceExtensionContext context) {
        if (apisixAdminClient == null) {
            TransferConfig config = transferConfig(context);
            apisixAdminClient = new ApisixAdminClient(monitor, okHttpClient, config.getApisix().address(), objectMapper, config.getApisix().token());
        }
        return apisixAdminClient;

    }

    public CredentialsConfigServiceClient credentialsConfigServiceClient(TransferConfig.Oid4Vc oid4Vc) {
        if (credentialsConfigServiceClient == null) {
            credentialsConfigServiceClient = new CredentialsConfigServiceClient(monitor, okHttpClient, oid4Vc.credentialsConfigAddress(), objectMapper);
        }
        return credentialsConfigServiceClient;
    }

    public OdrlPapClient odrlPapClient(TransferConfig.Oid4Vc oid4Vc) {
        if (odrlPapClient == null) {
            odrlPapClient = new OdrlPapClient(monitor, okHttpClient, oid4Vc.odrlPapHost(), objectMapper);
        }
        return odrlPapClient;
    }


    @Provider
    public TransferMapper transferMapper(ServiceExtensionContext context) {
        if (transferMapper == null) {
            transferMapper = new TransferMapper(transferConfig(context));
        }
        return transferMapper;
    }

}
