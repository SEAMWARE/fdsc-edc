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
import jakarta.ws.rs.Produces;
import java.util.Map;
import org.eclipse.edc.spi.monitor.Monitor;
import org.seamware.edc.TransferConfig;

/**
 * In order to properly resolve the jwks, apisix requires an oid-discovery endpoint. We only include
 * the issuer and jwk, since they are the only things required.
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
    String jwksUri =
        transferConfig.getDcp().oidConfig().host() + transferConfig.getDcp().oidConfig().jwksPath();
    return Map.of("issuer", participantId, "jwks_uri", jwksUri);
  }
}
