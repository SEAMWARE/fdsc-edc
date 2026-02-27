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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.eclipse.edc.connector.controlplane.contract.spi.ContractOfferId;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractDefinition;
import org.eclipse.edc.connector.controlplane.contract.spi.validation.ValidatableConsumerOffer;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.ServiceResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.seamware.edc.domain.ExtendableProductOffering;
import org.seamware.edc.tmf.ProductCatalogApiClient;

public class TMForumConsumerOfferResolverTest {

  private static final String TEST_OFFER_ID = "test-def:test-asset:123";
  private static final ContractOfferId TEST_CONTRACT_OFFER_ID =
      ContractOfferId.create("test-def", "test-asset");

  private ProductCatalogApiClient productCatalogApiClient;
  private TMFEdcMapper tmfEdcMapper;
  private TMForumConsumerOfferResolver tmForumConsumerOfferResolver;

  @BeforeEach
  public void setup() {
    productCatalogApiClient = mock(ProductCatalogApiClient.class);
    tmfEdcMapper = mock(TMFEdcMapper.class);

    tmForumConsumerOfferResolver =
        new TMForumConsumerOfferResolver(
            mock(Monitor.class), productCatalogApiClient, tmfEdcMapper);
  }

  @Test
  public void testResolveOffer_success() {
    ExtendableProductOffering testOffer = getTestOffer();
    ValidatableConsumerOffer expectedOffer =
        ValidatableConsumerOffer.Builder.newInstance()
            .offerId(TEST_CONTRACT_OFFER_ID)
            .contractPolicy(testPolicy())
            .accessPolicy(testPolicy())
            .contractDefinition(testContractDefinition())
            .build();
    when(productCatalogApiClient.getProductOfferingByExternalId(eq(TEST_OFFER_ID)))
        .thenReturn(Optional.of(testOffer));
    when(tmfEdcMapper.consumerOfferFromProductOffering(eq(testOffer), any()))
        .thenReturn(Optional.of(expectedOffer));

    ServiceResult<ValidatableConsumerOffer> serviceResult =
        tmForumConsumerOfferResolver.resolveOffer(TEST_OFFER_ID);
    assertTrue(serviceResult.succeeded(), "The offer should have been successfully resolved.");
    assertEquals(
        expectedOffer, serviceResult.getContent(), "The correct offer should have been resolved.");
  }

  @Test
  public void testResolveOffer_no_such_offer() {
    when(productCatalogApiClient.getProductOfferingByExternalId(eq(TEST_OFFER_ID)))
        .thenReturn(Optional.empty());
    assertTrue(
        tmForumConsumerOfferResolver.resolveOffer(TEST_OFFER_ID).failed(),
        "If no such offer exists, the result should be a failure.");
  }

  @Test
  public void testResolveOffer_no_offer_from_tmf() {
    when(productCatalogApiClient.getProductOfferingByExternalId(eq(TEST_OFFER_ID)))
        .thenReturn(Optional.of(getTestOffer()));
    when(tmfEdcMapper.consumerOfferFromProductOffering(eq(getTestOffer()), any()))
        .thenReturn(Optional.empty());
    assertTrue(
        tmForumConsumerOfferResolver.resolveOffer(TEST_OFFER_ID).failed(),
        "If no such offer exists, the result should be a failure.");
  }

  @Test
  public void testResolveOffer_retrieval_error() {
    when(productCatalogApiClient.getProductOfferingByExternalId(TEST_OFFER_ID))
        .thenThrow(new RuntimeException("Something bad happened."));
    assertTrue(
        tmForumConsumerOfferResolver.resolveOffer(TEST_OFFER_ID).failed(),
        "If the client fails, a failure should be returned.");
  }

  @Test
  public void testResolveOffer_mapping_error() {
    when(productCatalogApiClient.getProductOfferingByExternalId(TEST_OFFER_ID))
        .thenReturn(Optional.of(getTestOffer()));
    when(tmfEdcMapper.consumerOfferFromProductOffering(eq(getTestOffer()), any()))
        .thenThrow(new RuntimeException("Something bad happened."));
    assertTrue(
        tmForumConsumerOfferResolver.resolveOffer(TEST_OFFER_ID).failed(),
        "If the mapping fails, a failure should be returned.");
  }

  private static ContractDefinition testContractDefinition() {
    return ContractDefinition.Builder.newInstance()
        .accessPolicyId("access-id")
        .contractPolicyId("contract-id")
        .build();
  }

  private static Policy testPolicy() {
    return Policy.Builder.newInstance().build();
  }

  private static ExtendableProductOffering getTestOffer() {
    return new ExtendableProductOffering().setExternalId(TEST_OFFER_ID);
  }
}
