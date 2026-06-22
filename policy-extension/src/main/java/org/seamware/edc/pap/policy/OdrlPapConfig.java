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

import java.util.Optional;
import java.util.function.Supplier;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.system.configuration.Config;

/**
 * Configuration for ODRL-PAP policy evaluation.
 *
 * <p>Controls whether the ODRL-PAP service is used as a pre-validator in the EDC {@code
 * PolicyEngine}, which scopes are enabled, and error handling behavior.
 *
 * <p>Supported properties:
 *
 * <ul>
 *   <li>{@value #SETTING_ENABLED} &mdash; enables PAP-based policy evaluation (default: {@value
 *       #DEFAULT_ENABLED})
 *   <li>{@value #SETTING_HOST} &mdash; base URL of the ODRL-PAP service (required when enabled)
 *   <li>{@value #SETTING_DENY_ON_ERROR} &mdash; when {@code true}, PAP communication errors cause
 *       policy denial (fail-closed); when {@code false}, errors are logged and the policy is
 *       allowed (fail-open) (default: {@value #DEFAULT_DENY_ON_ERROR})
 *   <li>{@value #SETTING_SCOPE_CATALOG} &mdash; register pre-validator for catalog scope (default:
 *       {@value #DEFAULT_SCOPE_CATALOG})
 *   <li>{@value #SETTING_SCOPE_NEGOTIATION} &mdash; register pre-validator for negotiation scope
 *       (default: {@value #DEFAULT_SCOPE_NEGOTIATION})
 *   <li>{@value #SETTING_SCOPE_TRANSFER} &mdash; register pre-validator for transfer scope
 *       (default: {@value #DEFAULT_SCOPE_TRANSFER})
 *   <li>{@value #SETTING_ADDITIONAL_CONTEXTS_PATH} &mdash; path to a JSON file containing
 *       additional JSON-LD contexts to include in PAP validation requests (default: not set)
 * </ul>
 */
public class OdrlPapConfig {

  /** EDC configuration prefix for the ODRL-PAP settings. */
  static final String ODRL_PAP_CONFIG_PREFIX = "odrlPap";

  /** EDC configuration prefix for ODRL-PAP policy evaluation settings. */
  static final String ODRL_PAP_POLICY_CONFIG_PREFIX = "odrlPap.policy";

  /** EDC configuration prefix for ODRL-PAP policy scope settings. */
  static final String ODRL_PAP_POLICY_SCOPES_CONFIG_PREFIX = "odrlPap.policy.scopes";

  /** EDC setting key for enabling/disabling PAP policy evaluation. */
  static final String SETTING_ENABLED = "odrlPap.policy.enabled";

  /** Default value for {@link #SETTING_ENABLED}. */
  static final boolean DEFAULT_ENABLED = false;

  /** EDC setting key for the ODRL-PAP service base URL. */
  static final String SETTING_HOST = "odrlPap.host";

  /** EDC setting key for fail-closed/fail-open error handling. */
  static final String SETTING_DENY_ON_ERROR = "odrlPap.policy.denyOnError";

  /** Default value for {@link #SETTING_DENY_ON_ERROR}. */
  static final boolean DEFAULT_DENY_ON_ERROR = true;

  /** EDC setting key for registering the catalog scope pre-validator. */
  static final String SETTING_SCOPE_CATALOG = "odrlPap.policy.scopes.catalog";

  /** Default value for {@link #SETTING_SCOPE_CATALOG}. */
  static final boolean DEFAULT_SCOPE_CATALOG = true;

  /** EDC setting key for registering the negotiation scope pre-validator. */
  static final String SETTING_SCOPE_NEGOTIATION = "odrlPap.policy.scopes.negotiation";

  /** Default value for {@link #SETTING_SCOPE_NEGOTIATION}. */
  static final boolean DEFAULT_SCOPE_NEGOTIATION = true;

  /** EDC setting key for registering the transfer scope pre-validator. */
  static final String SETTING_SCOPE_TRANSFER = "odrlPap.policy.scopes.transfer";

  /** Default value for {@link #SETTING_SCOPE_TRANSFER}. */
  static final boolean DEFAULT_SCOPE_TRANSFER = true;

  /**
   * EDC setting key for the path to a JSON file containing additional JSON-LD contexts to include
   * in PAP validation requests.
   */
  static final String SETTING_ADDITIONAL_CONTEXTS_PATH = "odrlPap.policy.additionalContextsPath";

  /**
   * EDC setting key for the path to a JSON file containing scope mapping rules that assign ODRL
   * permissions to evaluation scopes based on their properties.
   */
  static final String SETTING_SCOPE_MAPPINGS_PATH = "odrlPap.policy.scopeMappingsPath";

  private final boolean enabled;
  private final String host;
  private final boolean denyOnError;
  private final boolean scopeCatalog;
  private final boolean scopeNegotiation;
  private final boolean scopeTransfer;
  private final String additionalContextsPath;
  private final String scopeMappingsPath;

