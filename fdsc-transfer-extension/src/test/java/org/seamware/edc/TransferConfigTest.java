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

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class TransferConfigTest {

  @ParameterizedTest(name = "Config from {0}")
  @MethodSource("getValidConfig")
  public void testValidConfig(String testFile, TransferConfig expectedConfig) throws IOException {
    Config testConfig = fromFile(testFile);
    assertEquals(
        expectedConfig,
        TransferConfig.fromConfig(testConfig),
        "The config should have successfully been read.");
  }

  @ParameterizedTest(name = "Config from {0}")
  @MethodSource("getInvalidConfig")
  public void testInvalidConfig(String testFile, String expectedMessage) throws IOException {
    Config testConfig = fromFile(testFile);
    var e = assertThrows(NullPointerException.class, () -> TransferConfig.fromConfig(testConfig));
    assertEquals(expectedMessage, e.getMessage());
  }

  private static Stream<Arguments> getInvalidConfig() {
    return Stream.of(
        Arguments.of(
            "invalid/1.properties",
            "If FDSC Transfers are supported, the host address for the transfers needs to be provided."),
        Arguments.of(
            "invalid/2.properties",
            "If FDSC Transfer is enabled, an apisix admin token needs to be configured."),
        Arguments.of(
            "invalid/3.properties",
            "If FDSC Transfer is enabled, an apisix address needs to be configured."),
        Arguments.of(
            "invalid/4.properties",
            "If FDSC Transfer is enabled, an apisix address needs to be configured."),
        Arguments.of(
            "invalid/5.properties",
            "If FDSC Transfers are supported, the host address for the verifier needs to be provided."),
        Arguments.of(
            "invalid/6.properties",
            "If FDSC Transfers are supported, the host address for opa to be configured in apisix needs to be provided."),
        Arguments.of(
            "invalid/7.properties",
            "If FDSC Transfers are supported, the host address for odrl-pap needs to be provided."),
        Arguments.of(
            "invalid/8.properties", "If DCP is enabled, the OID host needs to be configured."));
  }

  private static Stream<Arguments> getValidConfig() throws IOException {
    List<Arguments> arguments = new ArrayList<>();

    TransferConfig transferConfig1 =
        TransferConfig.Builder.newInstance()
            .enabled(true)
            .transferHost("apisix-public-host.org")
            .oid4Vc(
                new TransferConfig.Oid4Vc.Builder()
                    .enabled(true)
                    .credentialsConfigAddress("http://ccs.org")
                    .verifierHost("https://verifier.org")
                    .verifierInternalHost("verifier:3000")
                    .opaHost("http://localhost:8181")
                    .odrlPapHost("http://odrl-pap.svc.local:8081")
                    .build())
            .dcp(
                new TransferConfig.Dcp.Builder()
                    .enabled(true)
                    .oidConfigBuilder(
                        new TransferConfig.OidConfig.Builder()
                            .host("apisix-public-host.org")
                            .jwksPath("/alternative/.well-known/jwks")
                            .openIdPath("/alternative/.well-known/openid-configuration"))
                    .build())
            .apisix(
                new TransferConfig.Apisix.Builder()
                    .address("http://apisix-admin.svc.local:9180")
                    .token("admin")
                    .httpsProxy("http://proxy.infra.svc.cluster.local:8888")
                    .build())
            .build();
    arguments.add(Arguments.of("valid/1.properties", transferConfig1));

    TransferConfig transferConfig2 =
        TransferConfig.Builder.newInstance()
            .enabled(true)
            .transferHost("apisix-public-host.org")
            .oid4Vc(new TransferConfig.Oid4Vc.Builder().enabled(false).build())
            .dcp(
                new TransferConfig.Dcp.Builder()
                    .enabled(true)
                    .oidConfigBuilder(
                        new TransferConfig.OidConfig.Builder()
                            .host("apisix-public-host.org")
                            .jwksPath("/alternative/.well-known/jwks")
                            .openIdPath("/alternative/.well-known/openid-configuration"))
                    .build())
            .apisix(
                new TransferConfig.Apisix.Builder()
                    .address("http://apisix-admin.svc.local:9180")
                    .token("admin")
                    .httpsProxy("http://proxy.infra.svc.cluster.local:8888")
                    .build())
            .build();
    arguments.add(Arguments.of("valid/2.properties", transferConfig2));

    TransferConfig transferConfig3 =
        TransferConfig.Builder.newInstance()
            .enabled(true)
            .transferHost("apisix-public-host.org")
            .oid4Vc(
                new TransferConfig.Oid4Vc.Builder()
                    .enabled(true)
                    .credentialsConfigAddress("http://ccs.org")
                    .verifierHost("https://verifier.org")
                    .verifierInternalHost("verifier:3000")
                    .opaHost("http://localhost:8181")
                    .odrlPapHost("http://odrl-pap.svc.local:8081")
                    .build())
            .dcp(new TransferConfig.Dcp.Builder().enabled(false).build())
            .apisix(
                new TransferConfig.Apisix.Builder()
                    .address("http://apisix-admin.svc.local:9180")
                    .token("admin")
                    .httpsProxy("http://proxy.infra.svc.cluster.local:8888")
                    .build())
            .build();
    arguments.add(Arguments.of("valid/3.properties", transferConfig3));

    TransferConfig transferConfig4 =
        TransferConfig.Builder.newInstance()
            .enabled(true)
            .transferHost("apisix-public-host.org")
            .oid4Vc(
                new TransferConfig.Oid4Vc.Builder()
                    .enabled(true)
                    .credentialsConfigAddress("http://ccs.org")
                    .verifierHost("https://verifier.org")
                    .verifierInternalHost("verifier:3000")
                    .opaHost("http://localhost:8181")
                    .odrlPapHost("http://odrl-pap.svc.local:8081")
                    .build())
            .dcp(
                new TransferConfig.Dcp.Builder()
                    .enabled(true)
                    .oidConfigBuilder(
                        new TransferConfig.OidConfig.Builder()
                            .host("apisix-public-host.org")
                            .jwksPath("/alternative/.well-known/jwks")
                            .openIdPath("/alternative/.well-known/openid-configuration"))
                    .build())
            .apisix(
                new TransferConfig.Apisix.Builder()
                    .address("http://apisix-admin.svc.local:9180")
                    .token("admin")
                    .build())
            .build();
    arguments.add(Arguments.of("valid/4.properties", transferConfig4));

    TransferConfig transferConfig5 =
        TransferConfig.Builder.newInstance()
            .enabled(true)
            .transferHost("apisix-public-host.org")
            .oid4Vc(
                new TransferConfig.Oid4Vc.Builder()
                    .enabled(true)
                    .credentialsConfigAddress("http://ccs.org")
                    .verifierHost("https://verifier.org")
                    .verifierInternalHost("https://verifier.org")
                    .opaHost("http://localhost:8181")
                    .odrlPapHost("http://odrl-pap.svc.local:8081")
                    .build())
            .dcp(
                new TransferConfig.Dcp.Builder()
                    .enabled(true)
                    .oidConfigBuilder(
                        new TransferConfig.OidConfig.Builder()
                            .host("apisix-public-host.org")
                            .jwksPath("/alternative/.well-known/jwks")
                            .openIdPath("/alternative/.well-known/openid-configuration"))
                    .build())
            .apisix(
                new TransferConfig.Apisix.Builder()
                    .address("http://apisix-admin.svc.local:9180")
                    .token("admin")
                    .httpsProxy("http://proxy.infra.svc.cluster.local:8888")
                    .build())
            .build();
    arguments.add(Arguments.of("valid/5.properties", transferConfig5));

    TransferConfig transferConfig6 =
        TransferConfig.Builder.newInstance()
            .enabled(true)
            .transferHost("apisix-public-host.org")
            .oid4Vc(
                new TransferConfig.Oid4Vc.Builder()
                    .enabled(true)
                    .credentialsConfigAddress("http://ccs.org")
                    .verifierHost("https://verifier.org")
                    .verifierInternalHost("verifier:3000")
                    .opaHost("http://localhost:8181")
                    .odrlPapHost("http://odrl-pap.svc.local:8081")
                    .build())
            .dcp(
                new TransferConfig.Dcp.Builder()
                    .enabled(true)
                    .oidConfigBuilder(
                        new TransferConfig.OidConfig.Builder()
                            .host("apisix-public-host.org")
                            .jwksPath("/.well-known/jwks")
                            .openIdPath("/.well-known/openid-configuration"))
                    .build())
            .apisix(
                new TransferConfig.Apisix.Builder()
                    .address("http://apisix-admin.svc.local:9180")
                    .token("admin")
                    .httpsProxy("http://proxy.infra.svc.cluster.local:8888")
                    .build())
            .build();
    arguments.add(Arguments.of("valid/6.properties", transferConfig6));

    return arguments.stream();
  }

  private static Config fromFile(String file) throws IOException {
    Properties properties = new Properties();
    properties.load(TransferConfigTest.class.getClassLoader().getResourceAsStream(file));
    return ConfigFactory.fromProperties(properties);
  }
}
