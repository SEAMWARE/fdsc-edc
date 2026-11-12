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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.logs.Logger;
import java.time.Clock;
import java.util.Map;
import org.eclipse.edc.connector.controlplane.contract.spi.validation.ContractValidationService;
import org.eclipse.edc.policy.engine.spi.PolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Checks what the extension registers. That the DSP protocol services are then built with the
 * decorator depends on EDC's dependency ordering and is verified by running the TCK.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class OtelPolicyAuditExtensionTest {

  @Mock private ContractValidationService contractValidationService;
  @Mock private PolicyEngine policyEngine;
  @Mock private Logger otelLogger;
  @Mock private Clock clock;
  @Mock private ServiceExtensionContext context;
  @Mock private Monitor monitor;

  @InjectMocks private OtelPolicyAuditExtension extension;

  @BeforeEach
  void setUp() {
    when(context.getMonitor()).thenReturn(monitor);
    when(context.getConfig())
        .thenReturn(ConfigFactory.fromMap(Map.of("edc.participant.id", "urn:connector:fdsc-edc")));
  }

  @Test
  void decoratesTheValidationServiceAndObservesEvaluations() throws Exception {
    setField("enabled", true);
    setField("excludedScopes", PolicyEvaluationAuditValidator.DEFAULT_EXCLUDED_SCOPES);

    extension.initialize(context);

    verify(policyEngine)
        .registerPostValidator(eq(PolicyContext.class), any(PolicyEvaluationAuditValidator.class));
    var registered = ArgumentCaptor.forClass(ContractValidationService.class);
    verify(context).registerService(eq(ContractValidationService.class), registered.capture());
    var decorator =
        assertInstanceOf(AuditingContractValidationService.class, registered.getValue());
    assertSame(contractValidationService, decorator.delegate());
  }

  @Test
  void disabledAuditRegistersTheOriginalServiceUnchanged() throws Exception {
    setField("enabled", false);

    extension.initialize(context);

    verify(context).registerService(ContractValidationService.class, contractValidationService);
    verify(policyEngine, never()).registerPostValidator(any(), any());
  }

  // EDC sets @Setting fields by reflection; Mockito only injects the @Inject services
  private void setField(String name, Object value) throws Exception {
    var field = OtelPolicyAuditExtension.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(extension, value);
  }
}
