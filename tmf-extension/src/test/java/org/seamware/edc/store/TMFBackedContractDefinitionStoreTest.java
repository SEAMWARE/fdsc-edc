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
package org.seamware.edc.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractDefinition;
import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.seamware.edc.domain.ExtendableProductOffering;
import org.seamware.edc.tmf.ProductCatalogApiClient;

public class TMFBackedContractDefinitionStoreTest {

  private static final String TEST_ID = "test-id";

  private ProductCatalogApiClient productCatalogApiClient;
  private TMFEdcMapper tmfEdcMapper;
  private TMFBackedContractDefinitionStore tmfBackedContractDefinitionStore;

  @BeforeEach
  public void setup() {
    productCatalogApiClient = mock(ProductCatalogApiClient.class);
    tmfEdcMapper = mock(TMFEdcMapper.class);

    tmfBackedContractDefinitionStore =
        new TMFBackedContractDefinitionStore(
            mock(Monitor.class), productCatalogApiClient, tmfEdcMapper);
  }

  @Test
  public void testFindById_success() {
    ContractDefinition testDefinition =
        ContractDefinition.Builder.newInstance()
            .accessPolicyId("access-policy-id")
            .contractPolicyId("contract-policy-id")
            .build();

    when(productCatalogApiClient.getProductOfferingByExternalId(eq(TEST_ID)))
        .thenReturn(Optional.of(getTestOffering()));
    when(tmfEdcMapper.fromProductOffer(eq(getTestOffering())))
        .thenReturn(Optional.of(testDefinition));

    assertEquals(
        testDefinition,
        tmfBackedContractDefinitionStore.findById(TEST_ID),
        "The correct contract should have been returned.");
  }

  @Test
  public void testFindById_no_spec() {
    when(productCatalogApiClient.getProductOfferingByExternalId(eq(TEST_ID)))
        .thenReturn(Optional.empty());

    assertNull(
        tmfBackedContractDefinitionStore.findById(TEST_ID),
        "If no corresponding offer exists, null should be returned.");
  }

  @Test
  public void testFindById_api_error() {
    when(productCatalogApiClient.getProductOfferingByExternalId(eq(TEST_ID)))
        .thenThrow(new RuntimeException("Something bad happened"));

    assertNull(
        tmfBackedContractDefinitionStore.findById(TEST_ID),
        "If no corresponding offer exists, null should be returned.");
  }

  @Test
  public void testFindById_mapping_error() {
    when(productCatalogApiClient.getProductOfferingByExternalId(eq(TEST_ID)))
        .thenReturn(Optional.of(getTestOffering()));
    when(tmfEdcMapper.fromProductOffer(eq(getTestOffering())))
        .thenThrow(new RuntimeException("Something bad happened"));

    assertNull(
        tmfBackedContractDefinitionStore.findById(TEST_ID),
        "If no corresponding offer exists, null should be returned.");
  }

  @Test
  public void testFindById_unmappable_spec() {
    when(productCatalogApiClient.getProductOfferingByExternalId(eq(TEST_ID)))
        .thenReturn(Optional.of(getTestOffering()));
    when(tmfEdcMapper.fromProductOffer(eq(getTestOffering()))).thenReturn(Optional.empty());

    assertNull(
        tmfBackedContractDefinitionStore.findById(TEST_ID),
        "If no corresponding offer exists, null should be returned.");
  }

  private ExtendableProductOffering getTestOffering() {
    return new ExtendableProductOffering().setExternalId(TEST_ID);
  }
}
