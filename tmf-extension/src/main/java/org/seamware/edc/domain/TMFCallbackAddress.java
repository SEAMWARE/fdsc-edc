package org.seamware.edc.domain;

public class TMFCallbackAddress {
    private String uri;
    private String authKey;
    private String authCodeId;

    public String getUri() {
        return uri;
    }

    public TMFCallbackAddress setUri(String uri) {
        this.uri = uri;
        return this;
    }

    public String getAuthKey() {
        return authKey;
    }

    public TMFCallbackAddress setAuthKey(String authKey) {
        this.authKey = authKey;
        return this;
    }

    public String getAuthCodeId() {
        return authCodeId;
    }

    public TMFCallbackAddress setAuthCodeId(String authCodeId) {
        this.authCodeId = authCodeId;
        return this;
    }
}
