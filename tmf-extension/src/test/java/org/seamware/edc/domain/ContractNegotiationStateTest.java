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
package org.seamware.edc.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Guards that {@link ContractNegotiationState#copy()} and {@code equals} cover every field. */
class ContractNegotiationStateTest {

  private static final String STRING_VALUE = "set";
  private static final long LONG_VALUE = 42L;

  static Stream<Field> stateFields() {
    return Arrays.stream(ContractNegotiationState.class.getDeclaredFields())
        .filter(f -> !Modifier.isStatic(f.getModifiers()));
  }

  @ParameterizedTest
  @MethodSource("stateFields")
  void copy_keeps_field(Field field) throws IllegalAccessException {
    field.setAccessible(true);
    ContractNegotiationState source = new ContractNegotiationState();
    field.set(source, nonDefaultValue(field));

    ContractNegotiationState copy = source.copy();

    assertEquals(field.get(source), field.get(copy));
    assertEquals(source, copy);
  }

  @ParameterizedTest
  @MethodSource("stateFields")
  void equals_compares_field(Field field) throws IllegalAccessException {
    field.setAccessible(true);
    ContractNegotiationState changed = new ContractNegotiationState();
    field.set(changed, nonDefaultValue(field));

    assertNotEquals(new ContractNegotiationState(), changed);
  }

  private static Object nonDefaultValue(Field field) {
    Class<?> type = field.getType();
    if (type == boolean.class) {
      return true;
    }
    if (type == long.class) {
      return LONG_VALUE;
    }
    if (type == String.class) {
      return STRING_VALUE;
    }
    throw new IllegalStateException("Add a test value for field type " + type);
  }
}
