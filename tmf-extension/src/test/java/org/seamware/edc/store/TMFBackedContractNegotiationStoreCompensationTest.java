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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.junit.jupiter.api.Test;
import org.seamware.edc.domain.*;
import org.seamware.tmforum.productorder.model.*;
import org.seamware.tmforum.quote.model.QuoteStateTypeVO;

/**
 * Tests that saga compensations are triggered when save() fails partway through multi-step TMForum
 * API operations.
 */
public class TMFBackedContractNegotiationStoreCompensationTest
    extends TMFBackedContractNegotiationStoreTest {

  @Test
  void initialSave_createQuote_fails_no_compensation_needed() {
    // When createQuote itself fails, there's nothing to compensate
    ContractNegotiation negotiation =
        getNegotiation(
            ContractNegotiation.Type.CONSUMER,
            ContractNegotiationStates.INITIAL,
            false,
            TEST_COUNTER_PARTY_ID,
            List.of(getOffer(TEST_OFFER_ID)));

    when(quoteApiClient.findByNegotiationId(any())).thenReturn(List.of());
    when(quoteApiClient.createQuote(any())).thenThrow(new RuntimeException("API down"));
    when(participantResolver.getTmfId(any())).thenReturn("tmf-id");
    when(tmfEdcMapper.fromConsumerContractOffer(any(), any()))
        .thenReturn(new ExtendableQuoteItemVO());

    assertThrows(
        EdcPersistenceException.class, () -> tmfBackedContractNegotiationStore.save(negotiation));

    // No compensation calls since createQuote failed (nothing was created)
    verify(quoteApiClient, never()).updateQuote(any(), any());
  }

  @Test
  void requestedSave_updateQuote_fails_compensates_createQuote() {
    // REQUESTED: createQuote succeeds, then updateQuote fails
    // Compensation should try to cancel the created quote
    ContractNegotiation negotiation =
        getNegotiation(
            ContractNegotiation.Type.CONSUMER,
            ContractNegotiationStates.REQUESTED,
            false,
            TEST_COUNTER_PARTY_ID,
            List.of(getOffer(TEST_OFFER_ID)));

    ExtendableQuoteVO createdQuote = new ExtendableQuoteVO();
    createdQuote.setId("created-quote-id");
    createdQuote.setState(QuoteStateTypeVO.IN_PROGRESS);

    when(quoteApiClient.findByNegotiationId(any())).thenReturn(List.of());
    when(quoteApiClient.createQuote(any())).thenReturn(createdQuote);
    when(quoteApiClient.updateQuote(any(), any())).thenThrow(new RuntimeException("Update failed"));
    when(participantResolver.getTmfId(any())).thenReturn("tmf-id");
    when(tmfEdcMapper.fromConsumerContractOffer(any(), any()))
        .thenReturn(new ExtendableQuoteItemVO());
    when(tmfEdcMapper.toUpdate(any(ExtendableQuoteVO.class)))
        .thenAnswer(
            invocation -> {
              ExtendableQuoteVO q = invocation.getArgument(0);
              return new TMFObjectMapperImpl().map(q);
            });

    assertThrows(
        EdcPersistenceException.class, () -> tmfBackedContractNegotiationStore.save(negotiation));

    // The compensation for createQuote should try to cancel it via updateQuote
    // First call is the failed updateQuote, second call is the compensation
    verify(quoteApiClient, atLeast(2)).updateQuote(any(), any());
  }

  @Test
  void requestedState_counterOffer_terminateFails_compensates() {
    // In REQUESTED state with an existing OFFERED quote, terminateQuote is called first
    // If terminateQuote fails, no compensation needed (nothing was modified yet)
    ContractNegotiation negotiation =
        getNegotiation(
            ContractNegotiation.Type.CONSUMER,
            ContractNegotiationStates.REQUESTED,
            false,
            TEST_COUNTER_PARTY_ID,
            List.of(getOffer(TEST_OFFER_ID)));

    ExtendableQuoteVO offeredQuote =
        getExtendableQuoteVo(
            new ContractNegotiationState().setState("OFFERED"),
            QuoteStateTypeVO.APPROVED,
            TEST_CONSUMER_TMF_ID,
            TEST_PROVIDER_TMF_ID,
            1,
            List.of(TEST_OFFER_ID));
    offeredQuote.setId("offered-quote-id");

    when(quoteApiClient.findByNegotiationId(any())).thenReturn(List.of(offeredQuote));

    when(quoteApiClient.updateQuote(any(), any()))
        .thenThrow(new RuntimeException("Terminate failed"));

    assertThrows(
        EdcPersistenceException.class, () -> tmfBackedContractNegotiationStore.save(negotiation));
  }

  @Test
  void agreeState_createAgreement_fails_compensates_quoteUpdate() {
    // In AGREED state: updateQuote succeeds, but createAgreement fails
    // Compensation should revert the quote
    ContractNegotiation negotiation =
        getNegotiation(
            ContractNegotiation.Type.PROVIDER,
            ContractNegotiationStates.AGREED,
            false,
            TEST_COUNTER_PARTY_ID,
            List.of(getOffer(TEST_OFFER_ID)),
            Optional.of(getValidContractAgreement()));

    ExtendableQuoteVO activeQuote =
        getExtendableQuoteVo(
            new ContractNegotiationState().setState("REQUESTED"),
            QuoteStateTypeVO.IN_PROGRESS,
            TEST_CONSUMER_TMF_ID,
            TEST_PROVIDER_TMF_ID,
            1,
            List.of(TEST_OFFER_ID));
    activeQuote.setId("active-quote-id");

    when(quoteApiClient.findByNegotiationId(any())).thenReturn(List.of(activeQuote));

    // updateQuote succeeds (first call)
    when(quoteApiClient.updateQuote(any(), any())).thenReturn(activeQuote);
    when(tmfEdcMapper.toUpdate(any(ExtendableQuoteVO.class)))
        .thenAnswer(
            invocation -> {
              ExtendableQuoteVO q = invocation.getArgument(0);
              return new TMFObjectMapperImpl().map(q);
            });
    when(participantResolver.getTmfId(any())).thenReturn("tmf-id");
    when(tmfEdcMapper.toAgreement(any(), any())).thenReturn(getTestAgreement());
    when(tmfEdcMapper.toCreate(any(ExtendableAgreementVO.class)))
        .thenReturn(new ExtendableAgreementCreateVO());
    when(agreementApiClient.createAgreement(any()))
        .thenThrow(new RuntimeException("Agreement API down"));
    when(productCatalogApiClient.getProductOfferingByExternalId(any()))
        .thenReturn(Optional.of(getExtendableProductOffering()));
    when(tmfEdcMapper.fromProviderContractOffer(any(), any(), any()))
        .thenReturn(new ExtendableQuoteItemVO());

    assertThrows(
        EdcPersistenceException.class, () -> tmfBackedContractNegotiationStore.save(negotiation));

    // updateQuote called at least twice: original + compensation revert
    verify(quoteApiClient, atLeast(2)).updateQuote(any(), any());
  }

  @Test
  void terminationState_cancelProductOrder_fails_compensates_quoteCancel() {
    // In TERMINATED state: quote cancellation succeeds, but cancelProductOrder fails
    // Compensation should revert the quote cancellation
    ContractNegotiation negotiation =
        getNegotiation(
            ContractNegotiation.Type.CONSUMER,
            ContractNegotiationStates.TERMINATED,
            false,
            TEST_COUNTER_PARTY_ID,
            List.of(getOffer(TEST_OFFER_ID)));

    ExtendableQuoteVO activeQuote =
        getExtendableQuoteVo(
            new ContractNegotiationState().setState("AGREED"),
            QuoteStateTypeVO.ACCEPTED,
            TEST_CONSUMER_TMF_ID,
            TEST_PROVIDER_TMF_ID,
            1,
            List.of(TEST_OFFER_ID));
    activeQuote.setId("active-quote-id");

    when(quoteApiClient.findByNegotiationId(any())).thenReturn(List.of(activeQuote));

    // First updateQuote (cancel quote) succeeds
    when(quoteApiClient.updateQuote(any(), any())).thenReturn(activeQuote);
    when(tmfEdcMapper.toUpdate(any(ExtendableQuoteVO.class)))
        .thenAnswer(
            invocation -> {
              ExtendableQuoteVO q = invocation.getArgument(0);
              return new TMFObjectMapperImpl().map(q);
            });

    // cancelProductOrder fails
    when(productOrderApiClient.findByQuoteId(any()))
        .thenThrow(new RuntimeException("Order API down"));

    assertThrows(
        EdcPersistenceException.class, () -> tmfBackedContractNegotiationStore.save(negotiation));

    // updateQuote: 1 for cancel + at least 1 for compensation revert
    verify(quoteApiClient, atLeast(2)).updateQuote(any(), any());
  }

  @Test
  void verifyState_productOrder_createFails_no_quote_compensation() {
    // In VERIFIED state: createProductOrder fails before any quote update
    // No quote compensation needed since no quote was modified
    ContractNegotiation negotiation =
        getNegotiation(
            ContractNegotiation.Type.CONSUMER,
            ContractNegotiationStates.VERIFIED,
            false,
            TEST_COUNTER_PARTY_ID,
            List.of(getOffer(TEST_OFFER_ID)));

    ExtendableQuoteVO activeQuote =
        getExtendableQuoteVo(
            new ContractNegotiationState().setState("AGREED"),
            QuoteStateTypeVO.ACCEPTED,
            TEST_CONSUMER_TMF_ID,
            TEST_PROVIDER_TMF_ID,
            1,
            List.of(TEST_OFFER_ID));
    activeQuote.setId("active-quote-id");

    when(quoteApiClient.findByNegotiationId(any())).thenReturn(List.of(activeQuote));
    when(productOrderApiClient.findByQuoteId(any())).thenReturn(List.of());
    when(productOrderApiClient.createProductOrder(any()))
        .thenThrow(new RuntimeException("Order creation failed"));
    when(participantResolver.getTmfId(any())).thenReturn("tmf-id");

    assertThrows(
        EdcPersistenceException.class, () -> tmfBackedContractNegotiationStore.save(negotiation));

    // No quote was updated, so no quote compensation
    verify(quoteApiClient, never()).updateQuote(any(), any());
    // createProductOrder was attempted
    verify(productOrderApiClient, times(1)).createProductOrder(any());
  }

  @Test
  void save_success_no_compensation_triggered() {
    // Successful save should not trigger any compensations
    ContractNegotiation negotiation =
        getNegotiation(
            ContractNegotiation.Type.CONSUMER,
            ContractNegotiationStates.INITIAL,
            false,
            TEST_COUNTER_PARTY_ID,
            List.of(getOffer(TEST_OFFER_ID)));

    ExtendableQuoteVO createdQuote = new ExtendableQuoteVO();
    createdQuote.setId("created-quote-id");
    createdQuote.setState(QuoteStateTypeVO.IN_PROGRESS);

    when(quoteApiClient.findByNegotiationId(any())).thenReturn(List.of());
    when(quoteApiClient.createQuote(any())).thenReturn(createdQuote);
    when(participantResolver.getTmfId(any())).thenReturn("tmf-id");
    when(tmfEdcMapper.fromConsumerContractOffer(any(), any()))
        .thenReturn(new ExtendableQuoteItemVO());

    // save should succeed without exception
    tmfBackedContractNegotiationStore.save(negotiation);

    // createQuote was called once, no updateQuote compensation calls
    verify(quoteApiClient, times(1)).createQuote(any());
    verify(quoteApiClient, never()).updateQuote(any(), any());
  }
}
