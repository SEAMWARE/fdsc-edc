package org.seamware.edc.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.seamware.tmforum.usage.model.UsageCreateVO;
import org.seamware.tmforum.usage.model.UsageVO;

import java.net.URI;

public class ExtendableUsageCreateVO extends UsageCreateVO {


    {
        setAtSchemaLocation(URI.create("https://raw.githubusercontent.com/wistefan/edc-dsc/refs/heads/init/usage.json"));
    }


    @JsonProperty("externalId")
    private String externalId;

    @JsonProperty("transferState")
    private String transferState;

    public String getExternalId() {
        return externalId;
    }

    public ExtendableUsageCreateVO setExternalId(String externalId) {
        this.externalId = externalId;
        return this;
    }

    public String getTransferState() {
        return transferState;
    }

    public ExtendableUsageCreateVO setTransferState(String transferState) {
        this.transferState = transferState;
        return this;
    }
}
