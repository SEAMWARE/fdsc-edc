package org.seamware.edc.transfer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.ProvisionedResource;

public class FDSCProvisionedResource extends ProvisionedResource {

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder extends ProvisionedResource.Builder<FDSCProvisionedResource, Builder> {

        private Builder() {
            super(new FDSCProvisionedResource());
        }

        @JsonCreator
        public static Builder newInstance() {
            return new Builder();
        }

        @Override
        public Builder transferProcessId(String transferProcessId) {
            provisionedResource.transferProcessId = transferProcessId;
            return this;
        }

    }
}
