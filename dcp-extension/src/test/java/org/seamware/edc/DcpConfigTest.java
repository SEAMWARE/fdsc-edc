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

import static org.junit.jupiter.api.Assertions.assertEquals;

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

public class DcpConfigTest {

  @ParameterizedTest(name = "Config from {0}")
  @MethodSource("getValidConfig")
  public void testValidConfig(String testFile, DcpConfig expectedConfig) throws IOException {
    Config testConfig = fromFile(testFile);
    assertEquals(
        expectedConfig,
        DcpConfig.fromConfig(testConfig),
        "The config should have successfully been read.");
  }

  private static Stream<Arguments> getValidConfig() throws IOException {
    List<Arguments> arguments = new ArrayList<>();

    DcpConfig dcpConfig1 =
        DcpConfig.Builder.newInstance()
            .enabled(true)
            .scopes(
                new DcpConfig.Scopes.Builder()
                    .catalog("catalog")
                    .negotiation("negotiation")
                    .version("version")
                    .transfer("transfer")
                    .build())
            .build();
    arguments.add(Arguments.of("valid/dcp/1.properties", dcpConfig1));

    DcpConfig dcpConfig2 =
        DcpConfig.Builder.newInstance()
            .enabled(true)
            .scopes(
                new DcpConfig.Scopes.Builder()
                    .catalog("catalog")
                    .negotiation("negotiation")
                    .transfer("transfer")
                    .build())
            .build();
    arguments.add(Arguments.of("valid/dcp/2.properties", dcpConfig2));

    DcpConfig dcpConfig3 =
        DcpConfig.Builder.newInstance()
            .enabled(true)
            .scopes(
                new DcpConfig.Scopes.Builder()
                    .catalog("catalog")
                    .negotiation("negotiation")
                    .version("version")
                    .build())
            .build();
    arguments.add(Arguments.of("valid/dcp/3.properties", dcpConfig3));

    DcpConfig dcpConfig4 =
        DcpConfig.Builder.newInstance()
            .enabled(true)
            .scopes(
                new DcpConfig.Scopes.Builder()
                    .catalog("catalog")
                    .transfer("transfer")
                    .version("version")
                    .build())
            .build();
    arguments.add(Arguments.of("valid/dcp/4.properties", dcpConfig4));

    DcpConfig dcpConfig5 =
        DcpConfig.Builder.newInstance()
            .enabled(true)
            .scopes(
                new DcpConfig.Scopes.Builder()
                    .negotiation("negotiation")
                    .transfer("transfer")
                    .version("version")
                    .build())
            .build();
    arguments.add(Arguments.of("valid/dcp/5.properties", dcpConfig5));

    DcpConfig dcpConfig6 =
        DcpConfig.Builder.newInstance()
            .enabled(false)
            .scopes(new DcpConfig.Scopes.Builder().build())
            .build();
    arguments.add(Arguments.of("valid/dcp/6.properties", dcpConfig6));
    return arguments.stream();
  }

  private static Config fromFile(String file) throws IOException {
    Properties properties = new Properties();
    properties.load(DcpConfigTest.class.getClassLoader().getResourceAsStream(file));
    return ConfigFactory.fromProperties(properties);
  }
}
