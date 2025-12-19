package org.seamware.edc.apisix;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class ProxyRewritePlugin extends ApisixPlugin {

    private String uri;

    public String getUri() {
        return uri;
    }

    public ProxyRewritePlugin setUri(String uri) {
        this.uri = uri;
        return this;
    }

    @JsonIgnore
    @Override
    public String getPluginName() {
        return "proxy-rewrite";
    }
}
