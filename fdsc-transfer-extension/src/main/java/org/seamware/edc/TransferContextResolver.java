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
package org.seamware.edc;

/*-
 * #%L
 * fdsc-transfer-extension
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

import java.util.Optional;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.spi.result.Result;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the participant context a data flow runs in: the single source for both the EDR issuer
 * and the APISIX route's discovery document, so the two cannot diverge.
 */
public class TransferContextResolver {

  private final TransferProcessStore transferProcessStore;
  private final @Nullable String defaultParticipantContextId;

  public TransferContextResolver(
      TransferProcessStore transferProcessStore, @Nullable String defaultParticipantContextId) {
    this.transferProcessStore = transferProcessStore;
    this.defaultParticipantContextId = defaultParticipantContextId;
  }

  /**
   * The flow id is the transfer process id: the control plane starts the flow with {@code processId
   * = transferProcess.getId()}. A transfer without a context belongs to the default context. Fails
   * when the transfer is unknown or no context can be resolved; never uses the counter-party id.
   */
  public Result<String> resolve(String flowId) {
    TransferProcess transferProcess = transferProcessStore.findById(flowId);
    if (transferProcess == null) {
      return Result.failure(
          String.format(
              "No transfer process %s found, its participant context is unknown.", flowId));
    }
    return Optional.ofNullable(transferProcess.getParticipantContextId())
        .filter(contextId -> !contextId.isBlank())
        .or(() -> Optional.ofNullable(defaultParticipantContextId))
        .map(Result::success)
        .orElseGet(
            () ->
                Result.failure(
                    String.format(
                        "Transfer process %s has no participant context and no default context is"
                            + " configured.",
                        flowId)));
  }
}
