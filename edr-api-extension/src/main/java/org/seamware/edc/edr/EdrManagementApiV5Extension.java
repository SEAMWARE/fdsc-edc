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

import jakarta.json.Json;
import java.util.Map;
import org.eclipse.edc.api.auth.spi.AuthorizationService;
import org.eclipse.edc.edr.spi.store.EndpointDataReferenceStore;
import org.eclipse.edc.edr.spi.types.EndpointDataReferenceEntry;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.seamware.edc.edr.transform.JsonObjectFromEndpointDataReferenceEntryTransformer;

/** Exposes the EDR store through the participant-context scoped management API (v5beta). */
@Extension(EdrManagementApiV5Extension.NAME)
public class EdrManagementApiV5Extension implements ServiceExtension {

  public static final String NAME = "FDSC EDR Management API v5 Extension";

  @Inject private EndpointDataReferenceStore endpointDataReferenceStore;

  @Inject private AuthorizationService authorizationService;

  @Inject private TypeTransformerRegistry transformerRegistry;

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public void initialize(ServiceExtensionContext context) {
    authorizationService.addLookupFunction(
        EndpointDataReferenceEntry.class, new EdrOwnershipLookup(endpointDataReferenceStore));
    // DataAddress -> JsonObject is already registered in this context by
    // management-api-configuration.
    transformerRegistry
        .forContext(MANAGEMENT_API_CONTEXT)
        .register(
            new JsonObjectFromEndpointDataReferenceEntryTransformer(
                Json.createBuilderFactory(Map.of())));
  }
}
