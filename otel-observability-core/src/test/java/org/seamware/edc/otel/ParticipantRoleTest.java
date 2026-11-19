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
package org.seamware.edc.otel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class ParticipantRoleTest {

  @ParameterizedTest
  @CsvSource(
      nullValues = "null",
      value = {
        "PROVIDER,provider",
        "CONSUMER,consumer",
        "provider,null",
        "UNKNOWN,null",
        "null,null"
      })
  void processTypeMapsToRole(String processType, String expected) {
    assertEquals(expected, ParticipantRole.fromProcessType(processType));
  }

  @ParameterizedTest
  @CsvSource(
      nullValues = "null",
      value = {
        "urn:connector:fdsc-edc,urn:connector:fdsc-edc,did:web:consumer,provider",
        "did:web:consumer,urn:connector:fdsc-edc,did:web:consumer,consumer",
        "did:web:other,urn:connector:fdsc-edc,did:web:consumer,null",
        "null,urn:connector:fdsc-edc,did:web:consumer,null",
        "did:web:consumer,null,null,null",
        // exact comparison: no trimming, no case folding
        "DID:WEB:CONSUMER,urn:connector:fdsc-edc,did:web:consumer,null"
      })
  void partiesGiveTheRoleOfSelf(String self, String provider, String consumer, String expected) {
    assertEquals(expected, ParticipantRole.fromParties(self, provider, consumer));
  }

  @ParameterizedTest
  @CsvSource(
      nullValues = "null",
      value = {
        "urn:connector:fdsc-edc,urn:connector:fdsc-edc,did:web:consumer,provider",
        "urn:connector:fdsc-edc,did:web:provider,did:web:provider,consumer",
        "null,did:web:provider,did:web:provider,consumer",
        "urn:connector:fdsc-edc,did:web:third,did:web:provider,null",
        "urn:connector:fdsc-edc,null,did:web:provider,null",
        "null,null,null,null"
      })
  void offerAssignerGivesTheNegotiationRole(
      String self, String assigner, String counterParty, String expected) {
    assertEquals(expected, ParticipantRole.fromOfferAssigner(self, assigner, counterParty));
  }
}
