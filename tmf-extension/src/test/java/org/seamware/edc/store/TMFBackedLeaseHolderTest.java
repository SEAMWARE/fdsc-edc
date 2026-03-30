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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.seamware.edc.domain.ContractNegotiationState;
import org.seamware.edc.domain.ExtendableQuoteUpdateVO;
import org.seamware.edc.domain.ExtendableQuoteVO;
import org.seamware.edc.tmf.QuoteApiClient;

public class TMFBackedLeaseHolderTest {

  private static final String CONTROLPLANE = "cp-1";
  private static final String OTHER_CONTROLPLANE = "cp-2";
  private static final long NOW = 1000000L;

  private QuoteApiClient quoteApi;
  private Clock clock;
  private Monitor monitor;
  private TMFBackedLeaseHolder leaseHolder;

  @BeforeEach
  void setUp() {
    quoteApi = mock(QuoteApiClient.class);
    clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneId.of("UTC"));
    monitor = mock(Monitor.class);
    leaseHolder = new TMFBackedLeaseHolder(quoteApi, CONTROLPLANE, clock, monitor);
  }

  private ExtendableQuoteVO quoteWith(
      String id, String controlplane, boolean leased, String leasedBy, long leaseExpiry) {
    ExtendableQuoteVO quote = new ExtendableQuoteVO();
    quote.setId(id);
    quote.setContractNegotiationState(
        new ContractNegotiationState()
            .setControlplane(controlplane)
            .setState("REQUESTING")
            .setCorrelationId("corr-1")
            .setCounterPartyAddress("http://peer")
            .setLeased(leased)
            .setLeasedBy(leasedBy)
            .setLeaseExpiry(leaseExpiry));
    return quote;
  }

  private ExtendableQuoteVO unleased(String id) {
    return quoteWith(id, CONTROLPLANE, false, null, 0);
  }

  @Nested
  class AcquireLease {

    @Test
    void succeeds_on_unleased_quote() {
      ExtendableQuoteVO quote = unleased("q-1");
      when(quoteApi.findByNegotiationIdAndStates(eq("neg-1"), anyCollection()))
          .thenReturn(List.of(quote));

      ExtendableQuoteVO updatedQuote = quoteWith("q-1", CONTROLPLANE, true, "lock-1", NOW + 60000);
      when(quoteApi.updateQuote(eq("q-1"), any(ExtendableQuoteUpdateVO.class)))
          .thenReturn(updatedQuote);

      assertDoesNotThrow(() -> leaseHolder.acquireLease("neg-1", "lock-1"));

      verify(quoteApi)
          .updateQuote(
              eq("q-1"),
              argThat(
                  update -> {
                    ContractNegotiationState s = update.getContractNegotiationState();
                    return s.isLeased()
                        && "lock-1".equals(s.getLeasedBy())
                        && s.getLeaseExpiry() == NOW + 60000
                        && "REQUESTING".equals(s.getState())
                        && "corr-1".equals(s.getCorrelationId())
                        && update.getRelatedParty() == null;
                  }));
    }

    @Test
    void succeeds_with_custom_lease_time() {
      ExtendableQuoteVO quote = unleased("q-1");
      when(quoteApi.findByNegotiationIdAndStates(eq("neg-1"), anyCollection()))
          .thenReturn(List.of(quote));

      ExtendableQuoteVO updatedQuote = quoteWith("q-1", CONTROLPLANE, true, "lock-1", NOW + 120000);
      when(quoteApi.updateQuote(eq("q-1"), any())).thenReturn(updatedQuote);

      leaseHolder.acquireLease("neg-1", "lock-1", Duration.ofSeconds(120));

      verify(quoteApi)
          .updateQuote(
              eq("q-1"),
              argThat(
                  update -> {
                    ContractNegotiationState s = update.getContractNegotiationState();
                    return s.getLeaseExpiry() == NOW + 120000;
                  }));
    }

    @Test
    void skips_lease_when_no_quotes_exist() {
      when(quoteApi.findByNegotiationIdAndStates(eq("neg-1"), anyCollection()))
          .thenReturn(List.of());

      assertDoesNotThrow(() -> leaseHolder.acquireLease("neg-1", "lock-1"));

      verify(quoteApi, never()).updateQuote(any(), any());
    }

    @Test
    void throws_when_quotes_exist_but_none_match_controlplane() {
      ExtendableQuoteVO otherCpQuote = quoteWith("q-other", OTHER_CONTROLPLANE, false, null, 0);
      when(quoteApi.findByNegotiationIdAndStates(eq("neg-1"), anyCollection()))
          .thenReturn(List.of(otherCpQuote));

      IllegalStateException ex =
          assertThrows(
              IllegalStateException.class, () -> leaseHolder.acquireLease("neg-1", "lock-1"));

      assertTrue(ex.getMessage().contains("No quote found for negotiation neg-1"));
      verify(quoteApi, never()).updateQuote(any(), any());
    }

    @Test
    void throws_when_leased_by_different_lock_and_not_expired() {
      ExtendableQuoteVO quote = quoteWith("q-1", CONTROLPLANE, true, "other-lock", NOW + 30000);
      when(quoteApi.findByNegotiationIdAndStates(eq("neg-1"), anyCollection()))
          .thenReturn(List.of(quote));

      IllegalStateException ex =
          assertThrows(
              IllegalStateException.class, () -> leaseHolder.acquireLease("neg-1", "lock-1"));

      assertTrue(ex.getMessage().contains("already leased by other-lock"));
      verify(quoteApi, never()).updateQuote(any(), any());
    }

    @Test
    void succeeds_when_leased_by_same_lock() {
      ExtendableQuoteVO quote = quoteWith("q-1", CONTROLPLANE, true, "lock-1", NOW + 30000);
      when(quoteApi.findByNegotiationIdAndStates(eq("neg-1"), anyCollection()))
          .thenReturn(List.of(quote));

      ExtendableQuoteVO updatedQuote = quoteWith("q-1", CONTROLPLANE, true, "lock-1", NOW + 60000);
      when(quoteApi.updateQuote(eq("q-1"), any())).thenReturn(updatedQuote);

      assertDoesNotThrow(() -> leaseHolder.acquireLease("neg-1", "lock-1"));
      verify(quoteApi).updateQuote(eq("q-1"), any());
    }

    @Test
    void succeeds_when_existing_lease_has_expired() {
      ExtendableQuoteVO quote = quoteWith("q-1", CONTROLPLANE, true, "other-lock", NOW - 1);
      when(quoteApi.findByNegotiationIdAndStates(eq("neg-1"), anyCollection()))
          .thenReturn(List.of(quote));

      ExtendableQuoteVO updatedQuote = quoteWith("q-1", CONTROLPLANE, true, "lock-1", NOW + 60000);
      when(quoteApi.updateQuote(eq("q-1"), any())).thenReturn(updatedQuote);

      assertDoesNotThrow(() -> leaseHolder.acquireLease("neg-1", "lock-1"));
      verify(quoteApi).updateQuote(eq("q-1"), any());
    }

    @Test
    void throws_on_read_after_write_race_detection() {
      ExtendableQuoteVO quote = unleased("q-1");
      when(quoteApi.findByNegotiationIdAndStates(eq("neg-1"), anyCollection()))
          .thenReturn(List.of(quote));

      // Simulate race: we wrote "lock-1" but response says "other-lock" won
      ExtendableQuoteVO raceWinner =
          quoteWith("q-1", CONTROLPLANE, true, "other-lock", NOW + 60000);
      when(quoteApi.updateQuote(eq("q-1"), any())).thenReturn(raceWinner);

      IllegalStateException ex =
          assertThrows(
              IllegalStateException.class, () -> leaseHolder.acquireLease("neg-1", "lock-1"));

      assertTrue(ex.getMessage().contains("Lost lease race"));
      assertTrue(ex.getMessage().contains("expected lock-1"));
      assertTrue(ex.getMessage().contains("got other-lock"));
    }

    @Test
    void ignores_quotes_from_other_controlplane() {
      ExtendableQuoteVO otherCpQuote = quoteWith("q-other", OTHER_CONTROLPLANE, false, null, 0);
      when(quoteApi.findByNegotiationIdAndStates(eq("neg-1"), anyCollection()))
          .thenReturn(List.of(otherCpQuote));

      assertThrows(IllegalStateException.class, () -> leaseHolder.acquireLease("neg-1", "lock-1"));

      verify(quoteApi, never()).updateQuote(any(), any());
    }

    @Test
    void ignores_quotes_without_contract_negotiation_state() {
      ExtendableQuoteVO nullStateQuote = new ExtendableQuoteVO();
      nullStateQuote.setId("q-null");
      when(quoteApi.findByNegotiationIdAndStates(eq("neg-1"), anyCollection()))
          .thenReturn(List.of(nullStateQuote));

      assertThrows(IllegalStateException.class, () -> leaseHolder.acquireLease("neg-1", "lock-1"));

      verify(quoteApi, never()).updateQuote(any(), any());
    }

    @Test
    void acquires_lease_on_all_matching_quotes() {
      ExtendableQuoteVO first = unleased("q-first");
      ExtendableQuoteVO second = unleased("q-second");
      when(quoteApi.findByNegotiationIdAndStates(eq("neg-1"), anyCollection()))
          .thenReturn(List.of(first, second));

      ExtendableQuoteVO updatedFirst =
          quoteWith("q-first", CONTROLPLANE, true, "lock-1", NOW + 60000);
      ExtendableQuoteVO updatedSecond =
          quoteWith("q-second", CONTROLPLANE, true, "lock-1", NOW + 60000);
      when(quoteApi.updateQuote(eq("q-first"), any())).thenReturn(updatedFirst);
      when(quoteApi.updateQuote(eq("q-second"), any())).thenReturn(updatedSecond);

      leaseHolder.acquireLease("neg-1", "lock-1");

      verify(quoteApi).updateQuote(eq("q-first"), any());
      verify(quoteApi).updateQuote(eq("q-second"), any());
    }

    @Test
    void preserves_state_fields_in_update() {
      ExtendableQuoteVO quote = unleased("q-1");
      quote.getContractNegotiationState().setPending(true).setCounterPartyAddress("http://counter");
      when(quoteApi.findByNegotiationIdAndStates(eq("neg-1"), anyCollection()))
          .thenReturn(List.of(quote));

      ExtendableQuoteVO updatedQuote = quoteWith("q-1", CONTROLPLANE, true, "lock-1", NOW + 60000);
      when(quoteApi.updateQuote(eq("q-1"), any())).thenReturn(updatedQuote);

      leaseHolder.acquireLease("neg-1", "lock-1");

      verify(quoteApi)
          .updateQuote(
              eq("q-1"),
              argThat(
                  update -> {
                    ContractNegotiationState s = update.getContractNegotiationState();
                    return s.isPending()
                        && CONTROLPLANE.equals(s.getControlplane())
                        && "http://counter".equals(s.getCounterPartyAddress());
                  }));
    }
  }

  @Nested
  class IsLeased {

    @Test
    void returns_true_when_leased_and_not_expired() {
      ExtendableQuoteVO quote = quoteWith("q-1", CONTROLPLANE, true, "lock-1", NOW + 30000);
      when(quoteApi.findByNegotiationIdAndStates(eq("neg-1"), anyCollection()))
          .thenReturn(List.of(quote));

      assertTrue(leaseHolder.isLeased("neg-1"));
    }

    @Test
    void returns_false_when_leased_but_expired() {
      ExtendableQuoteVO quote = quoteWith("q-1", CONTROLPLANE, true, "lock-1", NOW - 1);
      when(quoteApi.findByNegotiationIdAndStates(eq("neg-1"), anyCollection()))
          .thenReturn(List.of(quote));

      assertFalse(leaseHolder.isLeased("neg-1"));
    }

    @Test
    void returns_false_when_not_leased() {
      ExtendableQuoteVO quote = unleased("q-1");
      when(quoteApi.findByNegotiationIdAndStates(eq("neg-1"), anyCollection()))
          .thenReturn(List.of(quote));

      assertFalse(leaseHolder.isLeased("neg-1"));
    }

    @Test
    void returns_false_when_no_quotes() {
      when(quoteApi.findByNegotiationIdAndStates(eq("neg-1"), anyCollection()))
          .thenReturn(List.of());

      assertFalse(leaseHolder.isLeased("neg-1"));
    }

    @Test
    void ignores_quotes_from_other_controlplane() {
      ExtendableQuoteVO quote = quoteWith("q-1", OTHER_CONTROLPLANE, true, "lock-1", NOW + 30000);
      when(quoteApi.findByNegotiationIdAndStates(eq("neg-1"), anyCollection()))
          .thenReturn(List.of(quote));

      assertFalse(leaseHolder.isLeased("neg-1"));
    }

    @Test
    void returns_false_when_lease_expiry_equals_now() {
      // leaseExpiry > clock.millis() is the check, so exactly equal means expired
      ExtendableQuoteVO quote = quoteWith("q-1", CONTROLPLANE, true, "lock-1", NOW);
      when(quoteApi.findByNegotiationIdAndStates(eq("neg-1"), anyCollection()))
          .thenReturn(List.of(quote));

      assertFalse(leaseHolder.isLeased("neg-1"));
    }
  }

  @Nested
  class IsLeasedBy {

    @Test
    void returns_true_when_leased_by_matching_lock() {
      ExtendableQuoteVO quote = quoteWith("q-1", CONTROLPLANE, true, "lock-1", NOW + 30000);
      when(quoteApi.findByNegotiationIdAndStates(eq("neg-1"), anyCollection()))
          .thenReturn(List.of(quote));

      assertTrue(leaseHolder.isLeasedBy("neg-1", "lock-1"));
    }

    @Test
    void returns_false_when_leased_by_different_lock() {
      ExtendableQuoteVO quote = quoteWith("q-1", CONTROLPLANE, true, "other-lock", NOW + 30000);
      when(quoteApi.findByNegotiationIdAndStates(eq("neg-1"), anyCollection()))
          .thenReturn(List.of(quote));

      assertFalse(leaseHolder.isLeasedBy("neg-1", "lock-1"));
    }

    @Test
    void returns_false_when_leased_by_matching_lock_but_expired() {
      ExtendableQuoteVO quote = quoteWith("q-1", CONTROLPLANE, true, "lock-1", NOW - 1);
      when(quoteApi.findByNegotiationIdAndStates(eq("neg-1"), anyCollection()))
          .thenReturn(List.of(quote));

      assertFalse(leaseHolder.isLeasedBy("neg-1", "lock-1"));
    }

    @Test
    void returns_false_when_not_leased() {
      ExtendableQuoteVO quote = unleased("q-1");
      when(quoteApi.findByNegotiationIdAndStates(eq("neg-1"), anyCollection()))
          .thenReturn(List.of(quote));

      assertFalse(leaseHolder.isLeasedBy("neg-1", "lock-1"));
    }

    @Test
    void returns_false_when_no_quotes() {
      when(quoteApi.findByNegotiationIdAndStates(eq("neg-1"), anyCollection()))
          .thenReturn(List.of());

      assertFalse(leaseHolder.isLeasedBy("neg-1", "lock-1"));
    }
  }

  @Nested
  class FreeLease {

    @Test
    void clears_lease_fields_on_leased_quote() {
      ExtendableQuoteVO quote = quoteWith("q-1", CONTROLPLANE, true, "lock-1", NOW + 30000);
      when(quoteApi.findByNegotiationIdAndStates(eq("neg-1"), anyCollection()))
          .thenReturn(List.of(quote));
      when(quoteApi.updateQuote(eq("q-1"), any())).thenReturn(quote);

      leaseHolder.freeLease("neg-1", "done");

      verify(quoteApi)
          .updateQuote(
              eq("q-1"),
              argThat(
                  update -> {
                    ContractNegotiationState s = update.getContractNegotiationState();
                    return !s.isLeased()
                        && s.getLeasedBy() == null
                        && s.getLeaseExpiry() == 0
                        && update.getRelatedParty() == null;
                  }));
    }

    @Test
    void does_nothing_when_not_leased() {
      ExtendableQuoteVO quote = unleased("q-1");
      when(quoteApi.findByNegotiationIdAndStates(eq("neg-1"), anyCollection()))
          .thenReturn(List.of(quote));

      leaseHolder.freeLease("neg-1", "cleanup");

      verify(quoteApi, never()).updateQuote(any(), any());
    }

    @Test
    void does_nothing_when_no_quotes() {
      when(quoteApi.findByNegotiationIdAndStates(eq("neg-1"), anyCollection()))
          .thenReturn(List.of());

      leaseHolder.freeLease("neg-1", "cleanup");

      verify(quoteApi, never()).updateQuote(any(), any());
    }

    @Test
    void frees_multiple_leased_quotes() {
      ExtendableQuoteVO q1 = quoteWith("q-1", CONTROLPLANE, true, "lock-1", NOW + 30000);
      ExtendableQuoteVO q2 = quoteWith("q-2", CONTROLPLANE, true, "lock-1", NOW + 30000);
      when(quoteApi.findByNegotiationIdAndStates(eq("neg-1"), anyCollection()))
          .thenReturn(List.of(q1, q2));
      when(quoteApi.updateQuote(any(), any())).thenReturn(q1);

      leaseHolder.freeLease("neg-1", "done");

      verify(quoteApi).updateQuote(eq("q-1"), any());
      verify(quoteApi).updateQuote(eq("q-2"), any());
    }

    @Test
    void ignores_quotes_from_other_controlplane() {
      ExtendableQuoteVO quote = quoteWith("q-1", OTHER_CONTROLPLANE, true, "lock-1", NOW + 30000);
      when(quoteApi.findByNegotiationIdAndStates(eq("neg-1"), anyCollection()))
          .thenReturn(List.of(quote));

      leaseHolder.freeLease("neg-1", "done");

      verify(quoteApi, never()).updateQuote(any(), any());
    }

    @Test
    void preserves_state_fields_when_freeing() {
      ExtendableQuoteVO quote = quoteWith("q-1", CONTROLPLANE, true, "lock-1", NOW + 30000);
      quote.getContractNegotiationState().setPending(true).setCorrelationId("corr-123");
      when(quoteApi.findByNegotiationIdAndStates(eq("neg-1"), anyCollection()))
          .thenReturn(List.of(quote));
      when(quoteApi.updateQuote(eq("q-1"), any())).thenReturn(quote);

      leaseHolder.freeLease("neg-1", "done");

      verify(quoteApi)
          .updateQuote(
              eq("q-1"),
              argThat(
                  update -> {
                    ContractNegotiationState s = update.getContractNegotiationState();
                    return s.isPending()
                        && "corr-123".equals(s.getCorrelationId())
                        && CONTROLPLANE.equals(s.getControlplane())
                        && !s.isLeased();
                  }));
    }
  }

  @Nested
  class DefaultLeaseTime {

    @Test
    void two_arg_acquireLease_uses_60_second_default() {
      ExtendableQuoteVO quote = unleased("q-1");
      when(quoteApi.findByNegotiationIdAndStates(eq("neg-1"), anyCollection()))
          .thenReturn(List.of(quote));

      ExtendableQuoteVO updatedQuote = quoteWith("q-1", CONTROLPLANE, true, "lock-1", NOW + 60000);
      when(quoteApi.updateQuote(eq("q-1"), any())).thenReturn(updatedQuote);

      leaseHolder.acquireLease("neg-1", "lock-1");

      verify(quoteApi)
          .updateQuote(
              eq("q-1"),
              argThat(
                  update -> {
                    ContractNegotiationState s = update.getContractNegotiationState();
                    return s.getLeaseExpiry() == NOW + 60000;
                  }));
    }
  }
}
