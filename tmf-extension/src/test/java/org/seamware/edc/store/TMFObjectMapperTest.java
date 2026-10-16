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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.stream.Stream;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Covers the string-to-enum state parsing, including the pre-0.18.1 transfer-state spelling. */
public class TMFObjectMapperTest {

  private static final String UNKNOWN_STATE = "NOT_A_STATE";

  private final TMFObjectMapper mapper = new TMFObjectMapperImpl();

  @ParameterizedTest(name = "mapTransferState({0}) -> {1}")
  @MethodSource("transferStates")
  public void mapTransferState_maps_every_persisted_spelling(
      String persisted, TransferProcessStates expected) {
    assertEquals(expected, mapper.mapTransferState(persisted));
  }

  @ParameterizedTest(name = "mapTransferState rejects {0}")
  @ValueSource(strings = {UNKNOWN_STATE, "", "preparation_requested"})
  public void mapTransferState_rejects_unknown_states(String persisted) {
    assertThrows(IllegalArgumentException.class, () -> mapper.mapTransferState(persisted));
  }

  private static Stream<Arguments> transferStates() {
    // Every current state maps to its own name; the legacy alias is the one exception, so it is
    // listed explicitly rather than derived.
    Stream<Arguments> currentStates =
        Arrays.stream(TransferProcessStates.values())
            .map(state -> Arguments.of(state.name(), state));
    Stream<Arguments> legacyAlias =
        Stream.of(
            Arguments.of(
                TMFObjectMapper.LEGACY_PROVISIONING_REQUESTED_STATE,
                TransferProcessStates.PREPARATION_REQUESTED));
    return Stream.concat(currentStates, legacyAlias);
  }
}
