package org.seamware.edc;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.eclipse.edc.connector.dataplane.spi.DataFlow;
import org.eclipse.edc.connector.dataplane.spi.edr.EndpointDataReferenceService;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.seamware.edc.transfer.FDSCDataAddress;

import java.text.ParseException;
import java.time.Clock;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.seamware.edc.FDSCTransferControlExtension.KEY_NAME;


public class FDSCDcpEndpointDataReferenceService implements EndpointDataReferenceService {

    public static final String ENDPOINT_TYPE = "https://w3id.org/idsa/v4.1/HTTP";
    // TODO: make configurable
    private static final int EXPIRATION_MS = 300_000;
    private static final String TRANSFER_ID_CLAIM = "transferId";
    public static final String SCOPE_CLAIM = "scope";

    private final TransferConfig transferConfig;
    private final Vault vault;
    private final String issuerId;
    private final Clock clock;


    public FDSCDcpEndpointDataReferenceService(TransferConfig transferConfig, Vault vault, String issuerId, Clock clock) {
        this.transferConfig = transferConfig;
        this.vault = vault;
        this.issuerId = issuerId;
        this.clock = clock;
    }

    @Override
    public Result<DataAddress> createEndpointDataReference(DataFlow dataFlow) {
        Optional<String> optionalJwkJson = Optional.ofNullable(vault.resolveSecret(KEY_NAME));
        if (optionalJwkJson.isEmpty()) {
            return Result.failure("No signing key available.");
        }
        try {

            JWK jwk = JWK.parse(optionalJwkJson.get());
            JWSSigner jwsSigner = new RSASSASigner(jwk.toRSAKey());
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(jwk.getKeyID())  // MUST match JWKS "kid"
                    .type(JOSEObjectType.JWT)
                    .build();

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(issuerId)
                    .audience(issuerId)
                    .claim(SCOPE_CLAIM, dataFlow.getId())
                    .issueTime(new Date())
                    .expirationTime(new Date(clock.millis() + EXPIRATION_MS))
                    .jwtID(UUID.randomUUID().toString())
                    .build();
            SignedJWT signedJWT = new SignedJWT(header, claims);
            signedJWT.sign(jwsSigner);
            var fdscDataAddressBuilder =
                    FDSCDataAddress.Builder.newInstance()
                            .clientId(dataFlow.getId())
                            .type(ENDPOINT_TYPE)
                            .property(EDC_NAMESPACE + "token", signedJWT.serialize())
                            .property(EDC_NAMESPACE + "tokenType", "bearer")
                            .property(EDC_NAMESPACE + "endpoint", "http://" + transferConfig.getTransferHost() + "/" + dataFlow.getId())
                            .property(EDC_NAMESPACE + "endpointType", ENDPOINT_TYPE);

            return Result.success(fdscDataAddressBuilder.build());
        } catch (ParseException | JOSEException e) {
            return Result.failure(e.getMessage());
        }


    }

    @Override
    public ServiceResult<Void> revokeEndpointDataReference(String s, String s1) {
        // nothing to be revoked, since token handling happens at the OID4VC level
        return ServiceResult.success();
    }
}
