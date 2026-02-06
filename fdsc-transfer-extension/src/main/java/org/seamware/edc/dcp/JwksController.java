package org.seamware.edc.dcp;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.security.Vault;

import java.text.ParseException;
import java.util.Map;
import java.util.Optional;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.seamware.edc.FDSCTransferControlExtension.KEY_NAME;

@Produces(APPLICATION_JSON)
@Path("/.well-known/jwks")
public class JwksController {

    private final Vault vault;
    private final Monitor monitor;

    public JwksController(Vault vault, Monitor monitor) {
        this.vault = vault;
        this.monitor = monitor;
    }


    @GET
    public Map<String, Object> getJWKS() {
        return Optional.ofNullable(vault.resolveSecret(KEY_NAME))
                .map(jwkJson -> {
                    try {
                        return new JWKSet(JWK.parse(jwkJson).toPublicJWK());
                    } catch (ParseException e) {
                        monitor.warning("Was not able to parse the key", e);
                        return null;
                    }
                })
                .orElseGet(() -> {
                    monitor.info("No jwk is configured.");
                    return new JWKSet();
                }).toPublicJWKSet().toJSONObject();
    }
}
