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
package org.seamware.edc.services;

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.function.Function;
import org.eclipse.edc.protocol.spi.DefaultParticipantIdExtractionFunction;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.monitor.Monitor;
import org.jetbrains.annotations.NotNull;

public class TestParticipantIdExtractionFunction implements DefaultParticipantIdExtractionFunction {

  private final Monitor monitor;

  public TestParticipantIdExtractionFunction(Monitor monitor) {
    this.monitor = monitor;
  }

  @Override
  public String apply(ClaimToken claimToken) {
    try {
      monitor.warning("The token " + new ObjectMapper().writeValueAsString(claimToken));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
    return claimToken.getStringClaim("id");
  }

  @Override
  public @NotNull <V> Function<V, String> compose(
      @NotNull Function<? super V, ? extends ClaimToken> before) {
    return DefaultParticipantIdExtractionFunction.super.compose(before);
  }

  @Override
  public @NotNull <V> Function<ClaimToken, V> andThen(
      @NotNull Function<? super String, ? extends V> after) {
    return DefaultParticipantIdExtractionFunction.super.andThen(after);
  }
}
