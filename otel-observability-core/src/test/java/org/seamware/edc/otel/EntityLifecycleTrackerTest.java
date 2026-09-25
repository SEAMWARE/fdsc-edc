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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

public class EntityLifecycleTrackerTest {

  @Test
  void keepsFirstStateAndTimestampUntilFinalState() {
    var tracker = new EntityLifecycleTracker(10, Set.of("DONE"));
    var first = tracker.track("e1", "START", 100);
    assertNull(first.previousState());
    assertFalse(first.isFinal());

    tracker.track("e1", "MIDDLE", 200);
    var last = tracker.track("e1", "DONE", 300);
    assertEquals("MIDDLE", last.previousState());
    assertEquals("START", last.firstState());
    assertEquals(100, last.firstSeenAt());
    assertTrue(last.isFinal());

    assertNull(tracker.track("e1", "START", 400).previousState());
  }

  @Test
  void evictsLeastRecentlyUpdatedEntity() {
    var tracker = new EntityLifecycleTracker(2, Set.of());
    tracker.track("e1", "A", 1);
    tracker.track("e2", "A", 2);
    tracker.track("e1", "B", 3);
    tracker.track("e3", "A", 4); // evicts e2

    assertEquals("B", tracker.track("e1", "C", 5).previousState());
    assertNull(tracker.track("e2", "B", 6).previousState());
  }
}
