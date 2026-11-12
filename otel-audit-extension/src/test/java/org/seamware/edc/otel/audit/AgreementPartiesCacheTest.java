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
package org.seamware.edc.otel.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.policy.model.Policy;
import org.junit.jupiter.api.Test;
import org.seamware.edc.otel.audit.AgreementPartiesCache.AgreementParties;

public class AgreementPartiesCacheTest {

  @Test
  void agreementIsFoundByItsLocalId() {
    var cache = new AgreementPartiesCache(10);

    cache.put(agreement("local-1"));

    assertEquals(
        new AgreementParties("dsp-local-1", "did:web:provider", "urn:connector:consumer"),
        cache.get("local-1"));
    assertNull(cache.get("dsp-local-1"));
    assertNull(cache.get(null));
  }

  @Test
  void leastRecentlyUsedAgreementIsEvicted() {
    var cache = new AgreementPartiesCache(2);
    cache.put(agreement("local-1"));
    cache.put(agreement("local-2"));

    // reading local-1 makes local-2 the least recently used
    cache.get("local-1");
    cache.put(agreement("local-3"));

    assertNotNull(cache.get("local-1"));
    assertNull(cache.get("local-2"));
    assertNotNull(cache.get("local-3"));
  }

  @Test
  void missingAgreementIsIgnored() {
    var cache = new AgreementPartiesCache(10);

    cache.put(null);

    assertNull(cache.get("local-1"));
  }

  private static ContractAgreement agreement(String localId) {
    return ContractAgreement.Builder.newInstance()
        .id(localId)
        .agreementId("dsp-" + localId)
        .providerId("did:web:provider")
        .consumerId("urn:connector:consumer")
        .assetId("asset-1")
        .policy(Policy.Builder.newInstance().build())
        .build();
  }
}
