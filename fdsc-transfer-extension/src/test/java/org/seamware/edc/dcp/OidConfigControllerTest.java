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
package org.seamware.edc.dcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.eclipse.edc.participantcontext.spi.identity.ParticipantIdentityResolver;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.web.spi.exception.ObjectNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.seamware.edc.TransferConfig;
import org.seamware.edc.store.OwnParticipantIdentity;

class OidConfigControllerTest {

  private static final String OID_HOST = "http://controlplane:8081/api";
  private static final String EXPECTED_JWKS_URI = OID_HOST + "/.well-known/jwks";
  private static final String DEFAULT_CONTEXT = "default-context";
  private static final String DEFAULT_IDENTITY = "did:web:default.example";

  private final TransferConfig transferConfig =
      TransferConfig.Builder.newInstance()
          .dcp(
              new TransferConfig.Dcp.Builder()
                  .enabled(true)
                  .oidConfigBuilder(new TransferConfig.OidConfig.Builder().host(OID_HOST))
                  .build())
          .build();

  private final ParticipantIdentityResolver identityResolver = initIdentityResolver();

  private static ParticipantIdentityResolver initIdentityResolver() {
    ParticipantIdentityResolver resolver = mock(ParticipantIdentityResolver.class);
    when(resolver.getParticipantId(eq("context-a"), any())).thenReturn("did:web:a.example");
    when(resolver.getParticipantId(eq("context-b"), any())).thenReturn("did:web:b.example");
    when(resolver.getParticipantId(eq(DEFAULT_CONTEXT), any())).thenReturn(DEFAULT_IDENTITY);
    return resolver;
  }

  private OidConfigController controller(String defaultParticipantContextId) {
    return new OidConfigController(
        mock(Monitor.class),
        transferConfig,
        new OwnParticipantIdentity(
            () -> identityResolver, DEFAULT_IDENTITY, defaultParticipantContextId),
        defaultParticipantContextId);
  }

  @ParameterizedTest
  @CsvSource({
    "context-a, did:web:a.example",
    "context-b, did:web:b.example",
    "default-context, did:web:default.example"
  })
  void getOIDConfiguration_returnsIssuerOfContext(String contextId, String expectedIssuer) {
    assertOidConfig(controller(DEFAULT_CONTEXT).getOIDConfiguration(contextId), expectedIssuer);
  }

  @Test
  void getOIDConfiguration_defaultPathReturnsDefaultContextIssuer() {
    assertOidConfig(controller(DEFAULT_CONTEXT).getOIDConfiguration(), DEFAULT_IDENTITY);
  }

  @Test
  void getOIDConfiguration_unknownContextIsNotFound() {
    assertThrows(
        ObjectNotFoundException.class,
        () -> controller(DEFAULT_CONTEXT).getOIDConfiguration("unknown-context"));
  }

  @Test
  void getOIDConfiguration_defaultPathWithoutDefaultContextIsNotFound() {
    assertThrows(
        ObjectNotFoundException.class,
        () -> controller(null).getOIDConfiguration(),
        "Virtual mode without a default context must not answer with a guessed issuer.");
  }

  private static void assertOidConfig(Map<String, Object> oidConfig, String expectedIssuer) {
    assertEquals(
        Map.of(
            OidConfigController.ISSUER_KEY,
            expectedIssuer,
            OidConfigController.JWKS_URI_KEY,
            EXPECTED_JWKS_URI),
        oidConfig);
  }
}
