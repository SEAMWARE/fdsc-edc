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
package org.seamware.edc.edr;

import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.stream.Stream;
import org.eclipse.edc.edr.spi.types.EndpointDataReferenceEntry;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class NoopEdrTokenRefresherTest {

  private static final String TRACTUSX_AUTH_NS = "https://w3id.org/tractusx/auth/";
  private static final String ENDPOINT = "https://provider.example/data";
  private static final String TYPE = "https://w3id.org/idsa/v4.1/HTTP";

  static Stream<DataAddress> addresses() {
    var plain = DataAddress.Builder.newInstance().type(TYPE).property("endpoint", ENDPOINT).build();
    var renewable =
        DataAddress.Builder.newInstance()
            .type(TYPE)
            .property("endpoint", ENDPOINT)
            .property(TRACTUSX_AUTH_NS + "refreshEndpoint", ENDPOINT + "/token")
            .property(TRACTUSX_AUTH_NS + "refreshToken", "refresh-token")
            .property(TRACTUSX_AUTH_NS + "expiresIn", "300")
            .property(TRACTUSX_AUTH_NS + "refreshAudience", "did:web:consumer")
            .build();
    return Stream.of(plain, renewable);
  }

  @ParameterizedTest
  @MethodSource("addresses")
  void refreshIfNeeded_returnsCurrentUnchanged(DataAddress current) {
    var entry =
        EndpointDataReferenceEntry.Builder.newInstance()
            .participantContextId("dataspace-a")
            .transferProcessId("tp-1")
            .assetId("asset-1")
            .agreementId("agreement-1")
            .providerId("provider-1")
            .build();

    assertSame(current, new NoopEdrTokenRefresher().refreshIfNeeded(entry, current));
  }
}
