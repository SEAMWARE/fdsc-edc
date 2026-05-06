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
package org.seamware.edc.pap.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.system.configuration.Config;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link OdrlPapConfig}. */
class OdrlPapConfigTest {

  private static final String TEST_HOST = "http://odrl-pap:8080";

  /**
   * Creates a mock EDC {@link Config} hierarchy with optional overrides.
   *
   * @param enabled value for the enabled setting, or {@code null} to simulate missing key
   * @param host value for the host setting, or {@code null} to simulate missing key
   * @param denyOnError value for the denyOnError setting, or {@code null} to simulate missing key
   * @param scopeCatalog value for the catalog scope, or {@code null} to simulate missing key
   * @param scopeNegotiation value for the negotiation scope, or {@code null} to simulate missing
   *     key
   * @param scopeTransfer value for the transfer scope, or {@code null} to simulate missing key
   * @return a mock Config with the specified behavior
   */
  private Config createMockConfig(
      Boolean enabled,
      String host,
      Boolean denyOnError,
      Boolean scopeCatalog,
      Boolean scopeNegotiation,
      Boolean scopeTransfer) {
    Config rootConfig = mock(Config.class);
    Config odrlPapConfig = mock(Config.class);
    Config policyConfig = mock(Config.class);
    Config scopesConfig = mock(Config.class);

    when(rootConfig.getConfig(OdrlPapConfig.ODRL_PAP_CONFIG_PREFIX)).thenReturn(odrlPapConfig);
    when(rootConfig.getConfig(OdrlPapConfig.ODRL_PAP_POLICY_CONFIG_PREFIX))
        .thenReturn(policyConfig);
    when(rootConfig.getConfig(OdrlPapConfig.ODRL_PAP_POLICY_SCOPES_CONFIG_PREFIX))
        .thenReturn(scopesConfig);

    mockBooleanProperty(policyConfig, "enabled", enabled);
    mockStringProperty(odrlPapConfig, "host", host);
    mockBooleanProperty(policyConfig, "denyOnError", denyOnError);
    mockBooleanProperty(scopesConfig, "catalog", scopeCatalog);
    mockBooleanProperty(scopesConfig, "negotiation", scopeNegotiation);
    mockBooleanProperty(scopesConfig, "transfer", scopeTransfer);

    return rootConfig;
  }

  private void mockBooleanProperty(Config config, String key, Boolean value) {
    if (value != null) {
      when(config.getBoolean(key)).thenReturn(value);
    } else {
      when(config.getBoolean(key)).thenThrow(new EdcException("Key not found: " + key));
    }
  }

  private void mockStringProperty(Config config, String key, String value) {
    if (value != null) {
      when(config.getString(key)).thenReturn(value);
    } else {
      when(config.getString(key)).thenThrow(new EdcException("Key not found: " + key));
    }
  }

  @Nested
  @DisplayName("Default values")
  class DefaultValues {

    @Test
    @DisplayName("all properties use defaults when no config is set")
    void allDefaults() {
      Config config = createMockConfig(null, null, null, null, null, null);
      OdrlPapConfig result = OdrlPapConfig.fromConfig(config);

      assertFalse(result.isEnabled());
      assertNull(result.host());
      assertTrue(result.denyOnError());
      assertTrue(result.scopeCatalog());
      assertTrue(result.scopeNegotiation());
      assertTrue(result.scopeTransfer());
    }

    @Test
    @DisplayName("enabled defaults to false")
    void enabledDefaultsFalse() {
      Config config = createMockConfig(null, TEST_HOST, null, null, null, null);
      OdrlPapConfig result = OdrlPapConfig.fromConfig(config);

      assertFalse(result.isEnabled());
    }

    @Test
    @DisplayName("denyOnError defaults to true")
    void denyOnErrorDefaultsTrue() {
      Config config = createMockConfig(true, TEST_HOST, null, null, null, null);
      OdrlPapConfig result = OdrlPapConfig.fromConfig(config);

      assertTrue(result.denyOnError());
    }
  }

  @Nested
  @DisplayName("Explicit values")
  class ExplicitValues {

    @Test
    @DisplayName("enabled reads from config")
    void enabledFromConfig() {
      Config config = createMockConfig(true, TEST_HOST, null, null, null, null);
      OdrlPapConfig result = OdrlPapConfig.fromConfig(config);

      assertTrue(result.isEnabled());
    }

    @Test
    @DisplayName("host reads from config")
    void hostFromConfig() {
      Config config = createMockConfig(true, TEST_HOST, null, null, null, null);
      OdrlPapConfig result = OdrlPapConfig.fromConfig(config);

      assertEquals(TEST_HOST, result.host());
    }

    @Test
    @DisplayName("denyOnError false reads from config")
    void denyOnErrorFalseFromConfig() {
      Config config = createMockConfig(true, TEST_HOST, false, null, null, null);
      OdrlPapConfig result = OdrlPapConfig.fromConfig(config);

      assertFalse(result.denyOnError());
    }

    @Test
    @DisplayName("individual scopes can be disabled")
    void scopesDisabledFromConfig() {
      Config config = createMockConfig(true, TEST_HOST, null, false, true, false);
      OdrlPapConfig result = OdrlPapConfig.fromConfig(config);

      assertFalse(result.scopeCatalog());
      assertTrue(result.scopeNegotiation());
      assertFalse(result.scopeTransfer());
    }

    @Test
    @DisplayName("all scopes can be disabled")
    void allScopesDisabled() {
      Config config = createMockConfig(true, TEST_HOST, null, false, false, false);
      OdrlPapConfig result = OdrlPapConfig.fromConfig(config);

      assertFalse(result.scopeCatalog());
      assertFalse(result.scopeNegotiation());
      assertFalse(result.scopeTransfer());
    }
  }

  @Nested
  @DisplayName("Host handling")
  class HostHandling {

    @Test
    @DisplayName("host is null when not configured")
    void hostNullWhenMissing() {
      Config config = createMockConfig(true, null, null, null, null, null);
      OdrlPapConfig result = OdrlPapConfig.fromConfig(config);

      assertNull(result.host());
    }

    @Test
    @DisplayName("host preserves full URL with port")
    void hostWithPort() {
      String hostWithPort = "http://localhost:9090";
      Config config = createMockConfig(true, hostWithPort, null, null, null, null);
      OdrlPapConfig result = OdrlPapConfig.fromConfig(config);

      assertEquals(hostWithPort, result.host());
    }
  }
}
