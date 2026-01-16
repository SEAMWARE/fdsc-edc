package org.seamware.edc.domain;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.Nullable;
import org.seamware.edc.SchemaBaseUriHolder;
import org.seamware.tmforum.quote.model.ProductSpecificationRefVO;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.seamware.edc.domain.ExtendableProduct.EXTERNAL_ID_SCHEMA;

public class ExtendableProductSpecificationRef extends ProductSpecificationRefVO {

    @Override
    public @Nullable URI getAtSchemaLocation() {
        URI current = super.getAtSchemaLocation();
        if (current == null) {
            URI baseUri = SchemaBaseUriHolder.get(); // configurable
            URI resolved = baseUri.resolve(EXTERNAL_ID_SCHEMA);
            setAtSchemaLocation(resolved);
            return resolved;
        }
        return current;
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
