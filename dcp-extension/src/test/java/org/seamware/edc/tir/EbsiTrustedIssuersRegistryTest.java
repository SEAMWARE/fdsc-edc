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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.web.spi.exception.BadGatewayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.seamware.til.model.CredentialsVO;
import org.seamware.til.model.TrustedIssuerVO;

public class EbsiTrustedIssuersRegistryTest {

  private static final String TEST_ISSUER = "test-issuer";

  private TirClient tirClient;

  private EbsiTrustedIssuersRegistry ebsiTrustedIssuersRegistry;

  @BeforeEach
  public void setup() {
    tirClient = mock(TirClient.class);
    ebsiTrustedIssuersRegistry = new EbsiTrustedIssuersRegistry(tirClient);
  }

  @ParameterizedTest
  @MethodSource("getTypeSets")
  public void testGetSupportedTypes_success(Set<String> credentialTypes) {
    when(tirClient.getIssuer(eq(TEST_ISSUER)))
        .thenReturn(Optional.of(getIssuerVo(new ArrayList<>(credentialTypes))));
    assertEquals(
        credentialTypes, ebsiTrustedIssuersRegistry.getSupportedTypes(getIssuer(TEST_ISSUER)));
  }

  @Test
  public void testGetSupportedTypes_error_bubble() {
    when(tirClient.getIssuer(anyString())).thenThrow(new RuntimeException("Error"));
    assertThrows(
        RuntimeException.class,
        () -> ebsiTrustedIssuersRegistry.getSupportedTypes(getIssuer(TEST_ISSUER)),
        "Errors from the client should bubble.");
  }

  @Test
  public void testGetSupportedTypes_success_issuer_no_credentials() {
    TrustedIssuerVO trustedIssuerVO = new TrustedIssuerVO().addCredentialsItem(new CredentialsVO());
    when(tirClient.getIssuer(anyString())).thenReturn(Optional.of(trustedIssuerVO));

    assertTrue(
        ebsiTrustedIssuersRegistry.getSupportedTypes(getIssuer(TEST_ISSUER)).isEmpty(),
        "If the issuer has no types, none should be returned.");
  }

  @Test
  public void testGetSupportedTypes_success_no_such_issuer() {
    when(tirClient.getIssuer(anyString())).thenReturn(Optional.empty());

    assertTrue(
        ebsiTrustedIssuersRegistry.getSupportedTypes(getIssuer(TEST_ISSUER)).isEmpty(),
        "If the issuer has no types, none should be returned.");
  }

  @Test
  public void testGetSupportedTypes_union_of_all_tils() {
    TirClient profileTirClient = mock(TirClient.class);
    when(tirClient.getIssuer(eq(TEST_ISSUER)))
        .thenReturn(Optional.of(getIssuerVo(List.of("global-credential"))));
    when(profileTirClient.getIssuer(eq(TEST_ISSUER)))
        .thenReturn(Optional.of(getIssuerVo(List.of("profile-credential"))));
    EbsiTrustedIssuersRegistry registry =
        new EbsiTrustedIssuersRegistry(tirClient, List.of(profileTirClient), mock(Monitor.class));

    assertEquals(
        Set.of("global-credential", "profile-credential"),
        registry.getSupportedTypes(getIssuer(TEST_ISSUER)));
  }

  @Test
  public void testGetSupportedTypes_skips_failing_profile_til() {
    TirClient failingProfileClient = mock(TirClient.class);
    when(tirClient.getIssuer(eq(TEST_ISSUER)))
        .thenReturn(Optional.of(getIssuerVo(List.of("global-credential"))));
    when(failingProfileClient.getIssuer(anyString()))
        .thenThrow(new BadGatewayException("TIL unavailable"));
    EbsiTrustedIssuersRegistry registry =
        new EbsiTrustedIssuersRegistry(
            tirClient, List.of(failingProfileClient), mock(Monitor.class));

    assertEquals(Set.of("global-credential"), registry.getSupportedTypes(getIssuer(TEST_ISSUER)));
  }

  @Test
  public void testGetSupportedTypes_propagates_failing_primary_til() {
    TirClient profileTirClient = mock(TirClient.class);
    when(tirClient.getIssuer(anyString())).thenThrow(new BadGatewayException("TIL unavailable"));
    EbsiTrustedIssuersRegistry registry =
        new EbsiTrustedIssuersRegistry(tirClient, List.of(profileTirClient), mock(Monitor.class));

    assertThrows(
        BadGatewayException.class, () -> registry.getSupportedTypes(getIssuer(TEST_ISSUER)));
  }

  private static Stream<Arguments> getTypeSets() {
    return Stream.of(
        Arguments.of(Set.of("test-credential")),
        Arguments.of(Set.of()),
        Arguments.of(Set.of("test-credential", "other-credential")));
  }

  private Issuer getIssuer(String id) {
    return new Issuer(id);
  }

  private TrustedIssuerVO getIssuerVo(List<String> credentials) {
    TrustedIssuerVO trustedIssuerVO = new TrustedIssuerVO();
    credentials.stream()
        .map(type -> new CredentialsVO().credentialsType(type))
        .forEach(trustedIssuerVO::addCredentialsItem);
    return trustedIssuerVO;
  }
}
