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
package org.seamware.edc;

/*-
 * #%L
 * fdsc-transfer-extension
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

import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.dataplane.spi.DataFlow;
import org.eclipse.edc.participantcontext.spi.identity.ParticipantIdentityResolver;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.security.Vault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.seamware.edc.apisix.OpenidConnectPlugin;
import org.seamware.edc.dcp.OidConfigController;
import org.seamware.edc.store.OwnParticipantIdentity;
import org.seamware.edc.transfer.TransferMapper;

/**
 * Every participant context signs its EDRs with the same key and publishes it through the same
 * JWKS, so a signature alone does not say which dataspace a token came from (ADR-13). Isolation
 * rests on the two conditions an APISIX {@code openid-connect} route applies: the token's {@code
 * scope} has to cover the route's {@code required_scopes}, and its {@code iss} has to equal the
 * issuer of the discovery document the route points at. Asserts that an EDR opens its own route and
 * no other, across and within contexts.
 */
class EdrIsRejectedByForeignRouteTest {

  private static final String OID_HOST = "http://controlplane:8081/api";
  private static final String OPEN_ID_PATH = "/.well-known/openid-configuration";
  private static final String DEFAULT_CONTEXT = "default-context";
  private static final String DEFAULT_IDENTITY = "did:web:default.example";
  private static final String COUNTER_PARTY_ID = "did:web:counter-party.example";
  private static final String UPSTREAM = "upstream:8080";
  private static final String OPENID_CONNECT_PLUGIN = "openid-connect";
  private static final String ISSUER_KEY = "issuer";
  private static final String AUTHORIZATION_PROPERTY = EDC_NAMESPACE + "authorization";
  private static final String SCOPE_CLAIM = "scope";
  private static final String RSA_KEY_ID = "sig";
  private static final int RSA_KEY_SIZE = 2048;

  private static final String TRANSFER_A1 = "transfer-a1";
  private static final String TRANSFER_A2 = "transfer-a2";
  private static final String TRANSFER_B1 = "transfer-b1";
  private static final String CONTEXT_A = "context-a";
  private static final String CONTEXT_B = "context-b";
  private static final String IDENTITY_A = "did:web:a.example";
  private static final String IDENTITY_B = "did:web:b.example";

  private static final Map<String, String> CONTEXT_OF_TRANSFER =
      Map.of(TRANSFER_A1, CONTEXT_A, TRANSFER_A2, CONTEXT_A, TRANSFER_B1, CONTEXT_B);
  private static final Map<String, String> IDENTITY_OF_CONTEXT =
      Map.of(CONTEXT_A, IDENTITY_A, CONTEXT_B, IDENTITY_B);

  private final TransferConfig transferConfig =
      TransferConfig.Builder.newInstance()
          .transferHost("transfer.host")
          .transferProtocol("https")
          .apisix(new TransferConfig.Apisix("http://apisix-admin:9180", "token", null))
          .dcp(
              new TransferConfig.Dcp.Builder()
                  .enabled(true)
                  .oidConfigBuilder(new TransferConfig.OidConfig.Builder().host(OID_HOST))
                  .build())
          .build();

  @ParameterizedTest(name = "EDR of {0} on the route of {1} -> accepted={2}")
  @CsvSource({
    // its own route
    "transfer-a1, transfer-a1, true",
    "transfer-b1, transfer-b1, true",
    // another transfer of the same dataspace
    "transfer-a1, transfer-a2, false",
    "transfer-a2, transfer-a1, false",
    // a transfer of the other dataspace
    "transfer-a1, transfer-b1, false",
    "transfer-b1, transfer-a1, false",
  })
  void edrOpensOnlyItsOwnRoute(String edrTransfer, String routeTransfer, boolean expectedAccepted)
      throws Exception {
    assertEquals(
        expectedAccepted,
        routeAccepts(routeTransfer, edrClaims(edrTransfer)),
        String.format("EDR of %s against the route of %s", edrTransfer, routeTransfer));
  }

  /**
   * The scope binding alone would also hold with one identity for all contexts. Pins the second
   * layer: across contexts the issuers really do differ, so the discovery check bites too.
   */
  @Test
  void aCrossContextEdrAlsoFailsTheIssuerCheck() throws Exception {
    String issuerOfA = edrClaims(TRANSFER_A1).getIssuer();
    String issuerOfB = edrClaims(TRANSFER_B1).getIssuer();

    assertNotEquals(issuerOfB, issuerOfA);
    assertEquals(IDENTITY_A, issuerOfA);
    assertEquals(issuerOfB, routeIssuer(TRANSFER_B1));
    assertNotEquals(issuerOfA, routeIssuer(TRANSFER_B1));
  }

