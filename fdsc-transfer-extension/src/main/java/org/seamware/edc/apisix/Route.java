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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Route {

  private String id;
  private String host;
  private String uri;
  private Map<String, ApisixPlugin> plugins;
  private Upstream upstream;

  public String getId() {
    return id;
  }

  public Route setId(String id) {
    this.id = id;
    return this;
  }

  public String getHost() {
    return host;
  }

  public Route setHost(String host) {
    this.host = host;
    return this;
  }

  public String getUri() {
    return uri;
  }

  public Route setUri(String uri) {
    this.uri = uri;
    return this;
  }

  public Map<String, ApisixPlugin> getPlugins() {
    return plugins;
  }

  public Route setPlugins(Map<String, ApisixPlugin> plugins) {
    this.plugins = plugins;
    return this;
  }

  public Upstream getUpstream() {
    return upstream;
  }

  public Route setUpstream(Upstream upstream) {
    this.upstream = upstream;
    return this;
  }
}
