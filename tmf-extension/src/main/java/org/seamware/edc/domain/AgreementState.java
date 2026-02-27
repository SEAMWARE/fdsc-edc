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

import java.util.Arrays;

public enum AgreementState {
  IN_PROCESS("inProcess"),
  AGREED("agreed"),
  REJECTED("rejected");

  private final String value;

  AgreementState(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public AgreementState fromValue(String value) {
    return Arrays.stream(values())
        .filter(v -> v.value.equals(value))
        .findAny()
        .orElseThrow(
            () -> new IllegalArgumentException(String.format("Unsupported value %s", value)));
  }
}
