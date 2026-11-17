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

import static org.eclipse.edc.spi.constants.CoreConstants.JSON_LD;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.TransferProcessPolicyContext;
import org.eclipse.edc.iam.decentralizedclaims.spi.scope.ScopeExtractorRegistry;
import org.eclipse.edc.iam.decentralizedclaims.spi.verification.SignatureSuiteRegistry;
import org.eclipse.edc.iam.verifiablecredentials.spi.VcConstants;
import org.eclipse.edc.iam.verifiablecredentials.spi.validation.TrustedIssuerRegistry;
import org.eclipse.edc.policy.context.request.spi.RequestCatalogPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestContractNegotiationPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestTransferProcessPolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.engine.spi.RuleBindingRegistry;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.security.signature.jws2020.Jws2020SignatureSuite;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.transform.transformer.edc.to.JsonValueToGenericTypeTransformer;
import org.seamware.edc.policy.CredentialScopeExtractorRegistry;
import org.seamware.edc.policy.DayOfWeekEvaluator;
import org.seamware.edc.policy.DefaultScopeMappingFunction;

public class DCPExtension implements ServiceExtension {

  @Inject private Monitor monitor;

  @Inject private Clock clock;

  @Inject private ObjectMapper objectMapper;

  @Inject private TypeManager typeManager;

  @Inject private PolicyEngine policyEngine;

  @Inject private RuleBindingRegistry ruleBindingRegistry;

  @Inject private SignatureSuiteRegistry signatureSuiteRegistry;

  @Inject private TrustedIssuerRegistry trustedIssuerRegistry;

  @Inject private TypeTransformerRegistry typeTransformerRegistry;

  private ScopeExtractorRegistry scopeExtractorRegistry;

  private boolean enabled;

  @Override
  public void initialize(ServiceExtensionContext context) {

    DcpConfig dcpConfig = DcpConfig.fromConfig(context.getConfig());
    enabled = dcpConfig.isEnabled();

    if (enabled) {
      // register signature suite
      var suite = new Jws2020SignatureSuite(typeManager.getMapper(JSON_LD));
      signatureSuiteRegistry.register(VcConstants.JWS_2020_SIGNATURE_SUITE, suite);
      registerScopeValidator(
          dcpConfig, RequestCatalogPolicyContext.class, "catalog", DcpConfig.Scopes::catalog);
      registerScopeValidator(
          dcpConfig,
          RequestContractNegotiationPolicyContext.class,
          "negotiation",
          DcpConfig.Scopes::negotiation);
      registerScopeValidator(
          dcpConfig,
          RequestTransferProcessPolicyContext.class,
          "transfer",
          DcpConfig.Scopes::transfer);
      // ODRL-PAP based policy evaluation is handled by OdrlPapPolicyExtension
      // (policy-extension module). Enable via odrlPap.policy.enabled=true.

      // makes only sense to be evaluated on requests
      ruleBindingRegistry.bind("dayOfWeek", TransferProcessPolicyContext.TRANSFER_SCOPE);
      ruleBindingRegistry.bind("odrl:dayOfWeek", TransferProcessPolicyContext.TRANSFER_SCOPE);
      policyEngine.registerFunction(
          TransferProcessPolicyContext.class,
          Permission.class,
          "odrl:dayOfWeek",
          new DayOfWeekEvaluator<>(monitor, clock));
      policyEngine.registerFunction(
          TransferProcessPolicyContext.class,
          Permission.class,
          "dayOfWeek",
          new DayOfWeekEvaluator<>(monitor, clock));
    }
  }

  // Registered only when the request type has a global scope or at least one profile override.
  private <C extends RequestPolicyContext> void registerScopeValidator(
      DcpConfig dcpConfig,
      Class<C> contextType,
      String requestType,
      Function<DcpConfig.Scopes, String> scopeOf) {
    String defaultScope = scopeOf.apply(dcpConfig.getScopes());
    Map<String, String> profileScopes =
        dcpConfig.getProfileScopes().entrySet().stream()
            .filter(profile -> !scopeOf.apply(profile.getValue()).isEmpty())
            .collect(
                Collectors.toMap(Map.Entry::getKey, profile -> scopeOf.apply(profile.getValue())));
    if (defaultScope.isEmpty() && profileScopes.isEmpty()) {
      return;
    }
    monitor.debug(
        String.format(
            "[DCPExtension] Configure post validator for %s: %s, per profile: %s",
            requestType, defaultScope, profileScopes));
    policyEngine.registerPostValidator(
        contextType, new DefaultScopeMappingFunction(monitor, defaultScope, profileScopes)::apply);
  }

  /**
   * Registers the transformers that replace EDC's own. A transformer registry keeps the last
   * transformer registered for a type pair, and EDC registers its own while the extensions
   * initialize, so they are only registered once every extension has done so.
   */
  @Override
  public void prepare() {
    if (enabled) {
      typeTransformerRegistry.register(new JsonValueToGenericTypeTransformer(typeManager, JSON_LD));
      typeTransformerRegistry.register(new FixedJwtToVerifiableCredentialTransformer(monitor));
    }
  }

  @Provider
  public ScopeExtractorRegistry scopeExtractorRegistry() {
    if (scopeExtractorRegistry == null) {
      this.scopeExtractorRegistry = new CredentialScopeExtractorRegistry(monitor);
    }
    return this.scopeExtractorRegistry;
  }
}
