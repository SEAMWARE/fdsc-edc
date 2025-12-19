package org.seamware.edc.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.seamware.tmforum.productcatalog.model.ProductOfferingVO;
import org.seamware.tmforum.productcatalog.model.ProductSpecificationRefVO;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class ExtendableProductOffering extends ProductOfferingVO {

    public static final String EXTERNAL_ID_SCHEMA = "https://raw.githubusercontent.com/wistefan/edc-dsc/refs/heads/init/external-id.json";

    {
        setAtSchemaLocation(URI.create(EXTERNAL_ID_SCHEMA));
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
