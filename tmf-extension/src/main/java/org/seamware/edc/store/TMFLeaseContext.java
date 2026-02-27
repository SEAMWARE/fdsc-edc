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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.persistence.LeaseContext;
import org.eclipse.edc.transaction.spi.TransactionContext;

// TODO: reevaluate usage together with Usage as TransferProcess representation
public class TMFLeaseContext implements LeaseContext {

  private static final int LEASE_DURATION_IN_MS = 60000;

  private final Monitor monitor;
  private final TransactionContext transactionContext;

  private final ScheduledExecutorService leaseExecutor =
      Executors.newSingleThreadScheduledExecutor();
  private final List<String> leaseList = Collections.synchronizedList(new ArrayList<>());

  public TMFLeaseContext(Monitor monitor, TransactionContext transactionContext) {
    this.monitor = monitor;
    this.transactionContext = transactionContext;
  }

  @Override
  public void breakLease(String s) {
    transactionContext.execute(
        () -> {
          synchronized (leaseList) {
            if (leaseList.contains(s)) {
              leaseList.remove(s);
              monitor.debug(String.format("Broke lease %s", s));
            } else {
              monitor.debug("Lease does not exist " + s);
            }
          }
        });
  }

  @Override
  public void acquireLease(String entity) {
    transactionContext.execute(
        () -> {
          synchronized (leaseList) {
            if (leaseList.contains(entity)) {
              throw new IllegalStateException(String.format("%s is currently leased", entity));
            }
            leaseList.add(entity);
            monitor.debug("Acquired lease " + entity);
          }
          scheduleBreak(entity);
        });
  }

  private void scheduleBreak(String entity) {
    leaseExecutor.schedule(
        () -> {
          try {
            breakLease(entity);
          } catch (Exception e) {
            monitor.warning(String.format("Failed to break lease for %s. Reschedule.", entity), e);
            scheduleBreak(entity);
          }
        },
        LEASE_DURATION_IN_MS,
        TimeUnit.MILLISECONDS);
  }
}
