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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.StoreFailure;
import org.eclipse.edc.spi.result.StoreResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TMFLeaseContextTest {

  private static final String ENTITY = "entity-1";
  private static final String OTHER_ENTITY = "entity-2";

  private TMFLeaseContext leaseContext;

  @BeforeEach
  void setUp() {
    // The real transaction context is used because both execute() overloads must be honoured:
    // acquireLease/breakLease return through the result-carrying one.
    leaseContext =
        new TMFLeaseContext(mock(Monitor.class), new TMFTransactionContext(mock(Monitor.class)));
  }

  @Test
  void acquireLease_succeeds_when_not_leased() {
    assertTrue(leaseContext.acquireLease(ENTITY).succeeded());
  }

  @Test
  void acquireLease_reports_already_leased_when_held() {
    assertTrue(leaseContext.acquireLease(ENTITY).succeeded());

    StoreResult<Void> result = leaseContext.acquireLease(ENTITY);

    assertTrue(result.failed(), "Acquiring a lease on an already-leased entity should fail.");
    assertEquals(StoreFailure.Reason.ALREADY_LEASED, result.reason());
  }

  @Test
  void breakLease_removes_lease() {
    leaseContext.acquireLease(ENTITY);

    assertTrue(leaseContext.breakLease(ENTITY).succeeded());
    assertTrue(
        leaseContext.acquireLease(ENTITY).succeeded(), "The entity should be re-acquirable.");
  }

  @Test
  void breakLease_is_safe_when_no_lease_exists() {
    assertTrue(leaseContext.breakLease("nonexistent").succeeded());
  }

  @Test
  void acquireLease_succeeds_for_different_entities() {
    assertTrue(leaseContext.acquireLease(ENTITY).succeeded());
    assertTrue(leaseContext.acquireLease(OTHER_ENTITY).succeeded());
  }
}
