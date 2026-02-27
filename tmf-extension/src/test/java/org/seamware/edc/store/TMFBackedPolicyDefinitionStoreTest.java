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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.seamware.edc.tmf.ProductCatalogApiClient;

public class TMFBackedPolicyDefinitionStoreTest {

  private static final String TEST_ID = "test-id";

  private ProductCatalogApiClient productCatalogApiClient;
  private TMFBackedPolicyDefinitionStore tmfBackedPolicyDefinitionStore;

  @BeforeEach
  public void setup() {
    productCatalogApiClient = mock(ProductCatalogApiClient.class);

    tmfBackedPolicyDefinitionStore =
        new TMFBackedPolicyDefinitionStore(mock(Monitor.class), productCatalogApiClient);
  }

  @Test
  public void testFindById_success() {

    Policy testPolicy = getTestPolicy();
    PolicyDefinition expectedDefinition =
        PolicyDefinition.Builder.newInstance().policy(testPolicy).id(TEST_ID).build();

    when(productCatalogApiClient.getByPolicyId(eq(TEST_ID))).thenReturn(Optional.of(testPolicy));

    assertEquals(
        expectedDefinition,
        tmfBackedPolicyDefinitionStore.findById(TEST_ID),
        "The asset should successfully be returned.");
  }

  @Test
  public void testFindById_no_spec() {
    when(productCatalogApiClient.getByPolicyId(eq(TEST_ID))).thenReturn(Optional.empty());

    assertNull(
        tmfBackedPolicyDefinitionStore.findById(TEST_ID),
        "If no corresponding spec exists, null should be returned.");
  }

  @Test
  public void testFindById_api_error() {
    when(productCatalogApiClient.getByPolicyId(eq(TEST_ID)))
        .thenThrow(new RuntimeException("Something bad happened"));

    assertThrows(
        EdcPersistenceException.class,
        () -> tmfBackedPolicyDefinitionStore.findById(TEST_ID),
        "If an error happens, an EdcPersistence Exception should be thrown.");
  }

  private Policy getTestPolicy() {
    return Policy.Builder.newInstance()
        .extensibleProperty("http://www.w3.org/ns/odrl/2/uid", TEST_ID)
        .build();
  }
}
