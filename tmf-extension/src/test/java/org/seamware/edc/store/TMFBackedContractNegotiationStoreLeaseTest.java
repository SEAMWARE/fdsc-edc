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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.eclipse.edc.spi.result.StoreFailure;
import org.eclipse.edc.spi.result.StoreResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

  /**
   * A save that cannot take the lease writes nothing, and the protocol service discards the
   * StoreResult of most transitions - so contention must throw like any other lease failure, giving
   * the counterparty a 5xx and a resend rather than a silent 200.
   */
  private static Stream<Arguments> acquireLeaseFailures() {
    return Stream.of(
        Arguments.of(
            new LeaseContentionException(
                "Negotiation negotiation-id is already leased by other-instance until 1")),
        Arguments.of(
            new LeaseContentionException("Lost lease race on negotiation-id: expected a, got b")),
        Arguments.of(new IllegalStateException("No quote found for negotiation negotiation-id")),
        Arguments.of(new RuntimeException("TMForum unreachable")),
        Arguments.of(new RuntimeException()));
  }

  @ParameterizedTest(name = "acquireLease throws {0}")
  @MethodSource("acquireLeaseFailures")
  public void testSave_throws_when_the_lease_cannot_be_acquired(RuntimeException failure) {
    String negotiationId = "negotiation-id";
    doThrow(failure).when(leaseHolder).acquireLease(eq(negotiationId), any());

    ContractNegotiation negotiation =
        getValidNegotiationWithIdInState(negotiationId, ContractNegotiationStates.INITIAL);

    EdcPersistenceException exception =
        assertThrows(
            EdcPersistenceException.class,
            () -> tmfBackedContractNegotiationStore.save(negotiation));

    assertSame(failure, exception.getCause(), "The original lease failure must not be swallowed.");
    // The lease belongs to whoever holds it - a failed acquisition must not release it.
    verify(leaseHolder, never()).releaseLease(eq(negotiationId), any(), any());
  }

  @Test
  public void testSave_reports_a_lease_taken_over_during_the_write_as_already_leased() {
    String negotiationId = "negotiation-id";
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
    when(leaseHolder.releaseLease(eq(negotiationId), any(), any()))
        .thenReturn(LeaseHolder.LeaseRelease.HELD_BY_OTHER);

    ContractNegotiation negotiation =
        getValidNegotiationWithIdInState(negotiationId, ContractNegotiationStates.INITIAL);

    StoreResult<Void> result = tmfBackedContractNegotiationStore.save(negotiation);

    assertTrue(result.failed(), "A lease lost mid-save must be reported, not hidden.");
    assertEquals(StoreFailure.Reason.ALREADY_LEASED, result.reason());
    // Unlike a failed acquisition, the write itself did happen - the result reports the lease.
    verify(quoteApiClient, times(1)).createQuote(any());
  }

  /** Stubs the quote calls a save of an INITIAL negotiation makes, so only the lease is in play. */
  private void stubSuccessfulInitialSave(String negotiationId) {
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
  }

  @Test
  public void testSave_succeeds_when_the_lease_release_fails_after_the_write() {
    String negotiationId = "negotiation-id";
    stubSuccessfulInitialSave(negotiationId);
    // The release is two HTTP calls against TMForum and can fail on its own.
    when(leaseHolder.releaseLease(eq(negotiationId), any(), any()))
        .thenThrow(new RuntimeException("TMForum unreachable"));

    ContractNegotiation negotiation =
        getValidNegotiationWithIdInState(negotiationId, ContractNegotiationStates.INITIAL);

    StoreResult<Void> result = tmfBackedContractNegotiationStore.save(negotiation);

    // The DSP request/offer paths turn a failed result into HTTP 400, which the counterparty takes
    // as fatal - the wrong answer for a negotiation that was persisted.
    assertTrue(result.succeeded(), "The write landed, only handing the lease back failed.");
    // The exception must not escape the finally block and replace the save outcome.
    verify(quoteApiClient, times(1)).createQuote(any());
  }

  @Test
  public void testSave_deregisters_the_negotiation_when_the_lease_release_fails() {
    String negotiationId = "negotiation-id";
    stubSuccessfulInitialSave(negotiationId);
    when(leaseHolder.releaseLease(eq(negotiationId), any(), any()))
        .thenThrow(new RuntimeException("TMForum unreachable"));

    tmfBackedContractNegotiationStore.save(
        getValidNegotiationWithIdInState(negotiationId, ContractNegotiationStates.INITIAL));

    // An id left behind in the active set is reported as leased for the life of the JVM, which
    // strands the negotiation until restart.
    when(lockManager.writeLock(any()))
        .thenAnswer(
            invocationOnMock -> {
              Supplier<StoreResult<ContractNegotiation>> work = invocationOnMock.getArgument(0);
              return work.get();
            });
    List<ExtendableQuoteVO> quotes = getQuotes(negotiationId, TEST_CONTROL_PLANE_ID, 1);
    when(quoteApiClient.findByNegotiationId(eq(negotiationId))).thenReturn(quotes);
    when(tmfEdcMapper.toContractNegotiation(eq(quotes), any(), any(), any()))
        .thenReturn(getValidNegotiation());

    StoreResult<ContractNegotiation> afterwards =
        tmfBackedContractNegotiationStore.findByIdAndLease(negotiationId);

    assertTrue(afterwards.succeeded(), "The negotiation must be leasable again after the save.");
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
    verify(leaseHolder, times(1)).releaseLease(eq(negotiationId), any(), any());
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
    verify(leaseHolder, times(1)).releaseLease(eq(negotiationId), any(), any());
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
    verify(leaseHolder, times(1)).releaseLease(eq(negotiationId), any(), any());
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
    verify(leaseHolder, times(1)).releaseLease(eq(negotiationId), any(), any());
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
    verify(leaseHolder, times(1)).releaseLease(eq(negotiationId), any(), any());
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
    verify(leaseHolder, times(1)).releaseLease(eq(negotiationId), any(), any());
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
    verify(leaseHolder, times(1)).releaseLease(eq(negotiationId), any(), any());
  }

  // --- breakLease() ---

  @Test
  public void testBreakLease_frees_lease_held_by_this_instance() {
    String negotiationId = "negotiation-id";
    when(leaseHolder.releaseLease(eq(negotiationId), any(), any()))
        .thenReturn(LeaseHolder.LeaseRelease.RELEASED);

    ContractNegotiation negotiation =
        getValidNegotiationWithIdInState(negotiationId, ContractNegotiationStates.REQUESTED);

    assertTrue(tmfBackedContractNegotiationStore.breakLease(negotiation).succeeded());

    verify(leaseHolder, times(1)).releaseLease(eq(negotiationId), any(), any());
  }

  @Test
  public void testBreakLease_keeps_lease_held_by_another_instance() {
    String negotiationId = "negotiation-id";
    when(leaseHolder.releaseLease(eq(negotiationId), any(), any()))
        .thenReturn(LeaseHolder.LeaseRelease.HELD_BY_OTHER);

    ContractNegotiation negotiation =
        getValidNegotiationWithIdInState(negotiationId, ContractNegotiationStates.REQUESTED);

    StoreResult<Void> result = tmfBackedContractNegotiationStore.breakLease(negotiation);

    assertTrue(result.failed(), "A foreign lease must not be broken.");
    assertEquals(StoreFailure.Reason.ALREADY_LEASED, result.reason());
  }

  @Test
  public void testBreakLease_is_a_noop_when_nothing_is_leased() {
    String negotiationId = "negotiation-id";
    when(leaseHolder.releaseLease(eq(negotiationId), any(), any()))
        .thenReturn(LeaseHolder.LeaseRelease.NOT_LEASED);

    ContractNegotiation negotiation =
        getValidNegotiationWithIdInState(negotiationId, ContractNegotiationStates.REQUESTED);

    assertTrue(tmfBackedContractNegotiationStore.breakLease(negotiation).succeeded());
  }

  @Test
  public void testBreakLease_checks_and_frees_in_a_single_lease_holder_call() {
    String negotiationId = "negotiation-id";
    when(leaseHolder.releaseLease(eq(negotiationId), any(), any()))
        .thenReturn(LeaseHolder.LeaseRelease.RELEASED);

    ContractNegotiation negotiation =
        getValidNegotiationWithIdInState(negotiationId, ContractNegotiationStates.REQUESTED);

    tmfBackedContractNegotiationStore.breakLease(negotiation);

    // breakLease runs on essentially every state machine tick, so it must not fan out into
    // separate holder lookups - that is both three round trips and a TOCTOU window.
    verify(leaseHolder, never()).isLeasedBy(any(), any());
    verify(leaseHolder, never()).isLeased(any());
    verify(leaseHolder, times(1)).releaseLease(eq(negotiationId), any(), any());
  }

  @Test
  public void testBreakLease_reports_general_error_when_the_lease_holder_fails() {
    String negotiationId = "negotiation-id";
    when(leaseHolder.releaseLease(eq(negotiationId), any(), any()))
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

    // Document: releaseLease is NOT called — leases will only expire via TTL
    verify(leaseHolder, never()).releaseLease(any(), any(), any());
  }

  // --- findByIdAndLease() while this controlplane still processes the negotiation ---

  @Test
  public void testFindByIdAndLease_waits_until_the_own_processing_has_finished() throws Exception {
    String negotiationId = "negotiation-id";
    TMFBackedContractNegotiationStore store = getStoreWithOwnLeaseWait(Duration.ofSeconds(10));
    ContractNegotiation negotiation = stubLeasableNegotiation(negotiationId);
    // lease for the processing, failing save, lease for the counter-party's message
    doNothing()
        .doThrow(new IllegalStateException("Save failed."))
        .doNothing()
        .when(leaseHolder)
        .acquireLease(eq(negotiationId), any());

    assertTrue(
        store.findByIdAndLease(negotiationId).succeeded(),
        "The negotiation should be leased for its processing.");

    CompletableFuture<StoreResult<ContractNegotiation>> counterPartyMessage =
        CompletableFuture.supplyAsync(() -> store.findByIdAndLease(negotiationId));
    Thread.sleep(300);
    assertFalse(
        counterPartyMessage.isDone(),
        "The message should wait while the negotiation is still processed.");

    // the processing ends with the save, which releases the negotiation whatever its outcome
    assertThrows(EdcPersistenceException.class, () -> store.save(negotiation));

    StoreResult<ContractNegotiation> result = counterPartyMessage.get(5, TimeUnit.SECONDS);
    assertTrue(
        result.succeeded(),
        "The message should get the negotiation once the processing has finished.");
    assertEquals(negotiation, result.getContent(), "The correct negotiation should be returned.");
  }

  @Test
  public void testFindByIdAndLease_gives_up_when_the_own_processing_does_not_finish() {
    String negotiationId = "negotiation-id";
    Duration ownLeaseWait = Duration.ofMillis(300);
    TMFBackedContractNegotiationStore store = getStoreWithOwnLeaseWait(ownLeaseWait);
    stubLeasableNegotiation(negotiationId);

    assertTrue(
        store.findByIdAndLease(negotiationId).succeeded(),
        "The negotiation should be leased for its processing.");

    long start = System.nanoTime();
    StoreResult<ContractNegotiation> result = store.findByIdAndLease(negotiationId);
    long waitedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

    assertEquals(
        StoreFailure.Reason.ALREADY_LEASED,
        result.reason(),
        "A negotiation that is not released in time should still be reported as leased.");
    assertTrue(
        waitedMs >= ownLeaseWait.toMillis(),
        "The message should have waited for the release before giving up.");
  }

  private TMFBackedContractNegotiationStore getStoreWithOwnLeaseWait(Duration ownLeaseWait) {
    return new TMFBackedContractNegotiationStore(
        mock(Monitor.class),
        objectMapper,
        quoteApiClient,
        agreementApiClient,
        productOrderApiClient,
        productCatalogApiClient,
        productInventoryApiClient,
        participantResolver,
        tmfEdcMapper,
        TEST_PARTICIPANT_ID,
        TEST_CONTROL_PLANE_ID,
        criterionOperatorRegistry,
        leaseHolder,
        new TMFTransactionContext(mock(Monitor.class)),
        lockManager,
        ownLeaseWait);
  }

  private ContractNegotiation stubLeasableNegotiation(String negotiationId) {
    when(lockManager.writeLock(any()))
        .thenAnswer(
            invocationOnMock -> {
              Supplier<?> work = invocationOnMock.getArgument(0);
              return work.get();
            });
    ContractNegotiation negotiation = getValidNegotiationWithId(negotiationId);
    List<ExtendableQuoteVO> quotes = getQuotes(negotiationId, TEST_CONTROL_PLANE_ID, 1);
    when(quoteApiClient.findByNegotiationId(eq(negotiationId))).thenReturn(quotes);
    when(tmfEdcMapper.toContractNegotiation(eq(quotes), any(), any(), any()))
        .thenReturn(negotiation);
    return negotiation;
  }
}
