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

import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TMFLeaseContextTest {

  private TMFLeaseContext leaseContext;
  private Monitor monitor;

  @BeforeEach
  void setUp() {
    monitor = mock(Monitor.class);
    TransactionContext transactionContext = mock(TransactionContext.class);
    // Execute the block immediately — no real transaction needed
    doAnswer(
            invocation -> {
              TransactionContext.TransactionBlock block = invocation.getArgument(0);
              block.execute();
              return null;
            })
        .when(transactionContext)
        .execute(any(TransactionContext.TransactionBlock.class));

    leaseContext = new TMFLeaseContext(monitor, transactionContext);
  }

  @Test
  void acquireLease_succeeds_when_not_leased() {
    assertDoesNotThrow(() -> leaseContext.acquireLease("entity-1"));
  }

  @Test
  void acquireLease_throws_when_already_leased() {
    leaseContext.acquireLease("entity-1");

    assertThrows(
        IllegalStateException.class,
        () -> leaseContext.acquireLease("entity-1"),
        "Acquiring a lease on an already-leased entity should throw.");
  }

  @Test
  void breakLease_removes_lease() {
    leaseContext.acquireLease("entity-1");

    leaseContext.breakLease("entity-1");

    // After breaking, we should be able to acquire again
    assertDoesNotThrow(() -> leaseContext.acquireLease("entity-1"));
  }

  @Test
  void breakLease_is_safe_when_no_lease_exists() {
    // Breaking a lease that doesn't exist should not throw
    assertDoesNotThrow(() -> leaseContext.breakLease("nonexistent"));
  }

  @Test
  void acquireLease_succeeds_for_different_entities() {
    assertDoesNotThrow(() -> leaseContext.acquireLease("entity-1"));
    assertDoesNotThrow(() -> leaseContext.acquireLease("entity-2"));
  }

  @Test
  void acquireLease_succeeds_after_break() {
    leaseContext.acquireLease("entity-1");
    leaseContext.breakLease("entity-1");

    assertDoesNotThrow(
        () -> leaseContext.acquireLease("entity-1"),
        "After breaking a lease, re-acquisition should succeed.");
  }
}
