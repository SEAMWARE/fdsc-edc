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
package org.seamware.edc.store;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TMFTransactionContextTest {

  private Monitor monitor;
  private TMFTransactionContext txContext;

  @BeforeEach
  void setUp() {
    monitor = mock(Monitor.class);
    txContext = new TMFTransactionContext(monitor);
  }

  @Test
  void execute_block_runs_successfully() {
    AtomicBoolean ran = new AtomicBoolean(false);
    txContext.execute((TransactionContext.TransactionBlock) () -> ran.set(true));
    assertTrue(ran.get());
  }

  @Test
  void execute_result_block_returns_value() {
    String result = txContext.execute(() -> "hello");
    assertEquals("hello", result);
  }

  @Test
  void execute_compensates_on_exception() {
    AtomicBoolean compensated = new AtomicBoolean(false);

    assertThrows(
        RuntimeException.class,
        () ->
            txContext.execute(
                (TransactionContext.TransactionBlock)
                    () -> {
                      txContext.currentSaga().addCompensation("undo", () -> compensated.set(true));
                      throw new RuntimeException("failure");
                    }));

    assertTrue(compensated.get());
  }

  @Test
  void execute_does_not_compensate_on_success() {
    AtomicBoolean compensated = new AtomicBoolean(false);

    txContext.execute(
        (TransactionContext.TransactionBlock)
            () -> txContext.currentSaga().addCompensation("undo", () -> compensated.set(true)));

    assertFalse(compensated.get());
  }

  @Test
  void execute_result_compensates_on_exception() {
    AtomicBoolean compensated = new AtomicBoolean(false);

    assertThrows(
        RuntimeException.class,
        () ->
            txContext.execute(
                () -> {
                  txContext.currentSaga().addCompensation("undo", () -> compensated.set(true));
                  throw new RuntimeException("failure");
                }));

    assertTrue(compensated.get());
  }

  @Test
  void nested_execute_joins_existing_saga() {
    List<String> compensated = new ArrayList<>();

    assertThrows(
        RuntimeException.class,
        () ->
            txContext.execute(
                (TransactionContext.TransactionBlock)
                    () -> {
                      txContext
                          .currentSaga()
                          .addCompensation("outer", () -> compensated.add("outer"));
                      txContext.execute(
                          (TransactionContext.TransactionBlock)
                              () -> {
                                txContext
                                    .currentSaga()
                                    .addCompensation("inner", () -> compensated.add("inner"));
                                throw new RuntimeException("inner failure");
                              });
                    }));

    // Both compensations run — inner failure propagates to outer which triggers compensate
    assertEquals(List.of("inner", "outer"), compensated);
  }

  @Test
  void nested_execute_does_not_compensate_independently() {
    List<String> compensated = new ArrayList<>();

    txContext.execute(
        (TransactionContext.TransactionBlock)
            () -> {
              txContext.currentSaga().addCompensation("outer", () -> compensated.add("outer"));
              txContext.execute(
                  (TransactionContext.TransactionBlock)
                      () ->
                          txContext
                              .currentSaga()
                              .addCompensation("inner", () -> compensated.add("inner")));
              // Inner succeeds, outer continues — no compensation yet
            });

    // Everything succeeded — no compensation
    assertTrue(compensated.isEmpty());
  }

  @Test
  void saga_is_null_outside_transaction() {
    assertNull(txContext.currentSaga());
  }

  @Test
  void saga_is_available_during_transaction() {
    txContext.execute(
        (TransactionContext.TransactionBlock) () -> assertNotNull(txContext.currentSaga()));
  }

  @Test
  void saga_is_cleaned_up_after_transaction() {
    txContext.execute((TransactionContext.TransactionBlock) () -> {});
    assertNull(txContext.currentSaga());
  }

  @Test
  void saga_is_cleaned_up_after_failed_transaction() {
    try {
      txContext.execute(
          (TransactionContext.TransactionBlock)
              () -> {
                throw new RuntimeException("fail");
              });
    } catch (RuntimeException ignored) {
    }

    assertNull(txContext.currentSaga());
  }

  @Test
  void synchronization_called_on_success() {
    AtomicBoolean synced = new AtomicBoolean(false);

    txContext.execute(
        (TransactionContext.TransactionBlock)
            () -> txContext.registerSynchronization(() -> synced.set(true)));

    assertTrue(synced.get());
  }

  @Test
  void synchronization_not_called_on_failure() {
    AtomicBoolean synced = new AtomicBoolean(false);

    try {
      txContext.execute(
          (TransactionContext.TransactionBlock)
              () -> {
                txContext.registerSynchronization(() -> synced.set(true));
                throw new RuntimeException("fail");
              });
    } catch (RuntimeException ignored) {
    }

    assertFalse(synced.get());
  }

  @Test
  void exception_propagates_after_compensation() {
    RuntimeException original = new RuntimeException("original");

    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () ->
                txContext.execute(
                    (TransactionContext.TransactionBlock)
                        () -> {
                          txContext.currentSaga().addCompensation("undo", () -> {});
                          throw original;
                        }));

    assertSame(original, thrown);
  }
}
