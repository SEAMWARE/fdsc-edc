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

public class TirConfigTest {

  @ParameterizedTest(name = "Config from {0}")
  @MethodSource("getInvalidConfig")
  public void testInvalidConfig(String testFile, String expectedMessage) throws IOException {
    Config testConfig = fromFile(testFile);
    var e = assertThrows(NullPointerException.class, () -> TirConfig.fromConfig(testConfig));
    assertEquals(expectedMessage, e.getMessage());
  }

  private static Stream<Arguments> getInvalidConfig() {
    return Stream.of(
        Arguments.of(
            "invalid/tir/1.properties",
            "The til address needs to be provided, when ebsi tir is enabled."));
  }

  @ParameterizedTest(name = "Config from {0}")
  @MethodSource("getValidConfig")
  public void testValidConfig(String testFile, TirConfig expectedConfig) throws IOException {
    Config testConfig = fromFile(testFile);
    assertEquals(
        expectedConfig,
        TirConfig.fromConfig(testConfig),
        "The config should have successfully been read.");
  }

  private static Stream<Arguments> getValidConfig() throws IOException {
    List<Arguments> arguments = new ArrayList<>();

    TirConfig tirConfig1 =
        TirConfig.Builder.newInstance().enabled(true).tilAddress("http://my-til.org").build();
    arguments.add(Arguments.of("valid/tir/1.properties", tirConfig1));

    TirConfig tirConfig2 = TirConfig.Builder.newInstance().enabled(false).build();
    arguments.add(Arguments.of("valid/tir/2.properties", tirConfig2));

    return arguments.stream();
  }

  private static Config fromFile(String file) throws IOException {
    Properties properties = new Properties();
    properties.load(DcpConfigTest.class.getClassLoader().getResourceAsStream(file));
    return ConfigFactory.fromProperties(properties);
  }
}
