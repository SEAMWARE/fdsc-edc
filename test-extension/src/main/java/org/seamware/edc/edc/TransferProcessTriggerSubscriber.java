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
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessEvent;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.spi.event.Event;
import org.eclipse.edc.spi.event.EventEnvelope;
import org.eclipse.edc.spi.event.EventSubscriber;
import org.eclipse.edc.spi.persistence.StateEntityStore;

/** Fires triggers based on transfer events. */
public class TransferProcessTriggerSubscriber
    implements EventSubscriber, TransferProcessTriggerRegistry {
  private final List<Trigger<TransferProcess>> triggers = new ArrayList<>();
  private final StateEntityStore<TransferProcess> store;

  public TransferProcessTriggerSubscriber(StateEntityStore<TransferProcess> store) {
    this.store = store;
  }

  @Override
  public void register(Trigger<TransferProcess> trigger) {
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
              var event = (TransferProcessEvent) envelope.getPayload();
              var negotiation = store.findByIdAndLease(event.getTransferProcessId()).getContent();
              trigger.action().accept(negotiation);
              store.save(negotiation);
            });
  }
}
