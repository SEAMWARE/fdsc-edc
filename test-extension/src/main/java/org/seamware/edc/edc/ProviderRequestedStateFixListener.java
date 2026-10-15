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

import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess.Type.PROVIDER;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.INITIAL;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.REQUESTED;

import java.lang.reflect.Field;
import org.eclipse.edc.connector.controlplane.transfer.spi.observe.TransferProcessListener;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.spi.entity.StatefulEntity;
import org.eclipse.edc.spi.monitor.Monitor;

/**
 * Listener that transitions newly created provider transfers from INITIAL to REQUESTED state.
 *
 * <p>The DSP 2025-1 protocol specifies that a provider's transfer process should be in state
 * REQUESTED after acknowledging a TransferRequestMessage. However, EDC still creates provider
 * transfers in INITIAL state and {@link TransferProcess#transitionRequested()} throws for provider
 * types. This listener uses reflection to set the state directly, ensuring DSP protocol compliance
 * in TCK mode.
 *
 * <p>0.15.0 removed the {@code preCreated} callback; {@code initiated} is the earliest remaining
 * callback. In {@code TransferProcessProtocolServiceImpl.requestedAction} (0.18.1) it fires
 * <em>after</em> {@code transferProcessStore.save(process)}, on the same {@link TransferProcess}
 * instance that is then returned and used to build the DSP acknowledgement — so the REQUESTED state
 * set here reaches the acknowledgement, which is what DSP 2025-1 requires.
 *
 * <p>Because the initial save already happened by the time {@code initiated} fires, the reflective
 * mutation alone would leave the acknowledgement reporting REQUESTED while the stored row kept
 * INITIAL — a divergence from the removed {@code preCreated}, which fired before the save. To keep
 * store and protocol consistent (as the 0.14.1 baseline did), this listener re-persists the mutated
 * entity via {@link TransferProcessStore#save(StatefulEntity)}, which runs in the same transaction
 * the protocol service opened and releases no lease the service still needs.
 */
public class ProviderRequestedStateFixListener implements TransferProcessListener {

  private static final Field STATE_FIELD;

  static {
    try {
      STATE_FIELD = StatefulEntity.class.getDeclaredField("state");
      STATE_FIELD.setAccessible(true);
    } catch (NoSuchFieldException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private final Monitor monitor;
  private final TransferProcessStore transferProcessStore;

  public ProviderRequestedStateFixListener(
      Monitor monitor, TransferProcessStore transferProcessStore) {
    this.monitor = monitor;
    this.transferProcessStore = transferProcessStore;
  }

  @Override
  public void initiated(TransferProcess process) {
    if (process.getType() == PROVIDER && process.getState() == INITIAL.code()) {
      try {
        STATE_FIELD.setInt(process, REQUESTED.code());
      } catch (IllegalAccessException e) {
        monitor.warning(
            "ProviderRequestedStateFixListener: Failed to set REQUESTED state via reflection", e);
        return;
      }
      // Re-persist so the stored row matches the REQUESTED state carried in the DSP ack; the
      // reflective mutation above only touched the in-memory instance, which the protocol service
      // had already saved as INITIAL.
      var result = transferProcessStore.save(process);
      if (result.failed()) {
        monitor.warning(
            "ProviderRequestedStateFixListener: Failed to persist REQUESTED state for provider"
                + " transfer %s: %s".formatted(process.getId(), result.getFailureDetail()));
      } else {
        monitor.debug(
            () ->
                "ProviderRequestedStateFixListener: Set provider transfer %s to REQUESTED"
                    .formatted(process.getId()));
      }
    }
  }
}
