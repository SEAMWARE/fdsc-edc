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
package org.seamware.edc.services;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import org.eclipse.edc.spi.monitor.Monitor;

/**
 * JAX-RS request filter that injects a default {@code Authorization} header when none is present.
 * This allows the DSP TCK (which does not send authentication tokens) to pass the EDC's token null
 * check in {@code DspRequestHandlerImpl.createResource}.
 *
 * <p>The injected token is a JSON object containing the TCK participant ID, matching the format
 * expected by {@link TestIdentityService#verifyJwtToken}.
 */
public class DefaultTokenRequestFilter implements ContainerRequestFilter {

  private final Monitor monitor;

  /** Default participant ID injected when no Authorization header is present. */
  private static final String TCK_PARTICIPANT_ID = "TCK_PARTICIPANT";

  /** JSON token format matching TestIdentityService expectations. */
  private static final String DEFAULT_TOKEN = String.format("{\"id\": \"%s\"}", TCK_PARTICIPANT_ID);

  public DefaultTokenRequestFilter(Monitor monitor) {
    this.monitor = monitor;
  }

  @Override
  public void filter(ContainerRequestContext requestContext) {
    var authHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
    monitor.warning("Filter - got " + authHeader);
    if (authHeader == null || authHeader.isBlank()) {
      requestContext.getHeaders().putSingle(HttpHeaders.AUTHORIZATION, DEFAULT_TOKEN);
    }
  }
}
