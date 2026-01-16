package org.seamware.edc.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.Nullable;
import org.seamware.edc.SchemaBaseUriHolder;
import org.seamware.tmforum.productcatalog.model.ProductSpecificationVO;

import java.net.URI;

import static org.seamware.edc.domain.ExtendableProduct.EXTERNAL_ID_SCHEMA;

public class ExtendableProductSpecification extends ProductSpecificationVO {


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


    public String getExternalId() {
        return externalId;
    }

    public ExtendableProductSpecification setExternalId(String externalId) {
        this.externalId = externalId;
        return this;
    }
}
