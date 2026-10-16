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
package org.seamware.edc.edc;

/*-
 * #%L
 * test-extension
 * %%
 * Copyright (C) 2025 - 2026 Seamless Middleware Technologies S.L
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.REQUESTED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.INITIAL;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.*;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.*;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.policy.model.Policy;

/** Assembles data for the TCK scenarios. */
public class DataAssembly {
  // The id constants below are the canonical TCK id registry: scripts/init-tmforum.sh seeds the
  // TMForum backend from them and scripts/verify-tck-config.sh greps these literals to check that
  // config/tck/tck.properties stays in sync. Keep them even when no Java code reads them directly.

  /** Asset ID referenced by transfer process agreements; must exist in the TMForum catalog data. */
  static final String TRANSFER_ASSET_ID = "CAT0101";

  static final Set<String> ASSET_IDS =
      Set.of(
          "ACN0101", "ACN0102", "ACN0103", "ACN0104", "ACN0201", "ACN0202", "ACN0203", "ACN0204",
          "ACN0205", "ACN0206", "ACN0207", "ACN0301", "ACN0302", "ACN0303", "ACN0304", "CAT0101",
          "CAT0102");

  static final Set<String> AGREEMENT_IDS =
      Set.of(
          "ATP0101",
          "ATP0102",
          "ATP0103",
          "ATP0104",
          "ATP0105",
          "ATP0201",
          "ATP0202",
          "ATP0203",
          "ATP0204",
          "ATP0205",
          "ATP0301",
          "ATP0302",
          "ATP0303",
          "ATP0304",
          "ATP0305",
          "ATP0306",
          "ATPC0101",
          "ATPC0102",
          "ATPC0103",
          "ATPC0104",
          "ATPC0105",
          "ATPC0201",
          "ATPC0202",
          "ATPC0203",
          "ATPC0204",
          "ATPC0205",
          "ATPC0301",
          "ATPC0302",
          "ATPC0303",
          "ATPC0304",
          "ATPC0305",
          "ATPC0306");

  static final String POLICY_ID = "P123";
  static final String CONTRACT_DEFINITION_ID = "CD123";

  private static final Field STATE_FIELD;

