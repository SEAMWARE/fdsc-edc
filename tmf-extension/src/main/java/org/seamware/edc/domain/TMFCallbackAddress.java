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

public class TMFCallbackAddress {
  private String uri;
  private String authKey;
  private String authCodeId;

  public String getUri() {
    return uri;
  }

  public TMFCallbackAddress setUri(String uri) {
    this.uri = uri;
    return this;
  }

  public String getAuthKey() {
    return authKey;
  }

  public TMFCallbackAddress setAuthKey(String authKey) {
    this.authKey = authKey;
    return this;
  }

  public String getAuthCodeId() {
    return authCodeId;
  }

  public TMFCallbackAddress setAuthCodeId(String authCodeId) {
    this.authCodeId = authCodeId;
    return this;
  }
}
