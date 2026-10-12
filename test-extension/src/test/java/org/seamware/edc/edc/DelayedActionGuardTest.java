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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.spi.persistence.StateEntityStore;
import org.eclipse.edc.spi.result.StoreResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class DelayedActionGuardTest {

  private static final String NEGOTIATION_ID = "negotiation-1";
  private static final int ACTION_TIMEOUT_SECONDS = 30;
  private static final long SAVE_TIMEOUT_MS = 30_000;

  /** Long enough to cover the initial guard delay plus several lease retries. */
  private static final long NO_RETRY_WINDOW_MS = 3_000;

  /** Retry budget used in place of the production ones: long enough for several retries. */
  private static final long SHORT_BUDGET_MS = 1_500;

  /** {@link #SHORT_BUDGET_MS} plus slack, so the guard has certainly given up by the end of it. */
  private static final long GIVE_UP_WINDOW_MS = 2_500;

  /**
   * The state machine commonly still holds the lease when a queued action comes up, so a lease
   * failure must be retried rather than dropped — dropping it strands the entity as pending.
   */
  @ParameterizedTest
  @ValueSource(ints = {0, 1, 5})
  void runsActionOnceTheLeaseIsGranted(int leaseFailures) throws Exception {
    var negotiation = negotiation();
    // EDC core sets pending before the guarded entity is saved; the guard must clear it again.
    negotiation.setPending(true);
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

  /** A failure the lease cannot recover from must be reported at once, not waited out. */
  @ParameterizedTest
  @MethodSource("nonRetryableFailures")
  void dropsEntryOnNonRetryableStoreFailure(
      String name, Function<String, StoreResult<ContractNegotiation>> failure) {
    StateEntityStore<ContractNegotiation> store = mock();
    when(store.findByIdAndLease(NEGOTIATION_ID)).thenReturn(failure.apply(name));

    var actionRan = new CountDownLatch(1);
    var guard =
        new DelayedActionGuard<ContractNegotiation>(
            entity -> true, entity -> actionRan.countDown(), store);
    guard.start();
    try {
      assertTrue(guard.test(negotiation()));
      verify(store, after(NO_RETRY_WINDOW_MS)).findByIdAndLease(NEGOTIATION_ID);
      verify(store, never()).save(any(ContractNegotiation.class));
      assertEquals(1, actionRan.getCount());
    } finally {
      guard.stop();
    }
  }

  /**
   * The store reads through an eventually consistent backend, so an entity that is not visible yet
   * must be retried rather than dropped — dropping it strands the entity as pending.
   */
  @Test
  void retriesUntilTheEntityBecomesVisible() throws Exception {
    var negotiation = negotiation();
    negotiation.setPending(true);
    var attempts = new AtomicInteger();
    StateEntityStore<ContractNegotiation> store = mock();
    when(store.findByIdAndLease(NEGOTIATION_ID))
        .thenAnswer(
            invocation ->
                attempts.getAndIncrement() == 0
                    ? StoreResult.notFound("not visible yet")
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

  /**
   * A retryable failure that never clears must stop at its budget instead of retrying forever, and
   * must not run the action against an entity it never obtained.
   */
  @ParameterizedTest
  @MethodSource("expiringFailures")
  void givesUpOnceTheRetryBudgetExpires(
      String name, Function<String, StoreResult<ContractNegotiation>> failure) {
    var attempts = new AtomicInteger();
    StateEntityStore<ContractNegotiation> store = mock();
    when(store.findByIdAndLease(NEGOTIATION_ID))
        .thenAnswer(
            invocation -> {
              attempts.incrementAndGet();
              return failure.apply(name);
            });

    var actionRan = new CountDownLatch(1);
    var guard =
        new DelayedActionGuard<ContractNegotiation>(
            entity -> true,
            entity -> actionRan.countDown(),
            store,
            SHORT_BUDGET_MS,
            SHORT_BUDGET_MS);
    guard.start();
    try {
      assertTrue(guard.test(negotiation()));
      // The entry is retried while the budget lasts ...
      verify(store, after(GIVE_UP_WINDOW_MS).atLeast(2)).findByIdAndLease(NEGOTIATION_ID);
      // ... and not once more after it has run out.
      var attemptsAtGiveUp = attempts.get();
      verify(store, after(NO_RETRY_WINDOW_MS).times(attemptsAtGiveUp))
          .findByIdAndLease(NEGOTIATION_ID);
      verify(store, never()).save(any(ContractNegotiation.class));
      assertEquals(1, actionRan.getCount());
    } finally {
      guard.stop();
    }
  }

  private static Stream<Arguments> nonRetryableFailures() {
    return Stream.of(
        Arguments.of(
            "already exists",
            (Function<String, StoreResult<ContractNegotiation>>) StoreResult::alreadyExists),
        Arguments.of(
            "duplicate keys",
            (Function<String, StoreResult<ContractNegotiation>>) StoreResult::duplicateKeys));
  }

  private static Stream<Arguments> expiringFailures() {
    return Stream.of(
        Arguments.of(
            "already leased",
            (Function<String, StoreResult<ContractNegotiation>>) StoreResult::alreadyLeased),
        Arguments.of(
            "not found",
            (Function<String, StoreResult<ContractNegotiation>>) StoreResult::notFound));
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
