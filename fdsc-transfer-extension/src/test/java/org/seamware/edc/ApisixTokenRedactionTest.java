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
 * fdsc-transfer-extension
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The APISIX admin token grants write access to the gateway. {@code
 * FDSCTransferControlExtension#initialize} logs the whole configuration at INFO, so no rendering of
 * it may contain the token.
 */
public class ApisixTokenRedactionTest {

  private static final String APISIX_ADMIN_TOKEN = "super-secret-admin-token";
  private static final String APISIX_ADDRESS = "http://apisix-admin.svc.local:9180";
  private static final String HTTPS_PROXY = "http://proxy.infra.svc.cluster.local:8888";
  private static final String TRANSFER_HOST = "transfer.example.org";

  @ParameterizedTest(name = "{0} of the configuration hides the apisix admin token")
  @MethodSource("renderings")
  public void tokenIsNotRendered(String name, Function<TransferConfig, String> rendering) {
    String rendered = rendering.apply(transferConfig());

    assertFalse(
        rendered.contains(APISIX_ADMIN_TOKEN),
        String.format("%s leaks the apisix admin token: %s", name, rendered));
    assertTrue(
        rendered.contains(APISIX_ADDRESS),
        String.format("%s should still carry the non-secret settings: %s", name, rendered));
  }

  private static Stream<Arguments> renderings() {
    return Stream.of(
        // the very call FDSCTransferControlExtension#initialize makes
        Arguments.of(
            "JSON serialization",
            (Function<TransferConfig, String>) ApisixTokenRedactionTest::toJson),
        Arguments.of("toString", (Function<TransferConfig, String>) TransferConfig::toString),
        Arguments.of(
            "Apisix.toString",
            (Function<TransferConfig, String>) config -> config.getApisix().toString()));
  }

  private static String toJson(TransferConfig transferConfig) {
    try {
      return new ObjectMapper().writeValueAsString(transferConfig);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private static TransferConfig transferConfig() {
    return TransferConfig.Builder.newInstance()
        .enabled(true)
        .transferHost(TRANSFER_HOST)
        .apisix(
            new TransferConfig.Apisix.Builder()
                .address(APISIX_ADDRESS)
                .token(APISIX_ADMIN_TOKEN)
                .httpsProxy(HTTPS_PROXY)
                .build())
        .build();
  }
}
