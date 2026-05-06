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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import okhttp3.OkHttpClient;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.policy.context.request.spi.RequestCatalogPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestContractNegotiationPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestTransferProcessPolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.seamware.edc.pap.OdrlPapClient;

/**
 * EDC {@link ServiceExtension} that wires the ODRL-PAP policy validator into the EDC {@link
 * PolicyEngine}.
 *
 * <p>When enabled via {@value OdrlPapConfig#SETTING_ENABLED}, this extension creates an {@link
 * OdrlPapPolicyValidator} and registers it as a pre-validator with the {@link PolicyEngine} for
 * each configured scope (catalog, negotiation, transfer). The pre-validator intercepts policy
 * evaluation before EDC's built-in constraint functions run, delegating evaluation to the external
 * ODRL-PAP service.
 *
 * <p>The extension is disabled by default and must be explicitly enabled via configuration. When
 * disabled, no validators are registered and the extension has no effect.
 *
 * @see OdrlPapConfig for configuration properties
 * @see OdrlPapPolicyValidator for the validation logic
 */
public class OdrlPapPolicyExtension implements ServiceExtension {

  /** Display name for this extension in EDC logs and diagnostics. */
  static final String EXTENSION_NAME = "ODRL PAP Policy Extension";

  /** Log prefix for messages from this extension. */
  private static final String LOG_PREFIX = "[OdrlPapPolicyExtension] ";

  /** Scope label for catalog validators, used in log messages. */
  private static final String SCOPE_CATALOG = "catalog";

  /** Scope label for negotiation validators, used in log messages. */
  private static final String SCOPE_NEGOTIATION = "negotiation";

  /** Scope label for transfer validators, used in log messages. */
  private static final String SCOPE_TRANSFER = "transfer";

  @Inject PolicyEngine policyEngine;

  @Inject Monitor monitor;

  @Inject OkHttpClient okHttpClient;

  @Inject ObjectMapper objectMapper;

  @Inject TypeTransformerRegistry typeTransformerRegistry;

  @Inject JsonLd jsonLd;

  @Override
  public String name() {
    return EXTENSION_NAME;
  }

  /**
   * Initializes the ODRL-PAP policy extension.
   *
   * <p>Loads configuration from the EDC context, and if enabled, creates an {@link OdrlPapClient}
   * and {@link OdrlPapPolicyValidator}, then registers pre-validators with the {@link PolicyEngine}
   * for each enabled scope.
   *
   * <p>If the extension is enabled but no host is configured, initialization logs an error and
   * returns without registering any validators.
   *
   * @param context the EDC service extension context providing configuration access
   */
  @Override
  public void initialize(ServiceExtensionContext context) {
    OdrlPapConfig config = OdrlPapConfig.fromConfig(context.getConfig());

    if (!config.isEnabled()) {
      monitor.info(LOG_PREFIX + "ODRL-PAP policy evaluation is disabled.");
      return;
    }

    if (config.host() == null || config.host().isBlank()) {
      monitor.severe(
          LOG_PREFIX
              + "ODRL-PAP policy evaluation is enabled but no host is configured. "
              + "Set '"
              + OdrlPapConfig.SETTING_HOST
              + "' to the PAP service URL. "
              + "No validators will be registered.");
      return;
    }

    OdrlPapClient client = new OdrlPapClient(monitor, okHttpClient, config.host(), objectMapper);
    PolicyContextRequestMapper mapper = new PolicyContextRequestMapper();
    OdrlPapPolicyValidator validator =
        new OdrlPapPolicyValidator(
            client,
            typeTransformerRegistry,
            jsonLd,
            mapper,
            monitor,
            objectMapper,
            config.denyOnError());

    List<String> registeredScopes = new ArrayList<>();

    if (config.scopeCatalog()) {
      policyEngine.registerPreValidator(RequestCatalogPolicyContext.class, validator::apply);
      registeredScopes.add(SCOPE_CATALOG);
    }

    if (config.scopeNegotiation()) {
      policyEngine.registerPreValidator(
          RequestContractNegotiationPolicyContext.class, validator::apply);
      registeredScopes.add(SCOPE_NEGOTIATION);
    }

    if (config.scopeTransfer()) {
      policyEngine.registerPreValidator(
          RequestTransferProcessPolicyContext.class, validator::apply);
      registeredScopes.add(SCOPE_TRANSFER);
    }

    monitor.info(
        LOG_PREFIX
            + "Registered ODRL-PAP pre-validators for scopes: "
            + registeredScopes
            + " (denyOnError="
            + config.denyOnError()
            + ", host="
            + config.host()
            + ")");
  }
}
