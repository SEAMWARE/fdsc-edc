/*
 * Copyright 2025 Seamless Middleware Technologies S.L and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.seamware.edc.identity;

/*-
 * #%L
 * oid4vc-extension
 * %%
 * Copyright (C) 2025 - 2026 Seamless Middleware Technologies S.L
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import io.github.wistefan.oid4vp.model.TokenResponse;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.iam.TokenParameters;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class OID4VPIdentityServiceTest {

  private static final String TEST_AUDIENCE = "http://my-audience.org";
  private static final String TEST_CLIENT_ID = "test-client";
  private static final Set<String> TEST_SCOPE = Set.of("openid", "dsp");

  private static final String TEST_TOKEN = "my-access-token";

  private TokenProvider tokenProvider;
  private OID4VPIdentityService oid4VPIdentityService;

  @BeforeEach
  public void setup() {

    tokenProvider = mock(TokenProvider.class);
    oid4VPIdentityService =
        new OID4VPIdentityService(mock(Monitor.class), tokenProvider, TEST_CLIENT_ID, TEST_SCOPE);
  }

  @Test
  public void testObtainClientCredentials_success() {
    when(tokenProvider.getAccessToken(any()))
        .thenReturn(CompletableFuture.completedFuture(getTokenResponse(TEST_TOKEN)));

    TokenRepresentation expectedTokenRep = getTokenRepresentation(TEST_TOKEN);

    Result<TokenRepresentation> tokenResult =
        oid4VPIdentityService.obtainClientCredentials(getTokenParameters());
    assertTrue(tokenResult.succeeded(), "A token should have been obtained successfully.");

    assertEquals(
        expectedTokenRep.getToken(),
        tokenResult.getContent().getToken(),
        "The expected token should have been returned");
    assertEquals(
        expectedTokenRep.getExpiresIn(),
        tokenResult.getContent().getExpiresIn(),
        "The expected token should have been returned");
    assertEquals(
        expectedTokenRep.getAdditional(),
        tokenResult.getContent().getAdditional(),
        "The expected token should have been returned");
  }

  @Test
  public void testObtainClientCredentials_no_token() {
    when(tokenProvider.getAccessToken(any())).thenReturn(CompletableFuture.completedFuture(null));

    Result<TokenRepresentation> tokenResult =
        oid4VPIdentityService.obtainClientCredentials(getTokenParameters());

    assertTrue(tokenResult.failed(), "No token should have been returned");
  }

  @Test
  public void testObtainClientCredentials_unexpected_exception() {
    when(tokenProvider.getAccessToken(any())).thenThrow(new RuntimeException("Surprising error!"));

    Result<TokenRepresentation> tokenResult =
        oid4VPIdentityService.obtainClientCredentials(getTokenParameters());

    assertTrue(tokenResult.failed(), "No token should have been returned");
  }

  @ParameterizedTest(name = "Invalid response - {0}")
  @MethodSource("getInvalidTokens")
  public void testObtainClientCredentials_incomplete_token(
      String name, TokenResponse invalidResponse) {
    when(tokenProvider.getAccessToken(any()))
        .thenReturn(CompletableFuture.completedFuture(invalidResponse));

    Result<TokenRepresentation> tokenResult =
        oid4VPIdentityService.obtainClientCredentials(getTokenParameters());

    assertTrue(tokenResult.failed(), name);
  }

  private static Stream<Arguments> getInvalidTokens() {
    return Stream.of(
        Arguments.of("TokenResponse needs to contain token and expiry.", new TokenResponse()),
        Arguments.of(
            "TokenResponse needs to contain expiry.",
            new TokenResponse().setAccessToken(TEST_TOKEN)),
        Arguments.of(
            "TokenResponse needs to contain token.", new TokenResponse().setExpiresIn(10l)),
        Arguments.of(
            "TokenResponse needs to contain non empty token.",
            new TokenResponse().setAccessToken("").setExpiresIn(10l)),
        Arguments.of(
            "TokenResponse needs to contain a valid expiry.",
            new TokenResponse().setAccessToken(TEST_TOKEN).setExpiresIn(0l)));
  }

  @Test
  public void testVerifyJwtToken_success() {
    TokenRepresentation tokenRepresentation = getTokenRepresentation(getJWT());
    ClaimToken expectedClaimToken = ClaimToken.Builder.newInstance().claim("sub", "test").build();

    Result<ClaimToken> claimTokenResult =
        oid4VPIdentityService.verifyJwtToken(tokenRepresentation, null);
    assertTrue(claimTokenResult.succeeded(), "The claim token should have been extracted.");
    assertEquals(
        expectedClaimToken.getClaims(),
        claimTokenResult.getContent().getClaims(),
        "All claims should be included.");
  }

  @ParameterizedTest(name = "Invalid token - {0}")
  @MethodSource("getInvalidTokenRepresentation")
  public void testVerifyJwtToken_invalid_token(
      String name, TokenRepresentation invalidRepresentation) {
    Result<ClaimToken> claimTokenResult =
        oid4VPIdentityService.verifyJwtToken(invalidRepresentation, null);
    assertTrue(claimTokenResult.failed(), name);
  }

  private static Stream<Arguments> getInvalidTokenRepresentation() {
    return Stream.of(
        Arguments.of("null token representations are invalid", null),
        Arguments.of(
            "TokenRepresentations without an token are invalid.",
            TokenRepresentation.Builder.newInstance().build()),
        Arguments.of("Empty accesstokens are invalid.", getTokenRepresentation("")),
        Arguments.of(
            "Only valid JWTs can be used as tokens.", getTokenRepresentation("something-wild")));
  }

  private String getJWT() {
    JWTClaimsSet claimsSet = new JWTClaimsSet.Builder().subject("test").build();
    return new PlainJWT(claimsSet).serialize();
  }

  private TokenParameters getTokenParameters() {
    return TokenParameters.Builder.newInstance().claims("aud", TEST_AUDIENCE).build();
  }

  private TokenResponse getTokenResponse(String theToken) {
    return new TokenResponse().setAccessToken(theToken).setExpiresIn(10l);
  }

  private static TokenRepresentation getTokenRepresentation(String theToken) {
    return TokenRepresentation.Builder.newInstance()
        .token("Bearer " + theToken)
        .expiresIn(10l)
        .build();
  }
}
