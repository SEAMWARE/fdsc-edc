package org.seamware.edc.domain;

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.Nullable;
import org.seamware.edc.SchemaBaseUriHolder;
import org.seamware.tmforum.agreement.model.AgreementCreateVO;

import java.net.URI;

import static org.seamware.edc.TMFContractNegotiationExtension.SCHEMA_BASE_URI_PROP;
import static org.seamware.edc.domain.ExtendableAgreementVO.AGREEMENT_JSON;

public class ExtendableAgreementCreateVO extends AgreementCreateVO {

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

    public ExtendableAgreementCreateVO setNegotiationId(String negotiationId) {
        this.negotiationId = negotiationId;
        return this;
    }

    public String getExternalId() {
        return externalId;
    }

    public ExtendableAgreementCreateVO setExternalId(String externalId) {
        this.externalId = externalId;
        return this;
    }
}
