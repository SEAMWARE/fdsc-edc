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

import java.util.Set;
import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.transfer.spi.flow.DataFlowController;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.DataFlowResponse;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.jetbrains.annotations.NotNull;

/**
 * A no-op {@link DataFlowController} for DSP TCK conformance testing. Returns success for all
 * transfer operations without starting any actual data flow, allowing the transfer process state
 * machine to proceed through its states and send the correct DSP protocol messages.
 */
public class NoopDataFlowController implements DataFlowController {

  private final Monitor monitor;

  /** Transfer type handled by this controller in TCK mode. */
  private static final String TRANSFER_TYPE_HTTP_PULL = "HttpData-PULL";

  /** Dummy data address type returned for PULL transfers. */
  private static final String DATA_ADDRESS_TYPE = "HttpData";

  /** Dummy endpoint URL included in TransferStartMessage data addresses. */
  private static final String NOOP_ENDPOINT = "http://noop-dataplane.local/pull";

  public NoopDataFlowController(Monitor monitor) {
    this.monitor = monitor;
  }

  @Override
  public boolean canHandle(TransferProcess transferProcess) {
    return true;
  }

  @Override
  public @NotNull StatusResult<DataFlowResponse> provision(
      TransferProcess transferProcess, Policy policy) {
    monitor.warning("Provision process");
    return StatusResult.success(
        DataFlowResponse.Builder.newInstance()
            .dataAddress(noopDataAddress())
            .provisioning(false)
            .build());
  }

  @Override
  public @NotNull StatusResult<DataFlowResponse> start(
      TransferProcess transferProcess, Policy policy) {
    monitor.warning("Start process");
    return StatusResult.success(
        DataFlowResponse.Builder.newInstance()
            .dataAddress(noopDataAddress())
            .provisioning(false)
            .build());
  }

  @Override
  public @NotNull StatusResult<Void> suspend(TransferProcess transferProcess) {
    monitor.warning("Suspend process");
    return StatusResult.success();
  }

  @Override
  public @NotNull StatusResult<Void> terminate(TransferProcess transferProcess) {
    monitor.warning("Terminate process");
    return StatusResult.success();
  }

  @Override
  public Set<String> transferTypesFor(Asset asset) {

    monitor.warning("Types for asset");
    return Set.of(TRANSFER_TYPE_HTTP_PULL);
  }

  private DataAddress noopDataAddress() {
    monitor.warning("Get address " + NOOP_ENDPOINT);
    return DataAddress.Builder.newInstance()
        .type(DATA_ADDRESS_TYPE)
        .property("endpoint", NOOP_ENDPOINT)
        .build();
  }
}
