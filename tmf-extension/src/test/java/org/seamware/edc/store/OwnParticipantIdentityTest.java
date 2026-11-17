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
package org.seamware.edc.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.edc.participantcontext.spi.identity.ParticipantIdentityResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OwnParticipantIdentityTest {

  private static final String CONTEXT_A = "dataspace-a";
  private static final String CONTEXT_B = "dataspace-b";
  private static final String DID_A = "did:web:a";
  private static final String DID_B = "did:web:b";
  private static final String CONFIGURED_ID = "did:web:configured";
  private static final String PROTOCOL = "dataspace-protocol-http:2025-1";

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({CONTEXT_A + "," + DID_A, CONTEXT_B + "," + DID_B})
  void participantId_isResolvedPerContext(String context, String expectedId) {
    ParticipantIdentityResolver resolver = mock(ParticipantIdentityResolver.class);
    when(resolver.getParticipantId(eq(CONTEXT_A), eq(PROTOCOL))).thenReturn(DID_A);
    when(resolver.getParticipantId(eq(CONTEXT_B), eq(PROTOCOL))).thenReturn(DID_B);

    assertEquals(
        expectedId,
        new OwnParticipantIdentity(resolver, CONFIGURED_ID).participantId(context, PROTOCOL));
  }

  @ParameterizedTest(name = "context {0}")
  @CsvSource(
      value = {CONTEXT_A, "null"},
      nullValues = "null")
  void participantId_fallsBackToTheConfiguredIdWithoutResolver(String context) {
    assertEquals(
        CONFIGURED_ID,
        OwnParticipantIdentity.fixed(CONFIGURED_ID).participantId(context, PROTOCOL));
  }

  @Test
  void participantId_failsForAContextWithoutIdentity() {
    OwnParticipantIdentity identity =
        new OwnParticipantIdentity(mock(ParticipantIdentityResolver.class), CONFIGURED_ID);

    assertThrows(IllegalStateException.class, () -> identity.participantId(CONTEXT_A, PROTOCOL));
  }

  @Test
  void participantId_picksUpAResolverRegisteredAfterConstruction() {
    AtomicReference<ParticipantIdentityResolver> registry = new AtomicReference<>();
    OwnParticipantIdentity identity = new OwnParticipantIdentity(registry::get, CONFIGURED_ID);
    assertEquals(CONFIGURED_ID, identity.participantId(CONTEXT_A, PROTOCOL));

    ParticipantIdentityResolver resolver = mock(ParticipantIdentityResolver.class);
    when(resolver.getParticipantId(eq(CONTEXT_A), eq(PROTOCOL))).thenReturn(DID_A);
    registry.set(resolver);

    assertEquals(DID_A, identity.participantId(CONTEXT_A, PROTOCOL));
  }
}
