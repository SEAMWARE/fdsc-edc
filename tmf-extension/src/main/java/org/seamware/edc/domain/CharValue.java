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

import com.fasterxml.jackson.annotation.JsonProperty;

public class CharValue {

  private Object tmfValue;
  private boolean isDefault;

  public Object getTmfValue() {
    return tmfValue;
  }

  public CharValue setTmfValue(Object tmfValue) {
    this.tmfValue = tmfValue;
    return this;
  }

  @JsonProperty("isDefault")
  public boolean isDefault() {
    return isDefault;
  }

  @JsonProperty("isDefault")
  public CharValue setDefault(boolean aDefault) {
    isDefault = aDefault;
    return this;
  }
}
