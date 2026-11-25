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
package org.seamware.edc.edr;

import org.eclipse.edc.edr.spi.types.EndpointDataReferenceEntry;
import org.eclipse.edc.spi.types.domain.DataAddress;

/**
 * Renews an EDR token before the management API serves it; the default is a no-op.
 *
 * <p>Implementations must follow a named data-plane contract, persist renewals with an absolute
 * expiry and only call refresh endpoints on the EDR endpoint's origin or an allow-list (ADR-14).
 */
@FunctionalInterface
public interface EdrTokenRefresher {

  /**
   * Returns the address to serve. Persists any renewal itself and returns {@code current} unchanged
   * when no renewal is due or the renewal fails.
   */
  DataAddress refreshIfNeeded(EndpointDataReferenceEntry entry, DataAddress current);
}
