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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class OID4VPParticipantIdExtractionFunctionTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("getValidClaimTokens")
  public void testApply_success(
      String name, ClaimToken claimToken, String issuerClaim, String expectedIssuer) {
    OID4VPParticipantIdExtractionFunction extractionFunction =
        new OID4VPParticipantIdExtractionFunction(mock(Monitor.class), issuerClaim);
    assertEquals(expectedIssuer, extractionFunction.apply(claimToken), name);
  }

  private static Stream<Arguments> getValidClaimTokens() {
    return Stream.of(
        Arguments.of(
            "A plain string should be returned.",
            getToken("issuer", "my-issuer"),
            "issuer",
            "my-issuer"),
        Arguments.of(
            "Nested values on second level should be resolved.",
            getToken("vc", Map.of("issuer", "my-issuer")),
            "vc.issuer",
            "my-issuer"),
        Arguments.of(
            "Nested values with `-` should be resolved.",
            getToken("vc", Map.of("the-issuer", "my-issuer")),
            "vc.the-issuer",
            "my-issuer"),
        Arguments.of(
            "Nested values with `_` should be resolved.",
            getToken("vc", Map.of("the_issuer", "my-issuer")),
            "vc.the_issuer",
            "my-issuer"),
        Arguments.of(
            "Nested values should be resolved case sensitive.",
            getToken("vc", Map.of("theIssuer", "my-issuer", "theissuer", "other-issuer")),
            "vc.theIssuer",
            "my-issuer"),
        Arguments.of(
            "Complex nesting should be resolved.",
            getToken(
                "vc",
                Map.of("issuer", Map.of("some", Map.of("where", Map.of("below", "my-issuer"))))),
            "vc.issuer.some.where.below",
            "my-issuer"));
  }

  @ParameterizedTest(name = "Invalid - {0}")
  @MethodSource("getInvalidClaimTokens")
  public void testApply_failure(String name, ClaimToken claimToken, String issuerClaim) {
    OID4VPParticipantIdExtractionFunction extractionFunction =
        new OID4VPParticipantIdExtractionFunction(mock(Monitor.class), issuerClaim);
    assertThrows(IllegalArgumentException.class, () -> extractionFunction.apply(claimToken), name);
  }

  private static Stream<Arguments> getInvalidClaimTokens() {
    return Stream.of(
        Arguments.of("The claim needs to exist.", getToken("issuer", "my-issuer"), "organization"),
        Arguments.of(
            "The claim needs to be a string.", getToken("issuer", List.of("a", "b")), "issuer"),
        Arguments.of(
            "The claim path needs to exist.",
            getToken("issuer", Map.of("a", Map.of("b", "my-issuer"))),
            "issuer.a.c"));
  }

  private static ClaimToken getToken(String key, Object value) {
    return ClaimToken.Builder.newInstance().claim(key, value).build();
  }
}
