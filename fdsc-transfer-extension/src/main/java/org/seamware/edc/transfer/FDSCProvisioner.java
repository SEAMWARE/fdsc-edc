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
package org.seamware.edc.transfer;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.function.Consumer;
import org.eclipse.edc.connector.controlplane.transfer.spi.provision.Provisioner;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.ProvisionedResource;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.ResourceDefinition;
import org.eclipse.edc.spi.monitor.Monitor;
import org.seamware.edc.HttpClientException;
import org.seamware.edc.apisix.ApisixAdminClient;
import org.seamware.edc.tmf.ProductCatalogApiClient;

public abstract class FDSCProvisioner<RD extends ResourceDefinition, PR extends ProvisionedResource>
    implements Provisioner<RD, PR> {

  protected static final String SERVICE_CONFIGURATION_KEY = "serviceConfiguration";
  protected static final String TARGET_KEY = "targetSpecification";
  protected static final String UPSTREAM_KEY = "upstreamAddress";
  protected static final String ODRL_TARGET_KEY = "target";
  protected static final String ODRL_UID = "odrl:uid";

  protected final Monitor monitor;
  protected final ApisixAdminClient apisixAdminClient;
  protected final ProductCatalogApiClient productCatalogApiClient;
  protected final TransferMapper transferMapper;
  protected final ObjectMapper objectMapper;

  protected FDSCProvisioner(
      Monitor monitor,
      ApisixAdminClient apisixAdminClient,
      ProductCatalogApiClient productCatalogApiClient,
      TransferMapper transferMapper,
      ObjectMapper objectMapper) {
    this.monitor = monitor;
    this.apisixAdminClient = apisixAdminClient;
    this.productCatalogApiClient = productCatalogApiClient;
    this.transferMapper = transferMapper;
    this.objectMapper = objectMapper;
  }

  protected void executeDeletion(Consumer<String> deletionMethod, String idToDelete) {

    try {
      deletionMethod.accept(idToDelete);
    } catch (HttpClientException e) {
      if (e.getStatusCode().isPresent() && e.getStatusCode().get() == 404) {
        // If the request fails because no such service exists, we don't care
        monitor.info(
            String.format("Was not able to delete entity %s because it does not exist", idToDelete),
            e);
      } else {
        // bubble the exception
        throw e;
      }
    }
  }
}
