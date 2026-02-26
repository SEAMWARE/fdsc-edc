package org.seamware.edc.identity;

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import io.github.wistefan.oid4vp.OID4VPClient;
import io.github.wistefan.oid4vp.config.RequestParameters;
import io.github.wistefan.oid4vp.exception.Oid4VPException;
import io.github.wistefan.oid4vp.model.TokenResponse;
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
    private static final String BEARER_PREFIX = "Bearer ";

    private final Monitor monitor;
    private final TokenProvider tokenProvider;
    private final String clientId;
    private final Set<String> scope;

    public OID4VPIdentityService(Monitor monitor, TokenProvider tokenProvider, String clientId, Set<String> scope) {
        this.monitor = monitor;
        this.tokenProvider = tokenProvider;
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
            return tokenProvider.getAccessToken(requestParameters)
                    .thenApply(tr -> {
                        if (!validTokenResponse(tr)) {
                            throw new Oid4VPException("Token response does not contain mandatory fields.");
                        }
                        return tr;
                    })
                    .thenApply(tr -> TokenRepresentation.Builder.newInstance()
                            .token(BEARER_PREFIX + tr.getAccessToken())
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

    private boolean validTokenResponse(TokenResponse tokenResponse) {
        return tokenResponse.getAccessToken() != null && !tokenResponse.getAccessToken().isEmpty() && tokenResponse.getExpiresIn() > 0;
    }

    /**
     * In case of the FIWARE Dataspace Connector, the EDC endpoints are ALWAYS protected by the PEP(e.g. Apisix) which is responsible for verifying the token.
     * The method only has to do the decoding.
     */
    @Override
    public Result<ClaimToken> verifyJwtToken(TokenRepresentation tokenRepresentation, VerificationContext verificationContext) {
        try {
            String plainToken = tokenRepresentation.getToken().replaceFirst(BEARER_PREFIX, "");
            JWT jwt = JWTParser.parse(plainToken);

            Map<String, Object> claims = jwt.getJWTClaimsSet().getClaims();
            ClaimToken.Builder tokenBuilder = ClaimToken.Builder.newInstance();
            claims.forEach(tokenBuilder::claim);
            return Result.success(tokenBuilder.build());
        } catch (Exception e) {
            // all exceptions need to be signaled as Result.failure
            return Result.failure("[OID4VPIdentityService] Was not able to read the token " + e.getMessage() + " '");
        }
    }
}
