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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.spi.persistence.StateEntityStore;
import org.eclipse.edc.spi.result.StoreResult;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DelayedActionGuardTest {

  private static final String NEGOTIATION_ID = "negotiation-1";
  private static final int ACTION_TIMEOUT_SECONDS = 30;
  private static final long SAVE_TIMEOUT_MS = 30_000;

  /**
   * The state machine commonly still holds the lease when a queued action comes up, so a lease
   * failure must be retried rather than dropped — dropping it strands the entity as pending.
   */
  @ParameterizedTest
  @ValueSource(ints = {0, 1, 5})
  void runsActionOnceTheLeaseIsGranted(int leaseFailures) throws Exception {
    var negotiation = negotiation();
    var attempts = new AtomicInteger();
    StateEntityStore<ContractNegotiation> store = mock();
    when(store.findByIdAndLease(NEGOTIATION_ID))
        .thenAnswer(
            invocation ->
                attempts.getAndIncrement() < leaseFailures
                    ? StoreResult.alreadyLeased("still leased")
                    : StoreResult.success(negotiation));

    var actionRan = new CountDownLatch(1);
    var guard =
        new DelayedActionGuard<ContractNegotiation>(
            entity -> true, entity -> actionRan.countDown(), store);
    guard.start();
    try {
      assertTrue(guard.test(negotiation));
      assertTrue(actionRan.await(ACTION_TIMEOUT_SECONDS, SECONDS));
      verify(store, timeout(SAVE_TIMEOUT_MS)).save(any(ContractNegotiation.class));
      assertFalse(negotiation.isPending());
    } finally {
      guard.stop();
    }
  }

  private static ContractNegotiation negotiation() {
    return ContractNegotiation.Builder.newInstance()
        .id(NEGOTIATION_ID)
        .counterPartyId("counter-party")
        .counterPartyAddress("http://counter-party")
        .protocol("dataspace-protocol-http")
        .build();
  }
}
