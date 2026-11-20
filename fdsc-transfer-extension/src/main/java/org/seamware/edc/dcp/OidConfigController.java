/*
 * Copyright 2025 Seamless Middleware Technologies S.L and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.seamware.edc.dcp;

/*-
 * #%L
 * fdsc-transfer-extension
 * %%
 * Copyright (C) 2025 - 2026 Seamless Middleware Technologies S.L
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import java.util.Map;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.web.spi.exception.ObjectNotFoundException;
import org.jetbrains.annotations.Nullable;
import org.seamware.edc.TransferConfig;
import org.seamware.edc.store.OwnParticipantIdentity;

/**
 * In order to properly resolve the jwks, apisix requires an oid-discovery endpoint. We only include
 * the issuer and jwk, since they are the only things required. One document per participant
 * context; the bare path serves the default context. All contexts share one JWKS.
 */
@Produces(APPLICATION_JSON)
@Path("/.well-known/openid-configuration")
public class OidConfigController {

  static final String ISSUER_KEY = "issuer";
  static final String JWKS_URI_KEY = "jwks_uri";

  private final Monitor monitor;
  private final TransferConfig transferConfig;
  private final OwnParticipantIdentity ownParticipantIdentity;
  private final @Nullable String defaultParticipantContextId;

  public OidConfigController(
      Monitor monitor,
      TransferConfig transferConfig,
      OwnParticipantIdentity ownParticipantIdentity,
      @Nullable String defaultParticipantContextId) {
    this.monitor = monitor;
    this.transferConfig = transferConfig;
    this.ownParticipantIdentity = ownParticipantIdentity;
    this.defaultParticipantContextId = defaultParticipantContextId;
  }

  /**
   * Throws {@link ObjectNotFoundException} (404) when no default context is resolvable, e.g. in
   * virtual mode without one configured.
   */
  @GET
  public Map<String, Object> getOIDConfiguration() {
    return oidConfiguration(defaultParticipantContextId);
  }

  /**
   * Serves the context whose id is {@link ContextPathSegment}-encoded in the path. Throws {@link
   * ObjectNotFoundException} (404) for a malformed segment or a context without a known identity.
   */
  @GET
  @Path("/{encodedParticipantContextId}")
  public Map<String, Object> getOIDConfiguration(
      @PathParam("encodedParticipantContextId") String encodedParticipantContextId) {
    return oidConfiguration(
        ContextPathSegment.decode(encodedParticipantContextId)
            .orElseThrow(
                () ->
                    new ObjectNotFoundException(
                        OidConfigController.class, encodedParticipantContextId)));
  }

  private Map<String, Object> oidConfiguration(@Nullable String participantContextId) {
    String issuer;
    try {
      issuer = ownParticipantIdentity.participantId(participantContextId, null);
    } catch (IllegalStateException e) {
      monitor.debug("No openid-configuration for context " + participantContextId, e);
      throw new ObjectNotFoundException(OidConfigController.class, participantContextId);
    }
    String jwksUri =
        transferConfig.getDcp().oidConfig().host() + transferConfig.getDcp().oidConfig().jwksPath();
    return Map.of(ISSUER_KEY, issuer, JWKS_URI_KEY, jwksUri);
  }
}
