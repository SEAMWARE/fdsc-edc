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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.eclipse.edc.connector.dataplane.spi.provision.DeprovisionedResource;
import org.eclipse.edc.connector.dataplane.spi.provision.ProvisionResource;
import org.eclipse.edc.connector.dataplane.spi.provision.ProvisionedResource;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.response.ResponseStatus;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.web.spi.exception.BadGatewayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.seamware.edc.HttpClientException;
import org.seamware.edc.SchemaBaseUriHolder;
import org.seamware.edc.apisix.ApisixAdminClient;
import org.seamware.edc.apisix.Route;
import org.seamware.edc.tmf.ProductCatalogApiClient;

public class FDSCDcpProvisionerTest extends AbstractProvisionerTest {

  private ApisixAdminClient apisixAdminClient;
  private ProductCatalogApiClient productCatalogApiClient;
  private TransferMapper transferMapper;

  private FDSCDcpProvisioner provisioner;

  @BeforeEach
  public void setup() {

    SchemaBaseUriHolder.configure(URI.create("http://base.uri"));

    apisixAdminClient = mock(ApisixAdminClient.class);
    productCatalogApiClient = mock(ProductCatalogApiClient.class);
    transferMapper = mock(TransferMapper.class);

    provisioner =
        new FDSCDcpProvisioner(
            mock(Monitor.class),
            apisixAdminClient,
            productCatalogApiClient,
            transferMapper,
            new ObjectMapper(),
            dataPlaneStore,
            endpointDataReferenceService);
  }

  @Test
  public void testProvision_success() throws Exception {
    Route testRoute = getRoute();
    when(productCatalogApiClient.getProductSpecByExternalId(eq(TEST_ASSET_ID)))
        .thenReturn(
            Optional.of(getProductSpec(TEST_ASSET_ID, List.of(getUpstreamSpec(TEST_UPSTREAM)))));
    when(transferMapper.toDcpServiceRoute(any(), eq(TEST_UPSTREAM))).thenReturn(testRoute);

    ProvisionResource provisionResource = provisionResource();

    StatusResult<ProvisionedResource> result = provisioner.provision(provisionResource).get();
    assertTrue(result.succeeded(), "The resource should have been provisioned.");
    assertEquals(
        TEST_EDR,
        result.getContent().getDataAddress(),
        "The provisioned resource should carry the EDR of the flow.");
    ArgumentCaptor<Route> routeCaptor = ArgumentCaptor.forClass(Route.class);
    verify(apisixAdminClient, times(1)).addRoute(routeCaptor.capture());
    assertEquals(routeCaptor.getValue(), testRoute);
  }

  @Test
  public void testProvision_error_no_data_flow() throws Exception {
    when(productCatalogApiClient.getProductSpecByExternalId(eq(TEST_ASSET_ID)))
        .thenReturn(
            Optional.of(getProductSpec(TEST_ASSET_ID, List.of(getUpstreamSpec(TEST_UPSTREAM)))));
    when(dataPlaneStore.findById(any())).thenReturn(null);

    StatusResult<ProvisionedResource> result = provisioner.provision(provisionResource()).get();

    assertEquals(
        ResponseStatus.ERROR_RETRY,
        result.getFailure().status(),
        "Without the data flow, provisioning should be retried.");
    verify(apisixAdminClient, never()).addRoute(any());
  }

  @Test
  public void testProvision_error_edr_creation() throws Exception {
    when(productCatalogApiClient.getProductSpecByExternalId(eq(TEST_ASSET_ID)))
        .thenReturn(
            Optional.of(getProductSpec(TEST_ASSET_ID, List.of(getUpstreamSpec(TEST_UPSTREAM)))));
    when(endpointDataReferenceService.createEndpointDataReference(any()))
        .thenReturn(Result.failure("No signing key available."));

    StatusResult<ProvisionedResource> result = provisioner.provision(provisionResource()).get();

    assertEquals(
        ResponseStatus.ERROR_RETRY,
        result.getFailure().status(),
        "Without an EDR, provisioning should be retried.");
    verify(apisixAdminClient, never()).addRoute(any());
  }

  @Test
  public void testProvision_error_no_spec() throws Exception {
    when(productCatalogApiClient.getProductOfferingByExternalId(any()))
        .thenReturn(Optional.empty());
    StatusResult<ProvisionedResource> result = provisioner.provision(provisionResource()).get();

    assertTrue(
        result.fatalError(), "Without a corresponding spec, provisioning should fail permanently.");
  }

