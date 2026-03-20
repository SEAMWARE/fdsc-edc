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

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.eclipse.edc.spi.monitor.Monitor;
import org.seamware.edc.domain.ContractNegotiationState;
import org.seamware.edc.domain.ExtendableQuoteUpdateVO;
import org.seamware.edc.domain.ExtendableQuoteVO;
import org.seamware.edc.tmf.QuoteApiClient;
import org.seamware.tmforum.quote.model.QuoteStateTypeVO;

/**
 * Distributed lease manager that persists lease state through the TMForum Quote API. Each lease is
 * stored as metadata on the quote's {@link ContractNegotiationState}, enabling multiple EDC
 * controlplane instances to coordinate access to the same contract negotiation. Uses
 * read-after-write verification to detect race conditions during lease acquisition.
 *
 * <p>Queries the TMForum API with server-side state filtering, requesting only quotes in active
 * (non-terminal) states to reduce data transfer and avoid processing cancelled or rejected quotes.
 */
public class TMFBackedLeaseHolder implements LeaseHolder {

  private static final Duration DEFAULT_LEASE_TIME = Duration.ofSeconds(60);
  private static final Set<QuoteStateTypeVO> ACTIVE_QUOTE_STATES =
      Set.of(
          QuoteStateTypeVO.PENDING,
          QuoteStateTypeVO.IN_PROGRESS,
          QuoteStateTypeVO.APPROVED,
          QuoteStateTypeVO.ACCEPTED);

  private final QuoteApiClient quoteApi;
  private final String controlplane;
  private final Clock clock;
  private final Monitor monitor;

  public TMFBackedLeaseHolder(
      QuoteApiClient quoteApi, String controlplane, Clock clock, Monitor monitor) {
    this.quoteApi = quoteApi;
    this.controlplane = controlplane;
    this.clock = clock;
    this.monitor = monitor;
  }

  @Override
  public void acquireLease(String negotiationId, String lockId, Duration leaseTime) {
    List<ExtendableQuoteVO> quotes =
        quoteApi.findByNegotiationIdAndStates(negotiationId, ACTIVE_QUOTE_STATES);

    // No quotes exist yet — this is a new negotiation with nothing to lease-protect
    if (quotes.isEmpty()) {
      monitor.info(
          String.format(
              "No quotes found for negotiation %s, skipping lease acquisition.", negotiationId));
      return;
    }

    List<ExtendableQuoteVO> lockableQuotes =
        quotes.stream()
            .filter(q -> q.getContractNegotiationState() != null)
            .filter(q -> q.getContractNegotiationState().getControlplane().equals(controlplane))
            .toList();

    if (lockableQuotes.isEmpty()) {
      throw new IllegalStateException("No quote found for negotiation " + negotiationId);
    }

    // Check that none of the lockable quotes are already leased by someone else
    for (ExtendableQuoteVO quote : lockableQuotes) {
      ContractNegotiationState currentState = quote.getContractNegotiationState();
      if (currentState.isLeased()
          && !lockId.equals(currentState.getLeasedBy())
          && isLeaseNotExpired(currentState)) {
        throw new IllegalStateException(
            String.format(
                "Negotiation %s is already leased by %s until %d",
                negotiationId, currentState.getLeasedBy(), currentState.getLeaseExpiry()));
      }
    }

    long expiry = clock.millis() + leaseTime.toMillis();

    // Acquire lease on all active quotes for this controlplane
    for (ExtendableQuoteVO quote : lockableQuotes) {
      ContractNegotiationState updatedState =
          copyStateWithLease(quote.getContractNegotiationState(), true, lockId, expiry);

      ExtendableQuoteUpdateVO updateVO = new ExtendableQuoteUpdateVO();
      updateVO.setContractNegotiationState(updatedState);
      ExtendableQuoteVO written = quoteApi.updateQuote(quote.getId(), updateVO);

      // Read-after-write verification to detect race conditions
      ContractNegotiationState writtenState = written.getContractNegotiationState();
      if (writtenState != null && !lockId.equals(writtenState.getLeasedBy())) {
        throw new IllegalStateException(
            String.format(
                "Lost lease race on %s: expected %s, got %s",
                negotiationId, lockId, writtenState.getLeasedBy()));
      }
    }

    monitor.info(
        String.format("Acquired lease on %s by %s until %d", negotiationId, lockId, expiry));
  }

  @Override
  public void acquireLease(String negotiationId, String lockId) {
    acquireLease(negotiationId, lockId, DEFAULT_LEASE_TIME);
  }

  @Override
  public boolean isLeased(String negotiationId) {
    List<ExtendableQuoteVO> quotes =
        quoteApi.findByNegotiationIdAndStates(negotiationId, ACTIVE_QUOTE_STATES);
    return quotes.stream()
        .filter(q -> q.getContractNegotiationState() != null)
        .filter(q -> q.getContractNegotiationState().getControlplane().equals(controlplane))
        .anyMatch(
            q -> {
              ContractNegotiationState state = q.getContractNegotiationState();
              return state.isLeased() && isLeaseNotExpired(state);
            });
  }

  @Override
  public boolean isLeasedBy(String negotiationId, String lockId) {
    List<ExtendableQuoteVO> quotes =
        quoteApi.findByNegotiationIdAndStates(negotiationId, ACTIVE_QUOTE_STATES);
    return quotes.stream()
        .filter(q -> q.getContractNegotiationState() != null)
        .filter(q -> q.getContractNegotiationState().getControlplane().equals(controlplane))
        .anyMatch(
            q -> {
              ContractNegotiationState state = q.getContractNegotiationState();
              return state.isLeased()
                  && lockId.equals(state.getLeasedBy())
                  && isLeaseNotExpired(state);
            });
  }

  @Override
  public void freeLease(String negotiationId, String reason) {
    List<ExtendableQuoteVO> quotes =
        quoteApi.findByNegotiationIdAndStates(negotiationId, ACTIVE_QUOTE_STATES);
    quotes.stream()
        .filter(q -> q.getContractNegotiationState() != null)
        .filter(q -> q.getContractNegotiationState().getControlplane().equals(controlplane))
        .filter(q -> q.getContractNegotiationState().isLeased())
        .forEach(
            q -> {
              ContractNegotiationState updatedState =
                  copyStateWithLease(q.getContractNegotiationState(), false, null, 0);

              ExtendableQuoteUpdateVO updateVO = new ExtendableQuoteUpdateVO();
              updateVO.setContractNegotiationState(updatedState);
              quoteApi.updateQuote(q.getId(), updateVO);
            });

    monitor.info(String.format("Freed lease on %s because: %s", negotiationId, reason));
  }

  private boolean isLeaseNotExpired(ContractNegotiationState state) {
    return state.getLeaseExpiry() > clock.millis();
  }

  private ContractNegotiationState copyStateWithLease(
      ContractNegotiationState source, boolean leased, String leasedBy, long leaseExpiry) {
    return new ContractNegotiationState()
        .setControlplane(source.getControlplane())
        .setPending(source.isPending())
        .setState(source.getState())
        .setCorrelationId(source.getCorrelationId())
        .setCounterPartyAddress(source.getCounterPartyAddress())
        .setLeased(leased)
        .setLeasedBy(leasedBy)
        .setLeaseExpiry(leaseExpiry);
  }
}
