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
package org.seamware.edc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.connector.controlplane.services.spi.catalog.CatalogProtocolService;
import org.eclipse.edc.connector.controlplane.services.spi.protocol.ProtocolTokenValidator;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Requires;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.seamware.edc.store.TMFEdcMapper;
import org.seamware.edc.store.TMForumBackedCatalogProtocolService;
import org.seamware.edc.tmf.ProductCatalogApiClient;

/** Extension to provide the catalog with contents from TMForum */
@Requires(CatalogProtocolService.class)
public class CatalogProtocolServiceExtension implements ServiceExtension {

  private static final String NAME = "Protocol Service Extension";

  @Inject public ProductCatalogApiClient productCatalogApi;

  @Inject public Monitor monitor;
  @Inject public ObjectMapper objectMapper;

  @Inject public TMFEdcMapper tmfEdcMapper;

  @Inject public ProtocolTokenValidator protocolTokenValidator;

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public void initialize(ServiceExtensionContext context) {
    TMFConfig tmfConfig = TMFConfig.fromConfig(context.getConfig());
    if (tmfConfig.isEnabled() && tmfConfig.getCatalogConfig().enabled()) {
      context.registerService(
          CatalogProtocolService.class,
          new TMForumBackedCatalogProtocolService(
              tmfEdcMapper,
              productCatalogApi,
              context.getParticipantId(),
              monitor,
              protocolTokenValidator));
    } else {
      monitor.info("TMF Catalog Protocol Service is not enabled.");
    }
  }
}
