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
package org.seamware.edc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.dataplane.spi.DataFlow;
import org.eclipse.edc.spi.result.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TransferContextResolverTest {

  private static final String FLOW_ID = "transfer-1";
  private static final String CONTEXT_B = "context-b";
  private static final String DEFAULT_CONTEXT = "default-context";
  private static final String COUNTER_PARTY_ID = "did:web:counter-party.example";

  private final TransferProcessStore transferProcessStore = mock(TransferProcessStore.class);

  private static TransferProcess transfer(String participantContextId) {
    return TransferProcess.Builder.newInstance()
        .id(FLOW_ID)
        .participantContextId(participantContextId)
        .build();
  }

  private static Stream<Arguments> transfers() {
    return Stream.of(
        Arguments.of("transfer in B", transfer(CONTEXT_B), CONTEXT_B),
        Arguments.of("transfer without context", transfer(null), DEFAULT_CONTEXT),
        Arguments.of("transfer with blank context", transfer(" "), DEFAULT_CONTEXT),
        Arguments.of("no transfer found", null, null));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("transfers")
  void resolve_returnsTransferContextOrDefault(
      String description, TransferProcess transferProcess, String expectedContext) {
    when(transferProcessStore.findById(FLOW_ID)).thenReturn(transferProcess);
    // the counter-party id of the flow must never be taken as our context
    DataFlow dataFlow =
        DataFlow.Builder.newInstance().id(FLOW_ID).participantId(COUNTER_PARTY_ID).build();

    Result<String> result =
        new TransferContextResolver(transferProcessStore, DEFAULT_CONTEXT)
            .resolve(dataFlow.getId());

    if (expectedContext == null) {
      assertTrue(result.failed(), "An unknown transfer must not resolve to a guessed context.");
    } else {
      assertTrue(result.succeeded());
      assertEquals(expectedContext, result.getContent());
    }
  }

  @Test
  void resolve_failsWithoutContextAndDefault() {
    when(transferProcessStore.findById(FLOW_ID)).thenReturn(transfer(null));

    assertTrue(new TransferContextResolver(transferProcessStore, null).resolve(FLOW_ID).failed());
  }
}
