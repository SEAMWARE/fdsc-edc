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

import java.util.Optional;
import org.eclipse.edc.connector.dataplane.spi.DataFlow;
import org.seamware.edc.store.TMFEdcMapper;

/** Helpers shared by the {@code EndpointDataReferenceService} implementations. */
final class FDSCEndpoints {

  private FDSCEndpoints() {}

  /**
   * Builds the public transfer endpoint returned in the EDR. The base is {@code
   * protocol://host/{dataFlowId}}; if the data flow source carries a {@code transferPath} property
   * (declared on the TMForum product spec and propagated through the asset DataAddress), it is
   * appended. Absent or blank path keeps the legacy behaviour.
   */
  static String buildEndpoint(TransferConfig transferConfig, DataFlow dataFlow) {
    String base =
        transferConfig.getTransferProtocol()
            + "://"
            + transferConfig.getTransferHost()
            + "/"
            + dataFlow.getId();
    String path =
        Optional.ofNullable(dataFlow.getSource())
            .map(source -> source.getStringProperty(TMFEdcMapper.TRANSFER_PATH_KEY))
            .filter(p -> !p.isBlank())
            .orElse(null);
    if (path == null) {
      return base;
    }
    return base + (path.startsWith("/") ? path : "/" + path);
  }
}
