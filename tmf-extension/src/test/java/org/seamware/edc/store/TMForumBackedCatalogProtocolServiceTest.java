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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.edc.connector.controlplane.catalog.spi.Catalog;
import org.eclipse.edc.connector.controlplane.catalog.spi.CatalogRequestMessage;
import org.eclipse.edc.connector.controlplane.catalog.spi.Dataset;
import org.eclipse.edc.connector.controlplane.catalog.spi.DatasetRequestMessage;
import org.eclipse.edc.connector.controlplane.services.spi.protocol.ProtocolTokenValidator;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.ServiceFailure;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.types.domain.message.RemoteMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.seamware.edc.domain.ExtendableProductOffering;
import org.seamware.edc.domain.ExtendableProductSpecification;
import org.seamware.edc.tmf.ProductCatalogApiClient;

/**
 * Covers the protocol-facing contract of the catalog service: an unverified token must never reach
 * the catalog, and offerings the access policy hides must not become visible datasets.
 */
public class TMForumBackedCatalogProtocolServiceTest {

  private static final String PARTICIPANT_IDENTITY = "did:web:provider";
  private static final String DATASET_ID = "urn:asset:1";
  private static final String PROTOCOL = "dataspace-protocol-http";
  private static final String COUNTER_PARTY_ADDRESS = "http://consumer/protocol";
  private static final String COUNTER_PARTY_ID = "did:web:consumer";
  private static final String PROCESS_ID = "process-id";

  private TMFEdcMapper tmfEdcMapper;
  private ProductCatalogApiClient productCatalogApi;
  private ProtocolTokenValidator protocolTokenValidator;
  private PolicyEngine policyEngine;
  private ParticipantAgent participantAgent;
  private TMForumBackedCatalogProtocolService service;

  private final ParticipantContext participantContext =
      ParticipantContext.Builder.newInstance()
          .participantContextId(PARTICIPANT_IDENTITY)
          .identity(PARTICIPANT_IDENTITY)
          .build();
  private final TokenRepresentation token =
      TokenRepresentation.Builder.newInstance().token("a-token").build();

  @BeforeEach
  public void setup() {
    tmfEdcMapper = mock(TMFEdcMapper.class);
    productCatalogApi = mock(ProductCatalogApiClient.class);
    protocolTokenValidator = mock(ProtocolTokenValidator.class);
    policyEngine = mock(PolicyEngine.class);
    participantAgent = mock(ParticipantAgent.class);
    service =
        new TMForumBackedCatalogProtocolService(
            tmfEdcMapper,
            productCatalogApi,
            protocolTokenValidator,
            policyEngine,
            mock(Monitor.class),
            new ObjectMapper());
  }

  /** Both protocol entry points must refuse a token the validator rejects. */
  private static Stream<Arguments> unauthorizedRequests() {
    return Stream.of(
        Arguments.of(
            "getCatalog",
            (CatalogCall)
                (service, context, token) -> service.getCatalog(context, catalogRequest(), token)),
        Arguments.of(
            "getDataset",
            (CatalogCall)
                (service, context, token) -> service.getDataset(context, datasetRequest(), token)));
  }

  @ParameterizedTest(name = "{0} rejects an unverified token")
  @MethodSource("unauthorizedRequests")
  public void testRejectsUnverifiedToken(String name, CatalogCall call) {
    when(protocolTokenValidator.verify(
            eq(participantContext), eq(token), any(), any(RemoteMessage.class)))
        .thenReturn(ServiceResult.unauthorized("nope"));

    ServiceResult<?> result = call.invoke(service, participantContext, token);

    assertTrue(result.failed(), name + " must not answer an unverified request.");
    assertEquals(ServiceFailure.Reason.UNAUTHORIZED, result.reason());
    Mockito.verifyNoInteractions(productCatalogApi);
  }

  @Test
  public void testGetDataset_returnsNotFound_whenTheSpecDoesNotExist() {
    givenVerifiedToken();
    when(productCatalogApi.getProductSpecByExternalId(DATASET_ID)).thenReturn(Optional.empty());

    ServiceResult<Dataset> result = service.getDataset(participantContext, datasetRequest(), token);

    assertTrue(result.failed());
    assertEquals(ServiceFailure.Reason.NOT_FOUND, result.reason());
  }

