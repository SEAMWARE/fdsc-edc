package org.seamware.edc.identity;

import io.github.wistefan.oid4vp.OID4VPClient;
import io.github.wistefan.oid4vp.config.RequestParameters;
import io.github.wistefan.oid4vp.model.TokenResponse;

import java.util.concurrent.CompletableFuture;

/**
 * Implementation of the TokenProvider interface, just handing over to the {@link OID4VPClient}
 */
public class OID4VPTokenProvider implements TokenProvider {

    private final OID4VPClient oid4VPClient;

    public OID4VPTokenProvider(OID4VPClient oid4VPClient) {
        this.oid4VPClient = oid4VPClient;
    }

    @Override
    public CompletableFuture<TokenResponse> getAccessToken(RequestParameters requestParameters) {
        return oid4VPClient.getAccessToken(requestParameters);
    }
}