  /** Two transfers of one context share an issuer, so only the scope separates their routes. */
  @Test
  void withinOneContextOnlyTheScopeSeparates() throws Exception {
    assertEquals(routeIssuer(TRANSFER_A2), edrClaims(TRANSFER_A1).getIssuer());

    assertTrue(requiredScopes(TRANSFER_A2).contains(TRANSFER_A2));
    assertEquals(TRANSFER_A1, edrClaims(TRANSFER_A1).getStringClaim(SCOPE_CLAIM));
  }

  /** The two conditions an APISIX {@code openid-connect} route applies to a bearer token. */
  private boolean routeAccepts(String routeTransfer, JWTClaimsSet edr) throws ParseException {
    List<String> grantedScopes = List.of(edr.getStringClaim(SCOPE_CLAIM).split(" "));
    return grantedScopes.containsAll(requiredScopes(routeTransfer))
        && routeIssuer(routeTransfer).equals(edr.getIssuer());
  }

  private List<String> requiredScopes(String transferId) {
    return openidConnectPlugin(transferId).getRequiredScopes();
  }

  /** Resolves the route's discovery document to the issuer APISIX will compare {@code iss} to. */
  private String routeIssuer(String transferId) {
    String discovery = openidConnectPlugin(transferId).getDiscovery();
    String contextSuffix = discovery.substring((OID_HOST + OPEN_ID_PATH).length());
    OidConfigController oidConfigController =
        new OidConfigController(
            mock(Monitor.class), transferConfig, ownParticipantIdentity(), DEFAULT_CONTEXT);
    Map<String, Object> oidConfig =
        contextSuffix.isEmpty()
            ? oidConfigController.getOIDConfiguration()
            : oidConfigController.getOIDConfiguration(contextSuffix.substring(1));
    return (String) oidConfig.get(ISSUER_KEY);
  }

  private OpenidConnectPlugin openidConnectPlugin(String transferId) {
    return (OpenidConnectPlugin)
        new TransferMapper(transferConfig, DEFAULT_CONTEXT)
            .toDcpServiceRoute(
                transferId, UPSTREAM, contextResolver().resolve(transferId).getContent())
            .getPlugins()
            .get(OPENID_CONNECT_PLUGIN);
  }

  private JWTClaimsSet edrClaims(String transferId) throws Exception {
    Vault vault = mock(Vault.class);
    when(vault.resolveSecret(any())).thenReturn(signingKey());
    DataFlow dataFlow =
        DataFlow.Builder.newInstance().id(transferId).participantId(COUNTER_PARTY_ID).build();

    return SignedJWT.parse(
            new FDSCDcpEndpointDataReferenceService(
                    transferConfig,
                    vault,
                    contextResolver(),
                    ownParticipantIdentity(),
                    Clock.systemUTC())
                .createEndpointDataReference(dataFlow)
                .getContent()
                .getStringProperty(AUTHORIZATION_PROPERTY))
        .getJWTClaimsSet();
  }

  private TransferContextResolver contextResolver() {
    TransferProcessStore transferProcessStore = mock(TransferProcessStore.class);
    CONTEXT_OF_TRANSFER.forEach(
        (transferId, contextId) ->
            when(transferProcessStore.findById(transferId))
                .thenReturn(
                    TransferProcess.Builder.newInstance()
                        .id(transferId)
                        .participantContextId(contextId)
                        .build()));
    return new TransferContextResolver(transferProcessStore, DEFAULT_CONTEXT);
  }

  private OwnParticipantIdentity ownParticipantIdentity() {
    ParticipantIdentityResolver identityResolver = mock(ParticipantIdentityResolver.class);
    IDENTITY_OF_CONTEXT.forEach(
        (contextId, identity) ->
            when(identityResolver.getParticipantId(eq(contextId), any())).thenReturn(identity));
    return new OwnParticipantIdentity(() -> identityResolver, DEFAULT_IDENTITY, DEFAULT_CONTEXT);
  }

  private static String signingKey() throws JOSEException {
    return new RSAKeyGenerator(RSA_KEY_SIZE)
        .keyID(RSA_KEY_ID)
        .keyUse(KeyUse.SIGNATURE)
        .algorithm(JWSAlgorithm.RS256)
        .generate()
        .toJSONString();
  }
}
