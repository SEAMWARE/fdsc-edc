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
 * test-extension
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
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.system.configuration.Config;

public class TestConfig {

  private static final String TEST_CONFIG_PATH = "testExtension";
  private static final String TEST_CONTROLLER_CONFIG_PATH = "testExtension.controller";
  private static final String TEST_IDENTITY_CONFIG_PATH = "testExtension.identity";

  private boolean enabled;
  private ControllerConfig controllerConfig;
  private IdentityConfig identityConfig;

  public boolean isEnabled() {
    return enabled;
  }

  public ControllerConfig getControllerConfig() {
    return controllerConfig;
  }

  public IdentityConfig getIdentityConfig() {
    return identityConfig;
  }

  public static TestConfig fromConfig(Config config) {

    TestConfig.Builder testConfigBuilder = TestConfig.Builder.newInstance();

    Config controllerConfig = config.getConfig(TEST_CONTROLLER_CONFIG_PATH);
    ControllerConfig.Builder controllerConfigBuilder = new ControllerConfig.Builder();
    getNullSafeFromConfig(() -> controllerConfig.getBoolean("enabled"))
        .ifPresent(controllerConfigBuilder::enabled);
    getNullSafeFromConfig(() -> controllerConfig.getInteger("port"))
        .ifPresent(controllerConfigBuilder::port);
    getNullSafeFromConfig(() -> controllerConfig.getString("path"))
        .ifPresent(controllerConfigBuilder::path);

    Config identityConfig = config.getConfig(TEST_IDENTITY_CONFIG_PATH);
    IdentityConfig.Builder identityConfigBuilder = new IdentityConfig.Builder();
    getNullSafeFromConfig(() -> identityConfig.getBoolean("enabled"))
        .ifPresent(identityConfigBuilder::enabled);

    Config testConfig = config.getConfig(TEST_CONFIG_PATH);
    getNullSafeFromConfig(() -> testConfig.getBoolean("enabled"))
        .ifPresent(testConfigBuilder::enabled);

    return testConfigBuilder
        .identityConfig(identityConfigBuilder.build())
        .controllerConfig(controllerConfigBuilder.build())
        .build();
  }

  public static class Builder {
    private final TestConfig testConfig;

    private Builder(TestConfig testConfig) {
      this.testConfig = testConfig;
    }

    public static Builder newInstance() {
      return new Builder(new TestConfig());
    }

    public Builder enabled(boolean enabled) {
      this.testConfig.enabled = enabled;
      return this;
    }

    public Builder controllerConfig(ControllerConfig controllerConfig) {
      this.testConfig.controllerConfig = controllerConfig;
      return this;
    }

    public Builder identityConfig(IdentityConfig identityConfig) {
      this.testConfig.identityConfig = identityConfig;
      return this;
    }

    public TestConfig build() {
      return testConfig;
    }
  }

  public record ControllerConfig(boolean enabled, String path, int port) {
    public static class Builder {
      private boolean enabled;
      private String path;
      private int port;

      public Builder enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
      }

      public Builder path(String path) {
        this.path = path;
        return this;
      }

      public Builder port(int port) {
        this.port = port;
        return this;
      }

      public ControllerConfig build() {
        if (enabled) {
          Objects.requireNonNull(
              path, "If the test controller is enabled, its path needs to be configured.");
          Objects.requireNonNull(
              path, "If the test controller is enabled, its port needs to be configured.");
        }
        return new ControllerConfig(enabled, path, port);
      }
    }
  }

  public record IdentityConfig(boolean enabled) {
    public static class Builder {
      private boolean enabled;

      public Builder enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
      }

      public IdentityConfig build() {
        return new IdentityConfig(enabled);
      }
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
