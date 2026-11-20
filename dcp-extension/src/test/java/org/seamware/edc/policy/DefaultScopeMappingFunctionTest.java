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
package org.seamware.edc.policy;

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
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.edc.iam.decentralizedclaims.spi.scope.DcpScope;
import org.eclipse.edc.iam.decentralizedclaims.spi.scope.DcpScopeRegistry;
import org.eclipse.edc.policy.context.request.spi.RequestCatalogPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestPolicyContext;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.iam.RequestContext;
import org.eclipse.edc.spi.iam.RequestScope;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.types.domain.message.RemoteMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

public class DefaultScopeMappingFunctionTest {

  private static final String PROFILE_A = "dataspace-a";
  private static final String PROFILE_B = "dataspace-b";
  private static final String UNKNOWN_PROFILE = "unknown";
  private static final String DEFAULT_SCOPE = "org.eclipse.edc.vc.type:MembershipCredential:read";
  private static final String SCOPE_A = "org.eclipse.edc.vc.type:DataspaceACredential:read";
  private static final String UPSTREAM_SCOPE_A = "org.eclipse.edc.vc.type:UpstreamCredential:read";

  private static final Map<String, String> PROFILE_SCOPES = Map.of(PROFILE_A, SCOPE_A);

  @ParameterizedTest(name = "{0}")
  @MethodSource("scopeCases")
  public void scopeIsChosenByProfile(
      String description, String defaultScope, String protocol, Set<String> expectedScopes) {
    var mappingFunction =
        new DefaultScopeMappingFunction(mock(Monitor.class), defaultScope, PROFILE_SCOPES);
    var policyContext = policyContext(protocol);

    mappingFunction.apply(Policy.Builder.newInstance().build(), policyContext);

    assertEquals(expectedScopes, policyContext.requestScopeBuilder().build().getScopes());
  }

  private static Stream<Arguments> scopeCases() {
    return Stream.of(
        Arguments.of("profile with override", DEFAULT_SCOPE, PROFILE_A, Set.of(SCOPE_A)),
        Arguments.of("profile without override", DEFAULT_SCOPE, PROFILE_B, Set.of(DEFAULT_SCOPE)),
        Arguments.of("unknown profile", DEFAULT_SCOPE, UNKNOWN_PROFILE, Set.of(DEFAULT_SCOPE)),
        Arguments.of("no protocol", DEFAULT_SCOPE, null, Set.of(DEFAULT_SCOPE)),
        Arguments.of("override without default scope", "", PROFILE_A, Set.of(SCOPE_A)),
        Arguments.of("no override and no default scope", "", PROFILE_B, Set.of()));
  }

  /**
   * A profile name is also a configuration key, so a typo in {@code dcp.profiles.<name>} silently
   * falls back to the default scope. The fallback has to leave a trace naming both sides.
   */
  @ParameterizedTest(name = "protocol {0} -> diagnostic logged: {1}")
  @MethodSource("diagnosticCases")
  public void unmatchedOverrideIsLogged(String protocol, boolean expectDiagnostic) {
    var monitor = mock(Monitor.class);

    new DefaultScopeMappingFunction(monitor, DEFAULT_SCOPE, PROFILE_SCOPES)
        .apply(Policy.Builder.newInstance().build(), policyContext(protocol));

    verify(monitor, times(expectDiagnostic ? 1 : 0))
        .debug(contains("No scope override for profile"));
  }

  private static Stream<Arguments> diagnosticCases() {
    return Stream.of(
        // the typo case: a configured override that no message protocol matches
        Arguments.of(UNKNOWN_PROFILE, true),
        Arguments.of(PROFILE_B, true),
        // a profile that does have an override, and a message without a protocol
        Arguments.of(PROFILE_A, false),
        Arguments.of(null, false));
  }

  @Test
  public void noDiagnosticWithoutConfiguredOverrides() {
    var monitor = mock(Monitor.class);

    new DefaultScopeMappingFunction(monitor, DEFAULT_SCOPE)
        .apply(Policy.Builder.newInstance().build(), policyContext(UNKNOWN_PROFILE));

    verify(monitor, never()).debug(contains("No scope override for profile"));
  }

  // The policy engine runs post-validators in registration order, which is not fixed.
  @ParameterizedTest(name = "upstream validator first: {0}")
  @ValueSource(booleans = {true, false})
  public void upstreamDefaultScopeAndProfileScopeAreUnited(boolean upstreamFirst) {
    var scopeRegistry = mock(DcpScopeRegistry.class);
    when(scopeRegistry.getDefaultScopes())
        .thenReturn(
            ServiceResult.success(
                List.of(
                    DcpScope.Builder.newInstance()
                        .id("upstream-a")
                        .type(DcpScope.Type.DEFAULT)
                        .value(UPSTREAM_SCOPE_A)
                        .profile(PROFILE_A)
                        .build())));
    var upstreamFunction =
        new org.eclipse.edc.iam.decentralizedclaims.core.scope.DefaultScopeMappingFunction(
            scopeRegistry);
    var ourFunction =
        new DefaultScopeMappingFunction(mock(Monitor.class), DEFAULT_SCOPE, PROFILE_SCOPES);
    var policy = Policy.Builder.newInstance().build();
    var policyContext = policyContext(PROFILE_A);

    if (upstreamFirst) {
      upstreamFunction.apply(policy, policyContext);
      ourFunction.apply(policy, policyContext);
    } else {
      ourFunction.apply(policy, policyContext);
      upstreamFunction.apply(policy, policyContext);
    }

    assertEquals(
        Set.of(SCOPE_A, UPSTREAM_SCOPE_A), policyContext.requestScopeBuilder().build().getScopes());
  }

  private static RequestPolicyContext policyContext(String protocol) {
    var message = mock(RemoteMessage.class);
    when(message.getProtocol()).thenReturn(protocol);
    return new RequestCatalogPolicyContext(
        RequestContext.Builder.newInstance()
            .message(message)
            .direction(RequestContext.Direction.Egress)
            .build(),
        RequestScope.Builder.newInstance());
  }
}
