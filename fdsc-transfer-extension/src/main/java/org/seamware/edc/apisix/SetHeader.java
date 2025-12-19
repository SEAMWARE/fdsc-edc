package org.seamware.edc.apisix;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SetHeader extends ResponseRewriteHeader {

    private String contentType;

    @JsonProperty("content-type")
    public String getContentType() {
        return contentType;
    }

    @JsonProperty("content-type")
    public SetHeader setContentType(String contentType) {
        this.contentType = contentType;
        return this;
    }

    @JsonIgnore
    @Override
    public String getName() {
        return "set";
    }
}
