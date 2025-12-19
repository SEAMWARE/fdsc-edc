package org.seamware.edc;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import org.eclipse.edc.connector.controlplane.transfer.spi.provision.ProvisionManager;
import org.eclipse.edc.connector.controlplane.transfer.spi.provision.ResourceManifestGenerator;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance;
import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstanceStates;
import org.eclipse.edc.connector.dataplane.selector.spi.store.DataPlaneInstanceStore;
import org.eclipse.edc.connector.dataplane.spi.Endpoint;
import org.eclipse.edc.connector.dataplane.spi.edr.EndpointDataReferenceServiceRegistry;
import org.eclipse.edc.connector.dataplane.spi.iam.PublicEndpointGeneratorService;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.seamware.edc.apisix.ApisixAdminClient;
import org.seamware.edc.ccs.CredentialsConfigServiceClient;
import org.seamware.edc.tmf.ProductCatalogApiClient;
import org.seamware.edc.transfer.FDSCEndpointGenerator;
import org.seamware.edc.transfer.FDSCProviderResourceDefinitionGenerator;
import org.seamware.edc.transfer.FDSCProvisioner;
import org.seamware.edc.transfer.TransferMapper;


public class FDSCTransferControlExtension implements ServiceExtension {

    private static final String NAME = "FDSC Transfer Extension";

    private static final String DATAPLANE_ID = "FDSC";
    private static final String TYPE_HTTP_DATA = "HttpData";
    private static final String TRANSFER_TYPE_HTTP_PULL = "HttpData-PULL";

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

    private ApisixAdminClient apisixAdminClient;
    private CredentialsConfigServiceClient credentialsConfigServiceClient;
    private TransferConfig transferConfig;
    private TransferMapper transferMapper;
    private TransferProcessStore transferProcessStore;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {

        TransferConfig transferConfig = transferConfig(context);

        if (transferConfig.isEnabled()) {
            var monitor = context.getMonitor();
            FDSCProviderResourceDefinitionGenerator fdscProviderResourceDefinitionGenerator = new FDSCProviderResourceDefinitionGenerator(monitor);
            resourceManifestGenerator.registerGenerator(fdscProviderResourceDefinitionGenerator);

            FDSCProvisioner fdscProvisioner = new FDSCProvisioner(
                    monitor,
                    apisixAdminClient(context),
                    credentialsConfigServiceClient(context),
                    productCatalogApiClient,
                    transferMapper(context),
                    objectMapper);
            provisionManager.register(fdscProvisioner);

            dataPlaneInstanceStore.save(DataPlaneInstance.Builder.newInstance()
                    .id(DATAPLANE_ID)
                    .url(transferConfig.getApisix().address())
                    .state(DataPlaneInstanceStates.AVAILABLE.code())
                    .allowedSourceType("FDSC")
                    .allowedTransferType(TRANSFER_TYPE_HTTP_PULL)
                    .build());

            endpointDataReferenceServiceRegistry.register(TYPE_HTTP_DATA, new FDSCEndpointDataReferenceService(transferConfig));
            endpointDataReferenceServiceRegistry.register("FDSC", new FDSCEndpointDataReferenceService(transferConfig));
        } else {
            monitor.info("TMF TransferControl is not enabled.");
        }
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

    @Provider
    public CredentialsConfigServiceClient credentialsConfigServiceClient(ServiceExtensionContext context) {
        if (credentialsConfigServiceClient == null) {
            credentialsConfigServiceClient = new CredentialsConfigServiceClient(monitor, okHttpClient, transferConfig(context).getCredentialsConfigAddress(), objectMapper);
        }
        return credentialsConfigServiceClient;
    }

    @Provider
    public TransferMapper transferMapper(ServiceExtensionContext context) {
        if (transferMapper == null) {
            transferMapper = new TransferMapper(transferConfig(context));
        }
        return transferMapper;
    }

//
//    @Provider
//    public TransferProcessStore transferProcessStore() {
//        if (transferProcessStore == null) {
//            transferProcessStore = new TMFBackedTransferProcessStore(monitor, tmfEdcMapper, transactionContext, criterionOperatorRegistry, usageApi, agreementApi, productInventoryApi);
//        }
//        return transferProcessStore;
//    }

}