  /**
   * Creates a new configuration instance. Package-private to encourage use of {@link
   * #fromConfig(Config)}.
   *
   * @param enabled whether PAP policy evaluation is enabled
   * @param host the ODRL-PAP service base URL (may be {@code null} when disabled)
   * @param denyOnError whether to deny on PAP communication errors
   * @param scopeCatalog whether to register the catalog scope pre-validator
   * @param scopeNegotiation whether to register the negotiation scope pre-validator
   * @param scopeTransfer whether to register the transfer scope pre-validator
   * @param additionalContextsPath path to a JSON file with additional JSON-LD contexts, or {@code
   *     null} if not configured
   * @param scopeMappingsPath path to a JSON file with scope mapping rules, or {@code null} if not
   *     configured
   */
  OdrlPapConfig(
      boolean enabled,
      String host,
      boolean denyOnError,
      boolean scopeCatalog,
      boolean scopeNegotiation,
      boolean scopeTransfer,
      String additionalContextsPath,
      String scopeMappingsPath) {
    this.enabled = enabled;
    this.host = host;
    this.denyOnError = denyOnError;
    this.scopeCatalog = scopeCatalog;
    this.scopeNegotiation = scopeNegotiation;
    this.scopeTransfer = scopeTransfer;
    this.additionalContextsPath = additionalContextsPath;
    this.scopeMappingsPath = scopeMappingsPath;
  }

  /**
   * Loads ODRL-PAP policy configuration from an EDC {@link Config}.
   *
   * <p>Missing properties are resolved to their documented defaults. The {@value #SETTING_HOST}
   * property has no default and will be {@code null} when not set; the extension must validate its
   * presence when {@link #isEnabled()} is {@code true}.
   *
   * @param config the EDC configuration root
   * @return a populated {@link OdrlPapConfig} instance
   */
  public static OdrlPapConfig fromConfig(Config config) {
    Config odrlPapConfig = config.getConfig(ODRL_PAP_CONFIG_PREFIX);
    Config policyConfig = config.getConfig(ODRL_PAP_POLICY_CONFIG_PREFIX);
    Config scopesConfig = config.getConfig(ODRL_PAP_POLICY_SCOPES_CONFIG_PREFIX);

    boolean enabled =
        getNullSafeFromConfig(() -> policyConfig.getBoolean("enabled")).orElse(DEFAULT_ENABLED);
    String host = getNullSafeFromConfig(() -> odrlPapConfig.getString("host")).orElse(null);
    boolean denyOnError =
        getNullSafeFromConfig(() -> policyConfig.getBoolean("denyOnError"))
            .orElse(DEFAULT_DENY_ON_ERROR);
    boolean scopeCatalog =
        getNullSafeFromConfig(() -> scopesConfig.getBoolean("catalog"))
            .orElse(DEFAULT_SCOPE_CATALOG);
    boolean scopeNegotiation =
        getNullSafeFromConfig(() -> scopesConfig.getBoolean("negotiation"))
            .orElse(DEFAULT_SCOPE_NEGOTIATION);
    boolean scopeTransfer =
        getNullSafeFromConfig(() -> scopesConfig.getBoolean("transfer"))
            .orElse(DEFAULT_SCOPE_TRANSFER);
    String additionalContextsPath =
        getNullSafeFromConfig(() -> policyConfig.getString("additionalContextsPath")).orElse(null);
    String scopeMappingsPath =
        getNullSafeFromConfig(() -> policyConfig.getString("scopeMappingsPath")).orElse(null);

    return new OdrlPapConfig(
        enabled,
        host,
        denyOnError,
        scopeCatalog,
        scopeNegotiation,
        scopeTransfer,
        additionalContextsPath,
        scopeMappingsPath);
  }

  /** Returns {@code true} if PAP-based policy evaluation is enabled. */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Returns the ODRL-PAP service base URL, or {@code null} if not configured.
   *
   * @return the PAP host URL
   */
  public String host() {
    return host;
  }

  /**
   * Returns {@code true} if PAP communication errors should cause policy denial (fail-closed).
   *
   * @return the deny-on-error flag
   */
  public boolean denyOnError() {
    return denyOnError;
  }

  /**
   * Returns {@code true} if the catalog scope pre-validator should be registered.
   *
   * @return the catalog scope flag
   */
  public boolean scopeCatalog() {
    return scopeCatalog;
  }

  /**
   * Returns {@code true} if the negotiation scope pre-validator should be registered.
   *
   * @return the negotiation scope flag
   */
  public boolean scopeNegotiation() {
    return scopeNegotiation;
  }

  /**
   * Returns {@code true} if the transfer scope pre-validator should be registered.
   *
   * @return the transfer scope flag
   */
  public boolean scopeTransfer() {
    return scopeTransfer;
  }

  /**
   * Returns the path to the JSON file containing additional JSON-LD contexts, or {@code null} if
   * not configured.
   *
   * @return the additional contexts file path, or {@code null}
   */
  public String additionalContextsPath() {
    return additionalContextsPath;
  }

  /**
   * Returns the path to the JSON file containing scope mapping rules, or {@code null} if not
   * configured.
   *
   * @return the scope mappings file path, or {@code null}
   */
  public String scopeMappingsPath() {
    return scopeMappingsPath;
  }

  /**
   * Safely retrieves a configuration value, returning {@link Optional#empty()} if the key is not
   * found.
   *
   * @param fromConfig supplier that reads the value from EDC {@link Config}
   * @param <T> the value type
   * @return the value wrapped in an {@link Optional}, or empty if the key is missing
   */
  private static <T> Optional<T> getNullSafeFromConfig(Supplier<T> fromConfig) {
    try {
      return Optional.of(fromConfig.get());
    } catch (EdcException e) {
      return Optional.empty();
    }
  }
}
