package org.seamware.edc.domain;

public class ContractNegotiationState {

    private boolean isPending;
    private boolean isLeased;
    private String state;
    private String correlationId;
    private String counterPartyAddress;

    public boolean isPending() {
        return isPending;
    }

    public String getState() {
        return state;
    }

    public ContractNegotiationState setState(String state) {
        this.state = state;
        return this;
    }

    public ContractNegotiationState setPending(boolean pending) {
        isPending = pending;
        return this;
    }

    public boolean isLeased() {
        return isLeased;
    }

    public ContractNegotiationState setLeased(boolean leased) {
        isLeased = leased;
        return this;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public ContractNegotiationState setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
        return this;
    }

    public String getCounterPartyAddress() {
        return counterPartyAddress;
    }

    public ContractNegotiationState setCounterPartyAddress(String counterPartyAddress) {
        this.counterPartyAddress = counterPartyAddress;
        return this;
    }
}
