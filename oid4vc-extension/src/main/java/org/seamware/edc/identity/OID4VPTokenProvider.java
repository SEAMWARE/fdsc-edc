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
package org.seamware.edc.identity;

/*-
 * #%L
 * oid4vc-extension
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

import io.github.wistefan.oid4vp.OID4VPClient;
import io.github.wistefan.oid4vp.config.RequestParameters;
import io.github.wistefan.oid4vp.model.TokenResponse;
import java.util.concurrent.CompletableFuture;

/** Implementation of the TokenProvider interface, just handing over to the {@link OID4VPClient} */
public class OID4VPTokenProvider implements TokenProvider {

  private final OID4VPClient oid4VPClient;

  public OID4VPTokenProvider(OID4VPClient oid4VPClient) {
    this.oid4VPClient = oid4VPClient;
  }

  @Override
  public CompletableFuture<TokenResponse> getAccessToken(RequestParameters requestParameters) {
    return oid4VPClient.getAccessToken(requestParameters);
  }
}
