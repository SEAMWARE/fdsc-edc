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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TmfCatalogEventControllerTest {

  private TmfCatalogEventAuditor auditor;
  private Monitor monitor;
  private TmfCatalogEventController controller;

  @BeforeEach
  void setUp() {
    auditor = mock(TmfCatalogEventAuditor.class);
    monitor = mock(Monitor.class);
    controller = new TmfCatalogEventController(auditor, new ObjectMapper(), monitor);
  }

  @Test
  void validBodyIsParsedAndAudited() {
    controller.onEvent(
        """
        {"eventType":"ProductSpecificationCreateEvent",
         "event":{"productSpecification":{"id":"spec-1","lifecycleStatus":"Active"}},
         "tags":["a","b"]}
        """);
    verify(auditor)
        .audit(
            Map.of(
                "eventType",
                "ProductSpecificationCreateEvent",
                "event",
                Map.of("productSpecification", Map.of("id", "spec-1", "lifecycleStatus", "Active")),
                "tags",
                List.of("a", "b")));
    verifyNoInteractions(monitor);
  }

  @Test
  void nullBodyIsAuditedAsEmptyNotification() {
    assertDoesNotThrow(() -> controller.onEvent(null));

    verify(auditor).audit(Map.of());
    verifyNoInteractions(monitor);
  }

  @Test
  void blankBodyIsAuditedAsEmptyNotification() {
    assertDoesNotThrow(() -> controller.onEvent(""));
    assertDoesNotThrow(() -> controller.onEvent(" \n\t "));

    verify(auditor, times(2)).audit(Map.of());
    verifyNoInteractions(monitor);
  }

  @Test
  void malformedBodyIsSwallowedAndNeverAudited() {
    assertDoesNotThrow(() -> controller.onEvent("{\"eventType\":"));

    verify(auditor, never()).audit(anyMap());
    verify(monitor).warning(anyString(), any(IllegalArgumentException.class));
  }

  @Test
  void nonObjectBodyIsSwallowedAndNeverAudited() {
    assertDoesNotThrow(() -> controller.onEvent("[1,2,2]"));

    verify(auditor, never()).audit(anyMap());
    verify(monitor).warning(anyString(), any(IllegalArgumentException.class));
  }

  @Test
  void auditorFailureIsSwallowed() {
    var failure = new IllegalStateException("boom");
    doThrow(failure).when(auditor).audit(anyMap());

    assertDoesNotThrow(() -> controller.onEvent("{\"eventType\":\"ProductOfferingCreateEvent\"}"));

    verify(auditor).audit(Map.of("eventType", "ProductOfferingCreateEvent"));
    verify(monitor).warning(anyString(), eq(failure));
  }
}
