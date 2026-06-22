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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import okhttp3.OkHttpClient;
import org.eclipse.edc.connector.controlplane.catalog.spi.policy.CatalogPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.ContractNegotiationPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.TransferProcessPolicyContext;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.engine.spi.PolicyValidatorRule;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link OdrlPapPolicyExtension}.
 *
 * <p>Tests verify that the extension correctly reads configuration and registers pre-validators
 * with the {@link PolicyEngine} based on the enabled scopes.
 */
@ExtendWith(MockitoExtension.class)
class OdrlPapPolicyExtensionTest {

  private static final String TEST_HOST = "http://odrl-pap:8080";

  /** Number of scopes registered when all scopes are enabled. */
  private static final int ALL_SCOPES_COUNT = 3;

  @Mock private PolicyEngine policyEngine;
  @Mock private Monitor monitor;
  @Mock private OkHttpClient okHttpClient;
  @Mock private ObjectMapper objectMapper;
  @Mock private TypeTransformerRegistry typeTransformerRegistry;
  @Mock private JsonLd jsonLd;
  @Mock private ServiceExtensionContext context;

  private OdrlPapPolicyExtension extension;

  @BeforeEach
  void setUp() {
    extension = new OdrlPapPolicyExtension();
    extension.policyEngine = policyEngine;
    extension.monitor = monitor;
    extension.okHttpClient = okHttpClient;
    extension.objectMapper = objectMapper;
    extension.typeTransformerRegistry = typeTransformerRegistry;
    extension.jsonLd = jsonLd;
  }

  /**
   * Creates a mock EDC {@link Config} hierarchy suitable for {@link
   * OdrlPapConfig#fromConfig(Config)}.
   *
   * @param enabled value for the enabled setting, or {@code null} for missing
   * @param host value for the host setting, or {@code null} for missing
   * @param denyOnError value for denyOnError, or {@code null} for missing
   * @param scopeCatalog value for catalog scope, or {@code null} for missing
   * @param scopeNegotiation value for negotiation scope, or {@code null} for missing
   * @param scopeTransfer value for transfer scope, or {@code null} for missing
   */
  private void setupConfig(
      Boolean enabled,
      String host,
      Boolean denyOnError,
      Boolean scopeCatalog,
      Boolean scopeNegotiation,
      Boolean scopeTransfer) {
    setupConfig(enabled, host, denyOnError, scopeCatalog, scopeNegotiation, scopeTransfer, null);
  }

  private void setupConfig(
      Boolean enabled,
      String host,
      Boolean denyOnError,
      Boolean scopeCatalog,
      Boolean scopeNegotiation,
      Boolean scopeTransfer,
      String additionalContextsPath) {
    setupConfig(
        enabled,
        host,
        denyOnError,
        scopeCatalog,
        scopeNegotiation,
        scopeTransfer,
        additionalContextsPath,
        null);
  }

