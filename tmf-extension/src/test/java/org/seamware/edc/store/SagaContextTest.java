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
import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SagaContextTest {

  private Monitor monitor;
  private SagaContext saga;

  @BeforeEach
  void setUp() {
    monitor = mock(Monitor.class);
    saga = new SagaContext(monitor);
  }

  @Test
  void compensate_runs_steps_in_reverse_order() {
    List<String> order = new ArrayList<>();
    saga.addCompensation("first", () -> order.add("first"));
    saga.addCompensation("second", () -> order.add("second"));
    saga.addCompensation("third", () -> order.add("third"));

    saga.compensate();

    assertEquals(List.of("third", "second", "first"), order);
  }

  @Test
  void compensate_does_nothing_when_empty() {
    assertDoesNotThrow(() -> saga.compensate());
  }

  @Test
  void compensate_continues_after_step_failure() {
    List<String> executed = new ArrayList<>();
    saga.addCompensation("first", () -> executed.add("first"));
    saga.addCompensation(
        "failing",
        () -> {
          throw new RuntimeException("boom");
        });
    saga.addCompensation("third", () -> executed.add("third"));

    saga.compensate();

    assertEquals(List.of("third", "first"), executed);
    verify(monitor).severe(eq("Compensation failed: failing"), any(RuntimeException.class));
  }

  @Test
  void compensate_clears_steps_after_execution() {
    saga.addCompensation("step", () -> {});

    saga.compensate();
    assertEquals(0, saga.size());

    // Second compensate is a no-op
    saga.compensate();
  }

  @Test
  void size_tracks_registered_steps() {
    assertEquals(0, saga.size());

    saga.addCompensation("a", () -> {});
    assertEquals(1, saga.size());

    saga.addCompensation("b", () -> {});
    assertEquals(2, saga.size());
  }

  @Test
  void compensate_logs_each_step() {
    saga.addCompensation("revert quote", () -> {});
    saga.addCompensation("cancel order", () -> {});

    saga.compensate();

    verify(monitor).info("Compensating: cancel order");
    verify(monitor).info("Compensating: revert quote");
  }
}
