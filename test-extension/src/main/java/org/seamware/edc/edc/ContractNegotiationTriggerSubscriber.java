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
package org.seamware.edc.edc;

/*-
 * #%L
 * test-extension
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationEvent;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.spi.event.Event;
import org.eclipse.edc.spi.event.EventEnvelope;
import org.eclipse.edc.spi.event.EventSubscriber;
import org.eclipse.edc.spi.persistence.StateEntityStore;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.transaction.spi.TransactionContext;

/**
 * Fires triggers based on negotiation events. Retries lease acquisition when the negotiation is
 * still held by the state machine (common with TMF-backed stores whose HTTP-based saves take longer
 * than in-memory stores).
 */
public class ContractNegotiationTriggerSubscriber
    implements EventSubscriber, ContractNegotiationTriggerRegistry {

  private static final Logger LOG =
      Logger.getLogger(ContractNegotiationTriggerSubscriber.class.getName());

  /** Maximum number of attempts to acquire the lease before giving up. */
  private static final int MAX_LEASE_ATTEMPTS = 20;

  /** Milliseconds to wait between lease acquisition attempts. */
  private static final long LEASE_RETRY_DELAY_MS = 200;

  private final List<Trigger<ContractNegotiation>> triggers = new CopyOnWriteArrayList<>();
  private final StateEntityStore<ContractNegotiation> store;
  private final TransactionContext transactionContext;

  public ContractNegotiationTriggerSubscriber(
      StateEntityStore<ContractNegotiation> store, TransactionContext transactionContext) {
    this.store = store;
    this.transactionContext = transactionContext;
  }

  @Override
  public void register(Trigger<ContractNegotiation> trigger) {
    triggers.add(trigger);
  }

  @Override
  public <E extends Event> void on(EventEnvelope<E> envelope) {
    triggers.stream()
        .filter(trigger -> trigger.predicate().test(envelope.getPayload()))
        .forEach(
            trigger -> {
              var event = (ContractNegotiationEvent) envelope.getPayload();
              transactionContext.execute(
                  () -> {
                    var negotiation = findByIdAndLeaseWithRetry(event.getContractNegotiationId());
                    trigger.action().accept(negotiation);
                    store.save(negotiation);
                  });
            });
  }

  /**
   * Attempts to find and lease the negotiation, retrying when the entity is currently leased by the
   * state machine. With TMF-backed stores, saves involve HTTP round-trips that hold the lease
   * longer than in-memory stores, so the event may fire while the lease is still active.
   */
  private ContractNegotiation findByIdAndLeaseWithRetry(String negotiationId) {
    for (int attempt = 0; attempt < MAX_LEASE_ATTEMPTS; attempt++) {
      StoreResult<ContractNegotiation> result = store.findByIdAndLease(negotiationId);
      if (result.succeeded()) {
        return result.getContent();
      }
      LOG.warning(
          String.format(
              "Lease attempt %d/%d failed for %s: %s",
              attempt + 1, MAX_LEASE_ATTEMPTS, negotiationId, result.getFailureDetail()));
      try {
        TimeUnit.MILLISECONDS.sleep(LEASE_RETRY_DELAY_MS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Interrupted while waiting for lease on " + negotiationId, e);
      }
    }
    throw new IllegalStateException(
        String.format(
            "Failed to acquire lease on %s after %d attempts", negotiationId, MAX_LEASE_ATTEMPTS));
  }
}
