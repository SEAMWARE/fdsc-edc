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
package org.seamware.edc.edr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.edc.edr.spi.store.EndpointDataReferenceStore;
import org.eclipse.edc.edr.spi.types.EndpointDataReferenceEntry;
import org.eclipse.edc.edr.store.EndpointDataReferenceStoreImpl;
import org.eclipse.edc.edr.store.defaults.InMemoryEndpointDataReferenceEntryIndex;
import org.eclipse.edc.query.CriterionOperatorRegistryImpl;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Verifies EDR entries stay partitioned per participant context in EDC's real in-memory store. */
class EdrOwnershipLookupTest {

  private static final String CONTEXT_A = "dataspace-a";
  private static final String CONTEXT_B = "dataspace-b";
  private static final String UNKNOWN_CONTEXT = "dataspace-unknown";
  private static final String TP_A1 = "tp-a1";
  private static final String TP_A2 = "tp-a2";
  private static final String TP_B1 = "tp-b1";
  private static final String UNKNOWN_TP = "tp-unknown";
  private static final String DATA_ADDRESS_TYPE = "HttpData";

  private EndpointDataReferenceStore store;
  private EdrOwnershipLookup lookup;

  @BeforeEach
  void setUp() {
    store =
        new EndpointDataReferenceStoreImpl(
            new InMemoryEndpointDataReferenceEntryIndex(CriterionOperatorRegistryImpl.ofDefaults()),
            new InMemoryEndpointDataReferenceCache(),
            new NoopTransactionContext());
    save(CONTEXT_A, TP_A1);
    save(CONTEXT_A, TP_A2);
    save(CONTEXT_B, TP_B1);
    lookup = new EdrOwnershipLookup(store);
  }

  static Stream<Arguments> lookupCases() {
    return Stream.of(
        Arguments.of(CONTEXT_A, TP_A1, true),
        Arguments.of(CONTEXT_A, TP_A2, true),
        Arguments.of(CONTEXT_B, TP_B1, true),
        Arguments.of(CONTEXT_A, TP_B1, false),
        Arguments.of(CONTEXT_B, TP_A1, false),
        Arguments.of(CONTEXT_A, UNKNOWN_TP, false),
        Arguments.of(UNKNOWN_CONTEXT, TP_A1, false));
  }

  @ParameterizedTest(name = "{0} looks up {1}: found={2}")
  @MethodSource("lookupCases")
  void lookup_resolvesOnlyOwnEntries(String ownerId, String transferProcessId, boolean found) {
    var resource = lookup.apply(ownerId, transferProcessId);

    if (found) {
      var entry = (EndpointDataReferenceEntry) resource;
      assertEquals(transferProcessId, entry.getId());
      assertEquals(ownerId, entry.getParticipantContextId());
    } else {
      assertNull(resource);
    }
  }

  static Stream<Arguments> queryCases() {
    return Stream.of(
        Arguments.of(CONTEXT_A, Set.of(TP_A1, TP_A2)),
        Arguments.of(CONTEXT_B, Set.of(TP_B1)),
        Arguments.of(UNKNOWN_CONTEXT, Set.of()));
  }

  @ParameterizedTest(name = "query of {0} returns {1}")
  @MethodSource("queryCases")
  void query_filtersByInheritedParticipantContextId(String contextId, Set<String> expectedIds) {
    var query =
        QuerySpec.Builder.newInstance()
            .filter(
                Criterion.criterion(
                    EdrOwnershipLookup.PARTICIPANT_CONTEXT_ID_PROPERTY,
                    EdrOwnershipLookup.EQUALS_OPERATOR,
                    contextId))
            .build();

    var result = store.query(query);

    assertTrue(result.succeeded());
    assertEquals(
        expectedIds,
        result.getContent().stream()
            .map(EndpointDataReferenceEntry::getId)
            .collect(Collectors.toSet()));
  }

  private void save(String contextId, String transferProcessId) {
    var entry =
        EndpointDataReferenceEntry.Builder.newInstance()
            .participantContextId(contextId)
            .transferProcessId(transferProcessId)
            .assetId("asset-" + transferProcessId)
            .agreementId("agreement-" + transferProcessId)
            .providerId("provider-" + contextId)
            .build();
    var dataAddress = DataAddress.Builder.newInstance().type(DATA_ADDRESS_TYPE).build();
    assertTrue(store.save(entry, dataAddress).succeeded());
  }
}
