package org.seamware.edc.transfer;

import org.seamware.edc.TransferConfig;
import org.seamware.edc.apisix.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TransferMapper {

    private static final String WELL_KOWN_OPEN_ID_CONFIGURATION = "/.well-known/openid-configuration";
    private static final String WELL_KNOWN_ENDPOINT_TEMPLATE = "/services/%s" + WELL_KOWN_OPEN_ID_CONFIGURATION;
    private static final String REWRITE_TEMPLATE = "^/%s/(.*)";
    private static final String DISCOVERY_ENDPOINT_TEMPLATE = "%s" + WELL_KNOWN_ENDPOINT_TEMPLATE;
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

    public Route toDcpServiceRoute(FDSCDcpProviderResourceDefinition resourceDefinition, String upstreamAddress) {

        // configure the route to the service
        Upstream upstream = new Upstream()
                .setType(ROUTING_TYPE_ROUND_ROBIN)
                .setNodes(Map.of(upstreamAddress, 1));

        // remove the process id from the path before forwarding to the upstream
        ProxyRewritePlugin proxyRewritePlugin = new ProxyRewritePlugin()
                .setRegexUri(List.of(String.format(REWRITE_TEMPLATE, resourceDefinition.getTransferProcessId()), "/$1"));

        String discoveryAddress = transferConfig.getDcp().oidConfig().host() + transferConfig.getDcp().oidConfig().openIdPath();

        OpenidConnectPlugin openidConnectPlugin = new OpenidConnectPlugin()
                .setBearerOnly(true)
                .setClientId(resourceDefinition.getTransferProcessId())
                .setClientSecret("unused")
                .setDiscovery(discoveryAddress)
                .setRequiredScopes(List.of(resourceDefinition.getTransferProcessId()))
                .setUseJwks(true);
        Optional.ofNullable(transferConfig.getApisix().httpsProxy()).ifPresent((proxyAddress) ->
                openidConnectPlugin.setProxyOpts(Map.of("https_proxy", proxyAddress, "no_proxy", "*.cluster.local")));

        return new Route()
                .setId(String.format(SERVICE_ROUTE_ID, resourceDefinition.getTransferProcessId()))
                .setHost(transferConfig.getTransferHost())
                .setUpstream(upstream)
                .setUri("/" + resourceDefinition.getTransferProcessId() + "/*")
                .setPlugins(Map.of(
                        openidConnectPlugin.getPluginName(), openidConnectPlugin,
                        proxyRewritePlugin.getPluginName(), proxyRewritePlugin));
    }

    public Route toOid4VpServiceRoute(FDSCOID4VPProviderResourceDefinition resourceDefinition, String upstreamAddress, String policyAddress) {

        // configure the route to the service
        Upstream upstream = new Upstream()
                .setType(ROUTING_TYPE_ROUND_ROBIN)
                .setNodes(Map.of(upstreamAddress, 1));

        // configure the open-policy-agent connection - will enforce the policy
        OpaPlugin opaPlugin = new OpaPlugin()
                .setHost(transferConfig.getOid4Vc().opaHost())
                .setPolicy(policyAddress)
                .setWithBody(true)
                .setWithRoute(true);

        // remove the process id from the path before forwarding to the upstream
        ProxyRewritePlugin proxyRewritePlugin = new ProxyRewritePlugin()
                .setRegexUri(List.of(String.format(REWRITE_TEMPLATE, resourceDefinition.getTransferProcessId()), "/$1"));

        OpenidConnectPlugin openidConnectPlugin = new OpenidConnectPlugin()
                .setBearerOnly(true)
                .setClientId(resourceDefinition.getTransferProcessId())
                .setClientSecret("unused")
                .setDiscovery(
                        String.format(DISCOVERY_ENDPOINT_TEMPLATE, transferConfig.getOid4Vc().verifierHost(), resourceDefinition.getTransferProcessId()))
                .setSslVerify(false)
                .setUseJwks(true);
        Optional.ofNullable(transferConfig.getApisix().httpsProxy()).ifPresent((proxyAddress) ->
                openidConnectPlugin.setProxyOpts(Map.of("https_proxy", proxyAddress, "no_proxy", "*.cluster.local")));

        return new Route()
                .setId(String.format(SERVICE_ROUTE_ID, resourceDefinition.getTransferProcessId()))
                .setHost(transferConfig.getTransferHost())
                .setUpstream(upstream)
                .setUri("/" + resourceDefinition.getTransferProcessId() + "/*")
                .setPlugins(Map.of(
                        openidConnectPlugin.getPluginName(), openidConnectPlugin,
                        opaPlugin.getPluginName(), opaPlugin,
                        proxyRewritePlugin.getPluginName(), proxyRewritePlugin));
    }

    public Route toWellknownRouteRoute(FDSCOID4VPProviderResourceDefinition resourceDefinition) {

        Upstream upstream = new Upstream()
                .setType(ROUTING_TYPE_ROUND_ROBIN)
                .setNodes(Map.of(transferConfig.getOid4Vc().verifierInternalHost(), 1));

        ProxyRewritePlugin proxyRewritePlugin = new ProxyRewritePlugin()
                .setUri(String.format(WELL_KNOWN_ENDPOINT_TEMPLATE, resourceDefinition.getTransferProcessId()));


        return new Route()
                .setId(String.format(WELL_KNOWN_ROUTE_ID, resourceDefinition.getTransferProcessId()))
                .setHost(transferConfig.getTransferHost())
                .setUpstream(upstream)
                .setUri("/" + resourceDefinition.getTransferProcessId() + WELL_KOWN_OPEN_ID_CONFIGURATION)
                .setPlugins(Map.of(proxyRewritePlugin.getPluginName(), proxyRewritePlugin));
    }

}
