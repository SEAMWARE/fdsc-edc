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
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.DeprovisionedResource;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.ProvisionResponse;
import org.eclipse.edc.jsonld.TitaniumJsonLd;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.response.StatusResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.seamware.edc.HttpClientException;
import org.seamware.edc.apisix.ApisixAdminClient;
import org.seamware.edc.apisix.Route;
import org.seamware.edc.ccs.CredentialsConfigServiceClient;
import org.seamware.edc.pap.OdrlPapClient;
import org.seamware.edc.store.TMFEdcMapper;
import org.seamware.edc.tmf.ProductCatalogApiClient;
import org.seamware.pap.model.PolicyPathVO;
import org.seamware.pap.model.ServiceCreateVO;

public class FDSCOID4VPProvisionerTest extends AbstractProvisionerTest {

  private static final String TEST_POLICY_PATH = "policy-path";

  private ApisixAdminClient apisixAdminClient;
  private CredentialsConfigServiceClient credentialsConfigServiceClient;
  private OdrlPapClient odrlPapClient;
  private ProductCatalogApiClient productCatalogApiClient;
  private TransferMapper transferMapper;
  private TMFEdcMapper tmfEdcMapper;
  private JsonLd jsonLd;

  private FDSCOID4VPProvisioner provisioner;

  @BeforeEach
  public void setup() {
    apisixAdminClient = mock(ApisixAdminClient.class);
    credentialsConfigServiceClient = mock(CredentialsConfigServiceClient.class);
    odrlPapClient = mock(OdrlPapClient.class);
    productCatalogApiClient = mock(ProductCatalogApiClient.class);
    transferMapper = mock(TransferMapper.class);
    tmfEdcMapper = mock(TMFEdcMapper.class);
    jsonLd = new TitaniumJsonLd(mock(Monitor.class));

    provisioner =
        new FDSCOID4VPProvisioner(
            mock(Monitor.class),
            apisixAdminClient,
            credentialsConfigServiceClient,
            odrlPapClient,
            productCatalogApiClient,
            transferMapper,
            new ObjectMapper(),
            tmfEdcMapper,
            jsonLd);
  }

  @Test
  public void testProvision_success() throws Exception {
    ServiceCreateVO expectedService = new ServiceCreateVO().id(TEST_TRANSFER_PROCESS_ID);
    Policy testPolicy = getTestPolicy();
    Route serviceRoute = getRoute("service");
    Route wellKnownRoute = getRoute("well-known");

    when(productCatalogApiClient.getProductSpecByExternalId(eq(TEST_ASSET_ID)))
        .thenReturn(
            Optional.of(getProductSpec(TEST_ASSET_ID, List.of(getUpstreamSpec(TEST_UPSTREAM)))));
    when(odrlPapClient.createService(eq(expectedService)))
        .thenReturn(new PolicyPathVO().policyPath(TEST_POLICY_PATH));
    when(tmfEdcMapper.fromEdcPolicy(eq(testPolicy)))
        .thenAnswer(
            invocation -> {
              Policy policy = invocation.getArgument(0);
              if (policy
                  .getExtensibleProperties()
                  .get("odrl:uid")
                  .equals(TEST_TRANSFER_PROCESS_ID)) {
                return getTestOdrlPolicy(TEST_TRANSFER_PROCESS_ID);
              }
              return Assertions.fail("The process id needs to be included in the policy.");
            });

    when(transferMapper.toOid4VpServiceRoute(any(), eq(TEST_UPSTREAM), eq(TEST_POLICY_PATH)))
        .thenReturn(serviceRoute);
    when(transferMapper.toWellknownRoute(any())).thenReturn(wellKnownRoute);

    StatusResult<ProvisionResponse> result =
        provisioner.provision(getResourceDefinition(), testPolicy).get();
    assertTrue(result.succeeded(), "Provisioning should have succeeded.");

    ArgumentCaptor<Map<String, Object>> policyCaptor = ArgumentCaptor.forClass(Map.class);
    ArgumentCaptor<String> serviceIdCaptor = ArgumentCaptor.forClass(String.class);
    verify(odrlPapClient, times(1)).createPolicy(serviceIdCaptor.capture(), policyCaptor.capture());
    assertEquals(TEST_TRANSFER_PROCESS_ID, serviceIdCaptor.getValue());
    assertEquals(TEST_TRANSFER_PROCESS_ID, policyCaptor.getValue().get("odrl:uid"));

    verify(apisixAdminClient, times(1)).addRoute(eq(serviceRoute));
    verify(apisixAdminClient, times(1)).addRoute(eq(wellKnownRoute));
  }

