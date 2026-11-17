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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.validation.TrustedIssuerRegistry;
import org.eclipse.edc.spi.monitor.Monitor;
import org.seamware.til.model.CredentialsVO;
import org.seamware.til.model.TrustedIssuerVO;

/**
 * Trusted issuer registry backed by one or more EBSI TILs. Lookups return the union of all TILs;
 * registrations go to the first (primary) TIL only. A failing additional TIL is skipped (its
 * issuers are untrusted) so it cannot break authentication in other dataspaces.
 */
public class EbsiTrustedIssuersRegistry implements TrustedIssuerRegistry {

  private final TirClient tirClient;
  private final List<TirClient> additionalClients;
  private final Monitor monitor;

  public EbsiTrustedIssuersRegistry(TirClient tirClient) {
    this(tirClient, List.of(), null);
  }

  public EbsiTrustedIssuersRegistry(
      TirClient tirClient, List<TirClient> additionalClients, Monitor monitor) {
    this.tirClient = tirClient;
    this.additionalClients = List.copyOf(additionalClients);
    this.monitor = monitor;
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
    return Stream.concat(
            tirClient.getIssuer(issuer.id()).stream(),
            additionalClients.stream()
                .flatMap(client -> lookupSkippingFailure(client, issuer.id()).stream()))
        .filter(trustedIssuer -> trustedIssuer.getCredentials() != null)
        .flatMap(trustedIssuer -> trustedIssuer.getCredentials().stream())
        .map(CredentialsVO::getCredentialsType)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  private Optional<TrustedIssuerVO> lookupSkippingFailure(TirClient client, String issuerId) {
    try {
      return client.getIssuer(issuerId);
    } catch (RuntimeException e) {
      monitor.warning(
          String.format("Skipping unavailable profile TIL for issuer %s.", issuerId), e);
      return Optional.empty();
    }
  }
}
