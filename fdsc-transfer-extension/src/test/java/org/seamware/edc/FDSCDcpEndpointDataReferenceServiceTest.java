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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.dataplane.spi.DataFlow;
import org.eclipse.edc.participantcontext.spi.identity.ParticipantIdentityResolver;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.seamware.edc.store.OwnParticipantIdentity;

public class FDSCDcpEndpointDataReferenceServiceTest {

  private static final String TEST_TRANSFER_HOST = "transfer.host";
  private static final String TEST_FLOW_ID = "my-flow";
  private static final String CONTEXT_A = "context-a";
  private static final String CONTEXT_B = "context-b";
  private static final String DEFAULT_CONTEXT = "default-context";
  private static final String IDENTITY_A = "did:web:a.example";
  private static final String IDENTITY_B = "did:web:b.example";
  private static final String DEFAULT_IDENTITY = "did:web:default.example";
  private static final String COUNTER_PARTY_ID = "did:web:counter-party.example";
  private static final String UNKNOWN_CONTEXT = "unknown-context";

  private Vault vault;
  private Clock clock;
  private TransferProcessStore transferProcessStore;

  private FDSCDcpEndpointDataReferenceService fdscDcpEndpointDataReferenceService;

  @BeforeEach
  public void setup() {
    vault = mock(Vault.class);
    clock = Clock.fixed(Instant.EPOCH, ZoneId.systemDefault());

    TransferConfig transferConfig =
        TransferConfig.Builder.newInstance()
            .transferHost(TEST_TRANSFER_HOST)
            .transferProtocol("https")
            .build();

    transferProcessStore = mock(TransferProcessStore.class);
    givenTransferInContext(DEFAULT_CONTEXT);
    ParticipantIdentityResolver identityResolver = mock(ParticipantIdentityResolver.class);
    when(identityResolver.getParticipantId(eq(CONTEXT_A), any())).thenReturn(IDENTITY_A);
    when(identityResolver.getParticipantId(eq(CONTEXT_B), any())).thenReturn(IDENTITY_B);
    when(identityResolver.getParticipantId(eq(DEFAULT_CONTEXT), any()))
        .thenReturn(DEFAULT_IDENTITY);
    fdscDcpEndpointDataReferenceService =
        new FDSCDcpEndpointDataReferenceService(
            transferConfig,
            vault,
            new TransferContextResolver(transferProcessStore, DEFAULT_CONTEXT),
            new OwnParticipantIdentity(() -> identityResolver, DEFAULT_IDENTITY, DEFAULT_CONTEXT),
            clock);
  }

  private void givenTransferInContext(String participantContextId) {
    when(transferProcessStore.findById(TEST_FLOW_ID))
        .thenReturn(
            TransferProcess.Builder.newInstance()
                .id(TEST_FLOW_ID)
                .participantContextId(participantContextId)
                .build());
  }

  private static Stream<Arguments> transferContexts() {
    return Stream.of(
        Arguments.of(CONTEXT_A, IDENTITY_A),
        Arguments.of(CONTEXT_B, IDENTITY_B),
        Arguments.of(null, DEFAULT_IDENTITY));
  }

  @ParameterizedTest
  @MethodSource("transferContexts")
  public void testCreateEndpointReference_issuer_is_identity_of_transfer_context(
      String participantContextId, String expectedIssuer) throws Exception {
    when(vault.resolveSecret(any())).thenReturn(getRSAJWK());
    givenTransferInContext(participantContextId);
    DataFlow counterPartyFlow =
        DataFlow.Builder.newInstance().id(TEST_FLOW_ID).participantId(COUNTER_PARTY_ID).build();

    Result<DataAddress> result =
        fdscDcpEndpointDataReferenceService.createEndpointDataReference(counterPartyFlow);

    assertTrue(result.succeeded(), "The data address should have been returned.");
    SignedJWT token =
        SignedJWT.parse(result.getContent().getStringProperty(EDC_NAMESPACE + "authorization"));
    assertEquals(
        expectedIssuer,
        token.getJWTClaimsSet().getIssuer(),
        "The EDR must be issued by the transfer's own context, never by the counter-party.");
    assertEquals(List.of(expectedIssuer), token.getJWTClaimsSet().getAudience());
  }

