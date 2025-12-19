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

    public TestIdentityService(Monitor monitor, ObjectMapper objectMapper) {
        this.monitor = monitor;
        this.objectMapper = objectMapper;
    }

    @Override
    public Result<TokenRepresentation> obtainClientCredentials(TokenParameters tokenParameters) {
        monitor.warning("Tried to obtain credential.");
        return Result.success(TokenRepresentation.Builder.newInstance().token("1234").build());
    }

    @Override
    public Result<ClaimToken> verifyJwtToken(TokenRepresentation tokenRepresentation, VerificationContext verificationContext) {

        try {
            Map<String, String> claims = objectMapper.readValue(tokenRepresentation.getToken(), new TypeReference<Map<String, String>>() {
            });
            ClaimToken.Builder tokenBuilder = ClaimToken.Builder.newInstance();
            claims.forEach(tokenBuilder::claim);
            return Result.success(tokenBuilder.build());
        } catch (JsonProcessingException e) {
            return Result.failure("Was not able to read the token " + e.getMessage());
        }
    }
}
