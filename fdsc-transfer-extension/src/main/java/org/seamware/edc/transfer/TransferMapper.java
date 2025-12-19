package org.seamware.edc.transfer;

import org.seamware.edc.TransferConfig;
import org.seamware.edc.apisix.*;

import java.util.Map;

public class TransferMapper {

    private static final String WELL_KOWN_OPEN_ID_CONFIGURATION = "/.well-known/openid-configuration";
    private static final String WELL_KNOWN_ENDPOINT_TEMPLATE = "/services/%s" + WELL_KOWN_OPEN_ID_CONFIGURATION;
    private static final String DISCOVERY_ENDPOINT_TEMPLATE = "%s" + WELL_KNOWN_ENDPOINT_TEMPLATE;
    private static final String POLICY_MAIN = "policy/main";
    private static final String SERVICE_ROUTE_ID = "%s-service";
    private static final String WELL_KNOWN_ROUTE_ID = "%s-well-known";
    private static final String ROUTING_TYPE_ROUND_ROBIN = "roundrobin";

    private final TransferConfig transferConfig;

    public TransferMapper(TransferConfig transferConfig) {
        this.transferConfig = transferConfig;
    }

    public String toServiceRouteId(FDSCProvisionedResource provisionedResource) {
        return String.format(SERVICE_ROUTE_ID, provisionedResource.getTransferProcessId());
    }

    public String toWellKnownRouteId(FDSCProvisionedResource provisionedResource) {
        return String.format(WELL_KNOWN_ROUTE_ID, provisionedResource.getTransferProcessId());
    }


    public Route toServiceRoute(FDSCProviderResourceDefinition resourceDefinition, String serviceAddress) {

        Upstream upstream = new Upstream()
                .setType(ROUTING_TYPE_ROUND_ROBIN)
                .setNodes(Map.of(serviceAddress, 1));

        OpaPlugin opaPlugin = new OpaPlugin()
                .setHost(transferConfig.getOpaHost())
                .setPolicy(POLICY_MAIN)
                .setWithBody(true);

        OpenidConnectPlugin openidConnectPlugin = new OpenidConnectPlugin()
                .setBearerOnly(true)
                .setClientId(resourceDefinition.getTransferProcessId())
                .setClientSecret("unused")
                .setDiscovery(String.format(DISCOVERY_ENDPOINT_TEMPLATE, transferConfig.getVerifierHost(), resourceDefinition.getTransferProcessId()))
                .setSslVerify(false)
                .setUseJwks(true)
                // TODO: remove
                .setProxyOpts(Map.of("https_proxy", "http://squid-proxy.infra.svc.cluster.local:8888"));

        return new Route()
                .setId(String.format(SERVICE_ROUTE_ID, resourceDefinition.getTransferProcessId()))
                .setHost(transferConfig.getTransferHost())
                .setUpstream(upstream)
                .setUri(resourceDefinition.getTransferProcessId())
                .setPlugins(Map.of(opaPlugin.getPluginName(), opaPlugin, openidConnectPlugin.getPluginName(), openidConnectPlugin));
    }

    public Route toWellknownRouteRoute(FDSCProviderResourceDefinition resourceDefinition) {

        Upstream upstream = new Upstream()
                .setType(ROUTING_TYPE_ROUND_ROBIN)
                .setNodes(Map.of(transferConfig.getVerifierInternalHost(), 1));

        ProxyRewritePlugin proxyRewritePlugin = new ProxyRewritePlugin()
                // preconfigured service?
                .setUri(String.format(WELL_KNOWN_ENDPOINT_TEMPLATE, resourceDefinition.getTransferProcessId()));


        return new Route()
                .setId(String.format(WELL_KNOWN_ROUTE_ID, resourceDefinition.getTransferProcessId()))
                .setHost(transferConfig.getTransferHost())
                .setUpstream(upstream)
                .setUri(WELL_KOWN_OPEN_ID_CONFIGURATION)
                .setPlugins(Map.of(proxyRewritePlugin.getPluginName(), proxyRewritePlugin));
    }

}
