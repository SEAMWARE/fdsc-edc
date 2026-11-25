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
import static org.eclipse.edc.web.spi.configuration.ApiContext.MANAGEMENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.eclipse.edc.api.auth.spi.AuthorizationService;
import org.eclipse.edc.edr.spi.store.EndpointDataReferenceStore;
import org.eclipse.edc.edr.spi.types.EndpointDataReferenceEntry;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.eclipse.edc.web.jersey.providers.jsonld.JerseyJsonLdInterceptor;
import org.eclipse.edc.web.spi.WebService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.seamware.edc.edr.transform.JsonObjectFromEndpointDataReferenceEntryTransformer;

@ExtendWith(MockitoExtension.class)
class EdrManagementApiV5ExtensionTest {

  @Mock private EndpointDataReferenceStore endpointDataReferenceStore;
  @Mock private AuthorizationService authorizationService;
  @Mock private TypeTransformerRegistry transformerRegistry;
  @Mock private WebService webService;
  @Mock private JsonObjectValidatorRegistry validatorRegistry;
  @Mock private JsonLd jsonLd;
  @Mock private TypeManager typeManager;
  @InjectMocks private EdrManagementApiV5Extension extension;

  @Test
  void initialize_registersLookupTransformerAndController() {
    var managementApiRegistry = mock(TypeTransformerRegistry.class);
    when(transformerRegistry.forContext(MANAGEMENT_API_CONTEXT)).thenReturn(managementApiRegistry);
    var context = mock(ServiceExtensionContext.class);
    when(context.getMonitor()).thenReturn(mock(Monitor.class));

    extension.initialize(context);

    verify(authorizationService)
        .addLookupFunction(eq(EndpointDataReferenceEntry.class), any(EdrOwnershipLookup.class));
    verify(managementApiRegistry)
        .register(any(JsonObjectFromEndpointDataReferenceEntryTransformer.class));
    verify(webService).registerResource(eq(MANAGEMENT), any(EdrApiV5Controller.class));
    verify(webService)
        .registerDynamicResource(
            eq(MANAGEMENT), eq(EdrApiV5Controller.class), any(JerseyJsonLdInterceptor.class));
  }

  @Test
  void name_isStable() {
    assertEquals(EdrManagementApiV5Extension.NAME, extension.name());
  }
}