  @Test
  public void testProvision_error_no_upstream_key() throws Exception {
    when(productCatalogApiClient.getProductSpecByExternalId(any()))
        .thenReturn(Optional.of(getProductSpec(TEST_ASSET_ID, List.of())));
    StatusResult<ProvisionedResource> result = provisioner.provision(provisionResource()).get();

    assertTrue(
        result.fatalError(),
        "Without no configured upstream, provisioning should fail permanently.");
  }

  @Test
  public void testProvision_error_route_mapping() throws Exception {
    when(productCatalogApiClient.getProductSpecByExternalId(eq(TEST_ASSET_ID)))
        .thenReturn(
            Optional.of(getProductSpec(TEST_ASSET_ID, List.of(getUpstreamSpec(TEST_UPSTREAM)))));
    when(transferMapper.toDcpServiceRoute(any(), any()))
        .thenThrow(new RuntimeException("Failed to map"));
    StatusResult<ProvisionedResource> result = provisioner.provision(provisionResource()).get();

    assertTrue(
        result.fatalError(), "If no route can be built, provisioning should fail permanently.");
  }

  @Test
  public void testProvision_error_route_creation() throws Exception {
    when(productCatalogApiClient.getProductSpecByExternalId(eq(TEST_ASSET_ID)))
        .thenReturn(
            Optional.of(getProductSpec(TEST_ASSET_ID, List.of(getUpstreamSpec(TEST_UPSTREAM)))));
    when(transferMapper.toDcpServiceRoute(any(), any())).thenReturn(getRoute());
    when(apisixAdminClient.addRoute(any())).thenThrow(new BadGatewayException("Failed to add."));
    StatusResult<ProvisionedResource> result = provisioner.provision(provisionResource()).get();

    assertTrue(result.failed(), "If no route can be added, provisioning should fail but retried.");
    assertFalse(
        result.fatalError(), "If no route can be added, provisioning should fail but retried.");
  }

  @Test
  public void testDeprovision_success() throws Exception {
    when(transferMapper.toServiceRouteId(any())).thenReturn(TEST_TRANSFER_PROCESS_ID);

    StatusResult<DeprovisionedResource> result = provisioner.deprovision(provisionResource()).get();
    assertTrue(result.succeeded(), "The route should have been deprovisioned.");
    assertFalse(result.getContent().isPending(), "Deprovisioning should have finished");
    verify(apisixAdminClient, (times(1))).deleteRoute(eq(TEST_TRANSFER_PROCESS_ID));
  }

  @Test
  public void testDeprovision_success_if_already_deleted() throws Exception {
    when(transferMapper.toServiceRouteId(any())).thenReturn(TEST_TRANSFER_PROCESS_ID);
    doThrow(new HttpClientException("Not found.", 404)).when(apisixAdminClient).deleteRoute(any());
    StatusResult<DeprovisionedResource> result = provisioner.deprovision(provisionResource()).get();
    assertTrue(result.succeeded(), "The route should have been deprovisioned.");
    assertFalse(result.getContent().isPending(), "Deprovisioning should have finished");
    verify(apisixAdminClient, (times(1))).deleteRoute(eq(TEST_TRANSFER_PROCESS_ID));
  }

  @Test
  public void testDeprovision_failure_mapping() throws Exception {
    when(transferMapper.toServiceRouteId(any()))
        .thenThrow(new RuntimeException("Unable to resolve route id."));

    StatusResult<DeprovisionedResource> result = provisioner.deprovision(provisionResource()).get();
    assertTrue(
        result.fatalError(),
        "If the route id cannot be resolved, deprovisioning should fail permanently.");
  }

  @Test
  public void testDeprovision_failure_route_deletion() throws Exception {
    when(transferMapper.toServiceRouteId(any())).thenReturn(TEST_TRANSFER_PROCESS_ID);
    doThrow(new HttpClientException("Failed to delete"))
        .when(apisixAdminClient)
        .deleteRoute(eq(TEST_TRANSFER_PROCESS_ID));
    StatusResult<DeprovisionedResource> result = provisioner.deprovision(provisionResource()).get();
    assertTrue(
        result.failed(), "If apisix cannot delete, deprovisioning should fail but be retried.");
    assertFalse(
        result.fatalError(), "If apisix cannot delete, deprovisioning should fail but be retried.");
  }

  private static ProvisionResource provisionResource() {
    return getProvisionResource(FDSCDcpProvisioner.PROVISION_TYPE);
  }
}
