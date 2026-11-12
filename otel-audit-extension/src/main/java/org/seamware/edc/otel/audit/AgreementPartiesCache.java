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

import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;

/**
 * Bounded in-memory LRU of the agreements seen in finalized negotiations, keyed by the local
 * agreement id. Transfer events only carry that local id, so this is where their shared agreement
 * id and parties come from without reading the stores.
 *
 * <p>Best effort: agreements finalized before a restart, or by another replica, are not found.
 */
public class AgreementPartiesCache {

  /**
   * Immutable view of an agreement: nothing that changes over the agreement lifecycle is kept.
   *
   * @param agreementId agreement id shared by both connectors in DSP
   */
  public record AgreementParties(String agreementId, String providerId, String consumerId) {}

  private final Map<String, AgreementParties> entries;

  public AgreementPartiesCache(int maxEntries) {
    // access order: agreements still used by transfers are the last to be evicted
    this.entries =
        new LinkedHashMap<>(16, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<String, AgreementParties> eldest) {
            return size() > maxEntries;
          }
        };
  }

  /** Remembers the agreement under its local id. Agreements without local id are ignored. */
  public synchronized void put(ContractAgreement agreement) {
    if (agreement == null || agreement.getId() == null) {
      return;
    }
    entries.put(
        agreement.getId(),
        new AgreementParties(
            agreement.getAgreementId(), agreement.getProviderId(), agreement.getConsumerId()));
  }

  /** The agreement with this local id, or {@code null} when it is not cached. */
  public synchronized AgreementParties get(String localAgreementId) {
    return localAgreementId == null ? null : entries.get(localAgreementId);
  }
}
