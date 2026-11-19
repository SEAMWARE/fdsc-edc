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
package org.seamware.edc.otel.audit;

import io.opentelemetry.api.logs.Logger;
import java.time.Clock;
import org.eclipse.edc.connector.controlplane.contract.spi.validation.ContractValidationService;
import org.eclipse.edc.policy.engine.spi.PolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.seamware.edc.otel.OtelCoreExtension;

/**
 * Audits policy decisions: a policy engine post-validator records permitted evaluations, and a
 * decorator of the {@link ContractValidationService} records the DSP requests it denies.
 *
 * <p>It provides the decorated {@link ContractValidationService}, so the DSP protocol services,
 * which inject it, are built after this extension and get the decorator.
 */
@Provides(ContractValidationService.class)
@Extension(value = OtelPolicyAuditExtension.NAME)
public class OtelPolicyAuditExtension implements ServiceExtension {

  public static final String NAME = "OpenTelemetry Policy Audit";

  @Setting(
      key = "edc.otel.audit.enabled",
      description = "Enables OpenTelemetry audit logs for negotiation, transfer and policy events",
      defaultValue = "true")
  private boolean enabled;

  @Setting(
      key = "edc.otel.audit.policy.excluded.scopes",
      description =
          "Comma-separated policy scopes whose permitted evaluations are not audited. An entry"
              + " ending in '*' excludes every scope starting with it",
      defaultValue = PolicyEvaluationAuditValidator.DEFAULT_EXCLUDED_SCOPES)
  private String excludedScopes;

  @Inject private ContractValidationService contractValidationService;

  @Inject private PolicyEngine policyEngine;

  @Inject private Logger otelLogger;

  @Inject private Clock clock;

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public void initialize(ServiceExtensionContext context) {
    var monitor = context.getMonitor();
    if (!enabled) {
      // this extension provides the service, so it registers the original one unchanged
      context.registerService(ContractValidationService.class, contractValidationService);
      monitor.info("Otel Audit: policy audit disabled via edc.otel.audit.enabled");
      return;
    }

    var participantId = OtelCoreExtension.participantId(context.getConfig());
    policyEngine.registerPostValidator(
        PolicyContext.class,
        new PolicyEvaluationAuditValidator(
            otelLogger,
            monitor,
            clock,
            participantId,
            PolicyEvaluationAuditValidator.parseScopes(excludedScopes)));

    var auditor = new PolicyDenyAuditor(otelLogger, monitor, clock, participantId);
    context.registerService(
        ContractValidationService.class,
        new AuditingContractValidationService(contractValidationService, auditor, monitor));

    monitor.info(
        "Otel Audit: policy audit enabled, excluded scopes ["
            + excludedScopes
            + "], ContractValidationService decorated (delegate "
            + contractValidationService.getClass().getName()
            + ")");
  }
}
