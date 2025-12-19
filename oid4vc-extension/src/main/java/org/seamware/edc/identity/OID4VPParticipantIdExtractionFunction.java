package org.seamware.edc.identity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.protocol.spi.DefaultParticipantIdExtractionFunction;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.monitor.Monitor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class OID4VPParticipantIdExtractionFunction implements DefaultParticipantIdExtractionFunction {

    private final Monitor monitor;
    private final String issuerClaim;

    public OID4VPParticipantIdExtractionFunction(Monitor monitor, String issuerClaim) {
        this.monitor = monitor;
        this.issuerClaim = issuerClaim;
    }

    @Override
    public String apply(ClaimToken claimToken) {

        Map<String, Object> claims = claimToken.getClaims();
        List<String> claimPath = Arrays.asList(issuerClaim.split("\\."));
        Map<String, Object> currentClaims = claims;
        for (int i = 0; i < claimPath.size(); i++) {
            String currentPath = claimPath.get(i);
            if (!currentClaims.containsKey(currentPath)) {
                throw new IllegalArgumentException("Was not able to extract the issuer.");
            }
            Object currentValue = currentClaims.get(currentPath);
            if (i == claimPath.size() - 1 && currentValue instanceof String issuerString) {
                monitor.info(String.format("The participant is %s.", issuerString));
                return issuerString;
            }
            if (i < claimPath.size() - 1 && currentValue instanceof Map<?, ?> claimMap) {
                currentClaims = (Map<String, Object>) claimMap;
            }
        }
        throw new IllegalArgumentException("Was not able to extract the issuer.");
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
