package org.seamware.edc.apisix;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Route {

    private String id;
    private String host;
    private String uri;
    private Map<String, ApisixPlugin> plugins;
    private Upstream upstream;

    public String getId() {
        return id;
    }

    public Route setId(String id) {
        this.id = id;
        return this;
    }

    public String getHost() {
        return host;
    }

    public Route setHost(String host) {
        this.host = host;
        return this;
    }

    public String getUri() {
        return uri;
    }

    public Route setUri(String uri) {
        this.uri = uri;
        return this;
    }

    public Map<String, ApisixPlugin> getPlugins() {
        return plugins;
    }

    public Route setPlugins(Map<String, ApisixPlugin> plugins) {
        this.plugins = plugins;
        return this;
    }

    public Upstream getUpstream() {
        return upstream;
    }

    public Route setUpstream(Upstream upstream) {
        this.upstream = upstream;
        return this;
    }
}
