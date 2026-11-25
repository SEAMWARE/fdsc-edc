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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.eclipse.edc.api.auth.spi.AuthorizationService;
import org.eclipse.edc.edr.spi.store.EndpointDataReferenceStore;
import org.eclipse.edc.edr.spi.types.EndpointDataReferenceEntry;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EdrManagementApiV5ExtensionTest {

  @Mock private EndpointDataReferenceStore endpointDataReferenceStore;
  @Mock private AuthorizationService authorizationService;
  @InjectMocks private EdrManagementApiV5Extension extension;

  @Test
  void initialize_registersEdrEntryOwnershipLookup() {
    extension.initialize(mock(ServiceExtensionContext.class));

    verify(authorizationService)
        .addLookupFunction(eq(EndpointDataReferenceEntry.class), any(EdrOwnershipLookup.class));
  }

  @Test
  void name_isStable() {
    assertEquals(EdrManagementApiV5Extension.NAME, extension.name());
  }
}
