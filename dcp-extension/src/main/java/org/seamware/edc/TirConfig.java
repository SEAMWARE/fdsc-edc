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
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.system.configuration.Config;

@EqualsAndHashCode
@ToString
public class TirConfig {

  private static final String EBSI_TIR_CONFIG = "ebsiTir";

  /** Per-profile TILs: {@code ebsiTir.profiles.<profileName>.tilAddress}. */
  public static final String EBSI_TIR_PROFILES_CONFIG = "ebsiTir.profiles";

  private static final String ENABLED_KEY = "enabled";
  private static final String TIL_ADDRESS_KEY = "tilAddress";

  private boolean enabled;
  private String tilAddress;
  private Map<String, String> profileTilAddresses = Map.of();

  public String getTilAddress() {
    return tilAddress;
  }

  /** TIL addresses keyed by dataspace profile name; empty when no profile has its own TIL. */
  public Map<String, String> getProfileTilAddresses() {
    return profileTilAddresses;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public static TirConfig fromConfig(Config config) {
    Config tirConfig = config.getConfig(EBSI_TIR_CONFIG);

    TirConfig.Builder tirConfigBuilder = TirConfig.Builder.newInstance();
    getNullSafeFromConfig(() -> tirConfig.getBoolean(ENABLED_KEY))
        .ifPresent(tirConfigBuilder::enabled);
    getNullSafeFromConfig(() -> tirConfig.getString(TIL_ADDRESS_KEY))
        .ifPresent(tirConfigBuilder::tilAddress);
    tirConfigBuilder.profileTilAddresses(
        config
            .getConfig(EBSI_TIR_PROFILES_CONFIG)
            .partition()
            .filter(
                profileConfig -> profileConfig.getRelativeEntries().containsKey(TIL_ADDRESS_KEY))
            .collect(
                Collectors.toMap(
                    Config::currentNode,
                    profileConfig -> profileConfig.getString(TIL_ADDRESS_KEY))));

    return tirConfigBuilder.build();
  }

  public static class Builder {
    private final TirConfig tirConfig;

    private Builder(TirConfig tirConfig) {
      this.tirConfig = tirConfig;
    }

    public static Builder newInstance() {
      return new Builder(new TirConfig());
    }

    public Builder tilAddress(String tilAddress) {
      tirConfig.tilAddress = tilAddress;
      return this;
    }

    public Builder enabled(boolean enabled) {
      tirConfig.enabled = enabled;
      return this;
    }

    public Builder profileTilAddresses(Map<String, String> profileTilAddresses) {
      tirConfig.profileTilAddresses = Map.copyOf(profileTilAddresses);
      return this;
    }

    public TirConfig build() {
      if (tirConfig.isEnabled()) {
        Objects.requireNonNull(
            tirConfig.getTilAddress(),
            "The til address needs to be provided, when ebsi tir is enabled.");
      }
      return tirConfig;
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
