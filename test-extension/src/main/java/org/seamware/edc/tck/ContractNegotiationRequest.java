package org.seamware.edc.tck;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Initiates a negotiation request.
 */
public record ContractNegotiationRequest(@JsonProperty("providerId") String providerId,
                                         @JsonProperty("connectorAddress") String connectorAddress,
                                         @JsonProperty("offerId") String offerId,
                                         @JsonProperty("datasetId") String datasetId) {
}
