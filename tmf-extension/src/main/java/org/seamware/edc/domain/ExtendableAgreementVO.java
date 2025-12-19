package org.seamware.edc.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.seamware.tmforum.agreement.model.AgreementVO;

import java.net.URI;

public class ExtendableAgreementVO extends AgreementVO {
    {
        setAtSchemaLocation(URI.create("https://raw.githubusercontent.com/wistefan/edc-dsc/refs/heads/init/external-id.json"));
    }


    @JsonProperty("externalId")
    private String externalId;


    public String getExternalId() {
        return externalId;
    }

    public ExtendableAgreementVO setExternalId(String externalId) {
        this.externalId = externalId;
        return this;
    }
}
