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
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.connector.controlplane.policy.spi.store.PolicyDefinitionStore;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.seamware.edc.tmf.ProductCatalogApiClient;

public class TMFBackedPolicyDefinitionStore implements PolicyDefinitionStore {

  private static final Logger LOGGER = Logger.getLogger("SeamPolicyDefinitionStore");

  private final Monitor monitor;

  private final ProductCatalogApiClient productCatalogApiClient;

  private final TMFEdcMapper tmfEdcMapper;

  public TMFBackedPolicyDefinitionStore(
      Monitor monitor, ProductCatalogApiClient productCatalogApiClient, TMFEdcMapper tmfEdcMapper) {
    this.monitor = monitor;
    this.productCatalogApiClient = productCatalogApiClient;
    this.tmfEdcMapper = tmfEdcMapper;
  }

  /** Resolves a plain or {@link ContextQualifiedId} policy id the way contract definitions are. */
  @Override
  public PolicyDefinition findById(String policyDefinitionId) {
    try {
      Optional<PolicyDefinition> plain =
          productCatalogApiClient
              .getByPolicyId(policyDefinitionId)
              .flatMap(offeringPolicy -> tmfEdcMapper.toPolicyDefinition(offeringPolicy, null));
      // a plain id that cannot address its offering (multi-context, or no default) is never issued
      if (plain.isEmpty()) {
        return resolveQualified(policyDefinitionId).orElse(null);
      }
      if (resolveQualified(policyDefinitionId).isPresent()) {
        monitor.severe(
            String.format(
                "Policy id %s is ambiguous: it is a plain policy id and the context-qualified id"
                    + " of another offering's policy. Neither resolves until one is renamed.",
                policyDefinitionId));
        return null;
      }
      return plain.orElse(null);
    } catch (RuntimeException e) {
      monitor.warning("Was not able to find the requested policy.", e);
      throw new EdcPersistenceException(
          String.format("Was not able to find policy with id %s.", policyDefinitionId), e);
    }
  }

  private Optional<PolicyDefinition> resolveQualified(String policyDefinitionId) {
    return ContextQualifiedId.parse(policyDefinitionId)
        .flatMap(
            qualified ->
                productCatalogApiClient
                    .getByPolicyId(qualified.id())
                    .flatMap(
                        offeringPolicy ->
                            tmfEdcMapper.toPolicyDefinition(
                                offeringPolicy, qualified.participantContextId())));
  }

  @Override
  public Stream<PolicyDefinition> findAll(QuerySpec querySpec) {
    throw new UnsupportedOperationException(
        "Querying for policy definitions currently is unsupported.");
  }

  @Override
  public StoreResult<PolicyDefinition> create(PolicyDefinition policyDefinition) {
    throw new UnsupportedOperationException(
        "Creating policy definitions currently is unsupported.");
  }

  @Override
  public StoreResult<PolicyDefinition> update(PolicyDefinition policyDefinition) {
    throw new UnsupportedOperationException(
        "Updating policy definitions currently is unsupported.");
  }

  @Override
  public StoreResult<PolicyDefinition> delete(String s) {
    throw new UnsupportedOperationException(
        "Deleting policy definitions currently is unsupported.");
  }
}
