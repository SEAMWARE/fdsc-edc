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

import static java.lang.String.format;

import java.util.Base64;
import org.eclipse.edc.connector.controlplane.contract.spi.ContractOfferId;
import org.eclipse.edc.spi.result.Result;

public class ContractOfferIdParser {

  public static Result<ContractOfferWithUid> parseId(String id) {

    if (id == null) {
      return Result.failure("id cannot be null");
    }

    var parts = id.split(":");
    if (parts.length != 3) {
      return Result.failure(
          format(
              "contract id should be in the form [definition-id]:[asset-id]:[UUID] but it was %s",
              id));
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
