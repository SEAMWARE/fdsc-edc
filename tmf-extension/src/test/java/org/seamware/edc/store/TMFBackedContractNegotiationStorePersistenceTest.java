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
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.seamware.edc.domain.*;
import org.seamware.tmforum.agreement.model.AgreementItemVO;
import org.seamware.tmforum.productinventory.model.ProductOfferingRefVO;
import org.seamware.tmforum.productinventory.model.ProductStatusTypeVO;
import org.seamware.tmforum.productorder.model.*;
import org.seamware.tmforum.quote.model.QuoteItemVO;
import org.seamware.tmforum.quote.model.QuoteStateTypeVO;
import org.seamware.tmforum.quote.model.RelatedPartyVO;

/** In order to keep the tests readable, all tests for the "save"-method are in this class. */
public class TMFBackedContractNegotiationStorePersistenceTest
    extends TMFBackedContractNegotiationStoreTest {

  public static final String NEW_QUOTE_ID = "new-quote";

  // -- INITIAL ---

  @ParameterizedTest(name = "{0}")
  @MethodSource("getInitialStates_update")
  public void testInitialSave_success_update(
      String name,
      ContractNegotiation contractNegotiation,
      List<ExtendableQuoteVO> negotiationQuotes,
      List<ExtendableQuoteItemVO> quoteItems,
      String expectedQuoteId,
      ExtendableQuoteUpdateVO expectedUpdate) {

    Map<String, List<ExtendableQuoteVO>> quotesByNeg = new HashMap<>();
    negotiationQuotes.forEach(
        nq -> {
          if (quotesByNeg.containsKey(nq.getExternalId())) {
            quotesByNeg.get(nq.getExternalId()).add(nq);
          } else {
            quotesByNeg.put(nq.getExternalId(), new ArrayList<>(List.of(nq)));
          }
        });
    quotesByNeg.forEach(
        (key, value) -> when(quoteApiClient.findByNegotiationId(eq(key))).thenReturn(value));

    ArgumentCaptor<ExtendableQuoteUpdateVO> quoteCaptor =
        ArgumentCaptor.forClass(ExtendableQuoteUpdateVO.class);
    ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);

    ExtendableProductOffering extendableProductOffering = getExtendableProductOffering();

    when(productCatalogApiClient.getProductOfferingByExternalId(eq(TEST_OFFER_ID)))
        .thenReturn(Optional.of(extendableProductOffering));
    switch (contractNegotiation.getType()) {
      case PROVIDER ->
          quoteItems.forEach(
              qi ->
                  when(tmfEdcMapper.fromProviderContractOffer(any(), any(), any())).thenReturn(qi));
      case CONSUMER ->
          quoteItems.forEach(
              qi -> when(tmfEdcMapper.fromConsumerContractOffer(any(), any())).thenReturn(qi));
    }

    when(tmfEdcMapper.toUpdate(any(ExtendableQuoteVO.class)))
        .thenAnswer(
            invocation -> {
              ExtendableQuoteVO extendableQuoteVO = invocation.getArgument(0);
              return new TMFObjectMapperImpl().map(extendableQuoteVO);
            });

    tmfBackedContractNegotiationStore.save(contractNegotiation);

    verify(quoteApiClient).updateQuote(idCaptor.capture(), quoteCaptor.capture());

    verify(leaseHolder, times(1)).acquireLease(eq(contractNegotiation.getId()), any());
    verify(leaseHolder, times(1)).freeLease(eq(contractNegotiation.getId()), any());

    ExtendableQuoteUpdateVO extendableQuoteUpdateVO = quoteCaptor.getValue();
    assertEquals(
        expectedQuoteId, idCaptor.getValue(), "The correct quote should have been updated.");
    assertEquals(
        expectedUpdate.getExternalId(),
        extendableQuoteUpdateVO.getExternalId(),
        "The correct negotiationId should be included.");
    assertEquals(
        expectedUpdate.getContractNegotiationState(),
        extendableQuoteUpdateVO.getContractNegotiationState(),
        "The correct negotiation state should be stored.");
    assertEquals(
        expectedUpdate.getState(),
        extendableQuoteUpdateVO.getState(),
        "The quote state should be stored.");
    assertEquals(
        expectedUpdate.getExtendableQuoteItem(),
        extendableQuoteUpdateVO.getExtendableQuoteItem(),
        "The offerid should be set for the quote item.");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getInitialStates_create")
  public void testInitialSave_success_create(
      String name,
      ContractNegotiation contractNegotiation,
      List<ExtendableQuoteVO> negotiationQuotes,
      List<ExtendableQuoteItemVO> quoteItems,
      ExtendableQuoteCreateVO expectedCreate) {
    Map<String, List<ExtendableQuoteVO>> quotesByNeg = new HashMap<>();
    negotiationQuotes.forEach(
        nq -> {
          if (quotesByNeg.containsKey(nq.getExternalId())) {
            quotesByNeg.get(nq.getExternalId()).add(nq);
          } else {
            quotesByNeg.put(nq.getExternalId(), new ArrayList<>(List.of(nq)));
          }
        });
    quotesByNeg.forEach(
        (key, value) -> when(quoteApiClient.findByNegotiationId(eq(key))).thenReturn(value));

    ArgumentCaptor<ExtendableQuoteCreateVO> quoteCaptor =
        ArgumentCaptor.forClass(ExtendableQuoteCreateVO.class);

    ExtendableProductOffering extendableProductOffering = getExtendableProductOffering();

    when(tmfEdcMapper.toUpdate(any(ExtendableQuoteVO.class)))
        .thenAnswer(
            invocation -> {
              ExtendableQuoteVO extendableQuoteVO = invocation.getArgument(0);
              return new TMFObjectMapperImpl().map(extendableQuoteVO);
            });
    when(productCatalogApiClient.getProductOfferingByExternalId(eq(TEST_OFFER_ID)))
        .thenReturn(Optional.of(extendableProductOffering));

    switch (contractNegotiation.getType()) {
      case PROVIDER ->
          quoteItems.forEach(
              qi ->
                  when(tmfEdcMapper.fromProviderContractOffer(any(), any(), any())).thenReturn(qi));
      case CONSUMER ->
          quoteItems.forEach(
              qi -> when(tmfEdcMapper.fromConsumerContractOffer(any(), any())).thenReturn(qi));
    }

    tmfBackedContractNegotiationStore.save(contractNegotiation);

    if (!negotiationQuotes.isEmpty()) {
      // in this case, we need to cancel
      verify(quoteApiClient, times(1)).updateQuote(any(), any());
    }

    verify(quoteApiClient).createQuote(quoteCaptor.capture());

    verify(leaseHolder, times(1)).acquireLease(eq(contractNegotiation.getId()), any());
    verify(leaseHolder, times(1)).freeLease(eq(contractNegotiation.getId()), any());

    ExtendableQuoteCreateVO extendableQuoteCreateVO = quoteCaptor.getValue();
    assertEquals(
        expectedCreate.getExternalId(),
        extendableQuoteCreateVO.getExternalId(),
        "The correct negotiationId should be included.");
    assertEquals(
        expectedCreate.getContractNegotiationState(),
        extendableQuoteCreateVO.getContractNegotiationState(),
        "The correct negotiation state should be stored.");
    assertEquals(
        expectedCreate.getQuoteItem(),
        extendableQuoteCreateVO.getExtendableQuoteItem(),
        "The offerid should be set for the quote item.");
    assertEquals(
        Set.of("Provider", "Consumer"),
        extendableQuoteCreateVO.getRelatedParty().stream()
            .map(RelatedPartyVO::getRole)
            .collect(Collectors.toSet()),
        "A consumer and a provider need to be included.");
  }

  @ParameterizedTest
  @EnumSource(ContractNegotiationStates.class)
  public void testSave_quote_api_error(ContractNegotiationStates negotiationState) {
    when(quoteApiClient.findByNegotiationId(any()))
        .thenThrow(new RuntimeException("Something bad."));

    assertThrows(
        EdcPersistenceException.class,
        () ->
            tmfBackedContractNegotiationStore.save(
                getValidNegotiationWithIdInState(TEST_NEGOTIATION_ID, negotiationState)),
        "All unexpected errors have to become EdcPersistenceExceptions.");
    verify(leaseHolder, times(1)).freeLease(eq(TEST_NEGOTIATION_ID), any());
  }

  @ParameterizedTest
  @EnumSource(
      value = ContractNegotiationStates.class,
      names = {"AGREEING", "AGREED", "VERIFYING", "VERIFIED", "FINALIZING", "FINALIZED"})
  public void testSave_no_quote(ContractNegotiationStates negotiationState) {
    when(quoteApiClient.findByNegotiationId(any())).thenReturn(List.of());
    assertThrows(
        EdcPersistenceException.class,
        () ->
            tmfBackedContractNegotiationStore.save(
                getValidNegotiationWithIdInState(TEST_NEGOTIATION_ID, negotiationState)),
        "All unexpected errors have to become EdcPersistenceExceptions.");
    verify(leaseHolder, times(1)).freeLease(eq(TEST_NEGOTIATION_ID), any());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getInvalidStateCombinations")
  public void testSave_no_active_quote(
      ContractNegotiationStates negotiationState, List<ContractNegotiationStates> illegalStates) {
    illegalStates.forEach(
        illegalState -> {
          String negotiationId = String.format("NEGOTIATION-%s", illegalState.name());
          when(quoteApiClient.findByNegotiationId(eq(negotiationId)))
              .thenReturn(
                  setActiveQuote(
                      illegalState,
                      "active-id",
                      getQuotes(negotiationId, TEST_CONTROL_PLANE_ID, 5)));
          EdcPersistenceException edcPersistenceException =
              assertThrows(
                  EdcPersistenceException.class,
                  () ->
                      tmfBackedContractNegotiationStore.save(
                          getValidNegotiationWithIdInState(negotiationId, negotiationState)),
                  String.format(
                      "[%s] All unexpected errors have to become EdcPersistenceExceptions.",
                      illegalState));
          verify(leaseHolder, times(1)).freeLease(eq(negotiationId), any());
          assertEquals(
              IllegalArgumentException.class,
              edcPersistenceException.getCause().getClass(),
              String.format(
                  "[%s] No active quote should lead to an IllegalArgument.", illegalState));
        });
  }

  @ParameterizedTest
  @EnumSource(
      value = ContractNegotiationStates.class,
      names = {"FINALIZING", "FINALIZED"})
  public void testSave_no_order(ContractNegotiationStates contractNegotiationState) {
    when(quoteApiClient.findByNegotiationId(any()))
        .thenReturn(
            setActiveQuote(
                ContractNegotiationStates.VERIFIED,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5),
                QuoteStateTypeVO.ACCEPTED,
                TEST_OFFER_ID));
    when(productOrderApiClient.findByQuoteId(any())).thenReturn(List.of());
    EdcPersistenceException edcPersistenceException =
        assertThrows(
            EdcPersistenceException.class,
            () ->
                tmfBackedContractNegotiationStore.save(
                    getValidNegotiationWithIdInState(
                        TEST_NEGOTIATION_ID, contractNegotiationState)),
            "All unexpected errors have to become EdcPersistenceExceptions.");
    verify(leaseHolder, times(1)).freeLease(eq(TEST_NEGOTIATION_ID), any());
    assertEquals(
        IllegalArgumentException.class,
        edcPersistenceException.getCause().getClass(),
        "No order should lead to an IllegalArgument.");
  }

  @ParameterizedTest
  @EnumSource(
      value = ContractNegotiationStates.class,
      names = {"FINALIZING", "FINALIZED"})
  public void testSave_to_much_orders(ContractNegotiationStates contractNegotiationState) {
    when(quoteApiClient.findByNegotiationId(any()))
        .thenReturn(
            setActiveQuote(
                ContractNegotiationStates.VERIFIED,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5),
                QuoteStateTypeVO.ACCEPTED,
                TEST_OFFER_ID));
    when(productOrderApiClient.findByQuoteId(any()))
        .thenReturn(List.of(new ProductOrderVO(), new ProductOrderVO()));
    EdcPersistenceException edcPersistenceException =
        assertThrows(
            EdcPersistenceException.class,
            () ->
                tmfBackedContractNegotiationStore.save(
                    getValidNegotiationWithIdInState(
                        TEST_NEGOTIATION_ID, contractNegotiationState)),
            "All unexpected errors have to become EdcPersistenceExceptions.");
    verify(leaseHolder, times(1)).freeLease(eq(TEST_NEGOTIATION_ID), any());
    assertEquals(
        IllegalArgumentException.class,
        edcPersistenceException.getCause().getClass(),
        "No order should lead to an IllegalArgument.");
  }

  @ParameterizedTest
  @EnumSource(
      value = ContractNegotiationStates.class,
      names = {"FINALIZING", "FINALIZED"})
  public void testSave_order_client_error(ContractNegotiationStates contractNegotiationState) {
    when(quoteApiClient.findByNegotiationId(any()))
        .thenReturn(
            setActiveQuote(
                ContractNegotiationStates.VERIFIED,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5),
                QuoteStateTypeVO.ACCEPTED,
                TEST_OFFER_ID));
    when(productOrderApiClient.findByQuoteId(any()))
        .thenThrow(new RuntimeException("Client error"));
    EdcPersistenceException edcPersistenceException =
        assertThrows(
            EdcPersistenceException.class,
            () ->
                tmfBackedContractNegotiationStore.save(
                    getValidNegotiationWithIdInState(
                        TEST_NEGOTIATION_ID, contractNegotiationState)),
            "All unexpected errors have to become EdcPersistenceExceptions.");
    verify(leaseHolder, times(1)).freeLease(eq(TEST_NEGOTIATION_ID), any());
    assertEquals(
        RuntimeException.class,
        edcPersistenceException.getCause().getClass(),
        "In case of a client issue, the client exception needs to be the cause.");
  }

  private static Stream<Arguments> getInvalidStateCombinations() {
    return Stream.of(
        Arguments.of(
            ContractNegotiationStates.AGREEING,
            List.of(
                ContractNegotiationStates.INITIAL,
                ContractNegotiationStates.REQUESTING,
                ContractNegotiationStates.VERIFYING,
                ContractNegotiationStates.VERIFIED,
                ContractNegotiationStates.FINALIZING,
                ContractNegotiationStates.FINALIZED,
                ContractNegotiationStates.TERMINATING,
                ContractNegotiationStates.TERMINATED)),
        Arguments.of(
            ContractNegotiationStates.AGREED,
            List.of(
                ContractNegotiationStates.INITIAL,
                ContractNegotiationStates.REQUESTING,
                ContractNegotiationStates.VERIFYING,
                ContractNegotiationStates.VERIFIED,
                ContractNegotiationStates.FINALIZING,
                ContractNegotiationStates.FINALIZED,
                ContractNegotiationStates.TERMINATING,
                ContractNegotiationStates.TERMINATED)),
        Arguments.of(
            ContractNegotiationStates.VERIFYING,
            List.of(
                ContractNegotiationStates.INITIAL,
                ContractNegotiationStates.REQUESTING,
                ContractNegotiationStates.REQUESTED,
                ContractNegotiationStates.OFFERING,
                ContractNegotiationStates.OFFERED,
                ContractNegotiationStates.ACCEPTING,
                ContractNegotiationStates.ACCEPTED,
                ContractNegotiationStates.AGREEING,
                ContractNegotiationStates.FINALIZING,
                ContractNegotiationStates.FINALIZED,
                ContractNegotiationStates.TERMINATING,
                ContractNegotiationStates.TERMINATED)),
        Arguments.of(
            ContractNegotiationStates.VERIFIED,
            List.of(
                ContractNegotiationStates.INITIAL,
                ContractNegotiationStates.REQUESTING,
                ContractNegotiationStates.REQUESTED,
                ContractNegotiationStates.OFFERING,
                ContractNegotiationStates.OFFERED,
                ContractNegotiationStates.ACCEPTING,
                ContractNegotiationStates.ACCEPTED,
                ContractNegotiationStates.AGREEING,
                ContractNegotiationStates.FINALIZING,
                ContractNegotiationStates.FINALIZED,
                ContractNegotiationStates.TERMINATING,
                ContractNegotiationStates.TERMINATED)),
        Arguments.of(
            ContractNegotiationStates.FINALIZING,
            List.of(
                ContractNegotiationStates.INITIAL,
                ContractNegotiationStates.REQUESTING,
                ContractNegotiationStates.REQUESTED,
                ContractNegotiationStates.OFFERING,
                ContractNegotiationStates.OFFERED,
                ContractNegotiationStates.ACCEPTING,
                ContractNegotiationStates.ACCEPTED,
                ContractNegotiationStates.AGREEING,
                ContractNegotiationStates.AGREED,
                ContractNegotiationStates.VERIFYING,
                ContractNegotiationStates.TERMINATING,
                ContractNegotiationStates.TERMINATED)),
        Arguments.of(
            ContractNegotiationStates.FINALIZED,
            List.of(
                ContractNegotiationStates.INITIAL,
                ContractNegotiationStates.REQUESTING,
                ContractNegotiationStates.REQUESTED,
                ContractNegotiationStates.OFFERING,
                ContractNegotiationStates.OFFERED,
                ContractNegotiationStates.ACCEPTING,
                ContractNegotiationStates.ACCEPTED,
                ContractNegotiationStates.AGREEING,
                ContractNegotiationStates.AGREED,
                ContractNegotiationStates.VERIFYING,
                ContractNegotiationStates.TERMINATING,
                ContractNegotiationStates.TERMINATED)));
  }

  private static Stream<Arguments> getInitialStates_update() {
    return Stream.of(
        Arguments.of(
            "[Provider]The initial state should properly be stored.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.INITIAL),
            setActiveQuote(
                ContractNegotiationStates.INITIAL,
                "active-quote",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123")),
            "active-quote",
            expectedQuoteUpdate(
                "INITIAL", QuoteStateTypeVO.IN_PROGRESS, List.of(getQuoteItem("123")))),
        Arguments.of(
            "[Provider]The initial state should be stored with all offers included as quote items.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(
                    getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.INITIAL),
            setActiveQuote(
                ContractNegotiationStates.INITIAL,
                "active-quote",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123"), getQuoteItem("124")),
            "active-quote",
            expectedQuoteUpdate(
                "INITIAL",
                QuoteStateTypeVO.IN_PROGRESS,
                List.of(getQuoteItem("123"), getQuoteItem("124")))),
        Arguments.of(
            "[Consumer]The initial state should properly be stored.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                ContractNegotiation.Type.CONSUMER,
                ContractNegotiationStates.INITIAL),
            setActiveQuote(
                ContractNegotiationStates.INITIAL,
                "active-quote",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123")),
            "active-quote",
            expectedQuoteUpdate(
                "INITIAL", QuoteStateTypeVO.IN_PROGRESS, List.of(getQuoteItem("123")))),
        Arguments.of(
            "[Consumer]The initial state should be stored with all offers included as quote items.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(
                    getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")),
                ContractNegotiation.Type.CONSUMER,
                ContractNegotiationStates.INITIAL),
            setActiveQuote(
                ContractNegotiationStates.INITIAL,
                "active-quote",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123"), getQuoteItem("124")),
            "active-quote",
            expectedQuoteUpdate(
                "INITIAL",
                QuoteStateTypeVO.IN_PROGRESS,
                List.of(getQuoteItem("123"), getQuoteItem("124")))),
        Arguments.of(
            "[Consumer]The requesting state should properly be stored.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                ContractNegotiation.Type.CONSUMER,
                ContractNegotiationStates.REQUESTING),
            setActiveQuote(
                ContractNegotiationStates.REQUESTING,
                "active-quote",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123")),
            "active-quote",
            expectedQuoteUpdate(
                "REQUESTING", QuoteStateTypeVO.IN_PROGRESS, List.of(getQuoteItem("123")))),
        Arguments.of(
            "[Consumer]The requesting state should be stored with all offers included as quote items.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(
                    getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")),
                ContractNegotiation.Type.CONSUMER,
                ContractNegotiationStates.REQUESTING),
            setActiveQuote(
                ContractNegotiationStates.REQUESTING,
                "active-quote",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123"), getQuoteItem("124")),
            "active-quote",
            expectedQuoteUpdate(
                "REQUESTING",
                QuoteStateTypeVO.IN_PROGRESS,
                List.of(getQuoteItem("123"), getQuoteItem("124")))),
        Arguments.of(
            "[Provider]The requesting state should properly be stored.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.REQUESTING),
            setActiveQuote(
                ContractNegotiationStates.REQUESTING,
                "active-quote",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123")),
            "active-quote",
            expectedQuoteUpdate(
                "REQUESTING", QuoteStateTypeVO.IN_PROGRESS, List.of(getQuoteItem("123")))),
        Arguments.of(
            "[Provider]The requesting state should be stored with all offers included as quote items.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(
                    getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.REQUESTING),
            setActiveQuote(
                ContractNegotiationStates.REQUESTING,
                "active-quote",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123"), getQuoteItem("124")),
            "active-quote",
            expectedQuoteUpdate(
                "REQUESTING",
                QuoteStateTypeVO.IN_PROGRESS,
                List.of(getQuoteItem("123"), getQuoteItem("124")))));
  }

  private static Stream<Arguments> getInitialStates_create() {
    return Stream.of(
        Arguments.of(
            "[Provider]The initial state should properly be stored.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.INITIAL),
            List.of(),
            List.of(getQuoteItem("123")),
            expectedQuoteCreate("INITIAL", List.of(getQuoteItem("123")))),
        Arguments.of(
            "[Provider]The state should be stored with all offers included as quote items.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(
                    getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.INITIAL),
            List.of(),
            List.of(getQuoteItem("123"), getQuoteItem("124")),
            expectedQuoteCreate("INITIAL", List.of(getQuoteItem("123"), getQuoteItem("124")))),
        Arguments.of(
            "[Consumer]The initial state should properly be stored in initial.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                ContractNegotiation.Type.CONSUMER,
                ContractNegotiationStates.INITIAL),
            List.of(),
            List.of(getQuoteItem("123")),
            expectedQuoteCreate("INITIAL", List.of(getQuoteItem("123")))),
        Arguments.of(
            "[Consumer]The state should be stored in initial with all offers included as quote items.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(
                    getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")),
                ContractNegotiation.Type.CONSUMER,
                ContractNegotiationStates.INITIAL),
            List.of(),
            List.of(getQuoteItem("123"), getQuoteItem("124")),
            expectedQuoteCreate("INITIAL", List.of(getQuoteItem("123"), getQuoteItem("124")))),
        Arguments.of(
            "[Consumer]The initial state should properly be stored in requesting.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                ContractNegotiation.Type.CONSUMER,
                ContractNegotiationStates.REQUESTING),
            List.of(),
            List.of(getQuoteItem("123")),
            expectedQuoteCreate("REQUESTING", List.of(getQuoteItem("123")))),
        Arguments.of(
            "[Consumer]The state should be stored in requesting with all offers included as quote items.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(
                    getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")),
                ContractNegotiation.Type.CONSUMER,
                ContractNegotiationStates.REQUESTING),
            List.of(),
            List.of(getQuoteItem("123"), getQuoteItem("124")),
            expectedQuoteCreate("REQUESTING", List.of(getQuoteItem("123"), getQuoteItem("124")))),
        Arguments.of(
            "[Provider]The initial state should properly be stored in requesting.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.REQUESTING),
            List.of(),
            List.of(getQuoteItem("123")),
            expectedQuoteCreate("REQUESTING", List.of(getQuoteItem("123")))),
        Arguments.of(
            "[Provider]The state should be stored in requesting with all offers included as quote items.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(
                    getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.REQUESTING),
            List.of(),
            List.of(getQuoteItem("123"), getQuoteItem("124")),
            expectedQuoteCreate("REQUESTING", List.of(getQuoteItem("123"), getQuoteItem("124")))),
        Arguments.of(
            "[Provider]The state should be stored with all offers included as quote items for counter offers from offering.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(
                    getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.REQUESTING),
            setActiveQuote(
                ContractNegotiationStates.OFFERING,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123"), getQuoteItem("124")),
            expectedQuoteCreate("REQUESTING", List.of(getQuoteItem("123"), getQuoteItem("124")))),
        Arguments.of(
            "[Provider]The state should be stored with all offers included as quote items for counter offers from offered.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(
                    getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.REQUESTING),
            setActiveQuote(
                ContractNegotiationStates.OFFERED,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123"), getQuoteItem("124")),
            expectedQuoteCreate("REQUESTING", List.of(getQuoteItem("123"), getQuoteItem("124")))));
  }

  // ----- REQUESTED -----

  @ParameterizedTest(name = "{0}")
  @MethodSource("getRequestStates")
  public void testRequestSave_success(
      String name,
      ContractNegotiation contractNegotiation,
      List<ExtendableQuoteVO> negotiationQuotes,
      List<ExtendableQuoteItemVO> quoteItems,
      String expectedQuoteId,
      boolean isCounterOffer,
      ExtendableQuoteUpdateVO expectedUpdate) {

    testSave_success(
        contractNegotiation,
        negotiationQuotes,
        quoteItems,
        expectedQuoteId,
        isCounterOffer,
        expectedUpdate);
  }

  private static Stream<Arguments> getRequestStates() {
    return Stream.of(
        Arguments.of(
            "The negotiation should be transitioned from initial.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.REQUESTED),
            setActiveQuote(
                ContractNegotiationStates.INITIAL,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123")),
            "active-id",
            false,
            expectedQuoteUpdate(
                "REQUESTED", QuoteStateTypeVO.APPROVED, List.of(getQuoteItem("123")))),
        Arguments.of(
            "The negotiation should be transitioned from initial, only new offers should be added.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(
                    getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.REQUESTED),
            setActiveQuote(
                ContractNegotiationStates.INITIAL,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123")),
            "active-id",
            false,
            expectedQuoteUpdate(
                "REQUESTED",
                QuoteStateTypeVO.APPROVED,
                List.of(getQuoteItem("123"), getQuoteItem("124")))),
        //                Arguments.of(
        //                        "The negotiation should be transitioned from requested.",
        //                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID,
        // List.of(getOffer(TEST_OFFER_ID + ":asset:123")), ContractNegotiation.Type.PROVIDER,
        // ContractNegotiationStates.REQUESTED),
        //                        setActiveQuote(ContractNegotiationStates.REQUESTED, "active-id",
        // getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
        //                        List.of(getQuoteItem("123")),
        //                        "active-id",
        //                        false,
        //                        expectedQuoteUpdate("REQUESTED", QuoteStateTypeVO.APPROVED,
        // List.of(getQuoteItem("123")))
        //                ),
        //                Arguments.of(
        //                        "The negotiation should be transitioned from requested, only new
        // offers should be added.",
        //                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID,
        // List.of(getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")),
        // ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.REQUESTED),
        //                        setActiveQuote(ContractNegotiationStates.REQUESTED, "active-id",
        // getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
        //                        List.of(getQuoteItem("123")),
        //                        "active-id",
        //                        false,
        //                        expectedQuoteUpdate("REQUESTED", QuoteStateTypeVO.APPROVED,
        // List.of(getQuoteItem("123"), getQuoteItem("124")))
        //                ),
        Arguments.of(
            "The negotiation should be transitioned from requesting.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.REQUESTED),
            setActiveQuote(
                ContractNegotiationStates.REQUESTING,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123")),
            "active-id",
            false,
            expectedQuoteUpdate(
                "REQUESTED", QuoteStateTypeVO.APPROVED, List.of(getQuoteItem("123")))),
        Arguments.of(
            "The negotiation should be transitioned from requesting, only new offers should be added.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(
                    getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.REQUESTED),
            setActiveQuote(
                ContractNegotiationStates.REQUESTING,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123")),
            "active-id",
            false,
            expectedQuoteUpdate(
                "REQUESTED",
                QuoteStateTypeVO.APPROVED,
                List.of(getQuoteItem("123"), getQuoteItem("124")))),
        Arguments.of(
            "The negotiation should be transitioned from offered.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.REQUESTED),
            setActiveQuote(
                ContractNegotiationStates.OFFERED,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123")),
            NEW_QUOTE_ID,
            true,
            expectedQuoteUpdate(
                "REQUESTED", QuoteStateTypeVO.APPROVED, List.of(getQuoteItem("123")))),
        Arguments.of(
            "The negotiation should be transitioned from offered, only new offers should be added.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(
                    getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.REQUESTED),
            setActiveQuote(
                ContractNegotiationStates.OFFERED,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123")),
            NEW_QUOTE_ID,
            true,
            expectedQuoteUpdate(
                "REQUESTED",
                QuoteStateTypeVO.APPROVED,
                List.of(getQuoteItem("123"), getQuoteItem("124")))));
  }

  // ----- OFFER ----
  @ParameterizedTest(name = "{0}")
  @MethodSource("getOfferStates")
  public void testOfferSave_success(
      String name,
      ContractNegotiation contractNegotiation,
      List<ExtendableQuoteVO> negotiationQuotes,
      List<ExtendableQuoteItemVO> quoteItems,
      String expectedQuoteId,
      boolean isCounterOffer,
      ExtendableQuoteUpdateVO expectedUpdate) {

    testSave_success(
        contractNegotiation,
        negotiationQuotes,
        quoteItems,
        expectedQuoteId,
        isCounterOffer,
        expectedUpdate);
  }

  private static Stream<Arguments> getOfferStates() {
    return Stream.of(
        Arguments.of(
            "The negotiation should be transitioned from initial.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.OFFERED),
            setActiveQuote(
                ContractNegotiationStates.INITIAL,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123")),
            "active-id",
            false,
            expectedQuoteUpdate(
                "OFFERED", QuoteStateTypeVO.APPROVED, List.of(getQuoteItem("123")))),
        Arguments.of(
            "The negotiation should be transitioned from initial, only new offers should be added.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(
                    getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.OFFERED),
            setActiveQuote(
                ContractNegotiationStates.INITIAL,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123")),
            "active-id",
            false,
            expectedQuoteUpdate(
                "OFFERED",
                QuoteStateTypeVO.APPROVED,
                List.of(getQuoteItem("123"), getQuoteItem("124")))),
        Arguments.of(
            "The negotiation should be transitioned from requested.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.OFFERED),
            setActiveQuote(
                ContractNegotiationStates.REQUESTED,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123")),
            "active-id",
            false,
            expectedQuoteUpdate(
                "OFFERED", QuoteStateTypeVO.APPROVED, List.of(getQuoteItem("123")))),
        Arguments.of(
            "The negotiation should be transitioned from requested, only new offers should be added.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(
                    getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.OFFERED),
            setActiveQuote(
                ContractNegotiationStates.REQUESTED,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123")),
            "active-id",
            false,
            expectedQuoteUpdate(
                "OFFERED",
                QuoteStateTypeVO.APPROVED,
                List.of(getQuoteItem("123"), getQuoteItem("124")))),
        Arguments.of(
            "The negotiation should be transitioned from offering.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.OFFERED),
            setActiveQuote(
                ContractNegotiationStates.OFFERING,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123")),
            "active-id",
            false,
            expectedQuoteUpdate(
                "OFFERED", QuoteStateTypeVO.APPROVED, List.of(getQuoteItem("123")))),
        Arguments.of(
            "The negotiation should be transitioned from offering, only new offers should be added.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(
                    getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.OFFERED),
            setActiveQuote(
                ContractNegotiationStates.OFFERING,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123")),
            "active-id",
            false,
            expectedQuoteUpdate(
                "OFFERED",
                QuoteStateTypeVO.APPROVED,
                List.of(getQuoteItem("123"), getQuoteItem("124")))),
        Arguments.of(
            "The negotiation should be created in offered.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.OFFERED),
            List.of(),
            List.of(getQuoteItem("123")),
            NEW_QUOTE_ID,
            false,
            expectedQuoteUpdate(
                "OFFERED", QuoteStateTypeVO.APPROVED, List.of(getQuoteItem("123")))));
  }

  // ----- ACCEPT ----

  @ParameterizedTest(name = "{0}")
  @MethodSource("getAcceptStates")
  public void testAcceptSave_success(
      String name,
      ContractNegotiation contractNegotiation,
      List<ExtendableQuoteVO> negotiationQuotes,
      List<ExtendableQuoteItemVO> quoteItems,
      String expectedQuoteId,
      ExtendableQuoteUpdateVO expectedUpdate) {

    testSave_success(
        contractNegotiation, negotiationQuotes, quoteItems, expectedQuoteId, false, expectedUpdate);
  }

  @ParameterizedTest
  @EnumSource(
      value = ContractNegotiationStates.class,
      names = {"ACCEPTED", "ACCEPTING"})
  public void testAcceptSave_terminating(ContractNegotiationStates negotiationState) {
    Map<String, List<ExtendableQuoteVO>> quotesByNeg = new HashMap<>();
    setActiveQuote(
            ContractNegotiationStates.TERMINATING,
            "active-id",
            getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5))
        .forEach(
            nq -> {
              if (quotesByNeg.containsKey(nq.getExternalId())) {
                quotesByNeg.get(nq.getExternalId()).add(nq);
              } else {
                quotesByNeg.put(nq.getExternalId(), new ArrayList<>(List.of(nq)));
              }
            });
    quotesByNeg.forEach(
        (key, value) -> when(quoteApiClient.findByNegotiationId(eq(key))).thenReturn(value));

    tmfBackedContractNegotiationStore.save(
        getValidNegotiationWithIdOfferAndType(
            TEST_NEGOTIATION_ID,
            List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
            ContractNegotiation.Type.PROVIDER,
            negotiationState));

    // nothing should happen while terminating.
    verify(quoteApiClient, times(0)).updateQuote(any(), any());
    verify(quoteApiClient, times(0)).createQuote(any());
  }

  private static Stream<Arguments> getAcceptStates() {
    return Stream.of(
        Arguments.of(
            "The negotiation should be transitioned from offered to accepted.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.ACCEPTED),
            setActiveQuote(
                ContractNegotiationStates.OFFERED,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123")),
            "active-id",
            expectedQuoteUpdate(
                "ACCEPTED", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123")))),
        Arguments.of(
            "The negotiation should be transitioned from initial to accepted, only new offers should be added.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(
                    getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.ACCEPTED),
            setActiveQuote(
                ContractNegotiationStates.OFFERED,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123")),
            "active-id",
            expectedQuoteUpdate(
                "ACCEPTED",
                QuoteStateTypeVO.ACCEPTED,
                List.of(getQuoteItem("123"), getQuoteItem("124")))),
        Arguments.of(
            "The negotiation should be transitioned from accepting to accepted.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.ACCEPTED),
            setActiveQuote(
                ContractNegotiationStates.ACCEPTING,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123")),
            "active-id",
            expectedQuoteUpdate(
                "ACCEPTED", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123")))),
        Arguments.of(
            "The negotiation should be transitioned from accepting to accepted, only new offers should be added.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(
                    getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.ACCEPTED),
            setActiveQuote(
                ContractNegotiationStates.ACCEPTING,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123")),
            "active-id",
            expectedQuoteUpdate(
                "ACCEPTED",
                QuoteStateTypeVO.ACCEPTED,
                List.of(getQuoteItem("123"), getQuoteItem("124")))),
        Arguments.of(
            "No active quote and no termination -> create in accepted.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.ACCEPTED),
            List.of(),
            List.of(getQuoteItem("123")),
            NEW_QUOTE_ID,
            expectedQuoteUpdate(
                "ACCEPTED", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123")))),
        Arguments.of(
            "The negotiation should be transitioned from offered to accepting.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.ACCEPTING),
            setActiveQuote(
                ContractNegotiationStates.OFFERED,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123")),
            "active-id",
            expectedQuoteUpdate(
                "ACCEPTING", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123")))),
        Arguments.of(
            "The negotiation should be transitioned from initial to accepting, only new offers should be added.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(
                    getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.ACCEPTING),
            setActiveQuote(
                ContractNegotiationStates.OFFERED,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123")),
            "active-id",
            expectedQuoteUpdate(
                "ACCEPTING",
                QuoteStateTypeVO.ACCEPTED,
                List.of(getQuoteItem("123"), getQuoteItem("124")))),
        Arguments.of(
            "The negotiation should be transitioned from accepting to accepting.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.ACCEPTING),
            setActiveQuote(
                ContractNegotiationStates.ACCEPTING,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123")),
            "active-id",
            expectedQuoteUpdate(
                "ACCEPTING", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123")))),
        Arguments.of(
            "The negotiation should be transitioned from accepting to accepting, only new offers should be added.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(
                    getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.ACCEPTING),
            setActiveQuote(
                ContractNegotiationStates.ACCEPTING,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123")),
            "active-id",
            expectedQuoteUpdate(
                "ACCEPTING",
                QuoteStateTypeVO.ACCEPTED,
                List.of(getQuoteItem("123"), getQuoteItem("124")))),
        Arguments.of(
            "No active quote and no termination -> create in accepting.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.ACCEPTING),
            List.of(),
            List.of(getQuoteItem("123")),
            NEW_QUOTE_ID,
            expectedQuoteUpdate(
                "ACCEPTING", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123")))));
  }

  // ----- AGREE ----

  @ParameterizedTest(name = "{0}")
  @MethodSource("getAgreeStates")
  public void testAgreeSave_success(
      String name,
      ContractNegotiation contractNegotiation,
      List<ExtendableQuoteVO> negotiationQuotes,
      List<ExtendableQuoteItemVO> quoteItems,
      String expectedQuoteId,
      ExtendableAgreementVO expectedAgreement,
      ExtendableQuoteUpdateVO expectedUpdate) {
    if (expectedAgreement != null) {
      when(tmfEdcMapper.toAgreement(eq(contractNegotiation.getId()), any()))
          .thenReturn(expectedAgreement);
      when(tmfEdcMapper.toCreate(any(ExtendableAgreementVO.class)))
          .thenAnswer(
              invocation -> {
                ExtendableAgreementVO extendableAgreementVO = invocation.getArgument(0);
                return new TMFObjectMapperImpl().map(extendableAgreementVO);
              });
    }

    testSave_success(
        contractNegotiation, negotiationQuotes, quoteItems, expectedQuoteId, false, expectedUpdate);

    if (expectedAgreement != null) {
      ArgumentCaptor<ExtendableAgreementCreateVO> agreementCaptor =
          ArgumentCaptor.forClass(ExtendableAgreementCreateVO.class);
      verify(agreementApiClient, times(1)).createAgreement(agreementCaptor.capture());
      ExtendableAgreementCreateVO createVO = agreementCaptor.getValue();
      assertEquals(
          AgreementState.IN_PROCESS.getValue(),
          createVO.getStatus(),
          "The agreement should be in process.");
      assertEquals(
          1,
          createVO.getAgreementItem().size(),
          "The terms and condition should have been added in state `under-negotiation`.");
      AgreementItemVO agreementItemVO = createVO.getAgreementItem().getFirst();
      assertEquals(
          1,
          agreementItemVO.getTermOrCondition().size(),
          "Terms and Conditions need to be included.");
    }
  }

  private static Stream<Arguments> getAgreeStates() {
    return Stream.of(
        Arguments.of(
            "[Provider]The negotiation should be transitioned from agreeing to agreed.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.AGREED),
            setActiveQuote(
                ContractNegotiationStates.AGREEING,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123")),
            "active-id",
            null,
            expectedQuoteUpdate("AGREED", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123")))),
        Arguments.of(
            "[Provider]The negotiation should be transitioned from agreeing to agreed with the provided agreement included.",
            addAgreement(
                getValidNegotiationWithIdOfferAndType(
                    TEST_NEGOTIATION_ID,
                    List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                    ContractNegotiation.Type.PROVIDER,
                    ContractNegotiationStates.AGREED)),
            setActiveQuote(
                ContractNegotiationStates.AGREEING,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123")),
            "active-id",
            getTestAgreement(),
            expectedQuoteUpdate("AGREED", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123")))),
        Arguments.of(
            "[Provider]The negotiation should be transitioned from accepted to agreed.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.AGREED),
            setActiveQuote(
                ContractNegotiationStates.ACCEPTED,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123")),
            "active-id",
            null,
            expectedQuoteUpdate("AGREED", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123")))),
        Arguments.of(
            "[Provider]The negotiation should be transitioned from accepted to agreed with the provided agreement included.",
            addAgreement(
                getValidNegotiationWithIdOfferAndType(
                    TEST_NEGOTIATION_ID,
                    List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                    ContractNegotiation.Type.PROVIDER,
                    ContractNegotiationStates.AGREED)),
            setActiveQuote(
                ContractNegotiationStates.ACCEPTED,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123")),
            "active-id",
            getTestAgreement(),
            expectedQuoteUpdate("AGREED", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123")))),
        Arguments.of(
            "[Provider]The negotiation should be transitioned from requested to agreed.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.AGREED),
            setActiveQuote(
                ContractNegotiationStates.REQUESTED,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123")),
            "active-id",
            null,
            expectedQuoteUpdate("AGREED", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123")))),
        Arguments.of(
            "[Provider]The negotiation should be transitioned from requested to agreed with the provided agreement included.",
            addAgreement(
                getValidNegotiationWithIdOfferAndType(
                    TEST_NEGOTIATION_ID,
                    List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                    ContractNegotiation.Type.PROVIDER,
                    ContractNegotiationStates.AGREED)),
            setActiveQuote(
                ContractNegotiationStates.REQUESTED,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123")),
            "active-id",
            getTestAgreement(),
            expectedQuoteUpdate("AGREED", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123")))),
        Arguments.of(
            "[Provider]The negotiation should be transitioned from accepted to agreeing.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.AGREEING),
            setActiveQuote(
                ContractNegotiationStates.ACCEPTED,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123")),
            "active-id",
            null,
            expectedQuoteUpdate(
                "AGREEING", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123")))),
        Arguments.of(
            "[Provider]The negotiation should be transitioned from accepted to agreeing with the provided agreement included.",
            addAgreement(
                getValidNegotiationWithIdOfferAndType(
                    TEST_NEGOTIATION_ID,
                    List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                    ContractNegotiation.Type.PROVIDER,
                    ContractNegotiationStates.AGREEING)),
            setActiveQuote(
                ContractNegotiationStates.ACCEPTED,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123")),
            "active-id",
            getTestAgreement(),
            expectedQuoteUpdate(
                "AGREEING", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123")))),
        Arguments.of(
            "[Provider]The negotiation should be transitioned from requested to agreeing.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.AGREEING),
            setActiveQuote(
                ContractNegotiationStates.REQUESTED,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123")),
            "active-id",
            null,
            expectedQuoteUpdate(
                "AGREEING", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123")))),
        Arguments.of(
            "[Provider]The negotiation should be transitioned from requested to agreeing with the provided agreement included.",
            addAgreement(
                getValidNegotiationWithIdOfferAndType(
                    TEST_NEGOTIATION_ID,
                    List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                    ContractNegotiation.Type.PROVIDER,
                    ContractNegotiationStates.AGREEING)),
            setActiveQuote(
                ContractNegotiationStates.REQUESTED,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
            List.of(getQuoteItem("123")),
            "active-id",
            getTestAgreement(),
            expectedQuoteUpdate(
                "AGREEING", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123")))));
  }

  // ----- VERIFICATION ----

  @ParameterizedTest(name = "{0}")
  @MethodSource("getVerificationStates")
  public void testVerificationSave_success(
      String name,
      ContractNegotiation contractNegotiation,
      List<ExtendableQuoteVO> negotiationQuotes,
      List<ExtendableQuoteItemVO> quoteItems,
      String participantId,
      ProductOrderVO existingOrder,
      String expectedQuoteId,
      ProductOrderCreateVO expectedProductOrder,
      ExtendableQuoteUpdateVO expectedUpdate) {
    // setup with the participant id
    tmfBackedContractNegotiationStore =
        new TMFBackedContractNegotiationStore(
            mock(Monitor.class),
            objectMapper,
            quoteApiClient,
            agreementApiClient,
            productOrderApiClient,
            productCatalogApiClient,
            productInventoryApiClient,
            participantResolver,
            tmfEdcMapper,
            participantId,
            TEST_CONTROL_PLANE_ID,
            criterionOperatorRegistry,
            leaseHolder,
            new TMFTransactionContext(mock(Monitor.class)),
            lockManager);

    if (existingOrder != null) {
      when(productOrderApiClient.findByQuoteId(eq(expectedQuoteId)))
          .thenReturn(List.of(existingOrder));
    }

    when(participantResolver.getTmfId(eq(TEST_PROVIDER_ID))).thenReturn(TMF_TEST_PROVIDER_ID);
    when(participantResolver.getTmfId(eq(TEST_CONSUMER_ID))).thenReturn(TMF_TEST_CONSUMER_ID);

    testSave_success(
        contractNegotiation, negotiationQuotes, quoteItems, expectedQuoteId, false, expectedUpdate);

    if (existingOrder != null) {
      verify(productOrderApiClient, times(0)).createProductOrder(any());
    } else {
      ArgumentCaptor<ProductOrderCreateVO> productOrderCaptor =
          ArgumentCaptor.forClass(ProductOrderCreateVO.class);
      verify(productOrderApiClient, times(1)).createProductOrder(productOrderCaptor.capture());

      ProductOrderCreateVO capturedProductOrder = productOrderCaptor.getValue();

      assertEquals(
          new HashSet<>(expectedProductOrder.getRelatedParty()),
          new HashSet<>(capturedProductOrder.getRelatedParty()),
          "All required related parties need to be included");
      assertEquals(
          new HashSet<>(expectedProductOrder.getQuote()),
          new HashSet<>(capturedProductOrder.getQuote()),
          "All quotes need to be included");
    }
  }

  private static Stream<Arguments> getVerificationStates() {
    return Stream.of(
        Arguments.of(
            "[Provider]The negotiation should be transitioned from agreed to verifying.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.VERIFYING,
                TEST_CONSUMER_ID),
            setActiveQuote(
                ContractNegotiationStates.AGREED,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5),
                QuoteStateTypeVO.ACCEPTED),
            List.of(getQuoteItem("123")),
            TEST_PROVIDER_ID,
            null,
            "active-id",
            getProductOrderCreate(
                "active-id", TMF_TEST_CONSUMER_ID, TMF_TEST_CONSUMER_ID, TMF_TEST_PROVIDER_ID),
            expectedQuoteUpdate(
                "VERIFYING", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123")))),
        Arguments.of(
            "[Consumer]The negotiation should be transitioned from agreed to verifying.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                ContractNegotiation.Type.CONSUMER,
                ContractNegotiationStates.VERIFYING,
                TEST_PROVIDER_ID),
            setActiveQuote(
                ContractNegotiationStates.AGREED,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5),
                QuoteStateTypeVO.ACCEPTED),
            List.of(getQuoteItem("123")),
            TEST_CONSUMER_ID,
            null,
            "active-id",
            getProductOrderCreate(
                "active-id", TMF_TEST_CONSUMER_ID, TMF_TEST_CONSUMER_ID, TMF_TEST_PROVIDER_ID),
            expectedQuoteUpdate(
                "VERIFYING", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123")))),
        Arguments.of(
            "[Provider]The negotiation should be transitioned from verifying to verified.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.VERIFIED,
                TEST_CONSUMER_ID),
            setActiveQuote(
                ContractNegotiationStates.VERIFYING,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5),
                QuoteStateTypeVO.ACCEPTED),
            List.of(getQuoteItem("123")),
            TEST_PROVIDER_ID,
            null,
            "active-id",
            getProductOrderCreate(
                "active-id", TMF_TEST_CONSUMER_ID, TMF_TEST_CONSUMER_ID, TMF_TEST_PROVIDER_ID),
            expectedQuoteUpdate(
                "VERIFIED", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123")))),
        Arguments.of(
            "[Consumer]The negotiation should be transitioned from verifying to verified.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                ContractNegotiation.Type.CONSUMER,
                ContractNegotiationStates.VERIFIED,
                TEST_PROVIDER_ID),
            setActiveQuote(
                ContractNegotiationStates.VERIFYING,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5),
                QuoteStateTypeVO.ACCEPTED),
            List.of(getQuoteItem("123")),
            TEST_CONSUMER_ID,
            null,
            "active-id",
            getProductOrderCreate(
                "active-id", TMF_TEST_CONSUMER_ID, TMF_TEST_CONSUMER_ID, TMF_TEST_PROVIDER_ID),
            expectedQuoteUpdate(
                "VERIFIED", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123")))),
        Arguments.of(
            "[Provider]The negotiation should be transitioned from agreed to verifying with an already existing order.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.VERIFYING,
                TEST_CONSUMER_ID),
            setActiveQuote(
                ContractNegotiationStates.AGREED,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5),
                QuoteStateTypeVO.ACCEPTED),
            List.of(getQuoteItem("123")),
            TEST_PROVIDER_ID,
            new ProductOrderVO(),
            "active-id",
            getProductOrderCreate(
                "active-id", TMF_TEST_CONSUMER_ID, TMF_TEST_CONSUMER_ID, TMF_TEST_PROVIDER_ID),
            expectedQuoteUpdate(
                "VERIFYING", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123")))),
        Arguments.of(
            "[Consumer]The negotiation should be transitioned from agreed to verifying with an already existing order.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                ContractNegotiation.Type.CONSUMER,
                ContractNegotiationStates.VERIFYING,
                TEST_PROVIDER_ID),
            setActiveQuote(
                ContractNegotiationStates.AGREED,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5),
                QuoteStateTypeVO.ACCEPTED),
            List.of(getQuoteItem("123")),
            TEST_CONSUMER_ID,
            new ProductOrderVO(),
            "active-id",
            getProductOrderCreate(
                "active-id", TMF_TEST_CONSUMER_ID, TMF_TEST_CONSUMER_ID, TMF_TEST_PROVIDER_ID),
            expectedQuoteUpdate(
                "VERIFYING", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123")))),
        Arguments.of(
            "[Provider]The negotiation should be transitioned from verifying to verified with an already existing order.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                ContractNegotiation.Type.PROVIDER,
                ContractNegotiationStates.VERIFIED,
                TEST_CONSUMER_ID),
            setActiveQuote(
                ContractNegotiationStates.VERIFYING,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5),
                QuoteStateTypeVO.ACCEPTED),
            List.of(getQuoteItem("123")),
            TEST_PROVIDER_ID,
            new ProductOrderVO(),
            "active-id",
            getProductOrderCreate(
                "active-id", TMF_TEST_CONSUMER_ID, TMF_TEST_CONSUMER_ID, TMF_TEST_PROVIDER_ID),
            expectedQuoteUpdate(
                "VERIFIED", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123")))),
        Arguments.of(
            "[Consumer]The negotiation should be transitioned from verifying to verified with an already existing order.",
            getValidNegotiationWithIdOfferAndType(
                TEST_NEGOTIATION_ID,
                List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                ContractNegotiation.Type.CONSUMER,
                ContractNegotiationStates.VERIFIED,
                TEST_PROVIDER_ID),
            setActiveQuote(
                ContractNegotiationStates.VERIFYING,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5),
                QuoteStateTypeVO.ACCEPTED),
            List.of(getQuoteItem("123")),
            TEST_CONSUMER_ID,
            new ProductOrderVO(),
            "active-id",
            getProductOrderCreate(
                "active-id", TMF_TEST_CONSUMER_ID, TMF_TEST_CONSUMER_ID, TMF_TEST_PROVIDER_ID),
            expectedQuoteUpdate(
                "VERIFIED", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123")))));
  }

  // ----- FINAL ----

  @ParameterizedTest(name = "{0}")
  @MethodSource("getFinalStates")
  public void testFinalSave_success(
      String name,
      ContractNegotiation contractNegotiation,
      List<ExtendableQuoteVO> negotiationQuotes,
      List<ExtendableQuoteItemVO> quoteItems,
      ProductOrderVO existingOrder,
      String expectedQuoteId,
      ExtendableProductCreate expectedProduct,
      ExtendableQuoteUpdateVO expectedUpdate) {
    when(productOrderApiClient.findByQuoteId(eq(expectedQuoteId)))
        .thenReturn(List.of(existingOrder));
    when(tmfEdcMapper.toUpdate(any(ProductOrderVO.class)))
        .thenAnswer(
            invocation -> {
              ProductOrderVO productOrderVO = invocation.getArgument(0);
              return new TMFObjectMapperImpl().map(productOrderVO);
            });
    when(tmfEdcMapper.toUpdate(any(ExtendableAgreementVO.class)))
        .thenAnswer(
            invocation -> {
              ExtendableAgreementVO agreementVO = invocation.getArgument(0);
              return new TMFObjectMapperImpl().mapToUpdate(agreementVO);
            });

    ExtendableProduct createdProduct = new ExtendableProduct();
    createdProduct.setId("created-product");
    when(productInventoryApiClient.createProduct(any())).thenReturn(createdProduct);

    when(participantResolver.getTmfId(eq(TEST_PROVIDER_ID))).thenReturn(TMF_TEST_PROVIDER_ID);
    when(participantResolver.getTmfId(eq(TEST_CONSUMER_ID))).thenReturn(TMF_TEST_CONSUMER_ID);

    ExtendableAgreementVO inProcessAgreementVO = getTestAgreement();
    inProcessAgreementVO.setId("test-agreement");
    inProcessAgreementVO.setStatus(AgreementState.IN_PROCESS.getValue());

    when(agreementApiClient.findByContractId(
            eq(contractNegotiation.getContractAgreement().getId())))
        .thenReturn(Optional.of(inProcessAgreementVO));

    testSave_success(
        contractNegotiation, negotiationQuotes, quoteItems, expectedQuoteId, false, expectedUpdate);

    ArgumentCaptor<ProductOrderUpdateVO> orderUpdateCaptor =
        ArgumentCaptor.forClass(ProductOrderUpdateVO.class);
    verify(productOrderApiClient, times(1))
        .updateProductOrder(eq(existingOrder.getId()), orderUpdateCaptor.capture());

    ArgumentCaptor<ExtendableProductCreate> productCreateCaptor =
        ArgumentCaptor.forClass(ExtendableProductCreate.class);
    verify(productInventoryApiClient, times(1)).createProduct(productCreateCaptor.capture());

    ArgumentCaptor<ExtendableAgreementUpdateVO> agreementUpdateCaptor =
        ArgumentCaptor.forClass(ExtendableAgreementUpdateVO.class);
    verify(agreementApiClient)
        .updateAgreement(eq(inProcessAgreementVO.getId()), agreementUpdateCaptor.capture());

    assertEquals(
        ProductOrderStateTypeVO.COMPLETED,
        orderUpdateCaptor.getValue().getState(),
        "The order should be completed.");
    assertEquals(
        expectedProduct, productCreateCaptor.getValue(), "The product should have been created.");

    ExtendableAgreementUpdateVO updatedAgreement = agreementUpdateCaptor.getValue();
    assertEquals(
        AgreementState.AGREED.getValue(),
        updatedAgreement.getStatus(),
        "The agreement should now be agreed.");

    List<AgreementItemVO> agreementItemVOS = updatedAgreement.getAgreementItem();
    assertEquals(
        1, agreementItemVOS.size(), "The product should be the only item in the agreement.");
    var productRefVOS = agreementItemVOS.getLast().getProduct();
    assertEquals(1, productRefVOS.size(), "Only single product references are allowed.");
    assertEquals(
        createdProduct.getId(),
        productRefVOS.getLast().getId(),
        "The correct product should be referenced.");
  }

  private static Stream<Arguments> getFinalStates() {
    return Stream.of(
        Arguments.of(
            "[Provider]The negotiation should be transitioned from verified to finalizing.",
            addAgreement(
                getValidNegotiationWithIdOfferAndType(
                    TEST_NEGOTIATION_ID,
                    List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                    ContractNegotiation.Type.PROVIDER,
                    ContractNegotiationStates.FINALIZING,
                    TEST_CONSUMER_ID)),
            setActiveQuote(
                ContractNegotiationStates.VERIFIED,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5),
                QuoteStateTypeVO.ACCEPTED,
                TEST_OFFER_ID),
            List.of(getQuoteItem("123", TEST_OFFER_ID)),
            getProductOrder(ProductOrderStateTypeVO.IN_PROGRESS),
            "active-id",
            getProductCreate(
                "test-agreement-" + TEST_ASSET_ID,
                TMF_TEST_PROVIDER_ID,
                TMF_TEST_CONSUMER_ID,
                Optional.of(TEST_OFFER_ID)),
            expectedQuoteUpdate(
                "FINALIZING",
                QuoteStateTypeVO.ACCEPTED,
                List.of(getQuoteItem("123", TEST_OFFER_ID)))),
        Arguments.of(
            "[Provider]The negotiation should be transitioned from verified to finalized.",
            addAgreement(
                getValidNegotiationWithIdOfferAndType(
                    TEST_NEGOTIATION_ID,
                    List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                    ContractNegotiation.Type.PROVIDER,
                    ContractNegotiationStates.FINALIZED,
                    TEST_CONSUMER_ID)),
            setActiveQuote(
                ContractNegotiationStates.VERIFIED,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5),
                QuoteStateTypeVO.ACCEPTED,
                TEST_OFFER_ID),
            List.of(getQuoteItem("123", TEST_OFFER_ID)),
            getProductOrder(ProductOrderStateTypeVO.IN_PROGRESS),
            "active-id",
            getProductCreate(
                "test-agreement-" + TEST_ASSET_ID,
                TMF_TEST_PROVIDER_ID,
                TMF_TEST_CONSUMER_ID,
                Optional.of(TEST_OFFER_ID)),
            expectedQuoteUpdate(
                "FINALIZED",
                QuoteStateTypeVO.ACCEPTED,
                List.of(getQuoteItem("123", TEST_OFFER_ID)))),
        Arguments.of(
            "[Provider]The negotiation should be transitioned from finalizing to finalized.",
            addAgreement(
                getValidNegotiationWithIdOfferAndType(
                    TEST_NEGOTIATION_ID,
                    List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                    ContractNegotiation.Type.PROVIDER,
                    ContractNegotiationStates.FINALIZED,
                    TEST_CONSUMER_ID)),
            setActiveQuote(
                ContractNegotiationStates.FINALIZING,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5),
                QuoteStateTypeVO.ACCEPTED,
                TEST_OFFER_ID),
            List.of(getQuoteItem("123", TEST_OFFER_ID)),
            getProductOrder(ProductOrderStateTypeVO.IN_PROGRESS),
            "active-id",
            getProductCreate(
                "test-agreement-" + TEST_ASSET_ID,
                TMF_TEST_PROVIDER_ID,
                TMF_TEST_CONSUMER_ID,
                Optional.of(TEST_OFFER_ID)),
            expectedQuoteUpdate(
                "FINALIZED",
                QuoteStateTypeVO.ACCEPTED,
                List.of(getQuoteItem("123", TEST_OFFER_ID)))),
        Arguments.of(
            "[Consumer]The negotiation should be transitioned from verified to finalizing.",
            addAgreement(
                getValidNegotiationWithIdOfferAndType(
                    TEST_NEGOTIATION_ID,
                    List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                    ContractNegotiation.Type.CONSUMER,
                    ContractNegotiationStates.FINALIZING,
                    TEST_CONSUMER_ID)),
            setActiveQuote(
                ContractNegotiationStates.VERIFIED,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5),
                QuoteStateTypeVO.ACCEPTED,
                TEST_OFFER_ID),
            List.of(getQuoteItem("123")),
            getProductOrder(ProductOrderStateTypeVO.IN_PROGRESS),
            "active-id",
            getProductCreate(
                "test-agreement-" + TEST_ASSET_ID,
                TMF_TEST_PROVIDER_ID,
                TMF_TEST_CONSUMER_ID,
                Optional.empty()),
            expectedQuoteUpdate(
                "FINALIZING", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123")))),
        Arguments.of(
            "[Consumer]The negotiation should be transitioned from verified to finalized.",
            addAgreement(
                getValidNegotiationWithIdOfferAndType(
                    TEST_NEGOTIATION_ID,
                    List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                    ContractNegotiation.Type.CONSUMER,
                    ContractNegotiationStates.FINALIZED,
                    TEST_CONSUMER_ID)),
            setActiveQuote(
                ContractNegotiationStates.VERIFIED,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5),
                QuoteStateTypeVO.ACCEPTED,
                TEST_OFFER_ID),
            List.of(getQuoteItem("123")),
            getProductOrder(ProductOrderStateTypeVO.IN_PROGRESS),
            "active-id",
            getProductCreate(
                "test-agreement-" + TEST_ASSET_ID,
                TMF_TEST_PROVIDER_ID,
                TMF_TEST_CONSUMER_ID,
                Optional.empty()),
            expectedQuoteUpdate(
                "FINALIZED", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123")))),
        Arguments.of(
            "[Consumer]The negotiation should be transitioned from finalizing to finalized.",
            addAgreement(
                getValidNegotiationWithIdOfferAndType(
                    TEST_NEGOTIATION_ID,
                    List.of(getOffer(TEST_OFFER_ID + ":asset:123")),
                    ContractNegotiation.Type.CONSUMER,
                    ContractNegotiationStates.FINALIZED,
                    TEST_CONSUMER_ID)),
            setActiveQuote(
                ContractNegotiationStates.FINALIZING,
                "active-id",
                getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5),
                QuoteStateTypeVO.ACCEPTED,
                TEST_OFFER_ID),
            List.of(getQuoteItem("123")),
            getProductOrder(ProductOrderStateTypeVO.IN_PROGRESS),
            "active-id",
            getProductCreate(
                "test-agreement-" + TEST_ASSET_ID,
                TMF_TEST_PROVIDER_ID,
                TMF_TEST_CONSUMER_ID,
                Optional.empty()),
            expectedQuoteUpdate(
                "FINALIZED", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123")))));
  }

  // ----- TERMINATION ----

  @ParameterizedTest(name = "{0}")
  @MethodSource("getTerminations")
  public void testTerminationSave_success(
      String name,
      ContractNegotiation contractNegotiation,
      List<QuoteProductHolder> negotiationQuotes,
      List<String> expectedOrderCancellations,
      List<String> expectedQuoteCancellation,
      Optional<String> expectedAgreementCancellation) {

    when(tmfEdcMapper.toUpdate(any(ProductOrderVO.class)))
        .thenAnswer(
            invocation -> {
              ProductOrderVO productOrderVO = invocation.getArgument(0);
              return new TMFObjectMapperImpl().map(productOrderVO);
            });
    when(tmfEdcMapper.toUpdate(any(ExtendableQuoteVO.class)))
        .thenAnswer(
            invocation -> {
              ExtendableQuoteVO extendableQuoteVO = invocation.getArgument(0);
              return new TMFObjectMapperImpl().map(extendableQuoteVO);
            });
    when(tmfEdcMapper.toUpdate(any(ExtendableAgreementVO.class)))
        .thenAnswer(
            invocation -> {
              ExtendableAgreementVO extendableAgreementVO = invocation.getArgument(0);
              return new TMFObjectMapperImpl().mapToUpdate(extendableAgreementVO);
            });

    Map<String, List<ExtendableQuoteVO>> quotesByNeg = new HashMap<>();
    negotiationQuotes.stream()
        .peek(
            qph -> {
              if (qph.productOrderVO() != null) {
                when(productOrderApiClient.findByQuoteId(qph.extendableQuoteVO.getId()))
                    .thenReturn(List.of(qph.productOrderVO()));
              }
            })
        .map(QuoteProductHolder::extendableQuoteVO)
        .forEach(
            nq -> {
              if (quotesByNeg.containsKey(nq.getExternalId())) {
                quotesByNeg.get(nq.getExternalId()).add(nq);
              } else {
                quotesByNeg.put(nq.getExternalId(), new ArrayList<>(List.of(nq)));
              }
            });
    quotesByNeg.forEach(
        (key, value) -> when(quoteApiClient.findByNegotiationId(eq(key))).thenReturn(value));

    expectedAgreementCancellation.ifPresent(
        agreementId -> {
          ExtendableAgreementVO extendableAgreementVO = new ExtendableAgreementVO();
          extendableAgreementVO.setId(agreementId);
          when(agreementApiClient.findByNegotiationId(eq(contractNegotiation.getId())))
              .thenReturn(Optional.of(extendableAgreementVO));
        });

    tmfBackedContractNegotiationStore.save(contractNegotiation);

    ArgumentCaptor<String> orderIdCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> quoteIdCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<ProductOrderUpdateVO> orderUpdateCaptor =
        ArgumentCaptor.forClass(ProductOrderUpdateVO.class);
    ArgumentCaptor<ExtendableQuoteUpdateVO> quoteUpdateCaptor =
        ArgumentCaptor.forClass(ExtendableQuoteUpdateVO.class);

    verify(quoteApiClient, times(expectedQuoteCancellation.size()))
        .updateQuote(quoteIdCaptor.capture(), quoteUpdateCaptor.capture());
    verify(productOrderApiClient, times(expectedOrderCancellations.size()))
        .updateProductOrder(orderIdCaptor.capture(), orderUpdateCaptor.capture());

    assertEquals(
        new HashSet<>(expectedOrderCancellations),
        new HashSet<>(orderIdCaptor.getAllValues()),
        "All expected orders should have been cancelled.");
    assertEquals(
        new HashSet<>(expectedQuoteCancellation),
        new HashSet<>(quoteIdCaptor.getAllValues()),
        "All expected quotes should have been cancelled.");

    quoteUpdateCaptor
        .getAllValues()
        .forEach(
            equ ->
                assertEquals(
                    QuoteStateTypeVO.CANCELLED,
                    equ.getState(),
                    "All quotes need to be updated to cancelled."));
    orderUpdateCaptor
        .getAllValues()
        .forEach(
            ou ->
                assertEquals(
                    ProductOrderStateTypeVO.CANCELLED,
                    ou.getState(),
                    "All orders need to be updated to cancelled."));

    if (expectedAgreementCancellation.isPresent()) {
      ArgumentCaptor<ExtendableAgreementUpdateVO> agreementUpdateCaptor =
          ArgumentCaptor.forClass(ExtendableAgreementUpdateVO.class);
      verify(agreementApiClient, times(1))
          .updateAgreement(
              eq(expectedAgreementCancellation.get()), agreementUpdateCaptor.capture());
      assertEquals(
          AgreementState.REJECTED.getValue(),
          agreementUpdateCaptor.getValue().getStatus(),
          "The agreement should have been rejected.");
    }
  }

  private static Stream<Arguments> getTerminations() {
    return Stream.of(
        Arguments.of(
            "[Initial] Terminate the initial quote.",
            getValidNegotiationWithIdInState(
                TEST_NEGOTIATION_ID, ContractNegotiationStates.TERMINATED),
            getWithoutProduct(
                setActiveQuote(
                    ContractNegotiationStates.INITIAL,
                    "active-id",
                    getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5),
                    QuoteStateTypeVO.IN_PROGRESS)),
            List.of(),
            List.of("active-id"),
            Optional.empty()),
        Arguments.of(
            "[Requesting] Terminate the requesting quote.",
            getValidNegotiationWithIdInState(
                TEST_NEGOTIATION_ID, ContractNegotiationStates.TERMINATED),
            getWithoutProduct(
                setActiveQuote(
                    ContractNegotiationStates.REQUESTING,
                    "active-id",
                    getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5),
                    QuoteStateTypeVO.IN_PROGRESS)),
            List.of(),
            List.of("active-id"),
            Optional.empty()),
        Arguments.of(
            "[Requested] Terminate the requested quote.",
            getValidNegotiationWithIdInState(
                TEST_NEGOTIATION_ID, ContractNegotiationStates.TERMINATED),
            getWithoutProduct(
                setActiveQuote(
                    ContractNegotiationStates.REQUESTED,
                    "active-id",
                    getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5),
                    QuoteStateTypeVO.APPROVED)),
            List.of(),
            List.of("active-id"),
            Optional.empty()),
        Arguments.of(
            "[Offering] Terminate the offering quote.",
            getValidNegotiationWithIdInState(
                TEST_NEGOTIATION_ID, ContractNegotiationStates.TERMINATED),
            getWithoutProduct(
                setActiveQuote(
                    ContractNegotiationStates.OFFERING,
                    "active-id",
                    getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5),
                    QuoteStateTypeVO.APPROVED)),
            List.of(),
            List.of("active-id"),
            Optional.empty()),
        Arguments.of(
            "[Offered] Terminate the offered quote.",
            getValidNegotiationWithIdInState(
                TEST_NEGOTIATION_ID, ContractNegotiationStates.TERMINATED),
            getWithoutProduct(
                setActiveQuote(
                    ContractNegotiationStates.OFFERED,
                    "active-id",
                    getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5),
                    QuoteStateTypeVO.APPROVED)),
            List.of(),
            List.of("active-id"),
            Optional.empty()),
        Arguments.of(
            "[Accepted] Terminate the accepted quote.",
            getValidNegotiationWithIdInState(
                TEST_NEGOTIATION_ID, ContractNegotiationStates.TERMINATED),
            getWithoutProduct(
                setActiveQuote(
                    ContractNegotiationStates.ACCEPTED,
                    "active-id",
                    getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5),
                    QuoteStateTypeVO.ACCEPTED)),
            List.of(),
            List.of("active-id"),
            Optional.empty()),
        Arguments.of(
            "[Accepting] Terminate the accepting quote.",
            getValidNegotiationWithIdInState(
                TEST_NEGOTIATION_ID, ContractNegotiationStates.TERMINATED),
            getWithoutProduct(
                setActiveQuote(
                    ContractNegotiationStates.ACCEPTING,
                    "active-id",
                    getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5),
                    QuoteStateTypeVO.ACCEPTED)),
            List.of(),
            List.of("active-id"),
            Optional.empty()),
        Arguments.of(
            "[Agreeing] Terminate the agreeing quote and reject the existing agreement.",
            getValidNegotiationWithIdInState(
                TEST_NEGOTIATION_ID, ContractNegotiationStates.TERMINATED),
            getWithoutProduct(
                setActiveQuote(
                    ContractNegotiationStates.ACCEPTING,
                    "active-id",
                    getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5),
                    QuoteStateTypeVO.ACCEPTED)),
            List.of(),
            List.of("active-id"),
            Optional.of("test-agreement")),
        Arguments.of(
            "[Agreed] Terminate the agreed quote and reject the existing agreement.",
            getValidNegotiationWithIdInState(
                TEST_NEGOTIATION_ID, ContractNegotiationStates.TERMINATED),
            getWithoutProduct(
                setActiveQuote(
                    ContractNegotiationStates.AGREED,
                    "active-id",
                    getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5),
                    QuoteStateTypeVO.ACCEPTED)),
            List.of(),
            List.of("active-id"),
            Optional.of("test-agreement")),
        Arguments.of(
            "[Verified] Terminate the verified quote, reject the existing agreement and cancel the product order.",
            getValidNegotiationWithIdInState(
                TEST_NEGOTIATION_ID, ContractNegotiationStates.TERMINATED),
            addQuoteProductHolder(
                getWithoutProduct(
                    getQuotes(
                        TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5, QuoteStateTypeVO.CANCELLED)),
                getWithProduct(
                    getQuote(
                        "active-id",
                        TEST_NEGOTIATION_ID,
                        TEST_CONTROL_PLANE_ID,
                        QuoteStateTypeVO.ACCEPTED,
                        ContractNegotiationStates.VERIFIED),
                    "test-product")),
            List.of("test-product"),
            List.of("active-id"),
            Optional.of("test-agreement")),
        Arguments.of(
            "[Verifying] Terminate the verifying quote, reject the existing agreement and cancel the product order.",
            getValidNegotiationWithIdInState(
                TEST_NEGOTIATION_ID, ContractNegotiationStates.TERMINATED),
            addQuoteProductHolder(
                getWithoutProduct(
                    getQuotes(
                        TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5, QuoteStateTypeVO.CANCELLED)),
                getWithProduct(
                    getQuote(
                        "active-id",
                        TEST_NEGOTIATION_ID,
                        TEST_CONTROL_PLANE_ID,
                        QuoteStateTypeVO.ACCEPTED,
                        ContractNegotiationStates.VERIFYING),
                    "test-product")),
            List.of("test-product"),
            List.of("active-id"),
            Optional.of("test-agreement")),
        Arguments.of(
            "[Finalized] Terminate the finalized quote, reject the existing agreement and cancel the product order.",
            getValidNegotiationWithIdInState(
                TEST_NEGOTIATION_ID, ContractNegotiationStates.TERMINATED),
            addQuoteProductHolder(
                getWithoutProduct(
                    getQuotes(
                        TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5, QuoteStateTypeVO.CANCELLED)),
                getWithProduct(
                    getQuote(
                        "active-id",
                        TEST_NEGOTIATION_ID,
                        TEST_CONTROL_PLANE_ID,
                        QuoteStateTypeVO.ACCEPTED,
                        ContractNegotiationStates.FINALIZED),
                    "test-product")),
            List.of("test-product"),
            List.of("active-id"),
            Optional.of("test-agreement")),
        Arguments.of(
            "[Terminating] Terminate the terminating quote, reject the existing agreement and cancel the product order.",
            getValidNegotiationWithIdInState(
                TEST_NEGOTIATION_ID, ContractNegotiationStates.TERMINATED),
            addQuoteProductHolder(
                getWithoutProduct(
                    getQuotes(
                        TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5, QuoteStateTypeVO.CANCELLED)),
                getWithProduct(
                    getQuote(
                        "active-id",
                        TEST_NEGOTIATION_ID,
                        TEST_CONTROL_PLANE_ID,
                        QuoteStateTypeVO.ACCEPTED,
                        ContractNegotiationStates.TERMINATING),
                    "test-product")),
            List.of("test-product"),
            List.of("active-id"),
            Optional.of("test-agreement")));
  }

  private static List<QuoteProductHolder> addQuoteProductHolder(
      List<QuoteProductHolder> quoteProductHolders, QuoteProductHolder quoteProductHolder) {
    quoteProductHolders.add(quoteProductHolder);
    return quoteProductHolders;
  }

  private static QuoteProductHolder getWithProduct(ExtendableQuoteVO quoteVO, String productId) {
    return new QuoteProductHolder(quoteVO, new ProductOrderVO().id(productId));
  }

  private static List<QuoteProductHolder> getWithoutProduct(List<ExtendableQuoteVO> quoteVOS) {
    return new ArrayList<>(
        quoteVOS.stream()
            .map(extendableQuoteVO -> new QuoteProductHolder(extendableQuoteVO, null))
            .toList());
  }

  private record QuoteProductHolder(
      ExtendableQuoteVO extendableQuoteVO, ProductOrderVO productOrderVO) {}

  private void testSave_success(
      ContractNegotiation contractNegotiation,
      List<ExtendableQuoteVO> negotiationQuotes,
      List<ExtendableQuoteItemVO> quoteItems,
      String expectedQuoteId,
      boolean isCounterOffer,
      ExtendableQuoteUpdateVO expectedUpdate) {
    Map<String, List<ExtendableQuoteVO>> quotesByNeg = new HashMap<>();
    negotiationQuotes.forEach(
        nq -> {
          if (quotesByNeg.containsKey(nq.getExternalId())) {
            quotesByNeg.get(nq.getExternalId()).add(nq);
          } else {
            quotesByNeg.put(nq.getExternalId(), new ArrayList<>(List.of(nq)));
          }
        });
    quotesByNeg.forEach(
        (key, value) -> when(quoteApiClient.findByNegotiationId(eq(key))).thenReturn(value));

    ArgumentCaptor<ExtendableQuoteUpdateVO> quoteCaptor =
        ArgumentCaptor.forClass(ExtendableQuoteUpdateVO.class);
    ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);

    ExtendableProductOffering extendableProductOffering = getExtendableProductOffering();

    when(productCatalogApiClient.getProductOfferingByExternalId(eq(TEST_OFFER_ID)))
        .thenReturn(Optional.of(extendableProductOffering));
    switch (contractNegotiation.getType()) {
      case PROVIDER ->
          quoteItems.forEach(
              qi ->
                  when(tmfEdcMapper.fromProviderContractOffer(any(), any(), any())).thenReturn(qi));
      case CONSUMER ->
          quoteItems.forEach(
              qi -> when(tmfEdcMapper.fromConsumerContractOffer(any(), any())).thenReturn(qi));
    }

    when(tmfEdcMapper.toUpdate(any(ExtendableQuoteVO.class)))
        .thenAnswer(
            invocation -> {
              ExtendableQuoteVO extendableQuoteVO = invocation.getArgument(0);
              return new TMFObjectMapperImpl().map(extendableQuoteVO);
            });

    when(quoteApiClient.createQuote(any(ExtendableQuoteCreateVO.class)))
        .thenAnswer(
            invocation -> {
              ExtendableQuoteCreateVO extendableQuoteCreateVO = invocation.getArgument(0);
              ExtendableQuoteVO extendableQuoteVO =
                  new ExtendableQuoteVO()
                      .setContractNegotiationState(
                          extendableQuoteCreateVO.getContractNegotiationState());
              extendableQuoteVO.setQuoteItem(extendableQuoteCreateVO.getQuoteItem());
              extendableQuoteVO.setId(NEW_QUOTE_ID);
              extendableQuoteVO.setExternalId(TEST_NEGOTIATION_ID);
              return extendableQuoteVO;
            });

    tmfBackedContractNegotiationStore.save(contractNegotiation);

    if (isCounterOffer) {
      verify(quoteApiClient, times(2)).updateQuote(idCaptor.capture(), quoteCaptor.capture());
    } else {
      verify(quoteApiClient, times(1)).updateQuote(idCaptor.capture(), quoteCaptor.capture());
    }
    verify(leaseHolder, times(1)).acquireLease(eq(contractNegotiation.getId()), any());
    verify(leaseHolder, times(1)).freeLease(eq(contractNegotiation.getId()), any());

    ExtendableQuoteUpdateVO extendableQuoteUpdateVO = quoteCaptor.getAllValues().getLast();

    assertEquals(
        expectedQuoteId, idCaptor.getValue(), "The correct quote should have been updated.");

    assertEquals(
        expectedUpdate.getExternalId(),
        extendableQuoteUpdateVO.getExternalId(),
        "The correct negotiationId should be included.");
    assertEquals(
        expectedUpdate.getContractNegotiationState(),
        extendableQuoteUpdateVO.getContractNegotiationState(),
        "The correct negotiation state should be stored.");
    assertEquals(
        expectedUpdate.getState(),
        extendableQuoteUpdateVO.getState(),
        "The quote state should be stored.");
    assertEquals(
        expectedUpdate.getExtendableQuoteItem(),
        extendableQuoteUpdateVO.getExtendableQuoteItem(),
        "The offerid should be set for the quote item.");
  }

  private static ExtendableProductCreate getProductCreate(
      String productId, String providerId, String consumerId, Optional<String> offeringId) {
    ExtendableProductCreate extendableProductCreate =
        new ExtendableProductCreate().setExternalId(productId);
    extendableProductCreate
        .status(ProductStatusTypeVO.ACTIVE)
        .addRelatedPartyItem(
            new org.seamware.tmforum.productinventory.model.RelatedPartyVO()
                .id(providerId)
                .role("Provider"))
        .addRelatedPartyItem(
            new org.seamware.tmforum.productinventory.model.RelatedPartyVO()
                .id(consumerId)
                .role("Consumer"));
    offeringId.ifPresent(
        id -> extendableProductCreate.productOffering(new ProductOfferingRefVO().id(id)));
    return extendableProductCreate;
  }

  private static ProductOrderVO getProductOrder(ProductOrderStateTypeVO state) {
    return new ProductOrderVO().state(state).id("test-order");
  }

  private static ProductOrderCreateVO getProductOrderCreate(
      String quoteId, String costumerId, String consumerId, String providerId) {
    ProductOrderCreateVO productOrderCreateVO = new ProductOrderCreateVO();
    productOrderCreateVO.quote(List.of(new QuoteRefVO().id(quoteId)));
    productOrderCreateVO.addRelatedPartyItem(
        new org.seamware.tmforum.productorder.model.RelatedPartyVO()
            .id(costumerId)
            .role("Customer"));
    productOrderCreateVO.addRelatedPartyItem(
        new org.seamware.tmforum.productorder.model.RelatedPartyVO()
            .id(consumerId)
            .role("Consumer"));
    productOrderCreateVO.addRelatedPartyItem(
        new org.seamware.tmforum.productorder.model.RelatedPartyVO()
            .id(providerId)
            .role("Provider"));
    return productOrderCreateVO;
  }

  private static ExtendableQuoteItemVO getQuoteItem(String externalId) {
    return new ExtendableQuoteItemVO().setExternalId(externalId);
  }

  private static ExtendableQuoteItemVO getQuoteItem(String externalId, String offering) {
    ExtendableQuoteItemVO extendableQuoteItemVO = getQuoteItem(externalId);
    extendableQuoteItemVO.setProductOffering(
        new org.seamware.tmforum.quote.model.ProductOfferingRefVO().id(offering));
    return extendableQuoteItemVO;
  }

  private static ExtendableQuoteCreateVO expectedQuoteCreate(
      String state, List<QuoteItemVO> quoteItemVOS) {
    ContractNegotiationState expectedState =
        new ContractNegotiationState()
            .setState(state)
            .setControlplane(TEST_CONTROL_PLANE)
            .setCounterPartyAddress(TEST_COUNTER_PARTY_ADDRESS)
            .setLeased(false)
            .setPending(false);

    ExtendableQuoteCreateVO expectedCreate =
        new ExtendableQuoteCreateVO().setContractNegotiationState(expectedState);
    expectedCreate.setQuoteItem(quoteItemVOS);
    expectedCreate.setExternalId(TEST_NEGOTIATION_ID);
    return expectedCreate;
  }

  private static ExtendableQuoteUpdateVO expectedQuoteUpdate(
      String state, QuoteStateTypeVO quoteState, List<ExtendableQuoteItemVO> quoteItemVOS) {
    ContractNegotiationState expectedState =
        new ContractNegotiationState()
            .setState(state)
            .setControlplane(TEST_CONTROL_PLANE)
            .setCounterPartyAddress(TEST_COUNTER_PARTY_ADDRESS)
            .setLeased(false)
            .setPending(false);

    ExtendableQuoteUpdateVO expectedUpdate =
        new ExtendableQuoteUpdateVO().setContractNegotiationState(expectedState);
    expectedUpdate.setExtendableQuoteItem(quoteItemVOS);
    expectedUpdate.setState(quoteState);
    expectedUpdate.setExternalId(TEST_NEGOTIATION_ID);
    return expectedUpdate;
  }

  private static List<ExtendableQuoteVO> setActiveQuote(
      ContractNegotiationStates activeState, String activeId, List<ExtendableQuoteVO> quoteVOS) {
    quoteVOS.forEach(
        qvo -> {
          qvo.getContractNegotiationState().setState(ContractNegotiationStates.TERMINATED.name());
          qvo.setState(QuoteStateTypeVO.CANCELLED);
        });
    quoteVOS.getLast().getContractNegotiationState().setState(activeState.name());
    quoteVOS.getLast().setId(activeId);
    return quoteVOS;
  }

  private static List<ExtendableQuoteVO> setActiveQuote(
      ContractNegotiationStates activeState,
      String activeId,
      List<ExtendableQuoteVO> quoteVOS,
      QuoteStateTypeVO stateTypeVO) {
    List<ExtendableQuoteVO> extendableQuoteVOS = setActiveQuote(activeState, activeId, quoteVOS);
    extendableQuoteVOS.getLast().setState(stateTypeVO);
    return extendableQuoteVOS;
  }

  private static List<ExtendableQuoteVO> setActiveQuote(
      ContractNegotiationStates activeState,
      String activeId,
      List<ExtendableQuoteVO> quoteVOS,
      QuoteStateTypeVO stateTypeVO,
      String offeringId) {
    List<ExtendableQuoteVO> extendableQuoteVOS =
        setActiveQuote(activeState, activeId, quoteVOS, stateTypeVO);
    ExtendableQuoteItemVO quoteItemVO = new ExtendableQuoteItemVO();
    quoteItemVO.productOffering(
        new org.seamware.tmforum.quote.model.ProductOfferingRefVO().id(offeringId));
    extendableQuoteVOS.getLast().setExtendableQuoteItem(List.of(quoteItemVO));
    return extendableQuoteVOS;
  }
}
