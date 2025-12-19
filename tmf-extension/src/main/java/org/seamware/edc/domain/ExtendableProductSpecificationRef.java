package org.seamware.edc.domain;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.seamware.tmforum.quote.model.ProductSpecificationRefVO;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class ExtendableProductSpecificationRef extends ProductSpecificationRefVO {

    {
        setAtSchemaLocation(URI.create("https://raw.githubusercontent.com/wistefan/edc-dsc/refs/heads/init/external-id.json"));
    }


    @JsonProperty("externalId")
    private String externalId;

    private Map<String, Object> unknownProperties = new HashMap<>();

    @JsonAnyGetter
    public Map<String, Object> getUnknownProperties() {
        return this.unknownProperties;
    }

    @JsonAnySetter
    public void setUnknownProperties(String propertyKey, Object value) {
        if (this.unknownProperties == null) {
            this.unknownProperties = new HashMap();
        }

        this.unknownProperties.put(propertyKey, value);
    }


    public String getExternalId() {
        return externalId;
    }

    public ExtendableProductSpecificationRef setExternalId(String externalId) {
        this.externalId = externalId;
        return this;
    }

}
