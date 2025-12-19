package org.seamware.edc.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.protocol.spi.DefaultParticipantIdExtractionFunction;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.monitor.Monitor;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public class TestParticipantIdExtractionFunction implements DefaultParticipantIdExtractionFunction {

    private final Monitor monitor;

    public TestParticipantIdExtractionFunction(Monitor monitor) {
        this.monitor = monitor;
    }

    @Override
    public String apply(ClaimToken claimToken) {
        try {
            monitor.warning("The token " + new ObjectMapper().writeValueAsString(claimToken));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return claimToken.getStringClaim("id");
    }

    @Override
    public @NotNull <V> Function<V, String> compose(@NotNull Function<? super V, ? extends ClaimToken> before) {
        return DefaultParticipantIdExtractionFunction.super.compose(before);
    }

    @Override
    public @NotNull <V> Function<ClaimToken, V> andThen(@NotNull Function<? super String, ? extends V> after) {
        return DefaultParticipantIdExtractionFunction.super.andThen(after);
    }
}
