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
package org.seamware.edc.identity;

/*-
 * #%L
 * oid4vc-extension
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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.eclipse.edc.protocol.spi.DefaultParticipantIdExtractionFunction;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.monitor.Monitor;
import org.jetbrains.annotations.NotNull;

/** Extract the participants id from a JWT issued in an OID4VC based ecosystem */
public class OID4VPParticipantIdExtractionFunction
    implements DefaultParticipantIdExtractionFunction {

  private final Monitor monitor;
  private final String issuerClaim;

  public OID4VPParticipantIdExtractionFunction(Monitor monitor, String issuerClaim) {
    this.monitor = monitor;
    this.issuerClaim = issuerClaim;
  }

  @Override
  public String apply(ClaimToken claimToken) {

    Map<String, Object> claims = claimToken.getClaims();
    List<String> claimPath = Arrays.asList(issuerClaim.split("\\."));
    Map<String, Object> currentClaims = claims;
    for (int i = 0; i < claimPath.size(); i++) {
      String currentPath = claimPath.get(i);
      if (!currentClaims.containsKey(currentPath)) {
        throw new IllegalArgumentException("Was not able to extract the issuer.");
      }
      Object currentValue = currentClaims.get(currentPath);
      if (i == claimPath.size() - 1 && currentValue instanceof String issuerString) {
        monitor.debug(String.format("The participant is %s.", issuerString));
        return issuerString;
      }
      if (i < claimPath.size() - 1 && currentValue instanceof Map<?, ?> claimMap) {
        currentClaims = (Map<String, Object>) claimMap;
      }
    }
    throw new IllegalArgumentException("Was not able to extract the issuer.");
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
