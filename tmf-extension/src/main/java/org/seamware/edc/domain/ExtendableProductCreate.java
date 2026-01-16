package org.seamware.edc.domain;

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.Nullable;
import org.seamware.edc.SchemaBaseUriHolder;
import org.seamware.tmforum.productinventory.model.ProductCreateVO;

import java.net.URI;

import static org.seamware.edc.TMFContractNegotiationExtension.SCHEMA_BASE_URI_PROP;
import static org.seamware.edc.domain.ExtendableProduct.EXTERNAL_ID_SCHEMA;

public class ExtendableProductCreate extends ProductCreateVO {


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

    public ExtendableProductCreate setExternalId(String externalId) {
        this.externalId = externalId;
        return this;
    }
}
