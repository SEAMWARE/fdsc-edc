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

import java.time.Duration;

public interface LeaseHolder {

  /** Outcome of {@link LeaseHolder#releaseLease}. */
  enum LeaseRelease {
    RELEASED,
    NOT_LEASED,
    HELD_BY_OTHER
  }

  /**
   * Acquires the lease for {@code lockId}. Throws {@link LeaseContentionException} when another
   * holder owns an unexpired lease; any other failure signals a backend or data problem.
   */
  void acquireLease(String id, String lockId, Duration leaseTime);

  boolean isLeasedBy(String id, String lockId);

  /**
   * Frees the lease in a single backend pass, but only when {@code lockId} owns it, so a lease
   * another holder is working under is never stolen. The returned value says which case applied.
   */
  LeaseRelease releaseLease(String id, String lockId, String reason);

  void acquireLease(String id, String lockId);

  boolean isLeased(String id);
}
