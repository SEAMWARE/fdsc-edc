package org.seamware.edc.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.protocol.spi.DefaultParticipantIdExtractionFunction;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.runtime.metamodel.annotation.Requires;
import org.eclipse.edc.spi.iam.*;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;

import java.util.Map;

public class TestIdentityService implements org.eclipse.edc.spi.iam.IdentityService {


    private final Monitor monitor;
    private final ObjectMapper objectMapper;
    private final String participantId;

    public TestIdentityService(Monitor monitor, ObjectMapper objectMapper, String participantId) {
        this.monitor = monitor;
        this.objectMapper = objectMapper;
        this.participantId = participantId;
    }

    @Override
    public Result<TokenRepresentation> obtainClientCredentials(TokenParameters tokenParameters) {
        monitor.debug("Obtain test credential.");

        return Result.success(TokenRepresentation.Builder.newInstance().token(String.format("{\"id\": \"%s\"}", participantId)).build());
    }

    @Override
    public Result<ClaimToken> verifyJwtToken(TokenRepresentation tokenRepresentation, VerificationContext verificationContext) {

        try {
            monitor.info("The token: " + tokenRepresentation.getToken());

            Map<String, String> claims = objectMapper.readValue(tokenRepresentation.getToken(), new TypeReference<Map<String, String>>() {
            });
            ClaimToken.Builder tokenBuilder = ClaimToken.Builder.newInstance();
            claims.forEach(tokenBuilder::claim);
            return Result.success(tokenBuilder.build());
        } catch (JsonProcessingException e) {
            return Result.failure("[TestIdentityService] Was not able to read the token " + e.getMessage());
        }
    }
}
