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

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.system.configuration.Config;

@EqualsAndHashCode
@ToString
public class TirConfig {

  private static final String EBSI_TIR_CONFIG = "ebsiTir";

  private boolean enabled;
  private String tilAddress;

  public String getTilAddress() {
    return tilAddress;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public static TirConfig fromConfig(Config config) {
    Config tirConfig = config.getConfig(EBSI_TIR_CONFIG);

    TirConfig.Builder tirConfigBuilder = TirConfig.Builder.newInstance();
    getNullSafeFromConfig(() -> tirConfig.getBoolean("enabled"))
        .ifPresent(tirConfigBuilder::enabled);
    getNullSafeFromConfig(() -> tirConfig.getString("tilAddress"))
        .ifPresent(tirConfigBuilder::tilAddress);

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
