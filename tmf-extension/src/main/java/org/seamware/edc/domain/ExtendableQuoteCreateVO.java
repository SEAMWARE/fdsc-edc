package org.seamware.edc.domain;

import com.fasterxml.jackson.annotation.*;
import org.seamware.tmforum.quote.model.QuoteCreateVO;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class ExtendableQuoteCreateVO extends QuoteCreateVO {

    {
        setAtSchemaLocation(URI.create("https://raw.githubusercontent.com/wistefan/edc-dsc/refs/heads/init/contract-negotiation.json"));
    }

    @JsonProperty("contractNegotiation")
    private ContractNegotiationState contractNegotiationState;


    @javax.annotation.Nonnull
    private List<ExtendableQuoteItemVO> quoteItem = new ArrayList<>();


    @javax.annotation.Nonnull
    @JsonProperty(JSON_PROPERTY_QUOTE_ITEM)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public List<ExtendableQuoteItemVO> getExtendableQuoteItem() {
        return quoteItem;
    }


    @JsonProperty(JSON_PROPERTY_QUOTE_ITEM)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public void setExtendableQuoteItem(@javax.annotation.Nonnull List<ExtendableQuoteItemVO> quoteItem) {
        this.quoteItem = quoteItem;
    }

    public ContractNegotiationState getContractNegotiationState() {
        return contractNegotiationState;
    }

    public ExtendableQuoteCreateVO setContractNegotiationState(ContractNegotiationState contractNegotiationState) {
        this.contractNegotiationState = contractNegotiationState;
        return this;
    }

}