  @Test
  public void testProvision_error_no_spec() throws Exception {
    when(productCatalogApiClient.getProductOfferingByExternalId(any()))
        .thenReturn(Optional.empty());
    StatusResult<ProvisionResponse> result =
        provisioner.provision(getResourceDefinition(), getTestPolicy()).get();

    assertTrue(
        result.fatalError(), "Without a corresponding spec, provisioning should fail permanently.");
  }

  @Test
  public void testProvision_error_no_upstream_key() throws Exception {
    when(productCatalogApiClient.getProductSpecByExternalId(any()))
        .thenReturn(Optional.of(getProductSpec(TEST_ASSET_ID, List.of())));
    StatusResult<ProvisionResponse> result =
        provisioner.provision(getResourceDefinition(), getTestPolicy()).get();

    assertTrue(
        result.fatalError(),
        "Without no configured upstream, provisioning should fail permanently.");
  }

  @Test
  public void testProvision_error_pap_service_creation() throws Exception {
    when(productCatalogApiClient.getProductSpecByExternalId(eq(TEST_ASSET_ID)))
        .thenReturn(
            Optional.of(getProductSpec(TEST_ASSET_ID, List.of(getUpstreamSpec(TEST_UPSTREAM)))));
    when(odrlPapClient.createService(any()))
        .thenThrow(new HttpClientException("Failed to create service."));

    StatusResult<ProvisionResponse> result =
        provisioner.provision(getResourceDefinition(), getTestPolicy()).get();

    assertTrue(
        result.failed(),
        "When failing to create service, provisioning should fail but be retried.");
    assertFalse(
        result.fatalError(),
        "When failing to create service, provisioning should fail but be retried.");
  }

  @Test
  public void testProvision_error_pap_service_creation_400() throws Exception {
    when(productCatalogApiClient.getProductSpecByExternalId(eq(TEST_ASSET_ID)))
        .thenReturn(
            Optional.of(getProductSpec(TEST_ASSET_ID, List.of(getUpstreamSpec(TEST_UPSTREAM)))));
    when(odrlPapClient.createService(any()))
        .thenThrow(new HttpClientException("Failed to create service.", 400));

    StatusResult<ProvisionResponse> result =
        provisioner.provision(getResourceDefinition(), getTestPolicy()).get();

    assertTrue(
        result.fatalError(),
        "If we cannot send a valid request, provisioning should fail permanently.");
  }

  @Test
  public void testProvision_error_route_mapping_service() throws Exception {
    ServiceCreateVO expectedService = new ServiceCreateVO().id(TEST_TRANSFER_PROCESS_ID);
    Policy testPolicy = getTestPolicy();

    when(productCatalogApiClient.getProductSpecByExternalId(eq(TEST_ASSET_ID)))
        .thenReturn(
            Optional.of(getProductSpec(TEST_ASSET_ID, List.of(getUpstreamSpec(TEST_UPSTREAM)))));
    when(odrlPapClient.createService(eq(expectedService)))
        .thenReturn(new PolicyPathVO().policyPath(TEST_POLICY_PATH));
    when(tmfEdcMapper.fromEdcPolicy(eq(testPolicy)))
        .thenAnswer(
            invocation -> {
              Policy policy = invocation.getArgument(0);
              if (policy
                  .getExtensibleProperties()
                  .get("odrl:uid")
                  .equals(TEST_TRANSFER_PROCESS_ID)) {
                return getTestOdrlPolicy(TEST_TRANSFER_PROCESS_ID);
              }
              return Assertions.fail("The process id needs to be included in the policy.");
            });

    when(transferMapper.toOid4VpServiceRoute(any(), eq(TEST_UPSTREAM), eq(TEST_POLICY_PATH)))
        .thenThrow(new RuntimeException("Failed to map."));

    StatusResult<ProvisionResponse> result =
        provisioner.provision(getResourceDefinition(), getTestPolicy()).get();

    assertTrue(
        result.fatalError(),
        "When no service route can be built, provisioning should fail permanently.");
  }

