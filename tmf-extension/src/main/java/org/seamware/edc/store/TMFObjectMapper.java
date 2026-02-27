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

import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates;
import org.mapstruct.Mapper;
import org.seamware.edc.domain.*;
import org.seamware.tmforum.productorder.model.ProductOrderUpdateVO;
import org.seamware.tmforum.productorder.model.ProductOrderVO;

@Mapper
public interface TMFObjectMapper {

  ProductOrderUpdateVO map(ProductOrderVO productOrderVO);

  ExtendableAgreementCreateVO map(ExtendableAgreementVO extendableAgreementVO);

  ExtendableAgreementUpdateVO mapToUpdate(ExtendableAgreementVO extendableAgreementVO);

  ExtendableQuoteUpdateVO map(ExtendableQuoteVO quoteVO);

  ExtendableUsageUpdateVO map(ExtendableUsageVO extendableUsageVO);

  ExtendableUsageCreateVO mapToCreate(ExtendableUsageVO extendableUsageVO);

  static ContractNegotiationStates mapNegotiationState(String state) {
    return switch (state) {
      case "INITIAL" -> ContractNegotiationStates.INITIAL;
      case "REQUESTING" -> ContractNegotiationStates.REQUESTING;
      case "REQUESTED" -> ContractNegotiationStates.REQUESTED;
      case "OFFERING" -> ContractNegotiationStates.OFFERING;
      case "OFFERED" -> ContractNegotiationStates.OFFERED;
      case "ACCEPTING" -> ContractNegotiationStates.ACCEPTING;
      case "ACCEPTED" -> ContractNegotiationStates.ACCEPTED;
      case "AGREEING" -> ContractNegotiationStates.AGREEING;
      case "AGREED" -> ContractNegotiationStates.AGREED;
      case "VERIFYING" -> ContractNegotiationStates.VERIFYING;
      case "VERIFIED" -> ContractNegotiationStates.VERIFIED;
      case "FINALIZING" -> ContractNegotiationStates.FINALIZING;
      case "FINALIZED" -> ContractNegotiationStates.FINALIZED;
      case "TERMINATING" -> ContractNegotiationStates.TERMINATING;
      case "TERMINATED" -> ContractNegotiationStates.TERMINATED;
      default ->
          throw new IllegalArgumentException(
              String.format("State %s is not a valid negotiation state.", state));
    };
  }

  default TransferProcessStates mapTransferState(String state) {
    return switch (state) {
      case "INITIAL" -> TransferProcessStates.INITIAL;
      case "PROVISIONING" -> TransferProcessStates.PROVISIONING;
      case "PROVISIONING_REQUESTED" -> TransferProcessStates.PROVISIONING_REQUESTED;
      case "PROVISIONED" -> TransferProcessStates.PROVISIONED;
      case "REQUESTING" -> TransferProcessStates.REQUESTING;
      case "REQUESTED" -> TransferProcessStates.REQUESTED;
      case "STARTING" -> TransferProcessStates.STARTING;
      case "STARTUP_REQUESTED" -> TransferProcessStates.STARTUP_REQUESTED;
      case "STARTED" -> TransferProcessStates.STARTED;
      case "SUSPENDING" -> TransferProcessStates.SUSPENDING;
      case "SUSPENDING_REQUESTED" -> TransferProcessStates.SUSPENDING_REQUESTED;
      case "SUSPENDED" -> TransferProcessStates.SUSPENDED;
      case "RESUMING" -> TransferProcessStates.RESUMING;
      case "RESUMED" -> TransferProcessStates.RESUMED;
      case "COMPLETING" -> TransferProcessStates.COMPLETING;
      case "COMPLETING_REQUESTED" -> TransferProcessStates.COMPLETING_REQUESTED;
      case "COMPLETED" -> TransferProcessStates.COMPLETED;
      case "TERMINATING" -> TransferProcessStates.TERMINATING;
      case "TERMINATING_REQUESTED" -> TransferProcessStates.TERMINATING_REQUESTED;
      case "TERMINATED" -> TransferProcessStates.TERMINATED;
      case "DEPROVISIONING" -> TransferProcessStates.DEPROVISIONING;
      case "DEPROVISIONING_REQUESTED" -> TransferProcessStates.DEPROVISIONING_REQUESTED;
      case "DEPROVISIONED" -> TransferProcessStates.DEPROVISIONED;
      default ->
          throw new IllegalArgumentException(
              String.format("State %s is not a valid transfer state.", state));
    };
  }
}
