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
import java.util.Map;
import okhttp3.OkHttpClient;
import org.eclipse.edc.connector.controlplane.catalog.spi.policy.CatalogPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.ContractNegotiationPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.TransferProcessPolicyContext;
import org.eclipse.edc.jsonld.spi.JsonLd;
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
 * each configured Layer 2 scope (catalog, contract negotiation, transfer process). These scopes are
 * evaluated <b>after</b> token verification, so the policy context carries the authenticated {@link
 * org.eclipse.edc.participant.spi.ParticipantAgent} with verified VerifiableCredential claims.
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
  private static final String SCOPE_NEGOTIATION = "contract.negotiation";

  /** Scope label for transfer validators, used in log messages. */
  private static final String SCOPE_TRANSFER = "transfer.process";

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
   * for each enabled Layer 2 scope.
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

    List<Map<String, Object>> additionalContexts = loadAdditionalContexts(config);
    List<ScopeMappingRule> scopeMappingRules = loadScopeMappingRules(config);

    OdrlPapClient client = new OdrlPapClient(monitor, okHttpClient, config.host(), objectMapper);
    PolicyContextInputMapper mapper = new PolicyContextInputMapper(objectMapper);
    OdrlPapPolicyValidator validator =
        new OdrlPapPolicyValidator(
            client,
            typeTransformerRegistry,
            jsonLd,
            mapper,
            monitor,
            objectMapper,
            config.denyOnError(),
            additionalContexts,
            scopeMappingRules);

    List<String> registeredScopes = new ArrayList<>();

    if (config.scopeCatalog()) {
      policyEngine.registerPreValidator(CatalogPolicyContext.class, validator::apply);
      registeredScopes.add(SCOPE_CATALOG);
    }

    if (config.scopeNegotiation()) {
      policyEngine.registerPreValidator(ContractNegotiationPolicyContext.class, validator::apply);
      registeredScopes.add(SCOPE_NEGOTIATION);
    }

    if (config.scopeTransfer()) {
      policyEngine.registerPreValidator(TransferProcessPolicyContext.class, validator::apply);
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

  /**
   * Loads additional JSON-LD contexts from the file path specified in the configuration.
   *
   * @param config the ODRL-PAP configuration
   * @return the loaded contexts, or an empty list if no path is configured
   * @throws IllegalArgumentException if the configured file cannot be read or parsed
   */
  private List<Map<String, Object>> loadAdditionalContexts(OdrlPapConfig config) {
    String path = config.additionalContextsPath();
    if (path == null || path.isBlank()) {
      monitor.info(LOG_PREFIX + "No additional contexts file configured.");
      return List.of();
    }

    List<Map<String, Object>> contexts = AdditionalContextsLoader.load(path, objectMapper);
    monitor.info(
        LOG_PREFIX + "Loaded " + contexts.size() + " additional context(s) from '" + path + "'.");
    return contexts;
  }

  /**
   * Loads scope mapping rules from the file path specified in the configuration.
   *
   * @param config the ODRL-PAP configuration
   * @return the loaded rules, or an empty list if no path is configured
   * @throws IllegalArgumentException if the configured file cannot be read or parsed
   */
  private List<ScopeMappingRule> loadScopeMappingRules(OdrlPapConfig config) {
    String path = config.scopeMappingsPath();
    if (path == null || path.isBlank()) {
      monitor.info(LOG_PREFIX + "No scope mappings file configured.");
      return List.of();
    }

    List<ScopeMappingRule> rules = ScopeMappingsLoader.load(path, objectMapper);
    monitor.info(
        LOG_PREFIX + "Loaded " + rules.size() + " scope mapping rule(s) from '" + path + "'.");
    return rules;
  }
}
