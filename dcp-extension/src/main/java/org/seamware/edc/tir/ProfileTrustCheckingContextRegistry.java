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

import java.util.Map;
import org.eclipse.edc.connector.controlplane.profile.DataspaceProfileContextRegistryImpl;
import org.eclipse.edc.iam.verifiablecredentials.spi.validation.TrustedIssuerRegistry;
import org.eclipse.edc.protocol.spi.DataspaceProfileContext;
import org.eclipse.edc.spi.monitor.Monitor;

/**
 * Profile registry that wraps every profile's id extraction in a {@link
 * ProfileTrustedIssuerIdExtractionFunction}: its own TIL if configured, else the global TIL. The
 * upstream registry resolves a name to the first profile registered under it, so the wrapping has
 * to happen at registration time.
 */
public class ProfileTrustCheckingContextRegistry extends DataspaceProfileContextRegistryImpl {

  private final Monitor monitor;
  private final TrustedIssuerRegistry defaultIssuers;
  private final Map<String, TrustedIssuerRegistry> profileIssuers;

  public ProfileTrustCheckingContextRegistry(
      Monitor monitor,
      TrustedIssuerRegistry defaultIssuers,
      Map<String, TrustedIssuerRegistry> profileIssuers) {
    this.monitor = monitor;
    this.defaultIssuers = defaultIssuers;
    this.profileIssuers = Map.copyOf(profileIssuers);
  }

  @Override
  public void registerDefault(DataspaceProfileContext profileContext) {
    super.registerDefault(withTrustCheck(profileContext));
  }

  @Override
  public void register(DataspaceProfileContext context) {
    super.register(withTrustCheck(context));
  }

  private DataspaceProfileContext withTrustCheck(DataspaceProfileContext context) {
    // The upstream issuer check runs against the union of all TILs, so without this a profile
    // lacking its own TIL would accept issuers trusted only by another dataspace.
    TrustedIssuerRegistry issuers = profileIssuers.getOrDefault(context.name(), defaultIssuers);
    return new DataspaceProfileContext(
        context.name(),
        context.protocolVersion(),
        context.webhook(),
        new ProfileTrustedIssuerIdExtractionFunction(
            monitor, context.name(), issuers, context.idExtractionFunction()),
        context.protocolNamespace(),
        context.jsonLdContextsUrl());
  }
}
