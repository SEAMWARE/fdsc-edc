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
import org.eclipse.edc.web.spi.exception.BadGatewayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.seamware.til.model.CredentialsVO;
import org.seamware.til.model.TrustedIssuerVO;

public class ProfileTrustCheckingContextRegistryTest {

  private static final String PROFILE_A = "dataspace-a";
  private static final String PROFILE_B = "dataspace-b";
  private static final String PROFILE_WITHOUT_TIL = "dataspace-c";
  // Classic mode registers only this profile, through registerDefault.
  private static final String DEFAULT_PROFILE = "dataspace-protocol-http:2025-1";
  private static final String ISSUER_A = "did:web:issuer-a.org";
  private static final String ISSUER_B = "did:web:issuer-b.org";
  private static final String ISSUER_GLOBAL = "did:web:issuer-global.org";
  private static final String CREDENTIAL_TYPE = "MembershipCredential";
  private static final String PARTICIPANT_ID = "did:web:consumer.org";

  private ParticipantIdExtractionFunction delegate;
  private ProfileTrustCheckingContextRegistry registry;
  private ProfileTrustCheckingContextRegistry defaultOnlyRegistry;
  private ProfileTrustCheckingContextRegistry unreachableTilRegistry;

  @BeforeEach
  public void setup() {
    TirClient tilA = mock(TirClient.class);
    when(tilA.getIssuer(any())).thenReturn(Optional.empty());
    when(tilA.getIssuer(ISSUER_A)).thenReturn(Optional.of(trustedIssuer(CREDENTIAL_TYPE)));
    TirClient tilB = mock(TirClient.class);
    when(tilB.getIssuer(any())).thenReturn(Optional.empty());
    when(tilB.getIssuer(ISSUER_B)).thenReturn(Optional.of(trustedIssuer(CREDENTIAL_TYPE)));
    TirClient globalTil = mock(TirClient.class);
    when(globalTil.getIssuer(any())).thenReturn(Optional.empty());
    when(globalTil.getIssuer(ISSUER_GLOBAL))
        .thenReturn(Optional.of(trustedIssuer(CREDENTIAL_TYPE)));

    delegate = mock(ParticipantIdExtractionFunction.class);
    when(delegate.apply(any())).thenReturn(PARTICIPANT_ID);

    registry =
        new ProfileTrustCheckingContextRegistry(
            mock(Monitor.class),
            new EbsiTrustedIssuersRegistry(globalTil),
            Map.of(
                PROFILE_A, new EbsiTrustedIssuersRegistry(tilA),
                PROFILE_B, new EbsiTrustedIssuersRegistry(tilB)));
    Stream.of(PROFILE_A, PROFILE_B, PROFILE_WITHOUT_TIL)
        .map(this::profile)
        .forEach(registry::register);
    // Upstream only serves default profiles while no standard profile is registered.
    defaultOnlyRegistry =
        new ProfileTrustCheckingContextRegistry(
            mock(Monitor.class),
            new EbsiTrustedIssuersRegistry(globalTil),
            Map.of(PROFILE_A, new EbsiTrustedIssuersRegistry(tilA)));
    defaultOnlyRegistry.registerDefault(profile(DEFAULT_PROFILE));

    // A TIL that answers no lookup at all, as an unreachable or erroring one does.
    TirClient unreachableTil = mock(TirClient.class);
    when(unreachableTil.getIssuer(any()))
        .thenThrow(new BadGatewayException("Was not able to get issuer from til."));
    unreachableTilRegistry =
        new ProfileTrustCheckingContextRegistry(
            mock(Monitor.class),
            new EbsiTrustedIssuersRegistry(globalTil),
            Map.of(
                PROFILE_A, new EbsiTrustedIssuersRegistry(unreachableTil),
                PROFILE_B, new EbsiTrustedIssuersRegistry(tilB)));
    Stream.of(PROFILE_A, PROFILE_B, PROFILE_WITHOUT_TIL)
        .map(this::profile)
        .forEach(unreachableTilRegistry::register);
  }

  /**
   * A TIL outage must reject the counterparty (401), not escape as an exception that the DSP
   * endpoint turns into a 5xx, and it must not affect the other dataspaces.
   */
  @ParameterizedTest(name = "unreachable TIL of {0} with issuer {1} -> {2}")
  @MethodSource("unreachableTilCases")
  public void unreachableProfileTilRejectsInsteadOfFailing(
      String profile, String issuer, String expectedParticipantId) {
    ClaimToken claimToken =
        ClaimToken.Builder.newInstance()
            .claim(CLAIMTOKEN_VC_KEY, List.of(credential(issuer, CREDENTIAL_TYPE)))
            .build();

    assertEquals(
        expectedParticipantId,
        unreachableTilRegistry.getIdExtractionFunction(profile).apply(claimToken));
  }

  private static Stream<Arguments> unreachableTilCases() {
    return Stream.of(
        // the dataspace whose TIL is down rejects every issuer, including its own
        Arguments.of(PROFILE_A, ISSUER_A, null),
        Arguments.of(PROFILE_A, ISSUER_GLOBAL, null),
        // the other dataspaces keep working
        Arguments.of(PROFILE_B, ISSUER_B, PARTICIPANT_ID),
        Arguments.of(PROFILE_WITHOUT_TIL, ISSUER_GLOBAL, PARTICIPANT_ID));
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
        Arguments.of(PROFILE_B, ISSUER_B, PARTICIPANT_ID),
        Arguments.of(PROFILE_B, ISSUER_GLOBAL, null),
        Arguments.of(PROFILE_WITHOUT_TIL, ISSUER_GLOBAL, PARTICIPANT_ID),
        Arguments.of(PROFILE_WITHOUT_TIL, ISSUER_A, null),
        Arguments.of(PROFILE_WITHOUT_TIL, ISSUER_B, null));
  }

  @ParameterizedTest(name = "default profile with issuer {0} -> {1}")
  @MethodSource("defaultProfileTrustCases")
  public void defaultProfileOnlyTrustsTheGlobalTil(String issuer, String expectedParticipantId) {
    ClaimToken claimToken =
        ClaimToken.Builder.newInstance()
            .claim(CLAIMTOKEN_VC_KEY, List.of(credential(issuer, CREDENTIAL_TYPE)))
            .build();

    assertTrue(
        defaultOnlyRegistry.getIdExtractionFunction(DEFAULT_PROFILE)
            instanceof ProfileTrustedIssuerIdExtractionFunction);
    assertEquals(
        expectedParticipantId,
        defaultOnlyRegistry.getIdExtractionFunction(DEFAULT_PROFILE).apply(claimToken));
  }

  private static Stream<Arguments> defaultProfileTrustCases() {
    return Stream.of(
        Arguments.of(ISSUER_GLOBAL, PARTICIPANT_ID),
        Arguments.of(ISSUER_A, null),
        Arguments.of(ISSUER_B, null));
  }

  @Test
  public void credentialOfUntrustedTypeIsRejected() {
    ClaimToken claimToken =
        ClaimToken.Builder.newInstance()
            .claim(CLAIMTOKEN_VC_KEY, List.of(credential(ISSUER_A, "OtherCredential")))
            .build();

    assertNull(registry.getIdExtractionFunction(PROFILE_A).apply(claimToken));
  }

  @ParameterizedTest
  @ValueSource(strings = {PROFILE_A, PROFILE_B, PROFILE_WITHOUT_TIL})
  public void everyProfileIsWrapped(String profile) {
    assertTrue(
        registry.getIdExtractionFunction(profile)
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
