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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationEvent;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.spi.event.Event;
import org.eclipse.edc.spi.event.EventEnvelope;
import org.eclipse.edc.spi.event.EventSubscriber;
import org.eclipse.edc.spi.persistence.StateEntityStore;
import org.eclipse.edc.transaction.spi.TransactionContext;

/** Fires triggers based on negotiation events. */
public class ContractNegotiationTriggerSubscriber
    implements EventSubscriber, ContractNegotiationTriggerRegistry {
  private final List<Trigger<ContractNegotiation>> triggers = new ArrayList<>();
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

    try {
      TimeUnit.MILLISECONDS.sleep(100);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    triggers.stream()
        .filter(trigger -> trigger.predicate().test(envelope.getPayload()))
        .forEach(
            trigger -> {
              var event = (ContractNegotiationEvent) envelope.getPayload();
              transactionContext.execute(
                  () -> {
                    var negotiation =
                        store.findByIdAndLease(event.getContractNegotiationId()).getContent();
                    trigger.action().accept(negotiation);
                    store.save(negotiation);
                  });
            });
  }
}
