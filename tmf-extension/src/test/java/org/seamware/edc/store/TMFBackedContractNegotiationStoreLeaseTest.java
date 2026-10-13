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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.eclipse.edc.spi.result.StoreFailure;
import org.eclipse.edc.spi.result.StoreResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.seamware.edc.domain.ContractNegotiationState;
import org.seamware.edc.domain.ExtendableQuoteVO;
import org.seamware.tmforum.quote.model.QuoteStateTypeVO;

/**
 * Tests specifically for lease behavior in the store. Focuses on lease acquisition, release, and
 * error handling around the lease lifecycle.
 */
public class TMFBackedContractNegotiationStoreLeaseTest
    extends TMFBackedContractNegotiationStoreTest {

  // --- save() lease lifecycle on exception ---

  @ParameterizedTest(name = "acquireLease fails with: {0}")
  @ValueSource(
      strings = {
        "Negotiation negotiation-id is already leased by other-instance until 1",
        "Lost lease race on negotiation-id: expected a, got b",
        "No quote found for negotiation negotiation-id"
      })
  public void testSave_reports_already_leased_when_acquireLease_fails(String failureMessage) {
    String negotiationId = "negotiation-id";
    doThrow(new IllegalStateException(failureMessage))
        .when(leaseHolder)
        .acquireLease(eq(negotiationId), any());

    ContractNegotiation negotiation =
        getValidNegotiationWithIdInState(negotiationId, ContractNegotiationStates.INITIAL);

    StoreResult<Void> result = tmfBackedContractNegotiationStore.save(negotiation);

    assertTrue(result.failed(), "Save should fail when the lease cannot be acquired.");
    assertEquals(StoreFailure.Reason.ALREADY_LEASED, result.reason());
    verify(leaseHolder, never()).freeLease(eq(negotiationId), any());
  }

  @Test
  public void testSave_frees_lease_when_handleInitialStates_throws() {
    String negotiationId = "negotiation-id";
    // Return empty list — handleInitialStates will look for active quote then try to create
    when(quoteApiClient.findByNegotiationId(eq(negotiationId)))
        .thenThrow(new RuntimeException("API error during initial state"));

    ContractNegotiation negotiation =
        getValidNegotiationWithIdInState(negotiationId, ContractNegotiationStates.INITIAL);

    assertThrows(
        EdcPersistenceException.class, () -> tmfBackedContractNegotiationStore.save(negotiation));

    verify(leaseHolder, times(1)).acquireLease(eq(negotiationId), any());
    verify(leaseHolder, times(1)).freeLease(eq(negotiationId), any());
  }

  @Test
  public void testSave_frees_lease_when_handleRequestedState_throws() {
    String negotiationId = "negotiation-id";
    when(quoteApiClient.findByNegotiationId(eq(negotiationId)))
        .thenThrow(new RuntimeException("API error during requested state"));

    ContractNegotiation negotiation =
        getValidNegotiationWithIdInState(negotiationId, ContractNegotiationStates.REQUESTED);

    assertThrows(
        EdcPersistenceException.class, () -> tmfBackedContractNegotiationStore.save(negotiation));

    verify(leaseHolder, times(1)).acquireLease(eq(negotiationId), any());
    verify(leaseHolder, times(1)).freeLease(eq(negotiationId), any());
  }

  @Test
  public void testSave_frees_lease_when_handleAgreeStates_throws() {
    String negotiationId = "negotiation-id";
    when(quoteApiClient.findByNegotiationId(eq(negotiationId)))
        .thenThrow(new RuntimeException("API error during agree state"));

    ContractNegotiation negotiation =
        getValidNegotiationWithIdInState(negotiationId, ContractNegotiationStates.AGREEING);

    assertThrows(
        EdcPersistenceException.class, () -> tmfBackedContractNegotiationStore.save(negotiation));

    verify(leaseHolder, times(1)).acquireLease(eq(negotiationId), any());
    verify(leaseHolder, times(1)).freeLease(eq(negotiationId), any());
  }

  @Test
  public void testSave_frees_lease_when_handleFinalStates_throws() {
    String negotiationId = "negotiation-id";
    when(quoteApiClient.findByNegotiationId(eq(negotiationId)))
        .thenThrow(new RuntimeException("API error during final state"));

    ContractNegotiation negotiation =
        getValidNegotiationWithIdInState(negotiationId, ContractNegotiationStates.FINALIZING);

    assertThrows(
        EdcPersistenceException.class, () -> tmfBackedContractNegotiationStore.save(negotiation));

    verify(leaseHolder, times(1)).acquireLease(eq(negotiationId), any());
    verify(leaseHolder, times(1)).freeLease(eq(negotiationId), any());
  }

  @Test
  public void testSave_frees_lease_when_handleVerificationStates_throws() {
    String negotiationId = "negotiation-id";
    when(quoteApiClient.findByNegotiationId(eq(negotiationId)))
        .thenThrow(new RuntimeException("API error during verification state"));

    ContractNegotiation negotiation =
        getValidNegotiationWithIdInState(negotiationId, ContractNegotiationStates.VERIFYING);

    assertThrows(
        EdcPersistenceException.class, () -> tmfBackedContractNegotiationStore.save(negotiation));

    verify(leaseHolder, times(1)).acquireLease(eq(negotiationId), any());
    verify(leaseHolder, times(1)).freeLease(eq(negotiationId), any());
  }

  @Test
  public void testSave_frees_lease_when_handleTerminationStates_throws() {
    String negotiationId = "negotiation-id";
    when(quoteApiClient.findByNegotiationId(eq(negotiationId)))
        .thenThrow(new RuntimeException("API error during termination state"));

    ContractNegotiation negotiation =
        getValidNegotiationWithIdInState(negotiationId, ContractNegotiationStates.TERMINATING);

    assertThrows(
        EdcPersistenceException.class, () -> tmfBackedContractNegotiationStore.save(negotiation));

    verify(leaseHolder, times(1)).acquireLease(eq(negotiationId), any());
    verify(leaseHolder, times(1)).freeLease(eq(negotiationId), any());
  }

  // --- save() lease re-acquisition ---

  @Test
  public void testSave_reacquires_lease_that_was_already_held_by_same_instance() {
    String negotiationId = "negotiation-id";

    // acquireLease succeeds (simulates same lockId re-acquiring)
    // The quote API returns empty for findByNegotiationId, which will cause create
    when(quoteApiClient.findByNegotiationId(eq(negotiationId))).thenReturn(List.of());
    when(quoteApiClient.createQuote(any()))
        .thenReturn(
            getQuote(
                "new-quote-id",
                negotiationId,
                TEST_CONTROL_PLANE_ID,
                QuoteStateTypeVO.IN_PROGRESS));
    when(quoteApiClient.updateQuote(any(), any()))
        .thenReturn(
            getQuote(
                "new-quote-id",
                negotiationId,
                TEST_CONTROL_PLANE_ID,
                QuoteStateTypeVO.IN_PROGRESS));

    ContractNegotiation negotiation =
        getValidNegotiationWithIdInState(negotiationId, ContractNegotiationStates.INITIAL);

    // Should not throw — re-acquisition by same lockId is allowed
    assertDoesNotThrow(() -> tmfBackedContractNegotiationStore.save(negotiation));

    verify(leaseHolder, times(1)).acquireLease(eq(negotiationId), any());
    verify(leaseHolder, times(1)).freeLease(eq(negotiationId), any());
  }

  // --- breakLease() ---

  @Test
  public void testBreakLease_frees_lease_held_by_this_instance() {
    String negotiationId = "negotiation-id";
    when(leaseHolder.isLeasedBy(eq(negotiationId), any())).thenReturn(true);

    ContractNegotiation negotiation =
        getValidNegotiationWithIdInState(negotiationId, ContractNegotiationStates.REQUESTED);

    assertTrue(tmfBackedContractNegotiationStore.breakLease(negotiation).succeeded());

    verify(leaseHolder, times(1)).freeLease(eq(negotiationId), any());
  }

  @Test
  public void testBreakLease_keeps_lease_held_by_another_instance() {
    String negotiationId = "negotiation-id";
    when(leaseHolder.isLeasedBy(eq(negotiationId), any())).thenReturn(false);
    when(leaseHolder.isLeased(eq(negotiationId))).thenReturn(true);

    ContractNegotiation negotiation =
        getValidNegotiationWithIdInState(negotiationId, ContractNegotiationStates.REQUESTED);

    StoreResult<Void> result = tmfBackedContractNegotiationStore.breakLease(negotiation);

    assertTrue(result.failed(), "A foreign lease must not be broken.");
    assertEquals(StoreFailure.Reason.ALREADY_LEASED, result.reason());
    verify(leaseHolder, never()).freeLease(eq(negotiationId), any());
  }

  @Test
  public void testBreakLease_is_a_noop_when_nothing_is_leased() {
    String negotiationId = "negotiation-id";
    when(leaseHolder.isLeasedBy(eq(negotiationId), any())).thenReturn(false);
    when(leaseHolder.isLeased(eq(negotiationId))).thenReturn(false);

    ContractNegotiation negotiation =
        getValidNegotiationWithIdInState(negotiationId, ContractNegotiationStates.REQUESTED);

    assertTrue(tmfBackedContractNegotiationStore.breakLease(negotiation).succeeded());

    verify(leaseHolder, never()).freeLease(eq(negotiationId), any());
  }

  @Test
  public void testBreakLease_reports_general_error_when_the_lease_holder_fails() {
    String negotiationId = "negotiation-id";
    when(leaseHolder.isLeasedBy(eq(negotiationId), any()))
        .thenThrow(new RuntimeException("TMForum unreachable"));

    ContractNegotiation negotiation =
        getValidNegotiationWithIdInState(negotiationId, ContractNegotiationStates.REQUESTED);

    StoreResult<Void> result = tmfBackedContractNegotiationStore.breakLease(negotiation);

    assertTrue(result.failed());
    assertEquals(StoreFailure.Reason.GENERAL_ERROR, result.reason());
  }

  // --- nextNotLeased delegates lease check to acquireLease ---

  @Test
  public void testNextNotLeased_acquires_lease_regardless_of_quote_isLeased_field() {
    // The store no longer calls isLeased() — it relies on acquireLease() to reject
    // already-leased negotiations. This avoids N+1 API calls with TMFBackedLeaseHolder.
    when(lockManager.writeLock(any()))
        .thenAnswer(
            invocationOnMock -> {
              Supplier<List<ContractNegotiation>> work = invocationOnMock.getArgument(0);
              return work.get();
            });

    List<ExtendableQuoteVO> quotes = new ArrayList<>();
    ExtendableQuoteVO quoteWithLeasedFlag = new ExtendableQuoteVO();
    quoteWithLeasedFlag.setExternalId("neg-leased-in-tmf");
    quoteWithLeasedFlag.setContractNegotiationState(
        new ContractNegotiationState()
            .setControlplane(TEST_CONTROL_PLANE_ID)
            .setLeased(true)); // leased in TMForum data
    quotes.add(quoteWithLeasedFlag);

    stubQuotes(quotes);

    ContractNegotiation negotiation = getValidNegotiationWithId("neg-leased-in-tmf");

    when(tmfEdcMapper.toContractNegotiation(any(), any(), any(), any())).thenReturn(negotiation);

    List<ContractNegotiation> result = tmfBackedContractNegotiationStore.nextNotLeased(10);

    // acquireLease is called directly — no isLeased pre-check
    assertEquals(1, result.size());
    verify(leaseHolder).acquireLease(eq("neg-leased-in-tmf"), any());
    verify(leaseHolder, never()).isLeased(any());
  }

  @Test
  public void testNextNotLeased_filters_out_when_acquireLease_throws() {
    when(lockManager.writeLock(any()))
        .thenAnswer(
            invocationOnMock -> {
              Supplier<List<ContractNegotiation>> work = invocationOnMock.getArgument(0);
              return work.get();
            });

    List<ExtendableQuoteVO> quotes = new ArrayList<>();
    ExtendableQuoteVO quote = new ExtendableQuoteVO();
    quote.setExternalId("neg-1");
    quote.setContractNegotiationState(
        new ContractNegotiationState().setControlplane(TEST_CONTROL_PLANE_ID).setLeased(false));
    quotes.add(quote);

    stubQuotes(quotes);

    ContractNegotiation negotiation = getValidNegotiationWithId("neg-1");

    // acquireLease throws — simulates the lease being held by another instance
    doThrow(new IllegalStateException("Already leased"))
        .when(leaseHolder)
        .acquireLease(eq("neg-1"), any());

    when(tmfEdcMapper.toContractNegotiation(any(), any(), any(), any())).thenReturn(negotiation);

    List<ContractNegotiation> result = tmfBackedContractNegotiationStore.nextNotLeased(10);

    assertEquals(
        0, result.size(), "Negotiations where acquireLease throws should be filtered out.");
    verify(leaseHolder, never()).isLeased(any());
  }

  // --- nextNotLeased partial failure ---

  @Test
  public void testNextNotLeased_does_not_free_leases_on_partial_acquire_failure() {
    // Documents current behavior: if acquireLease fails for some negotiations,
    // the already-acquired leases for other negotiations are NOT freed.
    // They will expire after the 60s TTL.
    when(lockManager.writeLock(any()))
        .thenAnswer(
            invocationOnMock -> {
              Supplier<List<ContractNegotiation>> work = invocationOnMock.getArgument(0);
              return work.get();
            });

    List<ExtendableQuoteVO> allQuotes = getNegotitations(3);
    stubQuotes(allQuotes);

    ContractNegotiation neg0 = getValidNegotiationWithId("neg-0");
    ContractNegotiation neg1 = getValidNegotiationWithId("neg-1");
    ContractNegotiation neg2 = getValidNegotiationWithId("neg-2");

    // neg-0 acquires successfully, neg-1 fails, neg-2 acquires successfully
    doNothing().when(leaseHolder).acquireLease(eq("neg-0"), any());
    doThrow(new IllegalStateException("race condition"))
        .when(leaseHolder)
        .acquireLease(eq("neg-1"), any());
    doNothing().when(leaseHolder).acquireLease(eq("neg-2"), any());

    when(tmfEdcMapper.toContractNegotiation(any(), any(), any(), any()))
        .thenAnswer(
            invocationOnMock -> {
              List<ExtendableQuoteVO> quotes = invocationOnMock.getArgument(0);
              String id = quotes.getFirst().getExternalId();
              return switch (id) {
                case "neg-0" -> neg0;
                case "neg-1" -> neg1;
                case "neg-2" -> neg2;
                default -> null;
              };
            });

    List<ContractNegotiation> result = tmfBackedContractNegotiationStore.nextNotLeased(10);

    assertEquals(2, result.size(), "Only successfully leased negotiations should be returned.");
    assertTrue(result.contains(neg0));
    assertTrue(result.contains(neg2));

    // Document: freeLease is NOT called — leases will only expire via TTL
    verify(leaseHolder, never()).freeLease(any(), any());
  }
}
