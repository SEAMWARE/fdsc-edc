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
package org.seamware.edc.edc;

/*-
 * #%L
 * test-extension
 * %%
 * Copyright (C) 2025 - 2026 Seamless Middleware Technologies S.L
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Records and plays a sequence of steps. Sequences may be repeated if {@link #repeat()} is enabled.
 */
public class StepRecorder<T> {
  private final Map<String, Sequence<T>> sequences = new HashMap<>();

  public synchronized StepRecorder<T> playNext(String key, T entity) {
    var sequence = sequences.get(key);
    if (sequence != null) {
      sequence.playNext(entity);
    }
    return this;
  }

  public synchronized StepRecorder<T> record(String key, Consumer<T> step) {
    var sequence = sequences.computeIfAbsent(key, k -> new Sequence<>());
    sequence.addStep(step);
    return this;
  }

  public synchronized StepRecorder<T> repeat() {
    for (var sequence : sequences.values()) {
      sequence.repeat = true;
    }
    return this;
  }

  private static class Sequence<T> {
    private final List<Consumer<T>> steps = new ArrayList<>();
    private boolean repeat;
    private int playIndex = 0;

    /** Adds a step to this sequence. Must be called under external synchronization. */
    public synchronized void addStep(Consumer<T> step) {
      steps.add(step);
    }

    /** Plays the next step in the sequence. Thread-safe via synchronization. */
    public synchronized void playNext(T entity) {
      if (steps.isEmpty()) {
        throw new IllegalStateException("No replay steps");
      }
      if (playIndex >= steps.size()) {
        throw new IllegalStateException("Exceeded replay steps");
      }
      steps.get(playIndex).accept(entity);
      if (repeat && playIndex == steps.size() - 1) {
        playIndex = 0;
      } else {
        playIndex++;
      }
    }
  }
}
