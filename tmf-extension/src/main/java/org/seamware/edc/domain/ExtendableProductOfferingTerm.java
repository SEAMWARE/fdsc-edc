package org.seamware.edc.domain;

import com.fasterxml.jackson.annotation.*;
import org.jetbrains.annotations.Nullable;
import org.seamware.edc.SchemaBaseUriHolder;
import org.seamware.tmforum.productcatalog.model.ProductOfferingTermVO;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.seamware.edc.TMFContractNegotiationExtension.SCHEMA_BASE_URI_PROP;
import static org.seamware.edc.domain.ExtendableProduct.EXTERNAL_ID_SCHEMA;

public class ExtendableProductOfferingTerm extends ProductOfferingTermVO {

    protected static final String CONTRACT_DEFINITION_SCHEMA = "contract-definition.json";

    private Map<String, Object> additionalProperties = new HashMap<>();

    @Override
    public @Nullable URI getAtSchemaLocation() {
        URI current = super.getAtSchemaLocation();
        if (current == null) {
            URI baseUri = SchemaBaseUriHolder.get(); // configurable
            URI resolved = baseUri.resolve(CONTRACT_DEFINITION_SCHEMA);
            setAtSchemaLocation(resolved);
            return resolved;
        }
        return current;
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
