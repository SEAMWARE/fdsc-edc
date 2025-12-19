package org.seamware.edc.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.edc.policy.model.Policy;
import org.seamware.tmforum.quote.model.QuoteItemVO;

import java.net.URI;

public class ExtendableQuoteItemVO extends QuoteItemVO {


    {
        setAtSchemaLocation(URI.create("https://raw.githubusercontent.com/wistefan/edc-dsc/refs/heads/init/quote-item.json"));
    }

    @JsonProperty("policy")
    private Policy policy;

    /**
     * Corresponds to the (dsp)offer id
     */
    @JsonProperty("externalId")
    private String externalId;

    /**
     * Corresponds to the (dsp) dataset id included in the corresponding offer
     */
    @JsonProperty("datasetId")
    private String datasetId;

    public Policy getPolicy() {
        return policy;
    }

    public ExtendableQuoteItemVO setPolicy(Policy policy) {
        this.policy = policy;
        return this;
    }

    public String getExternalId() {
        return externalId;
    }

    public ExtendableQuoteItemVO setExternalId(String externalId) {
        this.externalId = externalId;
        return this;
    }

    public String getDatasetId() {
        return datasetId;
    }

    public ExtendableQuoteItemVO setDatasetId(String datasetId) {
        this.datasetId = datasetId;
        return this;
    }
}
