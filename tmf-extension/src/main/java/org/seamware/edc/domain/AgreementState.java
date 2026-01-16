package org.seamware.edc.domain;

import java.util.Arrays;

public enum AgreementState {

    IN_PROCESS("inProcess"),
    AGREED("agreed"),
    REJECTED("rejected");

    private final String value;

    AgreementState(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public AgreementState fromValue(String value) {
        return Arrays.stream(values())
                .filter(v -> v.value.equals(value))
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException(String.format("Unsupported value %s", value)));
    }
}
