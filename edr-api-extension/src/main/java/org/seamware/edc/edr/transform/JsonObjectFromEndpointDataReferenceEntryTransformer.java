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
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;

import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import org.eclipse.edc.edr.spi.types.EndpointDataReferenceEntry;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Serializes an EDR entry for the v5beta management API: the upstream v3 shape plus the owning
 * {@code participantContextId}. Optional fields that are null are omitted.
 */
public class JsonObjectFromEndpointDataReferenceEntryTransformer
    extends AbstractJsonLdTransformer<EndpointDataReferenceEntry, JsonObject> {

  public static final String PARTICIPANT_CONTEXT_ID = "participantContextId";
  public static final String EDR_ENTRY_PARTICIPANT_CONTEXT_ID =
      EDC_NAMESPACE + PARTICIPANT_CONTEXT_ID;

  private final JsonBuilderFactory jsonFactory;

  public JsonObjectFromEndpointDataReferenceEntryTransformer(JsonBuilderFactory jsonFactory) {
    super(EndpointDataReferenceEntry.class, JsonObject.class);
    this.jsonFactory = jsonFactory;
  }

  @Override
  public @Nullable JsonObject transform(
      @NotNull EndpointDataReferenceEntry entry, @NotNull TransformerContext context) {
    var builder =
        jsonFactory
            .createObjectBuilder()
            .add(ID, entry.getId())
            .add(TYPE, EDR_ENTRY_TYPE)
            .add(EDR_ENTRY_PROVIDER_ID, entry.getProviderId())
            .add(EDR_ENTRY_ASSET_ID, entry.getAssetId())
            .add(EDR_ENTRY_AGREEMENT_ID, entry.getAgreementId())
            .add(EDR_ENTRY_TRANSFER_PROCESS_ID, entry.getTransferProcessId())
            .add(EDR_ENTRY_CREATED_AT, entry.getCreatedAt());
    addIfNotNull(entry.getContractNegotiationId(), EDR_ENTRY_CONTRACT_NEGOTIATION_ID, builder);
    addIfNotNull(entry.getParticipantContextId(), EDR_ENTRY_PARTICIPANT_CONTEXT_ID, builder);
    return builder.build();
  }
}
