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

import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.SignedJWT;
import java.time.Clock;
import java.util.Map;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.dataplane.spi.DataFlow;
import org.eclipse.edc.participantcontext.spi.identity.ParticipantIdentityResolver;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.security.Vault;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.seamware.edc.apisix.OpenidConnectPlugin;
import org.seamware.edc.dcp.OidConfigController;
import org.seamware.edc.store.OwnParticipantIdentity;
import org.seamware.edc.transfer.TransferMapper;

/**
 * APISIX accepts an EDR only if its {@code iss} equals the {@code issuer} of the discovery document
 * the route points at. Asserts that for transfers in different contexts.
 */
class EdrIssuerMatchesDiscoveryTest {

  private static final String OID_HOST = "http://controlplane:8081/api";
  private static final String OPEN_ID_PATH = "/.well-known/openid-configuration";
  private static final String DEFAULT_CONTEXT = "default-context";
  private static final String DEFAULT_IDENTITY = "did:web:default.example";
  private static final String COUNTER_PARTY_ID = "did:web:counter-party.example";
  private static final String RSA_KEY_ID = "sig";
  private static final int RSA_KEY_SIZE = 2048;

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

  @ParameterizedTest(name = "transfer in {1}")
  @CsvSource({
    "transfer-a, context-a, did:web:a.example",
    "transfer-b, context-b, did:web:b.example",
    "transfer-port, did:web:a.example%3A8443, did:web:a.example%3A8443",
    "transfer-default, default-context, did:web:default.example",
  })
  void edrIssuerEqualsIssuerOfRouteDiscovery(
      String flowId, String participantContextId, String identity) throws Exception {
    TransferProcessStore transferProcessStore = mock(TransferProcessStore.class);
    when(transferProcessStore.findById(flowId))
        .thenReturn(
            TransferProcess.Builder.newInstance()
                .id(flowId)
                .participantContextId(participantContextId)
                .build());
    ParticipantIdentityResolver identityResolver = mock(ParticipantIdentityResolver.class);
    when(identityResolver.getParticipantId(eq(participantContextId), any())).thenReturn(identity);
    OwnParticipantIdentity ownIdentity =
        new OwnParticipantIdentity(() -> identityResolver, DEFAULT_IDENTITY, DEFAULT_CONTEXT);
    TransferContextResolver contextResolver =
        new TransferContextResolver(transferProcessStore, DEFAULT_CONTEXT);
    Vault vault = mock(Vault.class);
    when(vault.resolveSecret(any())).thenReturn(signingKey());

    DataFlow dataFlow =
        DataFlow.Builder.newInstance().id(flowId).participantId(COUNTER_PARTY_ID).build();
    String edrIssuer =
        SignedJWT.parse(
                new FDSCDcpEndpointDataReferenceService(
                        transferConfig, vault, contextResolver, ownIdentity, Clock.systemUTC())
                    .createEndpointDataReference(dataFlow)
                    .getContent()
                    .getStringProperty(EDC_NAMESPACE + "authorization"))
            .getJWTClaimsSet()
            .getIssuer();

    String discovery =
        ((OpenidConnectPlugin)
                new TransferMapper(transferConfig, DEFAULT_CONTEXT)
                    .toDcpServiceRoute(
                        flowId, "upstream:8080", contextResolver.resolve(flowId).getContent())
                    .getPlugins()
                    .get("openid-connect"))
            .getDiscovery();
    String contextSuffix = discovery.substring((OID_HOST + OPEN_ID_PATH).length());
    OidConfigController oidConfigController =
        new OidConfigController(mock(Monitor.class), transferConfig, ownIdentity, DEFAULT_CONTEXT);
    Map<String, Object> oidConfig =
        contextSuffix.isEmpty()
            ? oidConfigController.getOIDConfiguration()
            : oidConfigController.getOIDConfiguration(contextSuffix.substring(1));

    assertEquals(edrIssuer, oidConfig.get("issuer"), "Discovery: " + discovery);
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
