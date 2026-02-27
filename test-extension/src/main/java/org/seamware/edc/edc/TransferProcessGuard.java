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

import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess.Type.PROVIDER;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.*;

import java.util.Set;
import java.util.function.Consumer;
import org.eclipse.edc.connector.controlplane.transfer.spi.TransferProcessPendingGuard;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.spi.persistence.StateEntityStore;

/** Contract negotiation guard for TCK testcases. */
public class TransferProcessGuard extends DelayedActionGuard<TransferProcess>
    implements TransferProcessPendingGuard {
  // the states not to apply the guard to - i.e., to allow automatic transitions by the transfer
  // process manager
  private static final Set<Integer> PROVIDER_AUTOMATIC_STATES =
      Set.of(
          INITIAL.code(),
          STARTING.code(),
          SUSPENDING.code(),
          SUSPENDING_REQUESTED.code(),
          RESUMING.code(),
          COMPLETING.code(),
          COMPLETING_REQUESTED.code(),
          PROVISIONING.code(),
          DEPROVISIONING.code(),
          TERMINATING.code(),
          TERMINATING_REQUESTED.code());

  private static final Set<Integer> CONSUMER_AUTOMATIC_STATES =
      Set.of(
          INITIAL.code(),
          REQUESTING.code(),
          PROVISIONING.code(),
          DEPROVISIONING.code(),
          SUSPENDING.code(),
          SUSPENDING_REQUESTED.code(),
          COMPLETING.code(),
          COMPLETING_REQUESTED.code(),
          TERMINATING.code(),
          TERMINATING_REQUESTED.code());

  public TransferProcessGuard(
      Consumer<TransferProcess> action, StateEntityStore<TransferProcess> store) {
    super(
        cn ->
            cn.getType() == PROVIDER
                ? !PROVIDER_AUTOMATIC_STATES.contains(cn.getState())
                : !CONSUMER_AUTOMATIC_STATES.contains(cn.getState()),
        action,
        store);
  }
}
