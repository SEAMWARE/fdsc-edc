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

import java.util.ArrayDeque;
import java.util.Deque;
import org.eclipse.edc.spi.monitor.Monitor;

/**
 * Tracks compensating actions for a saga (multi-step operation across independent services).
 * Compensations are registered after each successful write and executed in reverse order (LIFO) if
 * a subsequent step fails.
 */
public class SagaContext {

  private final Deque<CompensationStep> steps = new ArrayDeque<>();
  private final Monitor monitor;

  public SagaContext(Monitor monitor) {
    this.monitor = monitor;
  }

  /** Register a compensation action to run if a later step fails. */
  public void addCompensation(String description, Runnable compensation) {
    steps.push(new CompensationStep(description, compensation));
  }

  /** Run all registered compensations in reverse order (LIFO). Best-effort: logs failures. */
  public void compensate() {
    while (!steps.isEmpty()) {
      CompensationStep step = steps.pop();
      try {
        monitor.info("Compensating: " + step.description());
        step.action().run();
      } catch (Exception e) {
        monitor.severe("Compensation failed: " + step.description(), e);
      }
    }
  }

  /** Returns the number of registered compensation steps. */
  public int size() {
    return steps.size();
  }

  private record CompensationStep(String description, Runnable action) {}
}
