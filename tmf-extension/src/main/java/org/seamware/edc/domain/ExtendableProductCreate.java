package org.seamware.edc.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.seamware.tmforum.productinventory.model.ProductCreateVO;

import java.net.URI;

public class ExtendableProductCreate extends ProductCreateVO {

    public static final String EXTERNAL_ID_SCHEMA = "https://raw.githubusercontent.com/wistefan/edc-dsc/refs/heads/init/external-id.json";

    {
        setAtSchemaLocation(URI.create(EXTERNAL_ID_SCHEMA));
    }

    /**
     * Corresponds to data-set id
     */
    @JsonProperty("externalId")
    private String externalId;

    public String getExternalId() {
        return externalId;
    }

    public ExtendableProductCreate setExternalId(String externalId) {
        this.externalId = externalId;
        return this;
    }
}
