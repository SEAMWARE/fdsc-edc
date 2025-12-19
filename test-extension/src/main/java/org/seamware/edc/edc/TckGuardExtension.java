/*
 *  Copyright (c) 2025 Metaform Systems, Inc.
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Metaform Systems, Inc. - initial API and implementation
 *
 */

package org.seamware.edc.edc;

import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationEvent;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.ContractNegotiationPendingGuard;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.TransferProcessPendingGuard;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessEvent;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.event.EventRouter;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.seamware.edc.TestConfig;

import static org.seamware.edc.edc.DataAssembly.*;

/**
 * Loads the transition guard.
 */
public class TckGuardExtension implements ServiceExtension {
    private static final String NAME = "DSP TCK Guard";

    private ContractNegotiationGuard negotiationGuard;

    private TransferProcessGuard transferProcessGuard;

    @Inject
    private ContractNegotiationStore store;

    @Inject
    private TransferProcessStore transferProcessStore;

    @Inject
    private TransactionContext transactionContext;

    @Inject
    private EventRouter router;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        TestConfig testConfig = TestConfig.fromConfig(context.getConfig());
        if (testConfig.isEnabled()) {
            context.registerService(TransferProcessPendingGuard.class, transferProcessPendingGuard());
            context.registerService(ContractNegotiationPendingGuard.class, negotiationGuard());
        }
    }

    public ContractNegotiationPendingGuard negotiationGuard() {
        var recorder = createNegotiationRecorder();

        var registry = new ContractNegotiationTriggerSubscriber(store, transactionContext);
        createNegotiationTriggers().forEach(registry::register);
        router.register(ContractNegotiationEvent.class, registry);

        negotiationGuard = new ContractNegotiationGuard(cn -> recorder.playNext(cn.getContractOffers().get(0).getAssetId(), cn), store);
        return negotiationGuard;
    }

    public TransferProcessPendingGuard transferProcessPendingGuard() {
        var recorder = createTransferProcessRecorder();

        var tpRegistry = new TransferProcessTriggerSubscriber(transferProcessStore);
        createTransferProcessTriggers().forEach(tpRegistry::register);
        router.register(TransferProcessEvent.class, tpRegistry);

        transferProcessGuard = new TransferProcessGuard(tp -> recorder.playNext(tp.getContractId(), tp), transferProcessStore);
        return transferProcessGuard;
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
