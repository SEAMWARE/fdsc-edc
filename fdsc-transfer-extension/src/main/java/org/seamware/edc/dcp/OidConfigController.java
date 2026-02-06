package org.seamware.edc.dcp;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import org.eclipse.edc.spi.monitor.Monitor;
import org.seamware.edc.TransferConfig;

import java.util.Map;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * In order to properly resolve the jwks, apisix requires an oid-discovery endpoint. We only include the issuer and jwk, since they are the only things
 * required.
 */
@Produces(APPLICATION_JSON)
@Path("/.well-known/openid-configuration")
public class OidConfigController {

    private final Monitor monitor;
    private final TransferConfig transferConfig;
    private final String participantId;

    public OidConfigController(Monitor monitor, TransferConfig transferConfig, String participantId) {
        this.monitor = monitor;
        this.transferConfig = transferConfig;
        this.participantId = participantId;
    }

    @GET
    public Map<String, Object> getOIDConfiguration() {
        String jwksUri = transferConfig.getDcp().oidConfig().host() + transferConfig.getDcp().oidConfig().jwksPath();
        return Map.of("issuer", participantId, "jwks_uri", jwksUri);
    }
}
