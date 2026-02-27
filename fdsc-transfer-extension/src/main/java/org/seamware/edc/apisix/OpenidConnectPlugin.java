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
import com.fasterxml.jackson.annotation.OptBoolean;
import java.util.List;
import java.util.Map;

public class OpenidConnectPlugin extends ApisixPlugin {

  private boolean bearerOnly;
  private String clientId;
  private String clientSecret;
  private String discovery;
  private Map<String, Object> proxyOpts;
  private List<String> requiredScopes = null;
  private boolean sslVerify;
  private boolean useJwks;

  @JsonProperty("bearer_only")
  public boolean getBearerOnly() {
    return bearerOnly;
  }

  @JsonProperty("bearer_only")
  public OpenidConnectPlugin setBearerOnly(boolean bearerOnly) {
    this.bearerOnly = bearerOnly;
    return this;
  }

  @JsonProperty("client_id")
  public String getClientId() {
    return clientId;
  }

  @JsonProperty("client_id")
  public OpenidConnectPlugin setClientId(String clientId) {
    this.clientId = clientId;
    return this;
  }

  @JsonProperty("client_secret")
  public String getClientSecret() {
    return clientSecret;
  }

  @JsonProperty("client_secret")
  public OpenidConnectPlugin setClientSecret(String clientSecret) {
    this.clientSecret = clientSecret;
    return this;
  }

  public String getDiscovery() {
    return discovery;
  }

  public OpenidConnectPlugin setDiscovery(String discovery) {
    this.discovery = discovery;
    return this;
  }

  @JsonProperty("proxy_opts")
  public Map<String, Object> getProxyOpts() {
    return proxyOpts;
  }

  @JsonProperty("proxy_opts")
  public OpenidConnectPlugin setProxyOpts(Map<String, Object> proxyOpts) {
    this.proxyOpts = proxyOpts;
    return this;
  }

  @JsonProperty("ssl_verify")
  public boolean getSslVerify() {
    return sslVerify;
  }

  @JsonProperty("ssl_verify")
  public OpenidConnectPlugin setSslVerify(boolean sslVerify) {
    this.sslVerify = sslVerify;
    return this;
  }

  @JsonProperty("use_jwks")
  public boolean getUseJwks() {
    return useJwks;
  }

  @JsonProperty("use_jwks")
  public OpenidConnectPlugin setUseJwks(boolean useJwks) {
    this.useJwks = useJwks;
    return this;
  }

  @JsonProperty(value = "required_scopes", isRequired = OptBoolean.FALSE)
  public List<String> getRequiredScopes() {
    return requiredScopes;
  }

  @JsonProperty(value = "required_scopes", isRequired = OptBoolean.FALSE)
  public OpenidConnectPlugin setRequiredScopes(List<String> requiredScopes) {
    this.requiredScopes = requiredScopes;
    return this;
  }

  @JsonIgnore
  @Override
  public String getPluginName() {
    return "openid-connect";
  }
}
