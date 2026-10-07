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

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import java.util.Map;
import org.eclipse.edc.spi.monitor.Monitor;

/**
 * Receives TMForum product catalog notifications. Always answer 204: a failure to audit must not
 * make TMForum retry the delivery.
 */
@Consumes(APPLICATION_JSON)
@Path("/")
public class TmfCatalogEventController {

  private final TmfCatalogEventAuditor auditor;
  private final Monitor monitor;

  public TmfCatalogEventController(TmfCatalogEventAuditor auditor, Monitor monitor) {
    this.auditor = auditor;
    this.monitor = monitor;
  }

  @POST
  public void onEvent(Map<String, Object> notification) {
    try {
      auditor.audit(notification == null ? Map.of() : notification);
    } catch (RuntimeException e) {
      monitor.warning("Otel Audit: failed to audit TMForum catalog event", e);
    }
  }
}
