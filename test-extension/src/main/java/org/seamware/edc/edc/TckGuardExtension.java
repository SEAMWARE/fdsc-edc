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
import org.eclipse.edc.connector.controlplane.asset.spi.index.AssetIndex;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationEvent;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.ContractNegotiationPendingGuard;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.offer.store.ContractDefinitionStore;
import org.eclipse.edc.connector.controlplane.policy.spi.store.PolicyDefinitionStore;
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

  /** Configuration property that controls whether the TMForum storage backend is enabled. */
  private static final String TMF_ENABLED_PROPERTY = "tmfExtension.enabled";

  /** EDC setting holding the participant id; drives the participant context id in classic mode. */
  private static final String PARTICIPANT_ID_SETTING = "edc.participant.id";

  private volatile ContractNegotiationGuard negotiationGuard;

  private volatile TransferProcessGuard transferProcessGuard;

  @Inject private ContractNegotiationStore store;

  @Inject private TransferProcessStore transferProcessStore;

  @Inject private TransactionContext transactionContext;

  @Inject private EventRouter router;

  @Inject private AssetIndex assetIndex;

  @Inject private ContractDefinitionStore contractDefinitionStore;

  @Inject private PolicyDefinitionStore policyDefinitionStore;

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
      boolean tmfEnabled =
          Boolean.parseBoolean(context.getConfig().getString(TMF_ENABLED_PROPERTY, "false"));
      if (tmfEnabled) {
        LOG.info(
            "TMForum extension is enabled — skipping in-memory test data initialization. "
                + "Assets, policies, contract definitions, and agreements are provided by TMForum.");
      } else {
        // initializeTestData(
        //     context.getConfig().getString(PARTICIPANT_ID_SETTING,
        //         ServiceExtensionContext.ANONYMOUS_PARTICIPANT));
      }
      registerProviderRequestedStateFix();
    }
  }

  /**
   * Populates the EDC in-memory stores with test assets, policies, contract definitions, and
   * pre-signed agreements required by the DSP TCK test scenarios. This method is only called when
   * the TMForum extension is disabled (i.e., EDC uses in-memory stores).
   *
   * @param participantId the connector's participant ID from the runtime configuration
   */
  private void initializeTestData(String participantId) {
    LOG.info("Initializing TCK test data for participant: " + participantId);

    // Create the permissive policy used by all test contract definitions
    policyDefinitionStore.create(DataAssembly.createPolicyDefinition(participantId));
    LOG.info("Created TCK policy definition: " + DataAssembly.POLICY_ID);

    // Create the contract definition that matches all test assets
    contractDefinitionStore.save(DataAssembly.createContractDefinition(participantId));
    LOG.info("Created TCK contract definition: " + DataAssembly.CONTRACT_DEFINITION_ID);

    // Create all test assets for catalog and negotiation scenarios
    var assets = DataAssembly.createAllAssets(participantId);
    assets.forEach(assetIndex::create);
    LOG.info("Created " + assets.size() + " TCK test assets");

    // Create finalized contract negotiations with agreements for transfer process tests
    var agreements = DataAssembly.createAllAgreements(participantId);
    agreements.forEach(store::save);
    LOG.info("Created " + agreements.size() + " TCK contract agreements");
  }

  /**
   * Contributes the no-op {@link NoopDataFlowController} as a default so transfer processes can
   * proceed through every state transition without a real data plane, which is what the DSP TCK
   * exercises.
   *
   * <p>0.16.0 removed {@code DataFlowManager}: a runtime now wires exactly one {@link
   * DataFlowController}, resolved by injection. Marking this provider {@code isDefault = true}
   * keeps it out of the way of a real controller — the {@code data-plane-signaling} flow controller
   * present in the shipped controlplane images wins, and the noop is only used when nothing else
   * provides one. A non-default provider would instead silently overwrite the real one by load
   * order (see docs/migration/EDC-0.18.1-UPGRADE.md §8.3).
   */
  @Provider(isDefault = true)
  public DataFlowController noopDataFlowController() {
    return new NoopDataFlowController(monitor);
  }

  /**
   * Registers a listener that transitions provider transfers from INITIAL to REQUESTED state. The
   * DSP 2025-1 protocol requires provider transfers to be in REQUESTED state after acknowledging a
   * TransferRequestMessage, but EDC 0.14.1 creates them in INITIAL. This listener fixes the state
   * before the transfer is persisted and returned in the DSP response.
   */
  private void registerProviderRequestedStateFix() {
    transferProcessObservable.registerListener(new ProviderRequestedStateFixListener(monitor));
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
