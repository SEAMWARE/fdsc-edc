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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Remembers, per entity, the last state seen and when (and in which state) it was first seen. EDC
 * events do not carry the previous state, so this bounded LRU is the only source for it. Entries
 * are dropped once a final state is reached.
 *
 * <p>Each subscriber must own its instance: EDC dispatches async subscribers independtly, so a
 * shared instance would make one subscriber's view depend on the other's timing.
 */
public class EntityLifecycleTracker {

  /**
   * @param previousState last state seen before this one, {@code null} if the entity is new to us
   * @param firstState first state seen for the entity (this one if it is new)
   * @param firstSeenAt epoch millis of the first event seen for the entity
   * @param isFinal wheter this state ends the entity lifecycle
   */
  public record Transition(
      String previousState, String firstState, long firstSeenAt, boolean isFinal) {}

  private record Entry(String state, String firstState, long firstSeenAt) {}

  private final Set<String> finalStates;
  private final Map<String, Entry> entries;

  public EntityLifecycleTracker(int maxEntries, Set<String> finalStates) {
    this.finalStates = finalStates;
    this.entries =
        new LinkedHashMap<>() {
          @Override
          protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
            return size() > maxEntries;
          }
        };
  }

  /** Records {@code newState} observed at {@code at} (epoch millis) for the entity. */
  public synchronized Transition track(String entityId, String newState, long at) {
    // remove + put keeps the most recently updated entity at the tail (LRU eviction order)
    var previous = entries.remove(entityId);
    var firstState = previous == null ? newState : previous.firstState();
    var firstSeenAt = previous == null ? at : previous.firstSeenAt();
    var isFinal = finalStates.contains(newState);
    if (!isFinal) {
      entries.put(entityId, new Entry(newState, firstState, firstSeenAt));
    }
    return new Transition(
        previous == null ? null : previous.state(), firstState, firstSeenAt, isFinal);
  }
}