  @Test
  public void testGetDataset_returnsNotFound_whenTheAccessPolicyHidesEveryOffering() {
    givenVerifiedToken();
    ExtendableProductSpecification spec = mock(ExtendableProductSpecification.class);
    when(productCatalogApi.getProductSpecByExternalId(DATASET_ID)).thenReturn(Optional.of(spec));
    when(productCatalogApi.getProductOfferings(anyInt(), anyInt()))
        .thenReturn(List.of(mock(ExtendableProductOffering.class)));
    when(tmfEdcMapper.filterByAccessPolicy(any(), eq(policyEngine), eq(participantAgent)))
        .thenReturn(List.of());
    when(tmfEdcMapper.datasetFromProductSpecification(spec, List.of()))
        .thenReturn(Optional.empty());

    ServiceResult<Dataset> result = service.getDataset(participantContext, datasetRequest(), token);

    assertTrue(result.failed(), "A dataset whose offers are all filtered out must not be served.");
    assertEquals(ServiceFailure.Reason.NOT_FOUND, result.reason());
  }

  @Test
  public void testGetDataset_returnsTheDataset_whenAnOfferingIsAccessible() {
    givenVerifiedToken();
    ExtendableProductSpecification spec = mock(ExtendableProductSpecification.class);
    List<ExtendableProductOffering> accessible = List.of(mock(ExtendableProductOffering.class));
    Dataset dataset = Dataset.Builder.newInstance().id(DATASET_ID).build();
    when(productCatalogApi.getProductSpecByExternalId(DATASET_ID)).thenReturn(Optional.of(spec));
    when(productCatalogApi.getProductOfferings(anyInt(), anyInt())).thenReturn(List.of());
    when(tmfEdcMapper.filterByAccessPolicy(any(), eq(policyEngine), eq(participantAgent)))
        .thenReturn(accessible);
    when(tmfEdcMapper.datasetFromProductSpecification(spec, accessible))
        .thenReturn(Optional.of(dataset));

    ServiceResult<Dataset> result = service.getDataset(participantContext, datasetRequest(), token);

    assertTrue(result.succeeded());
    assertEquals(DATASET_ID, result.getContent().getId());
  }

  @Test
  public void testGetCatalog_stampsTheParticipantContextIdentity() {
    givenVerifiedToken();
    when(productCatalogApi.getProductSpecifications(anyInt(), anyInt())).thenReturn(List.of());
    when(productCatalogApi.getProductOfferings(anyInt(), anyInt())).thenReturn(List.of());
    when(tmfEdcMapper.filterByAccessPolicy(any(), eq(policyEngine), eq(participantAgent)))
        .thenReturn(List.of());

    ServiceResult<Catalog> result = service.getCatalog(participantContext, catalogRequest(), token);

    assertTrue(result.succeeded());
    assertEquals(PARTICIPANT_IDENTITY, result.getContent().getParticipantId());
  }

  private void givenVerifiedToken() {
    when(protocolTokenValidator.verify(
            eq(participantContext), eq(token), any(), any(RemoteMessage.class)))
        .thenReturn(ServiceResult.success(participantAgent));
  }

  private static CatalogRequestMessage catalogRequest() {
    return CatalogRequestMessage.Builder.newInstance()
        .protocol(PROTOCOL)
        .counterPartyAddress(COUNTER_PARTY_ADDRESS)
        .counterPartyId(COUNTER_PARTY_ID)
        .querySpec(QuerySpec.Builder.newInstance().build())
        .build();
  }

  private static DatasetRequestMessage datasetRequest() {
    return DatasetRequestMessage.Builder.newInstance()
        .datasetId(DATASET_ID)
        .processId(PROCESS_ID)
        .protocol(PROTOCOL)
        .counterPartyAddress(COUNTER_PARTY_ADDRESS)
        .counterPartyId(COUNTER_PARTY_ID)
        .build();
  }

  /** Lets the two protocol entry points share the authorization case table. */
  @FunctionalInterface
  interface CatalogCall {
    ServiceResult<?> invoke(
        TMForumBackedCatalogProtocolService service,
        ParticipantContext participantContext,
        TokenRepresentation token);
  }
}
