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
package org.seamware.edc.otel.audit;

import org.eclipse.edc.spi.monitor.Monitor;

/**
 * Runs audit code that executes inside EDC processing (policy evaluation, DSP request validation),
 * so that a failure of the audit can never change or abort what EDC is doing.
 */
final class AuditGuard {

  private AuditGuard() {}

  /** Runs {@code audit}; anything it throws, except a {@link VirtualMachineError}, is logged. */
  static void run(Runnable audit, Monitor monitor, String description) {
    try {
      audit.run();
    } catch (VirtualMachineError e) {
      throw e;
    } catch (Throwable e) {
      monitor.warning("Otel Audit: failed to " + description, e);
    }
  }
}
