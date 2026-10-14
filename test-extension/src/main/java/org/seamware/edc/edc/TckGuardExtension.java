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

import static org.seamware.edc.edc.DataAssembly.*;

import java.util.logging.Logger;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationEvent;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.ContractNegotiationPendingGuard;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.TransferProcessPendingGuard;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessEvent;
import org.eclipse.edc.connector.controlplane.transfer.spi.flow.DataFlowController;
import org.eclipse.edc.connector.controlplane.transfer.spi.observe.TransferProcessObservable;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.event.EventRouter;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.seamware.edc.TestConfig;

/** Loads the transition guard and initializes TCK test data in the EDC stores. */
public class TckGuardExtension implements ServiceExtension {
  private static final Logger LOG = Logger.getLogger(TckGuardExtension.class.getName());
  private static final String NAME = "DSP TCK Guard";

  private volatile ContractNegotiationGuard negotiationGuard;

  private volatile TransferProcessGuard transferProcessGuard;

  @Inject private ContractNegotiationStore store;

  @Inject private TransferProcessStore transferProcessStore;

  @Inject private TransactionContext transactionContext;

  @Inject private EventRouter router;

  @Inject private Monitor monitor;
  @Inject private TransferProcessObservable transferProcessObservable;

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public void initialize(ServiceExtensionContext context) {
    TestConfig testConfig = TestConfig.fromConfig(context.getConfig());
    if (testConfig.isEnabled()) {
      // The TCK runs against the TMForum storage backend (tmfExtension.enabled=true), which
      // supplies assets, policies, contract definitions and agreements — see
      // config/tck/edc.properties and scripts/init-tmforum.sh. No in-memory seeding is wired.
      registerProviderRequestedStateFix();
    }
  }

  /**
   * Contributes the no-op {@link NoopDataFlowController} as a default {@link DataFlowController}.
   *
   * <p>0.16.0 removed {@code DataFlowManager}: a runtime now wires exactly one {@link
   * DataFlowController}, resolved by injection, and a default provider is consulted only when no
   * non-default one supplied the service. This is Option B of docs/migration/EDC-0.18.1-UPGRADE.md
   * §8.3.2: {@code test-extension} ships in the same shaded jar as the production images, and both
   * controlplane modules declare {@code data-plane-signaling} (controlplane-oid4vc/pom.xml:199,
   * controlplane-dcp/pom.xml:189), whose flow controller is a non-default provider. So in the
   * shipped assembly — including TCK mode — the signaling controller always wins and this noop is
   * never selected; it only takes effect in a hypothetical runtime that excludes every signaling
   * module. {@code isDefault = true} is what keeps it from silently overwriting the real controller
   * by load order.
   *
   * <p>Consequence for Step 8: TCK transfers run through the real signaling flow controller with no
   * data plane behind it, so Step 8 must confirm a transfer actually reaches {@code STARTED} under
   * this assembly and diff the transfer scenarios against docs/migration/tck-baseline-0.14.1.md. If
   * the signaling flow cannot complete without a data plane — or any scenario regresses — take
   * Option A (a TCK-only runtime that excludes {@code data-plane-signaling} so this noop is actually
   * selected) rather than assuming it unnecessary.
   */
  @Provider(isDefault = true)
  public DataFlowController noopDataFlowController() {
    return new NoopDataFlowController(monitor);
  }

  /**
   * Registers a listener that transitions provider transfers from INITIAL to REQUESTED state. The
   * DSP 2025-1 protocol requires provider transfers to be in REQUESTED state after acknowledging a
   * TransferRequestMessage, but EDC 0.18.1 still creates them in INITIAL. This listener fixes the
   * state while the transfer is being created and returned in the DSP response, re-persisting it
   * through {@code transferProcessStore} so the stored row and the acknowledgement agree.
   */
  private void registerProviderRequestedStateFix() {
    transferProcessObservable.registerListener(
        new ProviderRequestedStateFixListener(monitor, transferProcessStore));
    LOG.info("Registered ProviderRequestedStateFixListener for DSP 2025-1 compliance");
  }

  @Provider
  public ContractNegotiationPendingGuard negotiationGuard(ServiceExtensionContext context) {

    TestConfig testConfig = TestConfig.fromConfig(context.getConfig());
    if (testConfig.isEnabled()) {
      var recorder = createNegotiationRecorder();

      var registry = new ContractNegotiationTriggerSubscriber(store, transactionContext);
      createNegotiationTriggers().forEach(registry::register);
      router.register(ContractNegotiationEvent.class, registry);

      negotiationGuard =
          new ContractNegotiationGuard(
              cn -> recorder.playNext(cn.getContractOffers().get(0).getAssetId(), cn), store);
      return negotiationGuard;
    }
    return it -> false;
  }

  @Provider
  public TransferProcessPendingGuard transferProcessPendingGuard(ServiceExtensionContext context) {
    TestConfig testConfig = TestConfig.fromConfig(context.getConfig());
    if (testConfig.isEnabled()) {
      var recorder = createTransferProcessRecorder();

      var tpRegistry =
          new TransferProcessTriggerSubscriber(transferProcessStore, transactionContext);
      createTransferProcessTriggers().forEach(tpRegistry::register);
      router.register(TransferProcessEvent.class, tpRegistry);

      transferProcessGuard =
          new TransferProcessGuard(
              tp -> recorder.playNext(tp.getContractId(), tp), transferProcessStore);
      return transferProcessGuard;
    }
    return it -> false;
  }

  @Override
  public void prepare() {
    if (negotiationGuard != null) {
      negotiationGuard.start();
    }
    if (transferProcessGuard != null) {
      transferProcessGuard.start();
    }
  }

  @Override
  public void shutdown() {
    if (negotiationGuard != null) {
      negotiationGuard.stop();
    }
    if (transferProcessGuard != null) {
      transferProcessGuard.stop();
    }
  }
}
