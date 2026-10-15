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

import static org.seamware.edc.store.TransferTypes.HTTP_DATA_PULL;

import org.eclipse.edc.connector.dataplane.spi.DataFlow;
import org.eclipse.edc.connector.dataplane.spi.provision.ProvisionResource;
import org.eclipse.edc.connector.dataplane.spi.provision.ResourceDefinitionGenerator;
import org.eclipse.edc.spi.monitor.Monitor;
import org.jetbrains.annotations.Nullable;
import org.seamware.edc.FDSCTransferControlExtension;

/**
 * Emits the DCP provisioning request for an FDSC-sourced data flow.
 *
 * <p>Registered as a provider generator, so it is selected on the data flow's <em>source</em>
 * address type; the emitted {@link ProvisionResource} type then selects the provisioner. Returns
 * {@code null} for transfer types this connector does not serve, which the generator manager drops.
 */
public class FDSCDcpProviderResourceDefinitionGenerator implements ResourceDefinitionGenerator {

  private final Monitor monitor;

  public FDSCDcpProviderResourceDefinitionGenerator(Monitor monitor) {
    this.monitor = monitor;
  }

  @Override
  public String supportedType() {
    return FDSCTransferControlExtension.FDSC_TYPE;
  }

  @Override
  public @Nullable ProvisionResource generate(DataFlow dataFlow) {
    if (!HTTP_DATA_PULL.equals(dataFlow.getTransferType().asString())) {
      return null;
    }

    monitor.debug(
        "Generate DCP resource definition for " + dataFlow.getAssetId() + " - " + dataFlow.getId());

    return ProvisionResource.Builder.newInstance()
        .flowId(dataFlow.getId())
        .type(FDSCDcpProvisioner.PROVISION_TYPE)
        .property(FDSCProvisioner.ASSET_ID_PROPERTY, dataFlow.getAssetId())
        .property(FDSCProvisioner.AGREEMENT_ID_PROPERTY, dataFlow.getAgreementId())
        .build();
  }
}
