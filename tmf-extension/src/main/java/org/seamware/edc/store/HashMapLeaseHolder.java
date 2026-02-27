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

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.persistence.Lease;

public class HashMapLeaseHolder implements LeaseHolder {

  private static final Duration DEFAULT_LEASE_TIME = Duration.ofSeconds(60);

  private final Monitor monitor;
  private final Clock clock;
  private final Map<String, Lease> leases = new HashMap<>();

  public HashMapLeaseHolder(Monitor monitor, Clock clock) {
    this.monitor = monitor;
    this.clock = clock;
  }

  @Override
  public void acquireLease(String id, String lockId, Duration leaseTime) {
    if (!isLeased(id) || isLeasedBy(id, lockId)) {
      monitor.info("Acquire lease " + id + " - " + lockId);
      leases.put(id, new Lease(lockId, clock.millis(), leaseTime.toMillis()));
    } else {
      throw new IllegalStateException("Cannot acquire lease, is already leased by someone else!");
    }
  }

  @Override
  public boolean isLeasedBy(String id, String lockId) {
    synchronized (leases) {
      return isLeased(id) && leases.get(id).getLeasedBy().equals(lockId);
    }
  }

  @Override
  public void freeLease(String id, String reason) {
    synchronized (leases) {
      monitor.info("Free lease " + id + " because " + reason);
      leases.remove(id);
    }
  }

  @Override
  public void acquireLease(String id, String lockId) {
    acquireLease(id, lockId, DEFAULT_LEASE_TIME);
  }

  @Override
  public boolean isLeased(String id) {
    synchronized (leases) {
      return leases.containsKey(id) && !leases.get(id).isExpired(clock.millis());
    }
  }
}
