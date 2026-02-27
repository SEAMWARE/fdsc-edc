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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.eclipse.edc.connector.dataplane.spi.DataFlow;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FDSCDcpEndpointDataReferenceServiceTest {

  private static final String TEST_TRANSFER_HOST = "transfer.host";
  private static final String TEST_ISSUER = "test-issuer";

  private Vault vault;
  private Clock clock;

  private FDSCDcpEndpointDataReferenceService fdscDcpEndpointDataReferenceService;

  @BeforeEach
  public void setup() {
    vault = mock(Vault.class);
    clock = Clock.fixed(Instant.EPOCH, ZoneId.systemDefault());

    TransferConfig transferConfig =
        TransferConfig.Builder.newInstance().transferHost(TEST_TRANSFER_HOST).build();

    fdscDcpEndpointDataReferenceService =
        new FDSCDcpEndpointDataReferenceService(transferConfig, vault, TEST_ISSUER, clock);
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
    assertEquals("bearer", dataAddress.getStringProperty(EDC_NAMESPACE + "tokenType"));
    assertEquals(
        "http://transfer.host/my-flow", dataAddress.getStringProperty(EDC_NAMESPACE + "endpoint"));
    assertEquals(
        "https://w3id.org/idsa/v4.1/HTTP",
        dataAddress.getStringProperty(EDC_NAMESPACE + "endpointType"));
    assertEquals("my-flow", dataAddress.getStringProperty("clientId"));
    assertFalse(dataAddress.getStringProperty(EDC_NAMESPACE + "token").isEmpty());
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
    DataFlow theFlow = DataFlow.Builder.newInstance().id("my-flow").build();
    return theFlow;
  }
}
