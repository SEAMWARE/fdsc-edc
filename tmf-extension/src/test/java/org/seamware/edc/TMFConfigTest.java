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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class TMFConfigTest {

  // Fallback applied by TMFConfig when edc.participant.id is unset, mirroring
  // ServiceExtensionContext.ANONYMOUS_PARTICIPANT.
  private static final String ANONYMOUS_PARTICIPANT = "anonymous";
  private static final String TEST_PARTICIPANT_ID = "test-participant";
  private static final String EXPLICIT_CONTEXT_ID = "explicit-context";
  private static final String SUPPLIED_CONTEXT_ID = "supplied-context";

  @ParameterizedTest(name = "explicit={0}, supplied={1}, edc.participant.id={2}")
  @MethodSource("getDefaultContextCases")
  public void testDefaultParticipantContextResolution(
      String explicitContextId,
      String suppliedContextId,
      String edcParticipantId,
      String expectedDefault,
      boolean expectWarning) {
    Map<String, String> settings = new HashMap<>();
    Optional.ofNullable(explicitContextId)
        .ifPresent(id -> settings.put(TMFConfig.DEFAULT_PARTICIPANT_CONTEXT_ID_SETTING, id));
    Optional.ofNullable(edcParticipantId)
        .ifPresent(id -> settings.put(TMFConfig.EDC_PARTICIPANT_ID_SETTING, id));
    ParticipantContext participantContext =
        Optional.ofNullable(suppliedContextId)
            .map(
                id ->
                    ParticipantContext.Builder.newInstance()
                        .participantContextId(id)
                        .identity(TEST_PARTICIPANT_ID)
                        .build())
            .orElse(null);
    Monitor monitor = mock(Monitor.class);

    TMFConfig tmfConfig =
        TMFConfig.fromConfig(ConfigFactory.fromMap(settings), participantContext, monitor);

    assertEquals(expectedDefault, tmfConfig.getDefaultParticipantContextId());
    assertEquals(expectedDefault, tmfConfig.getParticipantContextId());
    verify(monitor, times(expectWarning ? 1 : 0)).warning(anyString());
  }

  private static Stream<Arguments> getDefaultContextCases() {
    return Stream.of(
        Arguments.of(EXPLICIT_CONTEXT_ID, null, null, EXPLICIT_CONTEXT_ID, false),
        Arguments.of(EXPLICIT_CONTEXT_ID, EXPLICIT_CONTEXT_ID, null, EXPLICIT_CONTEXT_ID, false),
        Arguments.of(EXPLICIT_CONTEXT_ID, SUPPLIED_CONTEXT_ID, null, EXPLICIT_CONTEXT_ID, true),
        Arguments.of(null, SUPPLIED_CONTEXT_ID, null, SUPPLIED_CONTEXT_ID, false),
        Arguments.of(null, SUPPLIED_CONTEXT_ID, TEST_PARTICIPANT_ID, SUPPLIED_CONTEXT_ID, false),
        Arguments.of(" ", SUPPLIED_CONTEXT_ID, null, SUPPLIED_CONTEXT_ID, false),
        Arguments.of(null, null, TEST_PARTICIPANT_ID, null, true),
        Arguments.of(null, null, null, null, true));
  }

  @ParameterizedTest(name = "Config from {0}")
  @MethodSource("getValidConfig")
  public void testValidConfig(String testFile, TMFConfig expectedConfig) throws IOException {
    Config testConfig = fromFile(testFile);
    assertEquals(
        TMFConfig.fromConfig(testConfig, null, mock(Monitor.class)),
        expectedConfig,
        "The config should have successfully been read.");
  }

  @ParameterizedTest(name = "Endpoint-only config from {0}")
  @MethodSource("getValidConfig")
  public void testEndpointOnlyConfigLeavesTheParticipantIdentityUnset(
      String testFile, TMFConfig expectedConfig) throws IOException {
    Config testConfig = fromFile(testFile);

    TMFConfig tmfConfig = TMFConfig.fromConfig(testConfig);

    assertNull(
        tmfConfig.getParticipantId(), "A settings-derived identity could diverge unnoticed.");
    assertNull(tmfConfig.getDefaultParticipantContextId());
  }

  @ParameterizedTest(name = "Config from {0}")
  @MethodSource("getInvalidConfig")
  public void testInvalidConfig(String testFile, String expectedMessage) throws IOException {
    Config testConfig = fromFile(testFile);
    var e = assertThrows(NullPointerException.class, () -> TMFConfig.fromConfig(testConfig));
    assertEquals(expectedMessage, e.getMessage());
  }

  private static Stream<Arguments> getInvalidConfig() {
    return Stream.of(
        Arguments.of(
            "invalid/1.properties",
            "If TMFExtension is enabled, a valid quoteApi has to be provided."),
        Arguments.of(
            "invalid/2.properties",
            "If TMFExtension is enabled, a valid agreementApi has to be provided."),
        Arguments.of(
            "invalid/3.properties",
            "If TMFExtension is enabled, a valid productOrderApi has to be provided."),
        Arguments.of(
            "invalid/4.properties",
            "If TMFExtension is enabled, a valid productCatalogApi has to be provided."),
        Arguments.of(
            "invalid/5.properties",
            "If TMFExtension is enabled, a valid productInventoryApi has to be provided."),
        Arguments.of(
            "invalid/6.properties",
            "If TMFExtension is enabled, a valid usageManagementApi has to be provided."),
        Arguments.of(
            "invalid/7.properties",
            "If TMFExtension is enabled, a valid partyCatalogApi has to be provided."));
  }

  private static Stream<Arguments> getValidConfig() throws IOException {
    List<Arguments> arguments = new ArrayList<>();
    TMFConfig tmfConfig1 =
        TMFConfig.Builder.newInstance()
            .enabled(true)
            .participantId(ANONYMOUS_PARTICIPANT)
            .quoteApi("http://quote-api.de")
            .agreementApi("http://agreement-api.de")
            .productOrderApi("http://product-order-api.de")
            .productCatalogApi("http://product-catalog-api.de")
            .productInventoryApi("http://product-inventory-api.de")
            .usageManagementApi("http://usage-management-api.de")
            .partyCatalogApi("http://party-catalog-api.de")
            .schemaBaseUri("http://schema-base-api.de")
            .catalogConfig(new TMFConfig.CatalogConfig.Builder().enabled(true).build())
            .build();
    arguments.add(Arguments.of("valid/1.properties", tmfConfig1));

    TMFConfig tmfConfig2 =
        TMFConfig.Builder.newInstance()
            .enabled(true)
            .participantId(ANONYMOUS_PARTICIPANT)
            .quoteApi("http://quote-api.de")
            .agreementApi("http://agreement-api.de")
            .productOrderApi("http://product-order-api.de")
            .productCatalogApi("http://product-catalog-api.de")
            .productInventoryApi("http://product-inventory-api.de")
            .usageManagementApi("http://usage-management-api.de")
            .partyCatalogApi("http://party-catalog-api.de")
            .schemaBaseUri("http://schema-base-api.de")
            .catalogConfig(new TMFConfig.CatalogConfig.Builder().enabled(false).build())
            .build();
    arguments.add(Arguments.of("valid/2.properties", tmfConfig2));

    TMFConfig tmfConfig3 =
        TMFConfig.Builder.newInstance()
            .enabled(false)
            .participantId(ANONYMOUS_PARTICIPANT)
            .catalogConfig(new TMFConfig.CatalogConfig.Builder().enabled(false).build())
            .build();
    arguments.add(Arguments.of("valid/3.properties", tmfConfig3));

    TMFConfig tmfConfig4 =
        TMFConfig.Builder.newInstance()
            .enabled(true)
            .participantId(ANONYMOUS_PARTICIPANT)
            .quoteApi("http://quote-api.de")
            .agreementApi("http://agreement-api.de")
            .productOrderApi("http://product-order-api.de")
            .productCatalogApi("http://product-catalog-api.de")
            .productInventoryApi("http://product-inventory-api.de")
            .usageManagementApi("http://usage-management-api.de")
            .partyCatalogApi("http://party-catalog-api.de")
            .schemaBaseUri(
                "https://raw.githubusercontent.com/wistefan/edc-dsc/refs/heads/init/schemas/")
            .catalogConfig(new TMFConfig.CatalogConfig.Builder().enabled(true).build())
            .build();
    arguments.add(Arguments.of("valid/4.properties", tmfConfig4));

    // Without a supplier, edc.participant.* never yields a default context (decision 3).
    TMFConfig tmfConfig5 =
        TMFConfig.Builder.newInstance()
            .enabled(false)
            .participantId(TEST_PARTICIPANT_ID)
            .catalogConfig(new TMFConfig.CatalogConfig.Builder().enabled(false).build())
            .build();
    arguments.add(Arguments.of("valid/5.properties", tmfConfig5));

    // edc.participant.context.id is a per-context entry in virtual mode, so it is ignored too.
    TMFConfig tmfConfig6 =
        TMFConfig.Builder.newInstance()
            .enabled(false)
            .participantId(TEST_PARTICIPANT_ID)
            .catalogConfig(new TMFConfig.CatalogConfig.Builder().enabled(false).build())
            .build();
    arguments.add(Arguments.of("valid/6.properties", tmfConfig6));

    return arguments.stream();
  }

  public static Config fromFile(String file) throws IOException {
    Properties properties = new Properties();
    properties.load(TMFConfigTest.class.getClassLoader().getResourceAsStream(file));
    return ConfigFactory.fromProperties(properties);
  }
}