  @Test
  public void testCreateEndpointReference_fail_no_transfer_process() {
    when(vault.resolveSecret(any())).thenReturn(getRSAJWK());
    when(transferProcessStore.findById(any())).thenReturn(null);

    assertTrue(
        fdscDcpEndpointDataReferenceService.createEndpointDataReference(getDataFlow()).failed(),
        "Without a transfer, no issuer can be resolved and no EDR may be signed.");
  }

  @Test
  public void testCreateEndpointReference_fail_unknown_context_identity() {
    when(vault.resolveSecret(any())).thenReturn(getRSAJWK());
    givenTransferInContext(UNKNOWN_CONTEXT);

    assertTrue(
        fdscDcpEndpointDataReferenceService.createEndpointDataReference(getDataFlow()).failed(),
        "Without an identity for the transfer's context, no EDR may be signed.");
  }

  @Test
  public void testCreateEndpointReference_success() {
    when(vault.resolveSecret(any())).thenReturn(getRSAJWK());

    DataFlow theFlow = getDataFlow();
    Result<DataAddress> dataAddressResult =
        fdscDcpEndpointDataReferenceService.createEndpointDataReference(theFlow);
    assertTrue(dataAddressResult.succeeded(), "The data address should have been returned.");

    DataAddress dataAddress = dataAddressResult.getContent();

    assertEquals("FDSC", dataAddress.getType());
    assertEquals("bearer", dataAddress.getStringProperty(EDC_NAMESPACE + "authType"));
    assertEquals(
        // trailing slash on purpose: the gateway route is `/{transferProcessId}/*`
        // and does not match a URL that ends at the id
        "https://transfer.host/my-flow/",
        dataAddress.getStringProperty(EDC_NAMESPACE + "endpoint"));
    assertEquals(
        "https://w3id.org/idsa/v4.1/HTTP",
        dataAddress.getStringProperty(EDC_NAMESPACE + "endpointType"));
    assertEquals("my-flow", dataAddress.getStringProperty("clientId"));
    assertFalse(dataAddress.getStringProperty(EDC_NAMESPACE + "authorization").isEmpty());
  }

  @Test
  public void testCreateEndpointReference_fail_no_key() {
    when(vault.resolveSecret(any())).thenReturn(null);
    assertTrue(
        fdscDcpEndpointDataReferenceService.createEndpointDataReference(getDataFlow()).failed(),
        "Without a key, no address should be returned.");
  }

  @Test
  public void testCreateEndpointReference_fail_invalid_key() {
    when(vault.resolveSecret(any())).thenReturn("my-non-rsa-key");
    assertTrue(
        fdscDcpEndpointDataReferenceService.createEndpointDataReference(getDataFlow()).failed(),
        "Without a valid key, no address should be returned.");
  }

  private String getRSAJWK() {
    KeyPairGenerator kpg = null;
    try {
      kpg = KeyPairGenerator.getInstance("RSA");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException("RSA is not available.", e);
    }
    kpg.initialize(2048);
    KeyPair keyPair = kpg.generateKeyPair();

    RSAKey rsaJWK =
        new RSAKey.Builder((java.security.interfaces.RSAPublicKey) keyPair.getPublic())
            .privateKey((java.security.interfaces.RSAPrivateKey) keyPair.getPrivate())
            .keyID("sig")
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .build();
    return rsaJWK.toJSONString();
  }

  private static DataFlow getDataFlow() {
    DataFlow theFlow = DataFlow.Builder.newInstance().id(TEST_FLOW_ID).build();
    return theFlow;
  }

  private static DataFlow getDataFlow(String transferPath) {
    return DataFlow.Builder.newInstance()
        .id(TEST_FLOW_ID)
        .source(
            DataAddress.Builder.newInstance()
                .type("FDSC")
                .property("transferPath", transferPath)
                .build())
        .build();
  }
}
