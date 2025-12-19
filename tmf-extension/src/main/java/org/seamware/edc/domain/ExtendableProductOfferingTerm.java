package org.seamware.edc.domain;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.seamware.tmforum.productcatalog.model.ProductOfferingTermVO;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class ExtendableProductOfferingTerm extends ProductOfferingTermVO {


    private Map<String, Object> additionalProperties = new HashMap<>();

    {
        setAtSchemaLocation(URI.create("https://raw.githubusercontent.com/wistefan/edc-dsc/refs/heads/init/contract-definition.json"));
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperties(String propertyKey, Object value) {
        if (this.additionalProperties == null) {
            this.additionalProperties = new HashMap();
        }

        this.additionalProperties.put(propertyKey, value);
    }

    @JsonIgnore
    public ExtendableProductOfferingTerm additionalProperties(Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties;
        return this;
    }

}
