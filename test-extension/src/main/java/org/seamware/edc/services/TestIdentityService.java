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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.eclipse.edc.spi.iam.*;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;

public class TestIdentityService implements org.eclipse.edc.spi.iam.IdentityService {

  private final Monitor monitor;
  private final ObjectMapper objectMapper;
  private final String participantId;

  public TestIdentityService(Monitor monitor, ObjectMapper objectMapper, String participantId) {
    this.monitor = monitor;
    this.objectMapper = objectMapper;
    this.participantId = participantId;
  }

  @Override
  public Result<TokenRepresentation> obtainClientCredentials(TokenParameters tokenParameters) {
    monitor.debug("Obtain test credential.");

    return Result.success(
        TokenRepresentation.Builder.newInstance()
            .token(String.format("{\"id\": \"%s\"}", participantId))
            .build());
  }

  @Override
  public Result<ClaimToken> verifyJwtToken(
      TokenRepresentation tokenRepresentation, VerificationContext verificationContext) {

    try {
      monitor.info("The token: " + tokenRepresentation.getToken());

      Map<String, String> claims =
          objectMapper.readValue(
              tokenRepresentation.getToken(), new TypeReference<Map<String, String>>() {});
      ClaimToken.Builder tokenBuilder = ClaimToken.Builder.newInstance();
      claims.forEach(tokenBuilder::claim);
      return Result.success(tokenBuilder.build());
    } catch (JsonProcessingException e) {
      return Result.failure(
          "[TestIdentityService] Was not able to read the token " + e.getMessage());
    }
  }
}
