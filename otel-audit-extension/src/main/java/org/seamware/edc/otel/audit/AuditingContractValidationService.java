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
package org.seamware.edc.otel.audit;

import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractOffer;
import org.eclipse.edc.connector.controlplane.contract.spi.validation.ContractValidationService;
import org.eclipse.edc.connector.controlplane.contract.spi.validation.ValidatableConsumerOffer;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;

/**
 * Decorates the {@link ContractValidationService} used by the DSP protocol services to audit denied
 * requests. Every call goes to the original service and its result is returned unchanged; audit
 * failures are logged, never propagated.
 */
public class AuditingContractValidationService implements ContractValidationService {

  private final ContractValidationService delegate;
  private final PolicyDenyAuditor auditor;
  private final Monitor monitor;

  public AuditingContractValidationService(
      ContractValidationService delegate, PolicyDenyAuditor auditor, Monitor monitor) {
    this.delegate = delegate;
    this.auditor = auditor;
    this.monitor = monitor;
  }

  ContractValidationService delegate() {
    return delegate;
  }

  @Override
  public Result<Void> validateInitialOffer(
      ParticipantAgent agent, ValidatableConsumerOffer consumerOffer) {
    var result = delegate.validateInitialOffer(agent, consumerOffer);
    if (result.failed()) {
      audit(() -> auditor.initialOfferRejected(agent, consumerOffer, result.getFailureDetail()));
    }
    return result;
  }

  @Override
  public Result<ContractAgreement> validateAgreement(
      ParticipantAgent agent, ContractAgreement agreement) {
    var result = delegate.validateAgreement(agent, agreement);
    if (result.failed()) {
      audit(() -> auditor.transferAgreementRejected(agent, agreement, result.getFailureDetail()));
    }
    return result;
  }

  @Override
  public Result<Void> validateRequest(ParticipantAgent agent, ContractAgreement agreement) {
    var result = delegate.validateRequest(agent, agreement);
    if (result.failed()) {
      audit(() -> auditor.transferRequestRejected(agent, agreement, result.getFailureDetail()));
    }
    return result;
  }

  @Override
  public Result<Void> validateRequest(ParticipantAgent agent, ContractNegotiation negotiation) {
    var result = delegate.validateRequest(agent, negotiation);
    if (result.failed()) {
      audit(
          () -> auditor.negotiationRequestRejected(agent, negotiation, result.getFailureDetail()));
    }
    return result;
  }

  @Override
  public Result<Void> validateConfirmed(
      ParticipantAgent agent, ContractAgreement agreement, ContractOffer latestOffer) {
    var result = delegate.validateConfirmed(agent, agreement, latestOffer);
    if (result.failed()) {
      audit(
          () ->
              auditor.agreementConfirmationRejected(
                  agent, agreement, latestOffer, result.getFailureDetail()));
    }
    return result;
  }

  private void audit(Runnable audit) {
    AuditGuard.run(audit, monitor, "record rejected request");
  }
}