  static {
    try {
      STATE_FIELD = org.eclipse.edc.spi.entity.StatefulEntity.class.getDeclaredField("state");
      STATE_FIELD.setAccessible(true);
    } catch (NoSuchFieldException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private DataAssembly() {}

  /**
   * Resets a provider transfer process back to INITIAL state via reflection. Used by triggers that
   * fire after the DSP response (showing REQUESTED) has been sent, allowing the state machine to
   * process the transfer normally.
   */
  static void resetToInitial(TransferProcess process) {
    try {
      STATE_FIELD.setInt(process, INITIAL.code());
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("Failed to reset transfer state to INITIAL", e);
    }
  }

  public static StepRecorder<ContractNegotiation> createNegotiationRecorder() {
    var recorder = new StepRecorder<ContractNegotiation>();

    record01NegotiationSequences(recorder);
    record02NegotiationSequences(recorder);
    record03NegotiationSequences(recorder);

    recordC01NegotiationSequences(recorder);

    return recorder.repeat();
  }

  private static void recordC01NegotiationSequences(StepRecorder<ContractNegotiation> recorder) {}

  private static void record01NegotiationSequences(StepRecorder<ContractNegotiation> recorder) {
    recorder.record("ACN0101", ContractNegotiation::transitionOffering);

    recorder
        .record("ACN0102", ContractNegotiation::transitionOffering)
        .record("ACN0102", ContractNegotiation::transitionTerminating);

    recorder
        .record("ACN0103", ContractNegotiation::transitionOffering)
        .record("ACN0103", ContractNegotiation::transitionAgreeing)
        .record("ACN0103", ContractNegotiation::transitionFinalizing);

    recorder
        .record("ACN0104", ContractNegotiation::transitionAgreeing)
        .record("ACN0104", ContractNegotiation::transitionFinalizing);
  }

  private static void record02NegotiationSequences(StepRecorder<ContractNegotiation> recorder) {
    recorder.record("ACN0201", ContractNegotiation::transitionTerminating);

    recorder.record("ACN0203", ContractNegotiation::transitionAgreeing);

    recorder.record("ACN0204", ContractNegotiation::transitionOffering);

    recorder.record("ACN0205", ContractNegotiation::transitionOffering);

    recorder.record(
        "ACN0206",
        contractNegotiation -> {
          // only transition if in requested
          if (contractNegotiation.getState() == REQUESTED.code()) {
            contractNegotiation.transitionOffering();
          }
        });

    recorder
        .record("ACN0207", ContractNegotiation::transitionAgreeing)
        .record("ACN0207", ContractNegotiation::transitionTerminating);
  }

  private static void record03NegotiationSequences(StepRecorder<ContractNegotiation> recorder) {
    recorder
        .record("ACN0301", ContractNegotiation::transitionAgreeing)
        .record("ACN0301", ContractNegotiation::transitionFinalizing);

    recorder.record("ACN0302", ContractNegotiation::transitionOffering);
    recorder
        .record("ACN0303", ContractNegotiation::transitionOffering)
        .record("ACN0303", DataAssembly::noop);

    recorder
        .record(
            "ACN0304",
            cn -> {
              Logger.getLogger("ACN0304").warning("Transition to offering");
              cn.transitionOffering();
            })
        .record(
            "ACN0304",
            cn -> {
              Logger.getLogger("ACN0304").warning("No op");
              noop(cn);
            });
  }

  public static List<Trigger<ContractNegotiation>> createNegotiationTriggers() {
    return List.of(
        createTrigger(
            ContractNegotiationOffered.class,
            "ACN0205",
            cn -> {
              cn.transitionTerminating();
              cn.setPending(false);
            }),
        createTrigger(
            ContractNegotiationAccepted.class,
            "ACN0206",
            cn -> {
              cn.transitionTerminating();
              cn.setPending(false);
            }),
        createTrigger(
            ContractNegotiationAccepted.class,
            "ACN0303",
            cn -> {
              cn.setPending(true);
            }),
        createTrigger(
            ContractNegotiationOffered.class,
            "ACNC0101",
            contractNegotiation -> {
              contractNegotiation.transitionAccepting();
              contractNegotiation.setPending(false);
            }),
        createTrigger(
            ContractNegotiationAgreed.class,
            "ACNC0101",
            contractNegotiation -> {
              contractNegotiation.transitionVerifying();
              contractNegotiation.setPending(false);
            }),
        createTrigger(
            ContractNegotiationOffered.class,
            "ACNC0102",
            contractNegotiation -> {
              contractNegotiation.transitionRequesting();
              contractNegotiation.setPending(false);
            }),
        createTrigger(
            ContractNegotiationOffered.class,
            "ACNC0103",
            ContractNegotiation::transitionTerminating),
        createTrigger(
            ContractNegotiationAgreed.class,
            "ACNC0104",
            contractNegotiation -> {
              contractNegotiation.transitionVerifying();
              contractNegotiation.setPending(false);
            }),
        createTrigger(
            ContractNegotiationRequested.class,
            "ACNC0202",
            ContractNegotiation::transitionTerminating),
        createTrigger(
            ContractNegotiationAgreed.class,
            "ACNC0203",
            ContractNegotiation::transitionTerminating),
        createTrigger(
            ContractNegotiationOffered.class,
            "ACNC0205",
            contractNegotiation -> {
              contractNegotiation.transitionAccepting();
              contractNegotiation.setPending(false);
            }),
        createTrigger(
            ContractNegotiationAgreed.class,
            "ACNC0206",
            contractNegotiation -> {
              contractNegotiation.transitionVerifying();
              contractNegotiation.setPending(false);
            }),
        createTrigger(
            ContractNegotiationOffered.class,
            "ACNC0304",
            contractNegotiation -> {
              contractNegotiation.transitionAccepting();
              contractNegotiation.setPending(false);
            }),
        createTrigger(
            ContractNegotiationOffered.class,
            "ACNC0305",
            contractNegotiation -> {
              contractNegotiation.transitionAccepting();
              contractNegotiation.setPending(false);
            }),
        createTrigger(
            ContractNegotiationOffered.class,
            "ACNC0306",
            contractNegotiation -> {
              contractNegotiation.transitionAccepting();
              contractNegotiation.setPending(false);
            }),
        createTrigger(
            ContractNegotiationAgreed.class,
            "ACNC0306",
            contractNegotiation -> {
              contractNegotiation.setPending(true);
            }));
  }

  public static StepRecorder<TransferProcess> createTransferProcessRecorder() {
    var recorder = new StepRecorder<TransferProcess>();

    recordProvider01TransferSequences(recorder);
    recordProvider02TransferSequences(recorder);
    recordProvider03TransferSequences(recorder);

    recordConsumer01TransferSequences(recorder);
    recordConsumer02TransferSequences(recorder);
    recordConsumer03TransferSequences(recorder);

    return recorder.repeat();
  }

  private static void recordProvider01TransferSequences(StepRecorder<TransferProcess> recorder) {
    recorder.record("ATP0101", TransferProcess::transitionStarting);
    recorder.record("ATP0102", TransferProcess::transitionStarting);
    recorder.record("ATP0103", TransferProcess::transitionStarting);
    recorder.record("ATP0104", TransferProcess::transitionStarting);
    recorder.record("ATP0105", tp -> tp.transitionTerminating("error"));
  }

  private static void recordProvider02TransferSequences(StepRecorder<TransferProcess> recorder) {
    recorder.record("ATP0201", TransferProcess::transitionStarting);
    recorder.record("ATP0202", TransferProcess::transitionStarting);
    recorder.record("ATP0203", TransferProcess::transitionStarting);
    recorder.record("ATP0204", TransferProcess::transitionStarting);
  }

  private static void recordProvider03TransferSequences(StepRecorder<TransferProcess> recorder) {
    recorder.record("ATP0303", TransferProcess::transitionStarting);
    recorder.record("ATP0304", TransferProcess::transitionStarting);
    recorder.record("ATP0305", TransferProcess::transitionStarting);
    recorder.record("ATP0306", TransferProcess::transitionStarting);
  }

  // The TP_C:01 consumer is purely reactive: the TCK drives start, suspend, resume and
  // completion from the provider side. The only point the guard intercepts is RESUMING,
  // which 0.18.1 would otherwise process by dispatching a TransferStartMessage the TCK
  // rejects, so the recorded step carries the transfer to STARTED directly.
  private static void recordConsumer01TransferSequences(StepRecorder<TransferProcess> recorder) {
    recorder.record("ATPC0101", TransferProcess::transitionStarted);
    recorder.record("ATPC0102", TransferProcess::transitionStarted);
    recorder.record("ATPC0103", TransferProcess::transitionStarted);
    recorder.record("ATPC0104", TransferProcess::transitionStarted);
    recorder.record("ATPC0105", TransferProcess::transitionStarted);
  }

  private static void recordConsumer02TransferSequences(StepRecorder<TransferProcess> recorder) {
    recorder.record("ATPC0201", TransferProcess::transitionRequesting);
    recorder.record("ATPC0202", TransferProcess::transitionRequesting);
    recorder.record("ATPC0203", TransferProcess::transitionRequesting);
    recorder.record("ATPC0204", TransferProcess::transitionRequesting);
    recorder.record("ATPC0205", TransferProcess::transitionRequesting);
  }

  private static void recordConsumer03TransferSequences(StepRecorder<TransferProcess> recorder) {
    recorder.record("ATPC0301", TransferProcess::transitionRequesting);
    recorder.record("ATPC0302", TransferProcess::transitionRequesting);
    recorder.record("ATPC0303", TransferProcess::transitionRequesting);
    recorder.record("ATPC0304", TransferProcess::transitionRequesting);
    recorder.record("ATPC0305", TransferProcess::transitionRequesting);
    recorder.record("ATPC0306", TransferProcess::transitionRequesting);
  }

  public static List<Trigger<TransferProcess>> createTransferProcessTriggers() {
    return List.of(
        createTransferTrigger(
            TransferProcessStarted.class, "ATP0101", tp -> tp.transitionTerminating("error")),
        createTransferTrigger(
            TransferProcessStarted.class, "ATP0102", TransferProcess::transitionCompleting),
        createTransferTrigger(
            TransferProcessStarted.class,
            "ATP0103",
            (process) -> process.transitionSuspending("suspending")),
        createTransferTrigger(
            TransferProcessSuspended.class,
            "ATP0103",
            (process) -> process.transitionTerminating("terminating")),
        createTransferTrigger(TransferProcessStarted.class, "ATP0104", suspendResumeTrigger()),
        createTransferTrigger(
            TransferProcessSuspended.class, "ATP0104", TransferProcess::transitionStarting),
        // ATP01, ATP02, ATP03 (except 0301/0302): reset to INITIAL so the state machine
        // processes them after the REQUESTED DSP response has been sent
        createTransferTrigger(
            TransferProcessInitiated.class, "ATP0101", DataAssembly::resetToInitial),
        createTransferTrigger(
            TransferProcessInitiated.class, "ATP0102", DataAssembly::resetToInitial),
        createTransferTrigger(
            TransferProcessInitiated.class, "ATP0103", DataAssembly::resetToInitial),
        createTransferTrigger(
            TransferProcessInitiated.class, "ATP0104", DataAssembly::resetToInitial),
        createTransferTrigger(
            TransferProcessInitiated.class, "ATP0105", DataAssembly::resetToInitial),
        createTransferTrigger(
            TransferProcessInitiated.class, "ATP0201", DataAssembly::resetToInitial),
        createTransferTrigger(
            TransferProcessInitiated.class, "ATP0202", DataAssembly::resetToInitial),
        createTransferTrigger(
            TransferProcessInitiated.class, "ATP0203", DataAssembly::resetToInitial),
        createTransferTrigger(
            TransferProcessInitiated.class, "ATP0204", DataAssembly::resetToInitial),
        createTransferTrigger(
            TransferProcessInitiated.class, "ATP0205", (process) -> process.setPending(true)),
        // ATP0301/0302: stay in REQUESTED (no reset, no state machine processing)
        createTransferTrigger(
            TransferProcessInitiated.class, "ATP0303", DataAssembly::resetToInitial),
        createTransferTrigger(
            TransferProcessInitiated.class, "ATP0304", DataAssembly::resetToInitial),
        createTransferTrigger(
            TransferProcessInitiated.class, "ATP0305", DataAssembly::resetToInitial),
        createTransferTrigger(
            TransferProcessInitiated.class, "ATP0306", DataAssembly::resetToInitial),
        createTransferTrigger(
            TransferProcessStarted.class, "ATPC0201", TransferProcess::transitionTerminating),
        createTransferTrigger(
            TransferProcessStarted.class, "ATPC0202", TransferProcess::transitionCompleting),
        createTransferTrigger(
            TransferProcessStarted.class,
            "ATPC0203",
            (process) -> process.transitionSuspending("suspending")),
        createTransferTrigger(
            TransferProcessSuspended.class, "ATPC0203", TransferProcess::transitionTerminating),
        createTransferTrigger(TransferProcessStarted.class, "ATPC0204", suspendResumeTrigger()),
        createTransferTrigger(
            TransferProcessSuspended.class, "ATPC0204", TransferProcess::transitionStarting),
        createTransferTrigger(
            TransferProcessRequested.class,
            "ATPC0205",
            (process) -> process.transitionTerminating("error")));
  }

  /**
   * Creates a trigger that alternates between suspending and completing a transfer process. Uses
   * atomic compare-and-set to ensure thread-safe state transitions.
   */
  public static Consumer<TransferProcess> suspendResumeTrigger() {
    var count = new AtomicInteger(0);
    return (process) -> {
      if (count.compareAndSet(0, 1)) {
        process.transitionSuspending("suspending");
      } else if (count.compareAndSet(1, 0)) {
        process.transitionCompleting();
      }
    };
  }

  private static <E extends ContractNegotiationEvent> Trigger<ContractNegotiation> createTrigger(
      Class<E> type, String assetId, Consumer<ContractNegotiation> action) {
    return new Trigger<>(
        event -> {
          if (event.getClass().equals(type)) {
            return assetId.equals(
                ((ContractNegotiationEvent) event).getLastContractOffer().getAssetId());
          }
          return false;
        },
        action);
  }

  private static <E extends TransferProcessEvent> Trigger<TransferProcess> createTransferTrigger(
      Class<E> type, String agreementId, Consumer<TransferProcess> action) {
    return new Trigger<>(
        event -> {
          if (event.getClass().equals(type)) {
            return agreementId.equals(((TransferProcessEvent) event).getContractId());
          }
          return false;
        },
        action);
  }

  private static ContractNegotiation createContractNegotiation(String id) {

    return ContractNegotiation.Builder.newInstance()
        .contractAgreement(
            ContractAgreement.Builder.newInstance()
                .id(id)
                .providerId("providerId")
                .consumerId("TCK_PARTICIPANT")
                .assetId("ATP0101")
                .contractSigningDate(System.currentTimeMillis())
                .policy(Policy.Builder.newInstance().build())
                .build())
        .type(ContractNegotiation.Type.PROVIDER)
        .state(ContractNegotiationStates.FINALIZED.code())
        .counterPartyId("counterPartyId")
        .counterPartyAddress("https://test.com")
        .protocol("test")
        .build();
  }

  private static void noop(ContractNegotiation cn) {}
}