  @Test
  public void testProvision_error_route_mapping_well_known() throws Exception {
    ServiceCreateVO expectedService = new ServiceCreateVO().id(TEST_TRANSFER_PROCESS_ID);
    Policy testPolicy = getTestPolicy();

    when(productCatalogApiClient.getProductSpecByExternalId(eq(TEST_ASSET_ID)))
        .thenReturn(
            Optional.of(getProductSpec(TEST_ASSET_ID, List.of(getUpstreamSpec(TEST_UPSTREAM)))));
    when(odrlPapClient.createService(eq(expectedService)))
        .thenReturn(new PolicyPathVO().policyPath(TEST_POLICY_PATH));
    when(tmfEdcMapper.fromEdcPolicy(eq(testPolicy)))
        .thenAnswer(
            invocation -> {
              Policy policy = invocation.getArgument(0);
              if (policy
                  .getExtensibleProperties()
                  .get("odrl:uid")
                  .equals(TEST_TRANSFER_PROCESS_ID)) {
                return getTestOdrlPolicy(TEST_TRANSFER_PROCESS_ID);
              }
              return Assertions.fail("The process id needs to be included in the policy.");
            });

    when(transferMapper.toOid4VpServiceRoute(any(), eq(TEST_UPSTREAM), eq(TEST_POLICY_PATH)))
        .thenReturn(getRoute());
    when(transferMapper.toWellknownRoute(any())).thenThrow(new RuntimeException("Failed to map."));

    StatusResult<ProvisionResponse> result =
        provisioner.provision(getResourceDefinition(), getTestPolicy()).get();

    assertTrue(
        result.fatalError(),
        "When no well-known route can be built, provisioning should fail permanently.");
  }

  @Test
  public void testProvision_error_route_creation() throws Exception {
    ServiceCreateVO expectedService = new ServiceCreateVO().id(TEST_TRANSFER_PROCESS_ID);
    Policy testPolicy = getTestPolicy();

    when(productCatalogApiClient.getProductSpecByExternalId(eq(TEST_ASSET_ID)))
        .thenReturn(
            Optional.of(getProductSpec(TEST_ASSET_ID, List.of(getUpstreamSpec(TEST_UPSTREAM)))));
    when(odrlPapClient.createService(eq(expectedService)))
        .thenReturn(new PolicyPathVO().policyPath(TEST_POLICY_PATH));
    when(tmfEdcMapper.fromEdcPolicy(eq(testPolicy)))
        .thenAnswer(
            invocation -> {
              Policy policy = invocation.getArgument(0);
              if (policy
                  .getExtensibleProperties()
                  .get("odrl:uid")
                  .equals(TEST_TRANSFER_PROCESS_ID)) {
                return getTestOdrlPolicy(TEST_TRANSFER_PROCESS_ID);
              }
              return Assertions.fail("The process id needs to be included in the policy.");
            });

    when(transferMapper.toOid4VpServiceRoute(any(), eq(TEST_UPSTREAM), eq(TEST_POLICY_PATH)))
        .thenReturn(getRoute());
    when(transferMapper.toWellknownRoute(any())).thenReturn(getRoute());

    doThrow(new HttpClientException("Apisix unreachable")).when(apisixAdminClient).addRoute(any());

    StatusResult<ProvisionResponse> result =
        provisioner.provision(getResourceDefinition(), getTestPolicy()).get();

    assertTrue(
        result.failed(), "When failing to create route, provisioning should fail but be retried.");
    assertFalse(
        result.fatalError(),
        "When failing to create route, provisioning should fail but be retried.");
  }

