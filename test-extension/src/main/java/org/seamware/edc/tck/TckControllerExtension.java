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
package org.seamware.edc.tck;

/*-
 * #%L
 * test-extension
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

import org.eclipse.edc.connector.controlplane.services.spi.contractnegotiation.ContractNegotiationService;
import org.eclipse.edc.connector.controlplane.services.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.runtime.metamodel.annotation.Configuration;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.runtime.metamodel.annotation.Settings;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.WebServer;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.eclipse.edc.web.spi.configuration.PortMappingRegistry;
import org.seamware.edc.TestConfig;

public class TckControllerExtension implements ServiceExtension {

  private static final String NAME = "DSP TCK Controller";
  private static final String PROTOCOL = "tck";
  private static final String PATH = "/tck";
  private static final int PORT = 8687;
  @Inject private PortMappingRegistry mappingRegistry;
  @Inject private WebService webService;
  @Inject private WebServer webServer;
  @Inject private ContractNegotiationService negotiationService;
  @Inject private TransferProcessService transferProcessService;
  @Inject private Monitor monitor;
  @Configuration private TckWebhookApiConfiguration apiConfiguration;

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public void initialize(ServiceExtensionContext context) {
    TestConfig testConfig = TestConfig.fromConfig(context.getConfig());
    if (!testConfig.getControllerConfig().enabled()) {
      monitor.info("Test controller is not enabled.");
      return;
    }
    mappingRegistry.register(
        new PortMapping(PROTOCOL, apiConfiguration.port(), apiConfiguration.path()));
    webService.registerResource(
        PROTOCOL, new TckWebhookController(monitor, negotiationService, transferProcessService));
  }

  @Settings
  record TckWebhookApiConfiguration(
      @Setting(
              key = "web.http." + PROTOCOL + ".port",
              description = "Port for " + PROTOCOL + " api context",
              defaultValue = PORT + "")
          int port,
      @Setting(
              key = "web.http." + PROTOCOL + ".path",
              description = "Path for " + PROTOCOL + " api context",
              defaultValue = PATH)
          String path) {}
}
