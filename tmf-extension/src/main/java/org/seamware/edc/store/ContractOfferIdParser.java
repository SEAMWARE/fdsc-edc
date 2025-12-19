package org.seamware.edc.store;

import org.eclipse.edc.connector.controlplane.contract.spi.ContractOfferId;
import org.eclipse.edc.spi.result.Result;

import java.util.Base64;

import static java.lang.String.format;

public class ContractOfferIdParser {


    public static Result<ContractOfferWithUid> parseId(String id) {

        if (id == null) {
            return Result.failure("id cannot be null");
        }

        var parts = id.split(":");
        if (parts.length != 3) {
            return Result.failure(format("contract id should be in the form [definition-id]:[asset-id]:[UUID] but it was %s", id));
        }

        var definitionIdPart = parts[0];
        var assetIdPart = parts[1];
        var uuidPart = parts[2];

        var definitionId = decodeSafely(definitionIdPart);
        var assetId = decodeSafely(assetIdPart);
        var uuid = decodeSafely(uuidPart);

        var contractId = (definitionId == null || assetId == null || uuid == null)
                ? new ContractOfferWithUid(ContractOfferId.parseId(id).getContent(), uuidPart)
                : new ContractOfferWithUid(ContractOfferId.parseId(id).getContent(), uuid);

        return Result.success(contractId);
    }


    public static String toIdString(String definitionId, String assetId, String uuid) {
        Base64.Encoder encoder = Base64.getEncoder();
        return encoder.encodeToString(definitionId.getBytes()) + ":" + encoder.encodeToString(assetId.getBytes()) + ":" + encoder.encodeToString(uuid.getBytes());
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
