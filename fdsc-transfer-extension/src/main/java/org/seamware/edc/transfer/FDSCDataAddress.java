package org.seamware.edc.transfer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.eclipse.edc.spi.types.domain.DataAddress;

public class FDSCDataAddress extends DataAddress {

    private static final String CLIENT_ID = "clientId";
    private static final String TYPE = "FDSC";

    private FDSCDataAddress() {
        super();
        this.setType(TYPE);
    }

    @JsonIgnore
    public String getClientId() {
        return getStringProperty(CLIENT_ID);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder extends DataAddress.Builder<FDSCDataAddress, Builder> {

        private Builder() {
            super(new FDSCDataAddress());
        }

        @JsonCreator
        public static Builder newInstance() {
            return new Builder();
        }

        public Builder clientId(String clientId) {
            this.property(CLIENT_ID, clientId);
            return this;
        }

        @Override
        public FDSCDataAddress build() {
            this.type(TYPE);
            return address;
        }
    }
}
