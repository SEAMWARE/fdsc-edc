package org.seamware.edc.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.Nullable;
import org.seamware.edc.SchemaBaseUriHolder;
import org.seamware.tmforum.agreement.model.AgreementVO;

import java.net.URI;

public class ExtendableAgreementVO extends AgreementVO {

    protected static final String AGREEMENT_JSON = "agreement.json";

    @Override
    public @Nullable URI getAtSchemaLocation() {
        URI current = super.getAtSchemaLocation();
        if (current == null) {
            URI baseUri = SchemaBaseUriHolder.get(); // configurable
            URI resolved = baseUri.resolve(AGREEMENT_JSON);
            setAtSchemaLocation(resolved);
            return resolved;
        }
        return current;
    }

    @JsonProperty("externalId")
    private String externalId;

    @JsonProperty("negotiationId")
    private String negotiationId;

    public String getNegotiationId() {
        return negotiationId;
    }

    public ExtendableAgreementVO setNegotiationId(String negotiationId) {
        this.negotiationId = negotiationId;
        return this;
    }

    public String getExternalId() {
        return externalId;
    }

    public ExtendableAgreementVO setExternalId(String externalId) {
        this.externalId = externalId;
        return this;
    }

}
