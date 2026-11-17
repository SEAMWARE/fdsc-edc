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

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.system.configuration.Config;

@EqualsAndHashCode
@ToString
public class DcpConfig {

  private static final String DCP_CONFIG = "dcp";
  private static final String DCP_SCOPES_CONFIG = "dcp.scopes";

  /** Per-profile scope overrides: {@code dcp.profiles.<profileName>.scopes.*}. */
  public static final String DCP_PROFILES_CONFIG = "dcp.profiles";

  private static final String SCOPES_CONFIG = "scopes";
  private static final String CATALOG_KEY = "catalog";
  private static final String NEGOTIATION_KEY = "negotiation";
  private static final String TRANSFER_KEY = "transfer";

  private boolean enabled;
  private Scopes scopes;
  private Map<String, Scopes> profileScopes = Map.of();

  public boolean isEnabled() {
    return enabled;
  }

  public Scopes getScopes() {
    return scopes;
  }

  /** Scope overrides keyed by dataspace profile name; empty when no profile overrides are set. */
  public Map<String, Scopes> getProfileScopes() {
    return profileScopes;
  }

  public static DcpConfig fromConfig(Config config) {
    Config dcpConfig = config.getConfig(DCP_CONFIG);
    DcpConfig.Builder dcpConfigBuilder = DcpConfig.Builder.newInstance();

    getNullSafeFromConfig(() -> dcpConfig.getBoolean("enabled"))
        .ifPresent(dcpConfigBuilder::enabled);

    dcpConfigBuilder.scopes(scopesFrom(config.getConfig(DCP_SCOPES_CONFIG)));
    dcpConfigBuilder.profileScopes(
        config
            .getConfig(DCP_PROFILES_CONFIG)
            .partition()
            .collect(
                Collectors.toMap(
                    Config::currentNode,
                    profileConfig -> scopesFrom(profileConfig.getConfig(SCOPES_CONFIG)))));
    return dcpConfigBuilder.build();
  }

  private static Scopes scopesFrom(Config scopesConfig) {
    Scopes.Builder scopesBuilder = new Scopes.Builder();
    getNullSafeFromConfig(() -> scopesConfig.getString(CATALOG_KEY))
        .ifPresent(scopesBuilder::catalog);
    getNullSafeFromConfig(() -> scopesConfig.getString(NEGOTIATION_KEY))
        .ifPresent(scopesBuilder::negotiation);
    getNullSafeFromConfig(() -> scopesConfig.getString(TRANSFER_KEY))
        .ifPresent(scopesBuilder::transfer);
    return scopesBuilder.build();
  }

  public record Scopes(String catalog, String negotiation, String transfer) {
    public static class Builder {

      private String catalog;
      private String negotiation;
      private String transfer;

      public Builder catalog(String catalog) {
        this.catalog = catalog;
        return this;
      }

      public Builder negotiation(String negotiation) {
        this.negotiation = negotiation;
        return this;
      }

      public Builder transfer(String transfer) {
        this.transfer = transfer;
        return this;
      }

      public Scopes build() {
        if (catalog == null) {
          catalog = "";
        }
        if (negotiation == null) {
          negotiation = "";
        }
        if (transfer == null) {
          transfer = "";
        }
        return new Scopes(catalog, negotiation, transfer);
      }
    }
  }

  public static class Builder {

    private final DcpConfig dcpConfig;

    public Builder(DcpConfig dcpConfig) {
      this.dcpConfig = dcpConfig;
    }

    public static Builder newInstance() {
      return new Builder(new DcpConfig());
    }

    public Builder enabled(boolean enabled) {
      dcpConfig.enabled = enabled;
      return this;
    }

    public Builder scopes(Scopes scopes) {
      dcpConfig.scopes = scopes;
      return this;
    }

    public Builder profileScopes(Map<String, Scopes> profileScopes) {
      dcpConfig.profileScopes = Map.copyOf(profileScopes);
      return this;
    }

    public DcpConfig build() {
      return dcpConfig;
    }
  }

  private static <T> Optional<T> getNullSafeFromConfig(Supplier<T> fromConfig) {
    try {
      return Optional.of(fromConfig.get());
    } catch (EdcException e) {
      return Optional.empty();
    }
  }
}
