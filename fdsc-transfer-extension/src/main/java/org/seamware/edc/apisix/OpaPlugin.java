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
package org.seamware.edc.apisix;

/*-
 * #%L
 * fdsc-transfer-extension
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class OpaPlugin extends ApisixPlugin {

  private String host;
  private String policy;
  private boolean withBody;
  private boolean withRoute;

  public String getHost() {
    return host;
  }

  public OpaPlugin setHost(String host) {
    this.host = host;
    return this;
  }

  public String getPolicy() {
    return policy;
  }

  public OpaPlugin setPolicy(String policy) {
    this.policy = policy;
    return this;
  }

  @JsonProperty("with_body")
  public boolean getWithBody() {
    return withBody;
  }

  @JsonProperty("with_body")
  public OpaPlugin setWithBody(boolean withBody) {
    this.withBody = withBody;
    return this;
  }

  @JsonProperty("with_route")
  public boolean getWithRoute() {
    return withRoute;
  }

  @JsonProperty("with_route")
  public OpaPlugin setWithRoute(boolean withRoute) {
    this.withRoute = withRoute;
    return this;
  }

  @JsonIgnore
  @Override
  public String getPluginName() {
    return "opa";
  }
}
