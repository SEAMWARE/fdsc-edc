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
package org.seamware.edc.tir;

/*-
 * #%L
 * dcp-extension
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

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.validation.TrustedIssuerRegistry;
import org.seamware.til.model.CredentialsVO;
import org.seamware.til.model.TrustedIssuerVO;

public class EbsiTrustedIssuersRegistry implements TrustedIssuerRegistry {

  private final TirClient tirClient;

  public EbsiTrustedIssuersRegistry(TirClient tirClient) {
    this.tirClient = tirClient;
  }

  @Override
  public void register(Issuer issuer, String credentialType) {
    tirClient
        .getIssuer(issuer.id())
        .ifPresentOrElse(
            ti -> {
              ti.addCredentialsItem(new CredentialsVO().credentialsType(credentialType));
              tirClient.putIssuer(ti);
            },
            () ->
                tirClient.createIssuer(
                    new TrustedIssuerVO()
                        .did(issuer.id())
                        .addCredentialsItem(new CredentialsVO().credentialsType(credentialType))));
  }

  @Override
  public Set<String> getSupportedTypes(Issuer issuer) {
    return tirClient
        .getIssuer(issuer.id())
        .filter(trustedIssuer -> trustedIssuer.getCredentials() != null)
        .map(
            trustedIssuer ->
                trustedIssuer.getCredentials().stream()
                    .map(CredentialsVO::getCredentialsType)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet()))
        .orElse(Set.of());
  }
}
