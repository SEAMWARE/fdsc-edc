package org.seamware.edc.tck;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Initiates a transfer request.
 */
public record TransferProcessRequest(@JsonProperty("providerId") String providerId,
                                     @JsonProperty("connectorAddress") String connectorAddress,
                                     @JsonProperty("agreementId") String agreementId,
                                     @JsonProperty("format") String format) {
}
