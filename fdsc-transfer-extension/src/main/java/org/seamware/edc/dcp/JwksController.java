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
import static org.seamware.edc.FDSCTransferControlExtension.KEY_NAME;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import java.text.ParseException;
import java.util.Map;
import java.util.Optional;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.security.Vault;

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
        .map(
            jwkJson -> {
              try {
                return new JWKSet(JWK.parse(jwkJson).toPublicJWK());
              } catch (ParseException e) {
                monitor.warning("Was not able to parse the key", e);
                return null;
              }
            })
        .orElseGet(
            () -> {
              monitor.info("No jwk is configured.");
              return new JWKSet();
            })
        .toPublicJWKSet()
        .toJSONObject();
  }
}
