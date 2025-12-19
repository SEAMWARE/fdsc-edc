package org.seamware.edc.apisix;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class OpaPlugin extends ApisixPlugin {

    private String host;
    private String policy;
    private boolean withBody;

    public String getHost() {
        return host;
    }

    public OpaPlugin setHost(String host) {
        this.host = host;
        return this;
    }

    public String getPolicy() {
        return policy;
    }

    public OpaPlugin setPolicy(String policy) {
        this.policy = policy;
        return this;
    }

    @JsonProperty("with_body")
    public boolean getWithBody() {
        return withBody;
    }

    @JsonProperty("with_body")
    public OpaPlugin setWithBody(boolean withBody) {
        this.withBody = withBody;
        return this;
    }

    @JsonIgnore
    @Override
    public String getPluginName() {
        return "opa";
    }
}
