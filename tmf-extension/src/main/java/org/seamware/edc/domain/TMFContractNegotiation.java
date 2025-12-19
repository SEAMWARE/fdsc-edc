package org.seamware.edc.domain;

import org.eclipse.edc.spi.types.domain.callback.CallbackAddress;

import java.util.ArrayList;
import java.util.List;

public class TMFContractNegotiation {

    private List<TMFCallbackAddress> callbackAddresses = new ArrayList();
    private String counterPartyId;
    private String counterPartyAddress;
    private String protocol;

    public String getProtocol() {
        return protocol;
    }

    public TMFContractNegotiation setProtocol(String protocol) {
        this.protocol = protocol;
        return this;
    }

    public String getCounterPartyAddress() {
        return counterPartyAddress;
    }

    public TMFContractNegotiation setCounterPartyAddress(String counterPartyAddress) {
        this.counterPartyAddress = counterPartyAddress;
        return this;
    }

    public String getCounterPartyId() {
        return counterPartyId;
    }

    public TMFContractNegotiation setCounterPartyId(String counterPartyId) {
        this.counterPartyId = counterPartyId;
        return this;
    }

    public List<TMFCallbackAddress> getCallbackAddresses() {
        return callbackAddresses;
    }

    public TMFContractNegotiation setCallbackAddresses(List<TMFCallbackAddress> callbackAddresses) {
        this.callbackAddresses = callbackAddresses;
        return this;
    }
}
