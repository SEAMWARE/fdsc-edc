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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class OID4VPConfigTest {

  @ParameterizedTest(name = "Config from {0}")
  @MethodSource("getValidConfig")
  public void testValidConfig(String testFile, OID4VPConfig expectedConfig) throws IOException {
    Config testConfig = fromFile(testFile);
    assertEquals(
        expectedConfig,
        OID4VPConfig.fromConfig(testConfig),
        "The config should have successfully been read.");
  }

  @ParameterizedTest(name = "Config from {0}")
  @MethodSource("getInvalidConfig")
  public void testInvalidConfig(String testFile, String expectedMessage) throws IOException {
    Config testConfig = fromFile(testFile);
    var e = assertThrows(NullPointerException.class, () -> OID4VPConfig.fromConfig(testConfig));
    assertEquals(expectedMessage, e.getMessage());
  }

  private static Stream<Arguments> getInvalidConfig() {
    return Stream.of(
        Arguments.of(
            "invalid/1.properties", "When using OID4VP, the holder-id needs to be configured."),
        Arguments.of(
            "invalid/2.properties", "For OID4VP, a valid key path needs to be configured."),
        Arguments.of(
            "invalid/3.properties", "For OID4VP, a valid key type needs to be configured."),
        Arguments.of(
            "invalid/4.properties",
            "If a proxy should be used for OID4VP, the proxy host needs to be configured."));
  }

  private static Stream<Arguments> getValidConfig() throws IOException {
    List<Arguments> arguments = new ArrayList<>();

    OID4VPConfig oid4VPConfig1 =
        OID4VPConfig.Builder.newInstance()
            .enabled(true)
            .clientId("test")
            .scope(Set.of("openid", "edc"))
            .trustAll(false)
            .credentialsFolder("/credentials")
            .trustAnchorsFolder("/trust-anchors")
            .organizationClaim("issuer")
            .holder(
                new OID4VPConfig.HolderConfig(
                    "did:web:holder",
                    "did:web:holder",
                    new OID4VPConfig.KeyConfig("EC", "/my/key.pem"),
                    "ECDH-ES"))
            .proxy(new OID4VPConfig.ProxyConfig(true, "localhost", 8888))
            .build();

    arguments.add(Arguments.of("valid/1.properties", oid4VPConfig1));

    OID4VPConfig oid4VPConfig2 =
        OID4VPConfig.Builder.newInstance()
            .enabled(true)
            .clientId("test")
            .scope(Set.of("openid", "edc"))
            .trustAll(false)
            .credentialsFolder("/credentials")
            .trustAnchorsFolder("/trust-anchors")
            .organizationClaim("issuer")
            .holder(
                new OID4VPConfig.HolderConfig(
                    "did:web:holder",
                    "did:web:holder",
                    new OID4VPConfig.KeyConfig("EC", "/my/key.pem"),
                    "ECDH-ES"))
            .proxy(new OID4VPConfig.ProxyConfig.Builder().enabled(false).build())
            .build();

    arguments.add(Arguments.of("valid/2.properties", oid4VPConfig2));

    OID4VPConfig oid4VPConfig3 =
        OID4VPConfig.Builder.newInstance()
            .enabled(false)
            // defaults will still be set
            .clientId("dsp-connector")
            .scope(Set.of("openid"))
            .trustAll(false)
            .credentialsFolder("credentials")
            .organizationClaim("verifiableCredential.issuer")
            .proxy(new OID4VPConfig.ProxyConfig(false, null, 8888))
            .build();

    arguments.add(Arguments.of("valid/3.properties", oid4VPConfig3));

    return arguments.stream();
  }

  private static Config fromFile(String file) throws IOException {
    Properties properties = new Properties();
    properties.load(OID4VPConfigTest.class.getClassLoader().getResourceAsStream(file));
    return ConfigFactory.fromProperties(properties);
  }
}
