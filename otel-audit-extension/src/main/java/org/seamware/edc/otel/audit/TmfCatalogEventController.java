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
package org.seamware.edc.otel.audit;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import java.util.Map;
import org.eclipse.edc.spi.monitor.Monitor;

/**
 * Receives TMForum product catalog notifications. Always answer 204: a failure to audit must not
 * make TMForum retry the delivery. The body is parsed manually (instead of letting JAX-RS
 * deserialize the {@code Map} parameter) so a malformed payload is also swallowed here instead of
 * failing before this method runs
 */
@Consumes(APPLICATION_JSON)
@Path("/")
public class TmfCatalogEventController {

  private final TmfCatalogEventAuditor auditor;
  private final ObjectMapper objectMapper;
  private final Monitor monitor;

  public TmfCatalogEventController(
      TmfCatalogEventAuditor auditor, ObjectMapper objectMapper, Monitor monitor) {
    this.auditor = auditor;
    this.objectMapper = objectMapper;
    this.monitor = monitor;
  }

  @POST
  public void onEvent(String rawBody) {
    try {
      auditor.audit(parse(rawBody));
    } catch (RuntimeException e) {
      monitor.warning("Otel Audit: failed to audit TMForum catalog event", e);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> parse(String rawBody) {
    if (rawBody == null || rawBody.isBlank()) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(rawBody, Map.class);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("invalid TMForum notification payload", e);
    }
  }
}
