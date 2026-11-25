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
package org.seamware.edc.edr;

import static org.eclipse.edc.api.management.ManagementApi.MANAGEMENT_API_CONTEXT;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

import jakarta.json.JsonObject;
import java.lang.reflect.Field;
import org.eclipse.edc.connector.api.management.configuration.ManagementApiConfigurationExtension;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.participant.spi.ParticipantIdMapper;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.system.Hostname;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.apiversion.ApiVersionService;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.transform.TypeTransformerRegistryImpl;
import org.eclipse.edc.transform.transformer.edc.from.JsonObjectFromDataAddressTransformer;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.PortMappingRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Guards the assumption that EDC's management-api-configuration already registers the DataAddress
 * transformer in the {@code management-api} context, so this extension must not register its own.
 */
@ExtendWith(MockitoExtension.class)
class ManagementApiTransformerContextTest {

  private static final String API_CONFIGURATION_FIELD = "apiConfiguration";
  private static final String API_CONFIGURATION_TYPE =
      ManagementApiConfigurationExtension.class.getName() + "$ManagementApiConfiguration";
  private static final int MANAGEMENT_PORT = 8181;
  private static final String MANAGEMENT_PATH = "/api/management";

  @Spy
  private TypeTransformerRegistryImpl transformerRegistry =
      new TypeTransformerRegistryImpl(mock(Monitor.class));

  @Mock private ServiceExtensionContext context;
  @Mock private WebService webService;
  @Mock private PortMappingRegistry portMappingRegistry;
  @Mock private TypeManager typeManager;
  @Mock private JsonLd jsonLd;
  @Mock private ParticipantIdMapper participantIdMapper;
  @Mock private Hostname hostname;
  @Mock private CriterionOperatorRegistry criterionOperatorRegistry;
  @Mock private ApiVersionService apiVersionService;
  @InjectMocks private ManagementApiConfigurationExtension managementApiConfiguration;

  @Test
  void managementApiContext_providesDataAddressTransformer() throws Exception {
    setApiConfiguration();

    managementApiConfiguration.initialize(context);

    assertInstanceOf(
        JsonObjectFromDataAddressTransformer.class,
        transformerRegistry
            .forContext(MANAGEMENT_API_CONTEXT)
            .transformerFor(
                DataAddress.Builder.newInstance().type("test").build(), JsonObject.class));
  }

  /** The settings record is package-private, so it is built reflectively. */
  private void setApiConfiguration() throws Exception {
    var type = Class.forName(API_CONFIGURATION_TYPE);
    var constructor = type.getDeclaredConstructor(int.class, String.class);
    constructor.setAccessible(true);
    Field field =
        ManagementApiConfigurationExtension.class.getDeclaredField(API_CONFIGURATION_FIELD);
    field.setAccessible(true);
    field.set(
        managementApiConfiguration, constructor.newInstance(MANAGEMENT_PORT, MANAGEMENT_PATH));
  }
}
