package org.seamware.edc.identity;

import io.github.wistefan.oid4vp.config.RequestParameters;
import io.github.wistefan.oid4vp.model.TokenResponse;

import java.util.concurrent.CompletableFuture;

/**
 * Token providing interface to improve testability
 */
public interface TokenProvider {

    CompletableFuture<TokenResponse> getAccessToken(RequestParameters requestParameters);
}
