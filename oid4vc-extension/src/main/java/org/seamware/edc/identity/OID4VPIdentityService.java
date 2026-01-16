package org.seamware.edc.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import io.github.wistefan.oid4vp.OID4VPClient;
import io.github.wistefan.oid4vp.config.RequestParameters;
import io.github.wistefan.oid4vp.exception.Oid4VPException;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.iam.TokenParameters;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.iam.VerificationContext;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;

import java.net.URI;
import java.text.ParseException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Handle identity in an OID4VC based security ecosystem.
 * 1. Authenticate via OID4VP
 * 2. Accept and decode JWTs already verified by an apigateways
 */
public class OID4VPIdentityService implements org.eclipse.edc.spi.iam.IdentityService {

    private static final String AUD_PARAMETER = "aud";

    private final Monitor monitor;
    private final ObjectMapper objectMapper;
    private final OID4VPClient oid4VPClient;
    private final String clientId;
    private final Set<String> scope;

    public OID4VPIdentityService(Monitor monitor, ObjectMapper objectMapper, OID4VPClient oid4VPClient, String clientId, Set<String> scope) {
        this.monitor = monitor;
        this.objectMapper = objectMapper;
        this.oid4VPClient = oid4VPClient;
        this.clientId = clientId;
        this.scope = scope;
    }

    @Override
    public Result<TokenRepresentation> obtainClientCredentials(TokenParameters tokenParameters) {
        monitor.debug("Try to obtain credential via OID4VP.");

        String aud = tokenParameters.getStringClaim(AUD_PARAMETER);
        try {
            URI audURI = URI.create(aud);
            RequestParameters requestParameters = new RequestParameters(URI.create(audURI.getScheme() + "://" + audURI.getAuthority()), "", clientId, scope);
            return oid4VPClient.getAccessToken(requestParameters)
                    .thenApply(tr -> TokenRepresentation.Builder.newInstance()
                            .token(tr.getAccessToken())
                            .expiresIn(tr.getExpiresIn())
                            .build())
                    .thenApply(Result::success)
                    .get();
        } catch (Oid4VPException | InterruptedException | ExecutionException e) {
            monitor.warning("Was not able to successfully get a token through OID4VP.", e);
            return Result.failure("Was not able to successfully get a token through OID4VP.");
        } catch (Exception e) {
            monitor.severe("Failed to request a token.", e);
            return Result.failure("Failed to request a token.");
        }
    }

    /**
     * In case of the FIWARE Dataspace Connector, the EDC endpoints are ALWAYS protected by the PEP(e.g. Apisix) which is responsible for verifying the token.
     * The method only has to do the decoding.
     */
    @Override
    public Result<ClaimToken> verifyJwtToken(TokenRepresentation tokenRepresentation, VerificationContext verificationContext) {
        try {
            JWT jwt = JWTParser.parse(tokenRepresentation.getToken());

            Map<String, Object> claims = jwt.getJWTClaimsSet().getClaims();
            ClaimToken.Builder tokenBuilder = ClaimToken.Builder.newInstance();
            claims.forEach(tokenBuilder::claim);
            return Result.success(tokenBuilder.build());
        } catch (ParseException e) {
            return Result.failure("Was not able to read the token " + e.getMessage());
        }
    }
}
