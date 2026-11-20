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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.validation.TrustedIssuerRegistry;
import org.eclipse.edc.spi.monitor.Monitor;
import org.seamware.til.model.CredentialsVO;
import org.seamware.til.model.TrustedIssuerVO;

/**
 * Trusted issuer registry backed by one or more EBSI TILs. Lookups return the union of all TILs;
 * registrations go to the first (primary) TIL only. A failing TIL is skipped on lookup — its
 * issuers count as untrusted — for {@link #UNAVAILABLE_TIL_COOLDOWN}, so one unreachable list
 * cannot break or stall authentication for the dataspaces served by the others. That holds for the
 * primary TIL too: this registry also backs the global issuer check, where an escaping exception
 * would fail inbound verification for every dataspace, including one with a healthy TIL of its own.
 * Registration is not skipped and still fails loudly.
 */
public class EbsiTrustedIssuersRegistry implements TrustedIssuerRegistry {

  /**
   * How long a failed additional TIL is skipped; bounds a hanging TIL to one timeout per window.
   */
  public static final Duration UNAVAILABLE_TIL_COOLDOWN = Duration.ofSeconds(30);

  private final TirClient tirClient;
  private final List<TirClient> additionalClients;
  private final Monitor monitor;
  private final Clock clock;
  private final Map<TirClient, Instant> unavailableUntil = new ConcurrentHashMap<>();

  public EbsiTrustedIssuersRegistry(TirClient tirClient, Monitor monitor) {
    this(tirClient, List.of(), monitor);
  }

  public EbsiTrustedIssuersRegistry(
      TirClient tirClient, List<TirClient> additionalClients, Monitor monitor) {
    this(tirClient, additionalClients, monitor, Clock.systemUTC());
  }

  EbsiTrustedIssuersRegistry(
      TirClient tirClient, List<TirClient> additionalClients, Monitor monitor, Clock clock) {
    this.tirClient = tirClient;
    this.additionalClients = List.copyOf(additionalClients);
    this.monitor = monitor;
    this.clock = clock;
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
            lookupSkippingFailure(tirClient, issuer.id()).stream(),
            additionalClients.stream()
                .flatMap(client -> lookupSkippingFailure(client, issuer.id()).stream()))
        .filter(trustedIssuer -> trustedIssuer.getCredentials() != null)
        .flatMap(trustedIssuer -> trustedIssuer.getCredentials().stream())
        .map(CredentialsVO::getCredentialsType)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  private Optional<TrustedIssuerVO> lookupSkippingFailure(TirClient client, String issuerId) {
    Instant skipUntil = unavailableUntil.get(client);
    if (skipUntil != null && clock.instant().isBefore(skipUntil)) {
      return Optional.empty();
    }
    try {
      Optional<TrustedIssuerVO> trustedIssuer = client.getIssuer(issuerId);
      unavailableUntil.remove(client);
      return trustedIssuer;
    } catch (RuntimeException e) {
      unavailableUntil.put(client, clock.instant().plus(UNAVAILABLE_TIL_COOLDOWN));
      monitor.warning(
          String.format(
              "Skipping unavailable TIL for issuer %s; not asking it again for %s. Its issuers"
                  + " count as untrusted until it answers.",
              issuerId, UNAVAILABLE_TIL_COOLDOWN),
          e);
      return Optional.empty();
    }
  }
}