  @Test
  public void testProvision_error_policy_mapping_fails() throws Exception {

    when(productCatalogApiClient.getProductSpecByExternalId(eq(TEST_ASSET_ID)))
        .thenReturn(
            Optional.of(getProductSpec(TEST_ASSET_ID, List.of(getUpstreamSpec(TEST_UPSTREAM)))));
    when(odrlPapClient.createService(any()))
        .thenReturn(new PolicyPathVO().policyPath(TEST_POLICY_PATH));
    when(tmfEdcMapper.fromEdcPolicy(any())).thenThrow(new RuntimeException("Mapping failed."));

    StatusResult<ProvisionResponse> result =
        provisioner.provision(getResourceDefinition(), getTestPolicy()).get();
    assertTrue(
        result.fatalError(),
        "When the policy cannot be mapped, provisioning should fail permanently.");
  }

  @Test
  public void testDeprovision_succeed() throws Exception {

    when(transferMapper.toWellKnownRouteId(any())).thenReturn("well-known");
    when(transferMapper.toServiceRouteId(any())).thenReturn("service");

    StatusResult<DeprovisionedResource> result =
        provisioner
            .deprovision(getProvisionedResource(TEST_TRANSFER_PROCESS_ID), getTestPolicy())
            .get();

    assertTrue(result.succeeded(), "The resource should be deprovisioned.");

    verify(odrlPapClient, times(1)).deleteService(eq(TEST_TRANSFER_PROCESS_ID));
    verify(apisixAdminClient, times(1)).deleteRoute(eq("well-known"));
    verify(apisixAdminClient, times(1)).deleteRoute(eq("service"));
    verify(credentialsConfigServiceClient, times(1)).deleteService(eq(TEST_TRANSFER_PROCESS_ID));
  }

  @Test
  public void testDeprovision_succeed_pap_404() throws Exception {

    when(transferMapper.toWellKnownRouteId(any())).thenReturn("well-known");
    when(transferMapper.toServiceRouteId(any())).thenReturn("service");

    doThrow(new HttpClientException("Not found", 404)).when(odrlPapClient).deleteService(any());

    StatusResult<DeprovisionedResource> result =
        provisioner
            .deprovision(getProvisionedResource(TEST_TRANSFER_PROCESS_ID), getTestPolicy())
            .get();

    assertTrue(result.succeeded(), "The resource should be deprovisioned.");

    verify(odrlPapClient, times(1)).deleteService(eq(TEST_TRANSFER_PROCESS_ID));
    verify(apisixAdminClient, times(1)).deleteRoute(eq("well-known"));
    verify(apisixAdminClient, times(1)).deleteRoute(eq("service"));
    verify(credentialsConfigServiceClient, times(1)).deleteService(eq(TEST_TRANSFER_PROCESS_ID));
  }

  @Test
  public void testDeprovision_succeed_apisix_404() throws Exception {

    when(transferMapper.toWellKnownRouteId(any())).thenReturn("well-known");
    when(transferMapper.toServiceRouteId(any())).thenReturn("service");

    doThrow(new HttpClientException("Not found", 404)).when(apisixAdminClient).deleteRoute(any());

    StatusResult<DeprovisionedResource> result =
        provisioner
            .deprovision(getProvisionedResource(TEST_TRANSFER_PROCESS_ID), getTestPolicy())
            .get();

    assertTrue(result.succeeded(), "The resource should be deprovisioned.");

    verify(odrlPapClient, times(1)).deleteService(eq(TEST_TRANSFER_PROCESS_ID));
    verify(apisixAdminClient, times(1)).deleteRoute(eq("well-known"));
    verify(apisixAdminClient, times(1)).deleteRoute(eq("service"));
    verify(credentialsConfigServiceClient, times(1)).deleteService(eq(TEST_TRANSFER_PROCESS_ID));
  }

  @Test
  public void testDeprovision_succeed_ccs_404() throws Exception {

    when(transferMapper.toWellKnownRouteId(any())).thenReturn("well-known");
    when(transferMapper.toServiceRouteId(any())).thenReturn("service");

    doThrow(new HttpClientException("Not found", 404))
        .when(credentialsConfigServiceClient)
        .deleteService(any());

    StatusResult<DeprovisionedResource> result =
        provisioner
            .deprovision(getProvisionedResource(TEST_TRANSFER_PROCESS_ID), getTestPolicy())
            .get();

    assertTrue(result.succeeded(), "The resource should be deprovisioned.");

    verify(odrlPapClient, times(1)).deleteService(eq(TEST_TRANSFER_PROCESS_ID));
    verify(apisixAdminClient, times(1)).deleteRoute(eq("well-known"));
    verify(apisixAdminClient, times(1)).deleteRoute(eq("service"));
    verify(credentialsConfigServiceClient, times(1)).deleteService(eq(TEST_TRANSFER_PROCESS_ID));
  }

