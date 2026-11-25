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
package org.seamware.edc.edr.transform;

import static org.eclipse.edc.edr.spi.types.EndpointDataReferenceEntry.EDR_ENTRY_AGREEMENT_ID;
import static org.eclipse.edc.edr.spi.types.EndpointDataReferenceEntry.EDR_ENTRY_ASSET_ID;
import static org.eclipse.edc.edr.spi.types.EndpointDataReferenceEntry.EDR_ENTRY_CONTRACT_NEGOTIATION_ID;
import static org.eclipse.edc.edr.spi.types.EndpointDataReferenceEntry.EDR_ENTRY_CREATED_AT;
import static org.eclipse.edc.edr.spi.types.EndpointDataReferenceEntry.EDR_ENTRY_PROVIDER_ID;
import static org.eclipse.edc.edr.spi.types.EndpointDataReferenceEntry.EDR_ENTRY_TRANSFER_PROCESS_ID;
import static org.eclipse.edc.edr.spi.types.EndpointDataReferenceEntry.EDR_ENTRY_TYPE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.seamware.edc.edr.transform.JsonObjectFromEndpointDataReferenceEntryTransformer.EDR_ENTRY_PARTICIPANT_CONTEXT_ID;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.util.Map;
import java.util.stream.Stream;
import org.eclipse.edc.edr.spi.types.EndpointDataReferenceEntry;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JsonObjectFromEndpointDataReferenceEntryTransformerTest {

  private static final String TRANSFER_PROCESS_ID = "tp-1";
  private static final String ASSET_ID = "asset-1";
  private static final String AGREEMENT_ID = "agreement-1";
  private static final String NEGOTIATION_ID = "negotiation-1";
  private static final String PROVIDER_ID = "did:web:provider";
  private static final String CONTEXT_ID = "dataspace-a";
  private static final long CREATED_AT = 1_700_000_000_000L;

  private final TransformerContext context = mock(TransformerContext.class);
  private final JsonObjectFromEndpointDataReferenceEntryTransformer transformer =
      new JsonObjectFromEndpointDataReferenceEntryTransformer(Json.createBuilderFactory(Map.of()));

  static Stream<Arguments> optionalFields() {
    return Stream.of(
        Arguments.of("full entry", NEGOTIATION_ID, CONTEXT_ID),
        Arguments.of("no contract negotiation", null, CONTEXT_ID),
        Arguments.of("no participant context", NEGOTIATION_ID, null));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("optionalFields")
  void transform_writesV3ShapePlusParticipantContextId(
      String description, String negotiationId, String participantContextId) {
    var entry =
        EndpointDataReferenceEntry.Builder.newInstance()
            .transferProcessId(TRANSFER_PROCESS_ID)
            .assetId(ASSET_ID)
            .agreementId(AGREEMENT_ID)
            .providerId(PROVIDER_ID)
            .contractNegotiationId(negotiationId)
            .participantContextId(participantContextId)
            .createdAt(CREATED_AT)
            .build();

    var json = transformer.transform(entry, context);

    assertNotNull(json);
    assertEquals(TRANSFER_PROCESS_ID, json.getString(ID));
    assertEquals(EDR_ENTRY_TYPE, json.getString(TYPE));
    assertEquals(ASSET_ID, json.getString(EDR_ENTRY_ASSET_ID));
    assertEquals(AGREEMENT_ID, json.getString(EDR_ENTRY_AGREEMENT_ID));
    assertEquals(TRANSFER_PROCESS_ID, json.getString(EDR_ENTRY_TRANSFER_PROCESS_ID));
    assertEquals(PROVIDER_ID, json.getString(EDR_ENTRY_PROVIDER_ID));
    assertEquals(CREATED_AT, json.getJsonNumber(EDR_ENTRY_CREATED_AT).longValue());
    assertOptional(json, EDR_ENTRY_CONTRACT_NEGOTIATION_ID, negotiationId);
    assertOptional(json, EDR_ENTRY_PARTICIPANT_CONTEXT_ID, participantContextId);
    verifyNoInteractions(context);
  }

  @Test
  void transformer_mapsEdrEntryToJsonObject() {
    assertEquals(EndpointDataReferenceEntry.class, transformer.getInputType());
    assertEquals(JsonObject.class, transformer.getOutputType());
  }

  private static void assertOptional(JsonObject json, String key, String expected) {
    if (expected == null) {
      assertFalse(json.containsKey(key), key + " must be omitted when null");
    } else {
      assertEquals(expected, json.getString(key));
    }
  }
}
