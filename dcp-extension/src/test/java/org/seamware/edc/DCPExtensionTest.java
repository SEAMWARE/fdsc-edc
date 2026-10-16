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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.edc.iam.decentralizedclaims.spi.verification.SignatureSuiteRegistry;
import org.eclipse.edc.iam.verifiablecredentials.spi.VcConstants;
import org.eclipse.edc.policy.context.request.spi.RequestCatalogPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestContractNegotiationPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestTransferProcessPolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.engine.spi.RuleBindingRegistry;
import org.eclipse.edc.security.signature.jws2020.Jws2020SignatureSuite;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.seamware.edc.policy.CredentialScopeExtractorRegistry;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class DCPExtensionTest {

  private static final String ENABLED_KEY = "dcp.enabled";
  private static final String CATALOG_SCOPE_KEY = "dcp.scopes.catalog";
  private static final String NEGOTIATION_SCOPE_KEY = "dcp.scopes.negotiation";
  private static final String TRANSFER_SCOPE_KEY = "dcp.scopes.transfer";
  private static final String RETIRED_VERSION_SCOPE_KEY = "dcp.scopes.version";
  private static final String SCOPE_VALUE = "some-scope";

  @Mock private Monitor monitor;
  @Mock private Clock clock;
  @Mock private ObjectMapper objectMapper;
  @Mock private TypeManager typeManager;
  @Mock private PolicyEngine policyEngine;
  @Mock private RuleBindingRegistry ruleBindingRegistry;
  @Mock private SignatureSuiteRegistry signatureSuiteRegistry;
  @Mock private TypeTransformerRegistry typeTransformerRegistry;

  @InjectMocks private DCPExtension extension;

  private void initializeWith(Map<String, String> config) {
    ServiceExtensionContext context = org.mockito.Mockito.mock(ServiceExtensionContext.class);
    when(context.getConfig()).thenReturn(ConfigFactory.fromMap(config));
    when(context.getMonitor()).thenReturn(monitor);
    extension.initialize(context);
  }

  private static Map<String, String> enabledConfig() {
    Map<String, String> config = new HashMap<>();
    config.put(ENABLED_KEY, "true");
    return config;
  }

  @Test
  public void disabledExtensionRegistersNothing() {
    initializeWith(Map.of(ENABLED_KEY, "false"));

    verifyNoInteractions(policyEngine, signatureSuiteRegistry, typeTransformerRegistry);
  }

  @Test
  public void enabledExtensionRegistersSignatureSuite() {
    initializeWith(enabledConfig());

    verify(signatureSuiteRegistry)
        .register(eq(VcConstants.JWS_2020_SIGNATURE_SUITE), any(Jws2020SignatureSuite.class));
  }

  @ParameterizedTest(name = "post validator registered for {0}")
  @ValueSource(strings = {CATALOG_SCOPE_KEY, NEGOTIATION_SCOPE_KEY, TRANSFER_SCOPE_KEY})
  public void configuredScopeRegistersItsPostValidator(String scopeKey) {
    Map<String, String> config = enabledConfig();
    config.put(scopeKey, SCOPE_VALUE);

    initializeWith(config);

    verify(policyEngine).registerPostValidator(eq(contextFor(scopeKey)), any());
  }

  @ParameterizedTest(name = "no post validator for unconfigured {0}")
  @ValueSource(strings = {CATALOG_SCOPE_KEY, NEGOTIATION_SCOPE_KEY, TRANSFER_SCOPE_KEY})
  public void unconfiguredScopeRegistersNoPostValidator(String scopeKey) {
    initializeWith(enabledConfig());

    verify(policyEngine, never()).registerPostValidator(eq(contextFor(scopeKey)), any());
  }

  /**
   * dcp.scopes.version bound to RequestVersionPolicyContext, which EDC removed in 0.18.x — the DSP
   * version endpoint is unauthenticated now. A stale key must load without registering anything.
   */
  @Test
  public void retiredVersionScopeIsIgnored() {
    Map<String, String> config = enabledConfig();
    config.put(RETIRED_VERSION_SCOPE_KEY, SCOPE_VALUE);

    initializeWith(config);

    verify(policyEngine, never()).registerPostValidator(any(), any());
  }

  @Test
  public void scopeExtractorRegistryIsProvidedOnceAndCached() {
    var first = extension.scopeExtractorRegistry();

    assertInstanceOf(CredentialScopeExtractorRegistry.class, first);
    assertSame(first, extension.scopeExtractorRegistry(), "The registry should be cached.");
  }

  @SuppressWarnings("unchecked")
  private static <C extends PolicyContext> Class<C> contextFor(String scopeKey) {
    return (Class<C>)
        switch (scopeKey) {
          case CATALOG_SCOPE_KEY -> RequestCatalogPolicyContext.class;
          case NEGOTIATION_SCOPE_KEY -> RequestContractNegotiationPolicyContext.class;
          case TRANSFER_SCOPE_KEY -> RequestTransferProcessPolicyContext.class;
          default -> throw new IllegalArgumentException("Unknown scope key " + scopeKey);
        };
  }
}