  @Test
  public void testDeprovision_failure_get_service_route() throws Exception {

    when(transferMapper.toWellKnownRouteId(any())).thenReturn("well-known");
    when(transferMapper.toServiceRouteId(any())).thenThrow(new RuntimeException("Mapping error"));

    StatusResult<DeprovisionedResource> result =
        provisioner
            .deprovision(getProvisionedResource(TEST_TRANSFER_PROCESS_ID), getTestPolicy())
            .get();
    assertTrue(
        result.fatalError(), "When no route id can be retrieved, deprovisioning should fail. ");
  }

  @Test
  public void testDeprovision_failure_get_well_known_route() throws Exception {

    when(transferMapper.toWellKnownRouteId(any())).thenThrow(new RuntimeException("Mapping error"));
    when(transferMapper.toServiceRouteId(any())).thenReturn("service");

    StatusResult<DeprovisionedResource> result =
        provisioner
            .deprovision(getProvisionedResource(TEST_TRANSFER_PROCESS_ID), getTestPolicy())
            .get();
    assertTrue(
        result.fatalError(), "When no route id can be retrieved, deprovisioning should fail. ");
  }

  @Test
  public void testDeprovision_failure_fail_service_deletion() throws Exception {

    when(transferMapper.toWellKnownRouteId(any())).thenReturn("wellKnown");
    when(transferMapper.toServiceRouteId(any())).thenReturn("service");

    doThrow(new HttpClientException("Error")).when(odrlPapClient).deleteService(any());

    StatusResult<DeprovisionedResource> result =
        provisioner
            .deprovision(getProvisionedResource(TEST_TRANSFER_PROCESS_ID), getTestPolicy())
            .get();
    assertTrue(
        result.failed(),
        "When failing to delete the service, provisioning should fail but be retried.");
    assertFalse(
        result.fatalError(),
        "When failing to delete the service, provisioning should fail but be retried.");
  }

  @Test
  public void testDeprovision_failure_fail_route_deletion() throws Exception {

    when(transferMapper.toWellKnownRouteId(any())).thenReturn("wellKnown");
    when(transferMapper.toServiceRouteId(any())).thenReturn("service");

    doThrow(new HttpClientException("Error")).when(apisixAdminClient).deleteRoute(any());

    StatusResult<DeprovisionedResource> result =
        provisioner
            .deprovision(getProvisionedResource(TEST_TRANSFER_PROCESS_ID), getTestPolicy())
            .get();
    assertTrue(
        result.failed(),
        "When failing to delete the route, provisioning should fail but be retried.");
    assertFalse(
        result.fatalError(),
        "When failing to delete the route, provisioning should fail but be retried.");
  }

  @Test
  public void testDeprovision_failure_fail_config_deletion() throws Exception {

    when(transferMapper.toWellKnownRouteId(any())).thenReturn("wellKnown");
    when(transferMapper.toServiceRouteId(any())).thenReturn("service");

    doThrow(new HttpClientException("Error"))
        .when(credentialsConfigServiceClient)
        .deleteService(any());

    StatusResult<DeprovisionedResource> result =
        provisioner
            .deprovision(getProvisionedResource(TEST_TRANSFER_PROCESS_ID), getTestPolicy())
            .get();
    assertTrue(
        result.failed(),
        "When failing to delete the route, provisioning should fail but be retried.");
    assertFalse(
        result.fatalError(),
        "When failing to delete the route, provisioning should fail but be retried.");
  }

  private static FDSCOID4VPProviderResourceDefinition getResourceDefinition() {
    return FDSCOID4VPProviderResourceDefinition.Builder.newInstance()
        .assetId(TEST_ASSET_ID)
        .id(TEST_RESOURCE_DEFINITION_ID)
        .transferProcessId(TEST_TRANSFER_PROCESS_ID)
        .build();
  }
}
