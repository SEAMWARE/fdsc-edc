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

import org.eclipse.edc.connector.dataplane.spi.DataFlow;
import org.eclipse.edc.connector.dataplane.spi.edr.EndpointDataReferenceService;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.seamware.edc.transfer.FDSCDataAddress;

public class FDSCOid4VpEndpointDataReferenceService implements EndpointDataReferenceService {

  public static final String ENDPOINT_TYPE = "https://w3id.org/idsa/v4.1/HTTP";

  private final TransferConfig transferConfig;

  public FDSCOid4VpEndpointDataReferenceService(TransferConfig transferConfig) {
    this.transferConfig = transferConfig;
  }

  @Override
  public Result<DataAddress> createEndpointDataReference(DataFlow dataFlow) {

    var fdscDataAddressBuilder =
        FDSCDataAddress.Builder.newInstance()
            .clientId(dataFlow.getId())
            .type(ENDPOINT_TYPE)
            .property(
                EDC_NAMESPACE + "endpoint",
                "http://" + transferConfig.getTransferHost() + "/" + dataFlow.getId())
            .property(EDC_NAMESPACE + "endpointType", ENDPOINT_TYPE);

    return Result.success(fdscDataAddressBuilder.build());
  }

  @Override
  public ServiceResult<Void> revokeEndpointDataReference(String s, String s1) {
    // nothing to be revoked, since token handling happens at the OID4VC level
    return ServiceResult.success();
  }
}
