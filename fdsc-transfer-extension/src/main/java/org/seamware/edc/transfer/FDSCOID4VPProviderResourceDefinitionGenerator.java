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
package org.seamware.edc.transfer;

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

import static org.seamware.edc.FDSCTransferControlExtension.TRANSFER_TYPE_HTTP_PULL;

import org.eclipse.edc.connector.controlplane.transfer.spi.provision.ProviderResourceDefinitionGenerator;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.ResourceDefinition;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.jetbrains.annotations.Nullable;

public class FDSCOID4VPProviderResourceDefinitionGenerator
    implements ProviderResourceDefinitionGenerator {

  private final Monitor monitor;

  public FDSCOID4VPProviderResourceDefinitionGenerator(Monitor monitor) {
    this.monitor = monitor;
  }

  @Override
  public @Nullable ResourceDefinition generate(
      TransferProcess transferProcess, DataAddress assetAddress, Policy policy) {

    monitor.debug(
        "Generate resource definition for "
            + transferProcess.getAssetId()
            + " - "
            + transferProcess.getCorrelationId());

    return FDSCOID4VPProviderResourceDefinition.Builder.newInstance()
        .assetId(transferProcess.getAssetId())
        .id(transferProcess.getCorrelationId())
        .transferProcessId(transferProcess.getId())
        .build();
  }

  @Override
  public boolean canGenerate(
      TransferProcess transferProcess, DataAddress assetAddress, Policy policy) {
    return transferProcess.getTransferType().equals(TRANSFER_TYPE_HTTP_PULL);
  }
}
