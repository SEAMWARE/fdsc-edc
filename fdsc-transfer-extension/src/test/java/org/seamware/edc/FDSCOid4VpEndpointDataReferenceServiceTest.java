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

/*-
 * #%L
 * fdsc-transfer-extension
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

import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.edc.connector.dataplane.spi.DataFlow;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FDSCOid4VpEndpointDataReferenceServiceTest {

  private static final String TEST_TRANSFER_HOST = "transfer.host";

  private FDSCOid4VpEndpointDataReferenceService fdscOid4VpEndpointDataReferenceService;

  @BeforeEach
  public void setup() {
    TransferConfig transferConfig =
        TransferConfig.Builder.newInstance()
            .transferHost(TEST_TRANSFER_HOST)
            .transferProtocol("https")
            .build();

    fdscOid4VpEndpointDataReferenceService =
        new FDSCOid4VpEndpointDataReferenceService(transferConfig);
  }

  @Test
  public void testCreateEndpointReference_noPath() {
    Result<DataAddress> dataAddressResult =
        fdscOid4VpEndpointDataReferenceService.createEndpointDataReference(getDataFlow());
    assertTrue(dataAddressResult.succeeded(), "The data address should have been returned.");

    DataAddress dataAddress = dataAddressResult.getContent();
    assertEquals(
        "https://transfer.host/my-flow", dataAddress.getStringProperty(EDC_NAMESPACE + "endpoint"));
    assertEquals(
        "https://w3id.org/idsa/v4.1/HTTP",
        dataAddress.getStringProperty(EDC_NAMESPACE + "endpointType"));
    assertEquals("my-flow", dataAddress.getStringProperty("clientId"));
  }

  @Test
  public void testCreateEndpointReference_appendsTransferPath() {
    DataAddress dataAddress =
        fdscOid4VpEndpointDataReferenceService
            .createEndpointDataReference(getDataFlow("/ngsi-ld/v1/entities?type=CrowdFlowObserved"))
            .getContent();

    assertEquals(
        "https://transfer.host/my-flow/ngsi-ld/v1/entities?type=CrowdFlowObserved",
        dataAddress.getStringProperty(EDC_NAMESPACE + "endpoint"));
  }

  @Test
  public void testCreateEndpointReference_normalizesMissingLeadingSlash() {
    DataAddress dataAddress =
        fdscOid4VpEndpointDataReferenceService
            .createEndpointDataReference(getDataFlow("ngsi-ld/v1/entities"))
            .getContent();

    assertEquals(
        "https://transfer.host/my-flow/ngsi-ld/v1/entities",
        dataAddress.getStringProperty(EDC_NAMESPACE + "endpoint"));
  }

  private static DataFlow getDataFlow() {
    return DataFlow.Builder.newInstance().id("my-flow").build();
  }

  private static DataFlow getDataFlow(String transferPath) {
    return DataFlow.Builder.newInstance()
        .id("my-flow")
        .source(
            DataAddress.Builder.newInstance()
                .type("FDSC")
                .property("transferPath", transferPath)
                .build())
        .build();
  }
}
