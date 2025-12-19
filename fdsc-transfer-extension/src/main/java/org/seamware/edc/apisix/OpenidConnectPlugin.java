package org.seamware.edc.apisix;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class OpenidConnectPlugin extends ApisixPlugin {

    private boolean bearerOnly;
    private String clientId;
    private String clientSecret;
    private String discovery;
    private Map<String, Object> proxyOpts;
    private boolean sslVerify;
    private boolean useJwks;

    @JsonProperty("bearer_only")
    public boolean getBearerOnly() {
        return bearerOnly;
    }

    @JsonProperty("bearer_only")
    public OpenidConnectPlugin setBearerOnly(boolean bearerOnly) {
        this.bearerOnly = bearerOnly;
        return this;
    }

    @JsonProperty("client_id")
    public String getClientId() {
        return clientId;
    }

    @JsonProperty("client_id")
    public OpenidConnectPlugin setClientId(String clientId) {
        this.clientId = clientId;
        return this;
    }

    @JsonProperty("client_secret")
    public String getClientSecret() {
        return clientSecret;
    }

    @JsonProperty("client_secret")
    public OpenidConnectPlugin setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
        return this;
    }

    public String getDiscovery() {
        return discovery;
    }

    public OpenidConnectPlugin setDiscovery(String discovery) {
        this.discovery = discovery;
        return this;
    }

    @JsonProperty("proxy_opts")
    public Map<String, Object> getProxyOpts() {
        return proxyOpts;
    }

    @JsonProperty("proxy_opts")
    public OpenidConnectPlugin setProxyOpts(Map<String, Object> proxyOpts) {
        this.proxyOpts = proxyOpts;
        return this;
    }

    @JsonProperty("ssl_verify")
    public boolean getSslVerify() {
        return sslVerify;
    }

    @JsonProperty("ssl_verify")
    public OpenidConnectPlugin setSslVerify(boolean sslVerify) {
        this.sslVerify = sslVerify;
        return this;
    }

    @JsonProperty("use_jwks")
    public boolean getUseJwks() {
        return useJwks;
    }

    @JsonProperty("use_jwks")
    public OpenidConnectPlugin setUseJwks(boolean useJwks) {
        this.useJwks = useJwks;
        return this;
    }

    @JsonIgnore
    @Override
    public String getPluginName() {
        return "openid-connect";
    }
}
