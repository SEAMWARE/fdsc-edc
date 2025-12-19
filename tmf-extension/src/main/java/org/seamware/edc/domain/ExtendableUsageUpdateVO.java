package org.seamware.edc.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.seamware.tmforum.usage.model.UsageUpdateVO;
import org.seamware.tmforum.usage.model.UsageVO;

import java.net.URI;

public class ExtendableUsageUpdateVO extends UsageUpdateVO {


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

    public ExtendableUsageUpdateVO setExternalId(String externalId) {
        this.externalId = externalId;
        return this;
    }

    public String getTransferState() {
        return transferState;
    }

    public ExtendableUsageUpdateVO setTransferState(String transferState) {
        this.transferState = transferState;
        return this;
    }
}
