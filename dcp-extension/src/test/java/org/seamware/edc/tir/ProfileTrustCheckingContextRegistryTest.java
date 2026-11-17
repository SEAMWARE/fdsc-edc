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
package org.seamware.edc.tir;

/*-
 * #%L
 * dcp-extension
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

import static org.eclipse.edc.iam.decentralizedclaims.core.DcpDefaultServicesExtension.CLAIMTOKEN_VC_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.protocol.spi.DataspaceProfileContext;
import org.eclipse.edc.protocol.spi.ParticipantIdExtractionFunction;
import org.eclipse.edc.protocol.spi.ProtocolVersion;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.seamware.til.model.CredentialsVO;
import org.seamware.til.model.TrustedIssuerVO;

public class ProfileTrustCheckingContextRegistryTest {

  private static final String PROFILE_A = "dataspace-a";
  private static final String PROFILE_B = "dataspace-b";
  private static final String PROFILE_WITHOUT_TIL = "dataspace-c";
  private static final String ISSUER_A = "did:web:issuer-a.org";
  private static final String CREDENTIAL_TYPE = "MembershipCredential";
  private static final String PARTICIPANT_ID = "did:web:consumer.org";

  private ParticipantIdExtractionFunction delegate;
  private ProfileTrustCheckingContextRegistry registry;

  @BeforeEach
  public void setup() {
    TirClient tilA = mock(TirClient.class);
    when(tilA.getIssuer(any())).thenReturn(Optional.empty());
    when(tilA.getIssuer(ISSUER_A)).thenReturn(Optional.of(trustedIssuer(CREDENTIAL_TYPE)));
    TirClient tilB = mock(TirClient.class);
    when(tilB.getIssuer(any())).thenReturn(Optional.empty());

    delegate = mock(ParticipantIdExtractionFunction.class);
    when(delegate.apply(any())).thenReturn(PARTICIPANT_ID);

    registry =
        new ProfileTrustCheckingContextRegistry(
            mock(Monitor.class),
            Map.of(
                PROFILE_A, new EbsiTrustedIssuersRegistry(tilA),
                PROFILE_B, new EbsiTrustedIssuersRegistry(tilB)));
    Stream.of(PROFILE_A, PROFILE_B, PROFILE_WITHOUT_TIL)
        .map(this::profile)
        .forEach(registry::register);
  }

  @ParameterizedTest(name = "{0} with issuer {1} -> {2}")
  @MethodSource("trustCases")
  public void participantIdOnlyForIssuersTrustedInTheProfile(
      String profile, String issuer, String expectedParticipantId) {
    ClaimToken claimToken =
        ClaimToken.Builder.newInstance()
            .claim(CLAIMTOKEN_VC_KEY, List.of(credential(issuer, CREDENTIAL_TYPE)))
            .build();

    assertEquals(
        expectedParticipantId, registry.getIdExtractionFunction(profile).apply(claimToken));
  }

  private static Stream<Arguments> trustCases() {
    return Stream.of(
        Arguments.of(PROFILE_A, ISSUER_A, PARTICIPANT_ID),
        Arguments.of(PROFILE_B, ISSUER_A, null),
        Arguments.of(PROFILE_A, "did:web:unknown-issuer.org", null),
        Arguments.of(PROFILE_WITHOUT_TIL, ISSUER_A, PARTICIPANT_ID));
  }

  @Test
  public void credentialOfUntrustedTypeIsRejected() {
    ClaimToken claimToken =
        ClaimToken.Builder.newInstance()
            .claim(CLAIMTOKEN_VC_KEY, List.of(credential(ISSUER_A, "OtherCredential")))
            .build();

    assertNull(registry.getIdExtractionFunction(PROFILE_A).apply(claimToken));
  }

  @Test
  public void profileWithoutTilIsRegisteredUnchanged() {
    assertSame(delegate, registry.getIdExtractionFunction(PROFILE_WITHOUT_TIL));
    assertTrue(
        registry.getIdExtractionFunction(PROFILE_A)
            instanceof ProfileTrustedIssuerIdExtractionFunction);
  }

  private DataspaceProfileContext profile(String name) {
    return new DataspaceProfileContext(
        name,
        new ProtocolVersion("2025-1", "/2025-1", "HTTPS"),
        () -> "",
        delegate,
        null,
        List.of());
  }

  private static VerifiableCredential credential(String issuer, String type) {
    VerifiableCredential credential = mock(VerifiableCredential.class);
    when(credential.getIssuer()).thenReturn(new Issuer(issuer));
    when(credential.getType()).thenReturn(List.of(type));
    return credential;
  }

  private static TrustedIssuerVO trustedIssuer(String credentialType) {
    return new TrustedIssuerVO()
        .addCredentialsItem(new CredentialsVO().credentialsType(credentialType));
  }
}
