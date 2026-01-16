package org.seamware.edc.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.Nullable;
import org.seamware.edc.SchemaBaseUriHolder;
import org.seamware.tmforum.productinventory.model.ProductVO;

import java.net.URI;

public class ExtendableProduct extends ProductVO {

    public static final String EXTERNAL_ID_SCHEMA = "external-id.json";

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

    /**
     * Corresponds to data-set id
     */
    @JsonProperty("externalId")
    private String externalId;

    public String getExternalId() {
        return externalId;
    }

    public ExtendableProduct setExternalId(String externalId) {
        this.externalId = externalId;
        return this;
    }
}
