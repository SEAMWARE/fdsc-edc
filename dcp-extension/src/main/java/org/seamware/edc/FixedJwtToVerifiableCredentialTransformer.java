package org.seamware.edc;

import com.nimbusds.jwt.SignedJWT;
import org.eclipse.edc.iam.identitytrust.transform.to.JwtToVerifiableCredentialTransformer;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.*;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.ParseException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static java.util.Optional.ofNullable;

public class FixedJwtToVerifiableCredentialTransformer extends JwtToVerifiableCredentialTransformer {

    private static final String ID_PROPERTY = "id";
    private static final String VC_CLAIM = "vc";
    private static final String CREDENTIAL_SUBJECT_PROPERTY = "credentialSubject";
    private static final String CREDENTIAL_SCHEMA_PROPERTY = "credentialSchema";
    private static final String CREDENTIAL_STATUS_PROPERTY = "credentialStatus";
    private static final String EXPIRATION_DATE_PROPERTY = "expirationDate";
    private static final String ISSUANCE_DATE_PROPERTY = "issuanceDate";
    private static final String VALID_FROM_PROPERTY = "validFrom";
    private static final String VALID_UNTIL_PROPERTY = "validUntil";

    private final Monitor monitor;


    public FixedJwtToVerifiableCredentialTransformer(Monitor monitor) {
        super(monitor);
        this.monitor = monitor;
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nullable VerifiableCredential transform(@NotNull String serializedJwt, @NotNull TransformerContext context) {
        try {
            var jwt = SignedJWT.parse(serializedJwt);
            var claims = jwt.getJWTClaimsSet();

            Object vcObject;
            var builder = VerifiableCredential.Builder.newInstance();

            if (isVcDataModel2_0(claims)) {
                vcObject = claims.getClaims(); //in VCDM2.0 the credential is directly stored in the payload
                builder.dataModelVersion(DataModelVersion.V_2_0);
            } else {
                vcObject = claims.getClaim(VC_CLAIM);
            }


            if (vcObject instanceof Map<?, ?> vc) {

                ofNullable(claims.getJWTID())
                        .or(() -> ofNullable(vc.get("id")).map(Object::toString))
                        .ifPresent(builder::id);

                // types
                listOrReturn(vc.get(TYPE_PROPERTY), Object::toString).forEach(builder::type);

                // credential subjects
                listOrReturn(vc.get(CREDENTIAL_SUBJECT_PROPERTY), o -> extractSubject((Map<String, ?>) o, claims.getSubject())).forEach(builder::credentialSubject);

                // credential status
                listOrReturn(vc.get(CREDENTIAL_STATUS_PROPERTY), o -> extractStatus((Map<String, Object>) o)).forEach(builder::credentialStatus);

                //credential schema
                listOrReturn(vc.get(CREDENTIAL_SCHEMA_PROPERTY), o -> extractSchema((Map<String, Object>) o)).forEach(builder::credentialSchema);

                // expiration date
                extractDate(vc.get(EXPIRATION_DATE_PROPERTY), claims.getExpirationTime()).or(() -> extractDate(vc.get(VALID_UNTIL_PROPERTY), claims.getExpirationTime())).ifPresent(builder::expirationDate);

                // issuance date
                extractDate(vc.get(ISSUANCE_DATE_PROPERTY), claims.getNotBeforeTime()).or(() -> extractDate(vc.get(VALID_FROM_PROPERTY), claims.getNotBeforeTime())).ifPresent(builder::issuanceDate);

                // take issuer from JWT claim of from VC object
                var issuer = ofNullable(claims.getIssuer()).or(() -> ofNullable(vc.get("issuer")).map(Object::toString)).orElse(null);
                builder.issuer(new Issuer(issuer, Map.of()));
                builder.name(claims.getSubject()); // todo: is this correct?
                return builder.build();
            }
        } catch (ParseException e) {
            monitor.warning("Error parsing JWT", e);
            context.reportProblem("Error parsing JWT: %s".formatted(e.getMessage()));
        }
        return null;
    }

    /* +++++++ THIS IS THE FIX PART
     * For JWT Credentials, the issuanceDate is derrived from nbf and therefor a numeric timestamp
     * > https://github.com/w3c/vc-data-model/issues/782
     */
    private Optional<Instant> extractDate(@Nullable Object dateObject, Date fallback) {
        try {
            return ofNullable(dateObject)
                    .map(Object::toString)
                    .map(Instant::parse)
                    .or(() -> ofNullable(fallback).map(Date::toInstant));
        } catch (DateTimeParseException e) {
            monitor.info("Was not able to parse the timestring, might be a numeric timestamp.");
            if (dateObject instanceof Double numeric) {
                // small loss of precision is likely due to double conversion already, should not have any practical impact.
                return Optional.of(Instant.ofEpochSecond(numeric.longValue()));
            }
            // no double, bubble
            throw e;
        }
    }

    private CredentialStatus extractStatus(Map<String, Object> status) {
        if (status == null || status.isEmpty()) {
            return null;
        }
        var id = status.remove(ID_PROPERTY).toString();
        var type = status.remove(TYPE_PROPERTY).toString();

        return new CredentialStatus(id, type, status);
    }

    private CredentialSchema extractSchema(Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            return null;
        }
        var id = schema.remove(ID_PROPERTY).toString();
        var type = schema.remove(TYPE_PROPERTY).toString();

        return new CredentialSchema(id, type);
    }

    private CredentialSubject extractSubject(Map<String, ?> subject, String fallback) {
        var builder = CredentialSubject.Builder.newInstance();
        var id = Objects.requireNonNullElse(subject.remove(ID_PROPERTY), fallback);
        builder.id(id.toString());
        subject.forEach(builder::claim);
        return builder.build();
    }
}