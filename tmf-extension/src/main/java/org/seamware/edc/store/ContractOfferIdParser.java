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
package org.seamware.edc.store;

import java.util.Base64;
import org.eclipse.edc.connector.controlplane.contract.spi.ContractOfferId;
import org.eclipse.edc.spi.result.Result;

public class ContractOfferIdParser {

  /**
   * Parses a contract offer ID. Supports the standard EDC format {@code
   * [definition-id]:[asset-id]:[UUID]} as well as non-standard IDs (e.g. from the DSP TCK). For
   * non-standard IDs the raw value is used as both the contract offer ID and UUID.
   */
  public static Result<ContractOfferWithUid> parseId(String id) {

    if (id == null || id.isEmpty()) {
      return Result.failure("id cannot be null or empty");
    }

    var parts = id.split(":");
    if (parts.length != 3) {
      // Non-standard format (e.g. TCK offer IDs like "offerACNC0101"):
      // Build a synthetic ContractOfferId using the raw ID for all components
      // so that consumer negotiations can proceed.
      return Result.success(createNonStandardOfferWithUid(id));
    }

    var definitionIdPart = parts[0];
    var assetIdPart = parts[1];
    var uuidPart = parts[2];

    var definitionId = decodeSafely(definitionIdPart);
    var assetId = decodeSafely(assetIdPart);
    var uuid = decodeSafely(uuidPart);

    var contractId =
        (definitionId == null || assetId == null || uuid == null)
            ? new ContractOfferWithUid(ContractOfferId.parseId(id).getContent(), uuidPart)
            : new ContractOfferWithUid(ContractOfferId.parseId(id).getContent(), uuid);

    return Result.success(contractId);
  }

  /**
   * Creates a {@link ContractOfferWithUid} for offer IDs that do not follow the standard EDC {@code
   * [definition-id]:[asset-id]:[UUID]} format. The raw ID is Base64-encoded and used for all three
   * components of the synthetic {@link ContractOfferId}, so the decoded definition/asset/UUID parts
   * all resolve back to the original raw ID.
   */
  private static ContractOfferWithUid createNonStandardOfferWithUid(String rawId) {
    var encoded = Base64.getEncoder().encodeToString(rawId.getBytes());
    var syntheticId = encoded + ":" + encoded + ":" + encoded;
    return new ContractOfferWithUid(ContractOfferId.parseId(syntheticId).getContent(), rawId);
  }

  public static String toIdString(String definitionId, String assetId, String uuid) {
    Base64.Encoder encoder = Base64.getEncoder();
    return encoder.encodeToString(definitionId.getBytes())
        + ":"
        + encoder.encodeToString(assetId.getBytes())
        + ":"
        + encoder.encodeToString(uuid.getBytes());
  }

  private static String decodeSafely(String base64string) {
    try {
      return new String(Base64.getDecoder().decode(base64string));
    } catch (IllegalArgumentException var2) {
      return null;
    }
  }

  public record ContractOfferWithUid(ContractOfferId contractOfferId, String uuid) {

    public String asDecoded() {
      return contractOfferId.definitionPart() + ":" + contractOfferId.assetIdPart() + ":" + uuid;
    }
  }
}
