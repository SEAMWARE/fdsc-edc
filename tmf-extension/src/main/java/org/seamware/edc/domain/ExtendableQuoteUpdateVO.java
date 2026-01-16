package org.seamware.edc.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.Nullable;
import org.seamware.edc.SchemaBaseUriHolder;
import org.seamware.tmforum.quote.model.QuoteUpdateVO;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.seamware.edc.domain.ExtendableQuoteCreateVO.CONTRACT_NEGOTIATION_SCHEMA;

public class ExtendableQuoteUpdateVO extends QuoteUpdateVO {

    @Override
    public @Nullable URI getAtSchemaLocation() {
        URI current = super.getAtSchemaLocation();
        if (current == null) {
            URI baseUri = SchemaBaseUriHolder.get(); // configurable
            URI resolved = baseUri.resolve(CONTRACT_NEGOTIATION_SCHEMA);
            setAtSchemaLocation(resolved);
            return resolved;
        }
        return current;
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

    public ExtendableQuoteUpdateVO setContractNegotiationState(ContractNegotiationState contractNegotiationState) {
        this.contractNegotiationState = contractNegotiationState;
        return this;
    }
}
