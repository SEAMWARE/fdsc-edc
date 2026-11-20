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
package org.seamware.edc.store;

import java.util.Optional;
import org.eclipse.edc.spi.monitor.Monitor;
import org.seamware.edc.domain.ExtendableProductOffering;
import org.seamware.edc.tmf.ProductCatalogApiClient;

/**
 * Resolves a plain or {@link ContextQualifiedId} contract definition id to its ProductOffering and
 * the one context it denotes, per ADR-4. Ambiguous ids are logged severe and resolve to empty.
 * TMForum failures, including a duplicate {@code externalId}, propagate.
 */
public class ContractDefinitionIdResolver {

  private final Monitor monitor;
  private final ProductCatalogApiClient productCatalogApi;
  private final TMFEdcMapper tmfEdcMapper;

  public ContractDefinitionIdResolver(
      Monitor monitor, ProductCatalogApiClient productCatalogApi, TMFEdcMapper tmfEdcMapper) {
    this.monitor = monitor;
    this.productCatalogApi = productCatalogApi;
    this.tmfEdcMapper = tmfEdcMapper;
  }

  /** An offering together with the context a definition id selected. */
  public record ResolvedOffering(ExtendableProductOffering offering, String participantContextId) {}

  public Optional<ResolvedOffering> resolve(String definitionId) {
    Optional<ResolvedOffering> plain =
        productCatalogApi
            .getProductOfferingByExternalId(definitionId)
            .flatMap(
                offering ->
                    tmfEdcMapper
                        .contextForId(offering.getParticipantContextIds(), null)
                        .map(ctx -> new ResolvedOffering(offering, ctx)));
    // a plain id that cannot address its offering (multi-context, or no default) is never issued
    if (plain.isEmpty()) {
      return resolveQualified(definitionId);
    }
    if (resolveQualified(definitionId).isPresent()) {
      monitor.severe(
          String.format(
              "Contract definition id %s is ambiguous: it is the external id of offering %s and"
                  + " the context-qualified id of another offering. Neither resolves until one is"
                  + " renamed.",
              definitionId, plain.get().offering().getId()));
      return Optional.empty();
    }
    return plain;
  }

  private Optional<ResolvedOffering> resolveQualified(String definitionId) {
    return ContextQualifiedId.parse(definitionId)
        .flatMap(
            qualified ->
                productCatalogApi
                    .getProductOfferingByExternalId(qualified.id())
                    .flatMap(
                        offering ->
                            tmfEdcMapper
                                .contextForId(
                                    offering.getParticipantContextIds(),
                                    qualified.participantContextId())
                                .map(ctx -> new ResolvedOffering(offering, ctx))));
  }
}
