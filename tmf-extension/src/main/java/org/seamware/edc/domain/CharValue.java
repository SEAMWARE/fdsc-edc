package org.seamware.edc.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CharValue {

    private Object tmfValue;
    private boolean isDefault;

    public Object getTmfValue() {
        return tmfValue;
    }

    public CharValue setTmfValue(Object tmfValue) {
        this.tmfValue = tmfValue;
        return this;
    }

    @JsonProperty("isDefault")
    public boolean isDefault() {
        return isDefault;
    }

    @JsonProperty("isDefault")
    public CharValue setDefault(boolean aDefault) {
        isDefault = aDefault;
        return this;
    }
}
