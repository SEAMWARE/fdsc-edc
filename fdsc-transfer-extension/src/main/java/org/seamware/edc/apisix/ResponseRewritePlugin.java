package org.seamware.edc.apisix;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Map;

public class ResponseRewritePlugin extends ApisixPlugin {

    private Map<String, ResponseRewriteHeader> headers;

    @JsonIgnore
    @Override
    public String getPluginName() {
        return "response-rewrite";
    }

}
