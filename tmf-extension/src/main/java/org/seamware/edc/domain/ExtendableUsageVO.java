package org.seamware.edc.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.Nullable;
import org.seamware.edc.SchemaBaseUriHolder;
import org.seamware.tmforum.usage.model.UsageVO;

import java.net.URI;

import static org.seamware.edc.domain.ExtendableUsageCreateVO.USAGE_SCHEMA;

public class ExtendableUsageVO extends UsageVO {
    
    @Override
    public @Nullable URI getAtSchemaLocation() {
        URI current = super.getAtSchemaLocation();
        if (current == null) {
            URI baseUri = SchemaBaseUriHolder.get(); // configurable
            URI resolved = baseUri.resolve(USAGE_SCHEMA);
            setAtSchemaLocation(resolved);
            return resolved;
        }
        return current;
    }

    @JsonProperty("externalId")
    private String externalId;

    @JsonProperty("transferState")
    private String transferState;

    public String getExternalId() {
        return externalId;
    }

    public ExtendableUsageVO setExternalId(String externalId) {
        this.externalId = externalId;
        return this;
    }

    public String getTransferState() {
        return transferState;
    }

    public ExtendableUsageVO setTransferState(String transferState) {
        this.transferState = transferState;
        return this;
    }
}
