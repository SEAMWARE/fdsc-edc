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

import static org.eclipse.edc.iam.decentralizedclaims.core.DcpDefaultServicesExtension.CLAIMTOKEN_VC_KEY;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.iam.verifiablecredentials.spi.validation.TrustedIssuerRegistry;
import org.eclipse.edc.protocol.spi.ParticipantIdExtractionFunction;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.monitor.Monitor;

/**
 * Restricts a dataspace profile to the issuers of its own TIL. Returns {@code null} (rejected as
 * 401 upstream) unless every presented VC is trusted by that TIL, else delegates.
 */
public class ProfileTrustedIssuerIdExtractionFunction implements ParticipantIdExtractionFunction {

  private final Monitor monitor;
  private final String profile;
  private final TrustedIssuerRegistry profileIssuers;
  private final ParticipantIdExtractionFunction delegate;

  public ProfileTrustedIssuerIdExtractionFunction(
      Monitor monitor,
      String profile,
      TrustedIssuerRegistry profileIssuers,
      ParticipantIdExtractionFunction delegate) {
    this.monitor = monitor;
    this.profile = profile;
    this.profileIssuers = profileIssuers;
    this.delegate = delegate;
  }

  @Override
  public String apply(ClaimToken claimToken) {
    List<?> credentials =
        Optional.ofNullable(claimToken.getListClaim(CLAIMTOKEN_VC_KEY)).orElse(List.of());
    Optional<VerifiableCredential> untrusted =
        credentials.stream()
            .filter(VerifiableCredential.class::isInstance)
            .map(VerifiableCredential.class::cast)
            .filter(credential -> !isTrusted(credential))
            .findFirst();
    if (untrusted.isPresent()) {
      monitor.warning(
          String.format(
              "Credential %s of issuer %s is not trusted in profile %s.",
              untrusted.get().getType(),
              Optional.ofNullable(untrusted.get().getIssuer()).map(Issuer::id).orElse(null),
              profile));
      return null;
    }
    return delegate.apply(claimToken);
  }

  // Mirrors upstream HasValidIssuer, but against the profile's own TIL.
  private boolean isTrusted(VerifiableCredential credential) {
    Issuer issuer = credential.getIssuer();
    if (issuer == null || issuer.id() == null) {
      return false;
    }
    Set<String> supportedTypes = profileIssuers.getSupportedTypes(issuer);
    return supportedTypes.contains(TrustedIssuerRegistry.WILDCARD)
        || !Collections.disjoint(credential.getType(), supportedTypes);
  }
}
