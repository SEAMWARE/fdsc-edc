package org.seamware.edc.domain;

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.Nullable;
import org.seamware.edc.SchemaBaseUriHolder;
import org.seamware.tmforum.productcatalog.model.ProductOfferingVO;
import org.seamware.tmforum.productcatalog.model.ProductSpecificationRefVO;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.seamware.edc.TMFContractNegotiationExtension.SCHEMA_BASE_URI_PROP;
import static org.seamware.edc.domain.ExtendableProduct.EXTERNAL_ID_SCHEMA;

public class ExtendableProductOffering extends ProductOfferingVO {


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

    private ExtendableProductSpecificationRef productSpecification;

    private List<ExtendableProductOfferingTerm> productOfferingTerm = new ArrayList<>();

    @javax.annotation.Nullable
    @JsonProperty(JSON_PROPERTY_PRODUCT_OFFERING_TERM)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public List<ExtendableProductOfferingTerm> getExtendableProductOfferingTerm() {
        return productOfferingTerm;
    }


    @JsonProperty(JSON_PROPERTY_PRODUCT_OFFERING_TERM)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public void setExtendableProductOfferingTerm(@javax.annotation.Nonnull List<ExtendableProductOfferingTerm> productOfferingTerm) {
        this.productOfferingTerm = productOfferingTerm;
    }

    @javax.annotation.Nullable
    @JsonProperty(JSON_PROPERTY_PRODUCT_SPECIFICATION)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public ExtendableProductSpecificationRef getExtendableProductSpecification() {
        return productSpecification;
    }


    @JsonProperty(JSON_PROPERTY_PRODUCT_SPECIFICATION)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public void setExtendableProductSpecification(@javax.annotation.Nullable ExtendableProductSpecificationRef productSpecification) {
        this.productSpecification = productSpecification;
    }


    public String getExternalId() {
        return externalId;
    }

    public ExtendableProductOffering setExternalId(String externalId) {
        this.externalId = externalId;
        return this;
    }
}
