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

import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;

/**
 * Registers transfer process triggers.
 */
public interface TransferProcessTriggerRegistry {

    void register(Trigger<TransferProcess> trigger);

}
