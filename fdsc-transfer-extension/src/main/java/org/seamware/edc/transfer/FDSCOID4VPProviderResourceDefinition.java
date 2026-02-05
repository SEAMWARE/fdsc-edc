package org.seamware.edc.transfer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.ResourceDefinition;

import static java.util.Objects.requireNonNull;


@JsonTypeName("dataspaceconnector:fdscproviderresourcedefinition")
@JsonDeserialize(builder = FDSCProviderResourceDefinition.Builder.class)
public class FDSCProviderResourceDefinition extends ResourceDefinition {

    private String assetId;

    public String getAssetId() {
        return assetId;
    }

    @Override
    public Builder toBuilder() {
        return initializeBuilder(new Builder())
                .assetId(assetId);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder extends ResourceDefinition.Builder<FDSCProviderResourceDefinition, Builder> {

        private Builder() {
            super(new FDSCProviderResourceDefinition());
        }

        @JsonCreator
        public static Builder newInstance() {
            return new Builder();
        }

        public Builder assetId(String assetId) {
            resourceDefinition.assetId = assetId;
            return this;
        }

        @Override
        protected void verify() {
            super.verify();
            requireNonNull(resourceDefinition.assetId, "assetId");
        }

    }

}