  private void setupConfig(
      Boolean enabled,
      String host,
      Boolean denyOnError,
      Boolean scopeCatalog,
      Boolean scopeNegotiation,
      Boolean scopeTransfer,
      String additionalContextsPath,
      String scopeMappingsPath) {
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
    mockStringProperty(policyConfig, "additionalContextsPath", additionalContextsPath);
    mockStringProperty(policyConfig, "scopeMappingsPath", scopeMappingsPath);
    mockBooleanProperty(scopesConfig, "catalog", scopeCatalog);
    mockBooleanProperty(scopesConfig, "negotiation", scopeNegotiation);
    mockBooleanProperty(scopesConfig, "transfer", scopeTransfer);

    when(context.getConfig()).thenReturn(rootConfig);
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

  @Test
  @DisplayName("name() returns the extension display name")
  void nameReturnsExtensionName() {
    assertEquals(OdrlPapPolicyExtension.EXTENSION_NAME, extension.name());
  }

  @Nested
  @DisplayName("Disabled mode")
  class DisabledMode {

    @Test
    @DisplayName("no validators registered when disabled (default)")
    void noValidatorsWhenDisabled() {
      setupConfig(null, null, null, null, null, null);

      extension.initialize(context);

      verify(policyEngine, never()).registerPreValidator(any(), any());
      verify(monitor).info(contains("disabled"));
    }

    @Test
    @DisplayName("no validators registered when explicitly disabled")
    void noValidatorsWhenExplicitlyDisabled() {
      setupConfig(false, TEST_HOST, null, null, null, null);

      extension.initialize(context);

      verify(policyEngine, never()).registerPreValidator(any(), any());
    }
  }

  @Nested
  @DisplayName("Enabled with all scopes")
  class EnabledAllScopes {

    @Test
    @DisplayName("registers three pre-validators when all scopes enabled")
    void registersAllThreeValidators() {
      setupConfig(true, TEST_HOST, null, null, null, null);

      extension.initialize(context);

      verify(policyEngine)
          .registerPreValidator(eq(CatalogPolicyContext.class), any(PolicyValidatorRule.class));
      verify(policyEngine)
          .registerPreValidator(
              eq(ContractNegotiationPolicyContext.class), any(PolicyValidatorRule.class));
      verify(policyEngine)
          .registerPreValidator(
              eq(TransferProcessPolicyContext.class), any(PolicyValidatorRule.class));
    }

    @Test
    @DisplayName("logs registered scopes at info level")
    void logsRegisteredScopes() {
      setupConfig(true, TEST_HOST, null, null, null, null);

      extension.initialize(context);

      verify(monitor).info(contains("catalog"));
    }
  }

  @Nested
  @DisplayName("Enabled with subset of scopes")
  class EnabledSubsetScopes {

    @Test
    @DisplayName("registers only catalog validator when other scopes disabled")
    void onlyCatalog() {
      setupConfig(true, TEST_HOST, null, true, false, false);

      extension.initialize(context);

      verify(policyEngine)
          .registerPreValidator(eq(CatalogPolicyContext.class), any(PolicyValidatorRule.class));
      verify(policyEngine, never())
          .registerPreValidator(
              eq(ContractNegotiationPolicyContext.class), any(PolicyValidatorRule.class));
      verify(policyEngine, never())
          .registerPreValidator(
              eq(TransferProcessPolicyContext.class), any(PolicyValidatorRule.class));
    }

    @Test
    @DisplayName("registers only negotiation validator when other scopes disabled")
    void onlyNegotiation() {
      setupConfig(true, TEST_HOST, null, false, true, false);

      extension.initialize(context);

      verify(policyEngine, never())
          .registerPreValidator(eq(CatalogPolicyContext.class), any(PolicyValidatorRule.class));
      verify(policyEngine)
          .registerPreValidator(
              eq(ContractNegotiationPolicyContext.class), any(PolicyValidatorRule.class));
      verify(policyEngine, never())
          .registerPreValidator(
              eq(TransferProcessPolicyContext.class), any(PolicyValidatorRule.class));
    }

    @Test
    @DisplayName("registers only transfer validator when other scopes disabled")
    void onlyTransfer() {
      setupConfig(true, TEST_HOST, null, false, false, true);

      extension.initialize(context);

      verify(policyEngine, never())
          .registerPreValidator(eq(CatalogPolicyContext.class), any(PolicyValidatorRule.class));
      verify(policyEngine, never())
          .registerPreValidator(
              eq(ContractNegotiationPolicyContext.class), any(PolicyValidatorRule.class));
      verify(policyEngine)
          .registerPreValidator(
              eq(TransferProcessPolicyContext.class), any(PolicyValidatorRule.class));
    }

    @Test
    @DisplayName("registers no validators when all scopes explicitly disabled")
    void noScopesEnabled() {
      setupConfig(true, TEST_HOST, null, false, false, false);

      extension.initialize(context);

      verify(policyEngine, never()).registerPreValidator(any(), any());
    }
  }

  @Nested
  @DisplayName("Missing host when enabled")
  class MissingHost {

    @Test
    @DisplayName("logs error and registers no validators when host is null")
    void noValidatorsWhenHostNull() {
      setupConfig(true, null, null, null, null, null);

      extension.initialize(context);

      verify(policyEngine, never()).registerPreValidator(any(), any());
      verify(monitor).severe(contains(OdrlPapConfig.SETTING_HOST));
    }

    @Test
    @DisplayName("logs error and registers no validators when host is blank")
    void noValidatorsWhenHostBlank() {
      setupConfig(true, "  ", null, null, null, null);

      extension.initialize(context);

      verify(policyEngine, never()).registerPreValidator(any(), any());
      verify(monitor).severe(contains(OdrlPapConfig.SETTING_HOST));
    }
  }

  @Nested
  @DisplayName("Additional contexts loading")
  class AdditionalContextsLoading {

    @TempDir Path tempDir;

    @Test
    @DisplayName("initializes successfully when additional contexts file is configured")
    void initializesWithContextsFile() throws IOException {
      Path file = tempDir.resolve("contexts.json");
      Files.writeString(
          file,
          """
          [{"odrl:action": {"@id": "http://www.w3.org/ns/odrl/2/action"}}]
          """);
      extension.objectMapper = new ObjectMapper();
      setupConfig(true, TEST_HOST, null, null, null, null, file.toString());

      extension.initialize(context);

      verify(policyEngine)
          .registerPreValidator(eq(CatalogPolicyContext.class), any(PolicyValidatorRule.class));
      verify(monitor).info(contains("1 additional context(s)"));
    }

    @Test
    @DisplayName("initializes without contexts when path is not configured")
    void initializesWithoutContextsFile() {
      setupConfig(true, TEST_HOST, null, null, null, null);

      extension.initialize(context);

      verify(policyEngine)
          .registerPreValidator(eq(CatalogPolicyContext.class), any(PolicyValidatorRule.class));
      verify(monitor).info(contains("No additional contexts file"));
    }

    @Test
    @DisplayName("throws when additional contexts file does not exist")
    void throwsWhenContextsFileNotFound() {
      String missingPath = tempDir.resolve("nonexistent.json").toString();
      setupConfig(true, TEST_HOST, null, null, null, null, missingPath);

      assertThrows(IllegalArgumentException.class, () -> extension.initialize(context));
    }

    @Test
    @DisplayName("throws when additional contexts file contains invalid JSON")
    void throwsWhenContextsFileInvalid() throws IOException {
      Path file = tempDir.resolve("invalid.json");
      Files.writeString(file, "not json");
      extension.objectMapper = new ObjectMapper();
      setupConfig(true, TEST_HOST, null, null, null, null, file.toString());

      assertThrows(IllegalArgumentException.class, () -> extension.initialize(context));
    }
  }

  @Nested
  @DisplayName("Scope mappings loading")
  class ScopeMappingsLoading {

    @TempDir Path tempDir;

    @Test
    @DisplayName("initializes successfully when scope mappings file is configured")
    void initializesWithScopeMappingsFile() throws IOException {
      Path file = tempDir.resolve("scope-mappings.json");
      Files.writeString(
          file,
          """
          {
            "mappings": [
              {
                "match": { "http://www.w3.org/ns/odrl/2/leftOperand": "Membership" },
                "scopes": ["contract.negotiation"]
              }
            ]
          }
          """);
      extension.objectMapper = new ObjectMapper();
      setupConfig(true, TEST_HOST, null, null, null, null, null, file.toString());

      extension.initialize(context);

      verify(policyEngine)
          .registerPreValidator(eq(CatalogPolicyContext.class), any(PolicyValidatorRule.class));
      verify(monitor).info(contains("1 scope mapping rule(s)"));
    }

    @Test
    @DisplayName("initializes without scope mappings when path is not configured")
    void initializesWithoutScopeMappingsFile() {
      setupConfig(true, TEST_HOST, null, null, null, null);

      extension.initialize(context);

      verify(policyEngine)
          .registerPreValidator(eq(CatalogPolicyContext.class), any(PolicyValidatorRule.class));
      verify(monitor).info(contains("No scope mappings file"));
    }

    @Test
    @DisplayName("throws when scope mappings file does not exist")
    void throwsWhenScopeMappingsFileNotFound() {
      String missingPath = tempDir.resolve("nonexistent.json").toString();
      setupConfig(true, TEST_HOST, null, null, null, null, null, missingPath);

      assertThrows(IllegalArgumentException.class, () -> extension.initialize(context));
    }

    @Test
    @DisplayName("throws when scope mappings file contains invalid JSON")
    void throwsWhenScopeMappingsFileInvalid() throws IOException {
      Path file = tempDir.resolve("invalid.json");
      Files.writeString(file, "not json");
      extension.objectMapper = new ObjectMapper();
      setupConfig(true, TEST_HOST, null, null, null, null, null, file.toString());

      assertThrows(IllegalArgumentException.class, () -> extension.initialize(context));
    }
  }
}
