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
package org.seamware.edc.otel;

import java.util.Locale;
import java.util.Set;

/** State names derived from EDC contract negotiation and transfer process event names. */
public final class LifecycleStates {

  public static final String NEGOTIATION_EVENT_PREFIX = "contract.negotiation.";
  public static final String TRANSFER_EVENT_PREFIX = "transfer.process.";

  /** States after which no further events are expected for the entity. */
  public static final Set<String> NEGOTIATION_FINAL_STATES = Set.of("FINALIZED", "TERMINATED");

  public static final Set<String> TRANSFER_FINAL_STATES = Set.of("DEPROVISIONED");

  /**
   * First state emitted for a new negotiation: INITIATED on the consumer, REQUESTED on the
   * provider, OFFERED on a consumer receiving a provider-initiated offer.
   */
  public static final Set<String> NEGOTIATION_INITIAL_STATES =
      Set.of("INITIATED", "REQUESTED", "OFFERED");

  /** Both consumer and provider emit INITIATED when the transfer process is created. */
  public static final Set<String> TRANSFER_INITIAL_STATES = Set.of("INITIATED");

  private LifecycleStates() {}

  /** {@code transfer.process.provisioningRequested} -> {@code PROVISIONING_REQUESTED}. */
  public static String toState(String eventName, String prefix) {
    var suffix = eventName.startsWith(prefix) ? eventName.substring(prefix.length()) : eventName;
    return suffix.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
  }
}
