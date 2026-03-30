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

import static java.util.Comparator.comparingLong;
import static org.seamware.edc.tmf.ParticipantResolver.CONSUMER_ROLE;
import static org.seamware.edc.tmf.ParticipantResolver.PROVIDER_ROLE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.edc.connector.controlplane.contract.spi.ContractOfferId;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractOffer;
import org.eclipse.edc.spi.entity.StatefulEntity;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.seamware.edc.domain.*;
import org.seamware.edc.tmf.*;
import org.seamware.tmforum.agreement.model.AgreementItemVO;
import org.seamware.tmforum.agreement.model.AgreementTermOrConditionVO;
import org.seamware.tmforum.agreement.model.ProductRefVO;
import org.seamware.tmforum.productinventory.model.ProductOfferingRefVO;
import org.seamware.tmforum.productinventory.model.ProductStatusTypeVO;
import org.seamware.tmforum.productinventory.model.ProductVO;
import org.seamware.tmforum.productorder.model.*;
import org.seamware.tmforum.quote.model.QuoteStateTypeVO;
import org.seamware.tmforum.quote.model.QuoteVO;
import org.seamware.tmforum.quote.model.RelatedPartyVO;

public class TMFBackedContractNegotiationStore implements ContractNegotiationStore {

  public static final String CUSTOMER_ROLE = "Customer";
  private final Monitor monitor;
  private final ObjectMapper objectMapper;
  private final QuoteApiClient quoteApi;
  private final AgreementApiClient agreementApi;
  private final ProductOrderApiClient productOrderApi;
  private final ProductCatalogApiClient productCatalogApi;
  private final ProductInventoryApiClient productInventoryApi;
  private final ParticipantResolver participantResolver;
  private final TMFEdcMapper tmfEdcMapper;
  private final String participantId;
  private final String controlplane;

  private final CriterionOperatorRegistry criterionOperatorRegistry;

  private final String lockId;
  private final LockManager lockManager;
  private final LeaseHolder leaseHolder;
  private final TMFTransactionContext transactionContext;
  private final ConcurrentHashMap<String, Object> negotiationLocks = new ConcurrentHashMap<>();

  /**
   * Tracks negotiation IDs that are currently being processed within this JVM. Populated by {@link
   * #nextNotLeased} and {@link #findByIdAndLease} when they acquire a lease, cleared by {@link
   * #save} when it releases the lease. This prevents the same negotiation from being picked up by
   * the state machine while a previous processing cycle is still running, which the distributed
   * lease alone cannot do because all threads in the same instance share the same {@code lockId}.
   */
  private final Set<String> activeNegotiations =
      Collections.newSetFromMap(new ConcurrentHashMap<>());

  public TMFBackedContractNegotiationStore(
      Monitor monitor,
      ObjectMapper objectMapper,
      QuoteApiClient quoteApi,
      AgreementApiClient agreementApi,
      ProductOrderApiClient productOrderApi,
      ProductCatalogApiClient productCatalogApi,
      ProductInventoryApiClient productInventoryApi,
      ParticipantResolver participantResolver,
      TMFEdcMapper tmfEdcMapper,
      String participantId,
      String controlplane,
      CriterionOperatorRegistry criterionOperatorRegistry,
      LeaseHolder leaseHolder,
      TMFTransactionContext transactionContext) {
    this(
        monitor,
        objectMapper,
        quoteApi,
        agreementApi,
        productOrderApi,
        productCatalogApi,
        productInventoryApi,
        participantResolver,
        tmfEdcMapper,
        participantId,
        controlplane,
        criterionOperatorRegistry,
        leaseHolder,
        transactionContext,
        new AutomaticUnlockingLockManager(new ReentrantReadWriteLock(true), 10500, monitor));
  }

  public TMFBackedContractNegotiationStore(
      Monitor monitor,
      ObjectMapper objectMapper,
      QuoteApiClient quoteApi,
      AgreementApiClient agreementApi,
      ProductOrderApiClient productOrderApi,
      ProductCatalogApiClient productCatalogApi,
      ProductInventoryApiClient productInventoryApi,
      ParticipantResolver participantResolver,
      TMFEdcMapper tmfEdcMapper,
      String participantId,
      String controlplane,
      CriterionOperatorRegistry criterionOperatorRegistry,
      LeaseHolder leaseHolder,
      TMFTransactionContext transactionContext,
      LockManager lockManager) {
    this.monitor = monitor;
    this.objectMapper = objectMapper;
    this.quoteApi = quoteApi;
    this.agreementApi = agreementApi;
    this.productOrderApi = productOrderApi;
    this.productCatalogApi = productCatalogApi;
    this.productInventoryApi = productInventoryApi;
    this.participantResolver = participantResolver;
    this.tmfEdcMapper = tmfEdcMapper;
    this.participantId = participantId;
    this.leaseHolder = leaseHolder;
    this.controlplane = controlplane;
    this.criterionOperatorRegistry = criterionOperatorRegistry;
    this.transactionContext = transactionContext;
    this.lockId = UUID.randomUUID().toString();
    this.lockManager = lockManager;
  }

  @Override
  public @Nullable ContractAgreement findContractAgreement(String s) {
    monitor.debug("Find agreement " + s);
    try {

      return agreementApi
          .findByContractId(s)
          // only "AGREED" agreements can be considered as agreement in the terms of DSP
          .filter(agreement -> agreement.getStatus() != null)
          .filter(agreement -> agreement.getStatus().equals(AgreementState.AGREED.getValue()))
          .map(tmfEdcMapper::toContractAgreement)
          .orElse(null);
    } catch (RuntimeException e) {
      monitor.warning(String.format("Was not able to map agreement %s.", s), e);
      return null;
    }
  }

  @Override
  public StoreResult<Void> deleteById(String s) {
    throw new UnsupportedOperationException("Deletion is currently not supported.");
  }

  @Override
  public @NotNull Stream<ContractNegotiation> queryNegotiations(QuerySpec querySpec) {

    List<ContractNegotiation> negotiations = getNegotiations(querySpec.getFilterExpression());

    int fromIndex = Math.min(querySpec.getOffset(), negotiations.size());
    int toIndex = Math.min(querySpec.getOffset() + querySpec.getLimit(), negotiations.size());
    return negotiations.subList(fromIndex, toIndex).stream();
  }

  @Override
  public @NotNull Stream<ContractAgreement> queryAgreements(QuerySpec querySpec) {
    monitor.debug("Query agreements " + querySpec.toString());
    return getFilteredStream(querySpec.getFilterExpression())
        .sorted((a1, a2) -> Comparator.<String>naturalOrder().compare(a1.getId(), a2.getId()))
        .skip(querySpec.getOffset())
        .limit(querySpec.getLimit());
  }

  private Stream<ContractAgreement> getFilteredStream(List<Criterion> criteria) {
    monitor.debug("getFilteredStream");
    Predicate<ContractAgreement> filterPredicate =
        criteria.stream()
            .map(criterionOperatorRegistry::<ContractAgreement>toPredicate)
            .reduce(x -> true, Predicate::and);

    List<ContractAgreement> contractAgreements = new ArrayList<>();
    int offset = 0;
    boolean moreOfferingsAvailable = true;
    while (moreOfferingsAvailable) {
      List<ExtendableAgreementVO> agreements = agreementApi.getAgreements(offset, 100);
      agreements.stream()
          // only "AGREED" agreements can be considered as agreement in the terms of DSP
          .filter(agreement -> agreement.getStatus() != null)
          .filter(agreement -> agreement.getStatus().equals(AgreementState.AGREED.getValue()))
          .map(
              agreement -> {
                try {
                  return tmfEdcMapper.toContractAgreement(agreement);
                } catch (RuntimeException e) {
                  monitor.warning("Was not able to map agreement.", e);
                  return null;
                }
              })
          .filter(Objects::nonNull)
          .filter(filterPredicate)
          .forEach(contractAgreements::add);

      moreOfferingsAvailable = agreements.size() == 100;
      offset += 100;
    }
    return contractAgreements.stream();
  }

  @Override
  public @Nullable ContractNegotiation findById(String s) {
    List<ExtendableQuoteVO> contractNegotiations =
        quoteApi.findByNegotiationId(s).stream()
            // only those that this controlplane is responsible for
            .filter(eqv -> eqv.getContractNegotiationState().getControlplane().equals(controlplane))
            .toList();
    if (contractNegotiations == null || contractNegotiations.isEmpty()) {
      return null;
    }
    return tmfEdcMapper.toContractNegotiation(
        new ArrayList<>(contractNegotiations), agreementApi, participantResolver, participantId);
  }

  private Map<String, List<ExtendableQuoteVO>> fetchQuotesByNegotiationId() {
    Map<String, List<ExtendableQuoteVO>> negotiations = new HashMap<>();
    int offset = 0;
    boolean moreQuotesAvailable = true;
    int limit = 100;
    while (moreQuotesAvailable) {
      List<ExtendableQuoteVO> extendableQuoteVOS = quoteApi.getQuotes(offset, limit);
      extendableQuoteVOS.stream()
          .filter(eqv -> eqv.getContractNegotiationState() != null)
          .filter(eqv -> eqv.getContractNegotiationState().getControlplane().equals(controlplane))
          .forEach(
              eqv ->
                  negotiations
                      .computeIfAbsent(eqv.getExternalId(), k -> new ArrayList<>())
                      .add(eqv));
      moreQuotesAvailable = extendableQuoteVOS.size() == limit;
      offset += limit;
    }
    return negotiations;
  }

  private List<ContractNegotiation> getNegotiations(List<Criterion> criteria) {
    return toNegotiations(fetchQuotesByNegotiationId(), criteria);
  }

  private List<ContractNegotiation> toNegotiations(
      Map<String, List<ExtendableQuoteVO>> quotesMap, List<Criterion> criteria) {
    Predicate<ContractNegotiation> filterPredicate =
        criteria.stream()
            .map(criterionOperatorRegistry::<ContractNegotiation>toPredicate)
            .reduce(x -> true, Predicate::and);
    return quotesMap.entrySet().stream()
        .map(
            entry -> {
              try {
                return tmfEdcMapper.toContractNegotiation(
                    entry.getValue(), agreementApi, participantResolver, participantId);
              } catch (RuntimeException e) {
                monitor.warning(
                    String.format(
                        "Was not able to read negotiation %s from quotes.", entry.getKey()),
                    e);
                return null;
              }
            })
        .filter(Objects::nonNull)
        .filter(filterPredicate)
        .sorted(comparingLong(StatefulEntity::getStateTimestamp))
        .toList();
  }

  @Override
  public @NotNull List<ContractNegotiation> nextNotLeased(int max, Criterion... criteria) {
    try {
      return lockManager.writeLock(
          () ->
              getNegotiations(Arrays.asList(criteria)).stream()
                  .filter(cn -> !activeNegotiations.contains(cn.getId()))
                  .filter(
                      cn -> {
                        try {
                          leaseHolder.acquireLease(cn.getId(), lockId);
                          activeNegotiations.add(cn.getId());
                          return true;
                        } catch (Exception e) {
                          monitor.info(String.format("Was not able to lease %s", cn.getId()), e);
                          return false;
                        }
                      })
                  .limit(max)
                  .toList());
    } catch (Exception e) {
      monitor.warning("Failed to get", e);
      throw new EdcPersistenceException(e);
    }
  }

  @Override
  public StoreResult<ContractNegotiation> findByIdAndLease(String s) {
    try {
      return lockManager.writeLock(
          () -> {
            monitor.info("Find by Id and Lease " + s);
            if (activeNegotiations.contains(s)) {
              return StoreResult.alreadyLeased(String.format("%s is already leased.", s));
            }
            ContractNegotiation contractNegotiation = findById(s);
            if (contractNegotiation == null) {
              return StoreResult.notFound(String.format("Negotiation %s does not exist.", s));
            }
            try {
              leaseHolder.acquireLease(s, lockId);
              activeNegotiations.add(s);
              return StoreResult.success(contractNegotiation);
            } catch (IllegalStateException e) {
              return StoreResult.alreadyLeased(String.format("%s is already leased.", s));
            }
          });
    } catch (Exception e) {
      monitor.warning("Failed to find", e);
      return StoreResult.generalError(e.getMessage());
    }
  }

  @Override
  public void save(ContractNegotiation contractNegotiation) {
    // Per-negotiation lock prevents concurrent saves for the same negotiation within this
    // JVM. Without this, two DSP message handlers can enter handleAgreeStates simultaneously
    // and both create agreements before either sees the other's, producing duplicates.
    Object lock = negotiationLocks.computeIfAbsent(contractNegotiation.getId(), k -> new Object());
    synchronized (lock) {
      try {
        leaseHolder.acquireLease(contractNegotiation.getId(), lockId);
        transactionContext.execute(
            (TransactionContext.TransactionBlock)
                () -> {
                  try {
                    ContractNegotiationStates negotiationState =
                        ContractNegotiationStates.from(contractNegotiation.getState());
                    switch (negotiationState) {
                      case INITIAL, REQUESTING -> handleInitialStates(contractNegotiation);
                      case REQUESTED -> handleRequestedState(contractNegotiation);
                      case OFFERING, OFFERED -> handleOfferStates(contractNegotiation);
                      case ACCEPTED, ACCEPTING -> handleAcceptStates(contractNegotiation);
                      case AGREEING, AGREED ->
                          handleAgreeStates(contractNegotiation, negotiationState);
                      case VERIFIED, VERIFYING -> handleVerificationStates(contractNegotiation);
                      case FINALIZING, FINALIZED -> handleFinalStates(contractNegotiation);
                      case TERMINATED, TERMINATING -> handleTerminationStates(contractNegotiation);
                      default ->
                          monitor.warning(
                              String.format("State not supported: %s", negotiationState));
                    }
                  } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                  }
                });
      } catch (Exception e) {
        monitor.warning(
            String.format("Failed to save negotiation %s.", contractNegotiation.getId()), e);
        throw new EdcPersistenceException(
            String.format("Failed to save negotiation %s.", contractNegotiation.getId()), e);
      } finally {
        // always give up the lock
        leaseHolder.freeLease(contractNegotiation.getId(), "Finally saved.");
        activeNegotiations.remove(contractNegotiation.getId());
      }
    }
  }

  private void handleTerminationStates(ContractNegotiation contractNegotiation) {
    quoteApi.findByNegotiationId(contractNegotiation.getId()).stream()
        .filter(eq -> eq.getState() != QuoteStateTypeVO.CANCELLED)
        .forEach(
            cn -> {
              updateQuote(cn, contractNegotiation, QuoteStateTypeVO.CANCELLED);
              cancelProductOrder(cn);
            });
    cancelAgreements(contractNegotiation.getId());
  }

  private void handleFinalStates(ContractNegotiation contractNegotiation) {
    Optional<ExtendableQuoteVO> extendableQuoteVO =
        getActiveQuote(
            getQuotes(contractNegotiation),
            contractNegotiation,
            List.of(
                ContractNegotiationStates.VERIFIED,
                ContractNegotiationStates.FINALIZING,
                ContractNegotiationStates.FINALIZED));

    if (extendableQuoteVO.isEmpty()) {
      throw new IllegalArgumentException("In state verified, an accepted Quote needs to exist.");
    }
    finalizeOrder(contractNegotiation, extendableQuoteVO.get());
  }

  private void handleVerificationStates(ContractNegotiation contractNegotiation) {
    Optional<ExtendableQuoteVO> extendableQuoteVO =
        getActiveQuote(
            getQuotes(contractNegotiation),
            contractNegotiation,
            List.of(
                ContractNegotiationStates.AGREED,
                ContractNegotiationStates.VERIFYING,
                ContractNegotiationStates.VERIFIED));

    if (extendableQuoteVO.isEmpty()) {
      throw new IllegalArgumentException("In state verified, an accepted Quote needs to exist.");
    }

    ExtendableQuoteVO quoteVO = extendableQuoteVO.get();
    if (getProductOrder(quoteVO.getId()).isPresent()) {
      monitor.info("The order is already created.");
      // update the quote anyway to reflect the current state.
      updateQuote(quoteVO, contractNegotiation, quoteVO.getState());
      return;
    }

    ProductOrderCreateVO productOrderCreateVO = new ProductOrderCreateVO();
    getFromNegotiation(contractNegotiation).stream()
        .map(
            p ->
                new org.seamware.tmforum.productorder.model.RelatedPartyVO()
                    .id(p.partyId())
                    .role(p.role()))
        .forEach(
            pr -> {
              if (pr.getRole() != null && pr.getRole().equals(CONSUMER_ROLE)) {
                productOrderCreateVO.addRelatedPartyItem(pr);
                // Customer is the role expected by the Contract-Management
                productOrderCreateVO.addRelatedPartyItem(
                    new org.seamware.tmforum.productorder.model.RelatedPartyVO()
                        .id(pr.getId())
                        .role(CUSTOMER_ROLE));
              } else {
                productOrderCreateVO.addRelatedPartyItem(pr);
              }
            });
    productOrderCreateVO.quote(List.of(new QuoteRefVO().id(quoteVO.getId())));

    ProductOrderVO createdOrder = productOrderApi.createProductOrder(productOrderCreateVO);
    if (createdOrder != null) {
      registerProductOrderCompensation(
          "cancel created product order " + createdOrder.getId(),
          createdOrder.getId(),
          ProductOrderStateTypeVO.CANCELLED);
    }
    updateQuote(quoteVO, contractNegotiation, quoteVO.getState());
  }

  private void handleAgreeStates(
      ContractNegotiation contractNegotiation, ContractNegotiationStates negotiationState) {
    Optional<ExtendableQuoteVO> activeQuote =
        getActiveQuote(
            getQuotes(contractNegotiation),
            contractNegotiation,
            List.of(
                ContractNegotiationStates.AGREEING,
                ContractNegotiationStates.AGREED,
                ContractNegotiationStates.ACCEPTED,
                ContractNegotiationStates.REQUESTED));
    if (activeQuote.isEmpty()) {
      monitor.warning(
          String.format(
              "Cannot save transition to %s for %s.",
              negotiationState.name(), contractNegotiation.getId()));
      throw new IllegalArgumentException("In state agreed, an active Quote needs to exist.");
    } else {
      updateQuote(activeQuote.get(), contractNegotiation, QuoteStateTypeVO.ACCEPTED);
      if (contractNegotiation.getContractAgreement() != null) {
        boolean agreementExists =
            agreementApi
                .findByContractId(contractNegotiation.getContractAgreement().getId())
                .isPresent();
        if (agreementExists) {
          monitor.info("The agreement is already created.");
        } else {
          createAgreement(contractNegotiation);
        }
      }
    }
  }

  private void handleAcceptStates(ContractNegotiation contractNegotiation)
      throws JsonProcessingException {
    List<ExtendableQuoteVO> quotes = getQuotes(contractNegotiation);
    Optional<ExtendableQuoteVO> activeQuote =
        getActiveQuote(
            quotes,
            contractNegotiation,
            List.of(
                ContractNegotiationStates.OFFERED,
                ContractNegotiationStates.ACCEPTING,
                ContractNegotiationStates.ACCEPTED));
    Optional<ExtendableQuoteVO> terminatingQuote =
        quotes.stream()
            .filter(
                q ->
                    TMFEdcMapper.getContractNegotiationState(q)
                        == ContractNegotiationStates.TERMINATING)
            .findAny();
    if (activeQuote.isEmpty() && terminatingQuote.isEmpty()) {
      monitor.debug(
          "Create quote in accepted - existing quotes " + objectMapper.writeValueAsString(quotes));
      // if the first offer is directly accepted, no quote might exist
      ExtendableQuoteVO quoteVO = createQuote(contractNegotiation, QuoteStateTypeVO.ACCEPTED);
      // we need to create and update, since state changes are only able by patch
      updateQuote(quoteVO, contractNegotiation, QuoteStateTypeVO.ACCEPTED);
    } else if (activeQuote.isPresent()) {
      updateQuote(activeQuote.get(), contractNegotiation, QuoteStateTypeVO.ACCEPTED);
    } else {
      // negotiation is currently in terminating, don't do anything.
    }
  }

  private void handleOfferStates(ContractNegotiation contractNegotiation) {
    Optional<ExtendableQuoteVO> activeQuote =
        getActiveQuote(
            getQuotes(contractNegotiation),
            contractNegotiation,
            List.of(
                ContractNegotiationStates.REQUESTED,
                ContractNegotiationStates.INITIAL,
                ContractNegotiationStates.OFFERING,
                ContractNegotiationStates.OFFERED));
    if (activeQuote.isEmpty()) {
      monitor.info("Create quote in offered");
      ExtendableQuoteVO quoteVO = createQuote(contractNegotiation, QuoteStateTypeVO.APPROVED);
      // we need to create and update, since state changes are only able by patch
      updateQuote(quoteVO, contractNegotiation, QuoteStateTypeVO.APPROVED);
    } else {
      updateQuote(activeQuote.get(), contractNegotiation, QuoteStateTypeVO.APPROVED);
    }
  }

  private void handleRequestedState(ContractNegotiation contractNegotiation)
      throws JsonProcessingException {
    List<ExtendableQuoteVO> quotes = getQuotes(contractNegotiation);
    // REQUESTED must be included: a concurrent save from handleInitialStates can update
    // the quote's contractNegotiationState.state to REQUESTED before this handler runs.
    Optional<ExtendableQuoteVO> activeQuote =
        getActiveQuote(
            quotes,
            contractNegotiation,
            List.of(
                ContractNegotiationStates.INITIAL,
                ContractNegotiationStates.REQUESTING,
                ContractNegotiationStates.REQUESTED));
    if (activeQuote.isEmpty()) {
      monitor.debug(
          "Create quote in requested - existing quotes " + objectMapper.writeValueAsString(quotes));
      // in case of counter-offers, we will have a quote in approved or accepted and need to cancel
      // them
      getActiveQuote(quotes, contractNegotiation, List.of(ContractNegotiationStates.OFFERED))
          .ifPresent(q -> terminateQuote(q, contractNegotiation, QuoteStateTypeVO.CANCELLED));
      ExtendableQuoteVO quoteVO = createQuote(contractNegotiation, QuoteStateTypeVO.IN_PROGRESS);
      // we need to create and update, since state changes are only able by patch
      updateQuote(quoteVO, contractNegotiation, QuoteStateTypeVO.APPROVED);
    } else {
      updateQuote(activeQuote.get(), contractNegotiation, QuoteStateTypeVO.APPROVED);
    }
  }

  private void handleInitialStates(ContractNegotiation contractNegotiation) {
    List<ExtendableQuoteVO> quotes = getQuotes(contractNegotiation);
    Optional<ExtendableQuoteVO> activeQuote =
        getActiveQuote(
            quotes,
            contractNegotiation,
            List.of(ContractNegotiationStates.INITIAL, ContractNegotiationStates.REQUESTING));
    if (activeQuote.isEmpty()) {
      // in case of counter-offers, we will have a quote in approved or accepted and need to cancel
      // them
      getActiveQuote(
              quotes,
              contractNegotiation,
              List.of(ContractNegotiationStates.OFFERING, ContractNegotiationStates.OFFERED))
          .ifPresent(q -> terminateQuote(q, contractNegotiation, QuoteStateTypeVO.CANCELLED));
      // create the new quote
      createQuote(contractNegotiation, QuoteStateTypeVO.IN_PROGRESS);
    } else {
      updateQuote(activeQuote.get(), contractNegotiation, QuoteStateTypeVO.IN_PROGRESS);
    }
  }

  private void finalizeOrder(
      ContractNegotiation contractNegotiation, ExtendableQuoteVO finalQuote) {

    Optional<ProductOrderVO> orderVO = getProductOrder(finalQuote.getId());
    if (orderVO.isEmpty()) {
      throw new IllegalArgumentException("When finalizing, an order should already exist.");
    }

    updateQuote(finalQuote, contractNegotiation, QuoteStateTypeVO.ACCEPTED);
    ProductOrderUpdateVO productOrderUpdateVO = tmfEdcMapper.toUpdate(orderVO.get());
    productOrderUpdateVO.setState(ProductOrderStateTypeVO.COMPLETED);

    ExtendableProductCreate productCreateVO = new ExtendableProductCreate();
    String providerId =
        participantResolver.getTmfId(contractNegotiation.getContractAgreement().getProviderId());
    String consumerId =
        participantResolver.getTmfId(contractNegotiation.getContractAgreement().getConsumerId());
    productCreateVO
        // an asset can be materialized to a product multiple times, thus the agreement id needs to
        // be included
        .setExternalId(
            String.format(
                "%s-%S",
                contractNegotiation.getContractAgreement().getId(),
                contractNegotiation.getContractAgreement().getAssetId()))
        .status(ProductStatusTypeVO.ACTIVE)
        .addRelatedPartyItem(
            new org.seamware.tmforum.productinventory.model.RelatedPartyVO()
                .id(providerId)
                .role(PROVIDER_ROLE))
        .addRelatedPartyItem(
            new org.seamware.tmforum.productinventory.model.RelatedPartyVO()
                .id(consumerId)
                .role(CONSUMER_ROLE));
    // product will be the reference for the agreement, the asset can only be linked in the provider
    // side
    if (contractNegotiation.getType() == ContractNegotiation.Type.PROVIDER) {
      String productOfferingId =
          Optional.ofNullable(finalQuote.getExtendableQuoteItem().getLast().getProductOffering())
              .map(org.seamware.tmforum.quote.model.ProductOfferingRefVO::getId)
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "On the provider side, the quote should have a reference to a product offering."));
      productCreateVO.productOffering(new ProductOfferingRefVO().id(productOfferingId));
    }
    ProductVO product = productInventoryApi.createProduct(productCreateVO);

    ExtendableAgreementVO extendableAgreementVO =
        agreementApi
            .findByContractId(contractNegotiation.getContractAgreement().getId())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "An agreement needs to be present at that stage."));
    String previousAgreementStatus = extendableAgreementVO.getStatus();
    extendableAgreementVO.setStatus(AgreementState.AGREED.getValue());
    AgreementItemVO agreementItemVO =
        new AgreementItemVO().addProductItem(new ProductRefVO().id(product.getId()));
    // throw away the old ref, irrelevant by now
    extendableAgreementVO.agreementItem(List.of(agreementItemVO));

    agreementApi.updateAgreement(
        extendableAgreementVO.getId(), tmfEdcMapper.toUpdate(extendableAgreementVO));
    registerAgreementCompensation(
        "revert finalized agreement " + extendableAgreementVO.getId(),
        extendableAgreementVO.getId(),
        previousAgreementStatus);
    ProductOrderStateTypeVO previousOrderState = orderVO.get().getState();
    productOrderApi.updateProductOrder(orderVO.get().getId(), productOrderUpdateVO);
    registerProductOrderCompensation(
        "revert finalized product order " + orderVO.get().getId(),
        orderVO.get().getId(),
        previousOrderState);
  }

  private void cancelAgreements(String negotiationId) {
    agreementApi
        .findByNegotiationId(negotiationId)
        .ifPresent(
            ea -> {
              String previousStatus = ea.getStatus();
              ea.setStatus(AgreementState.REJECTED.getValue());
              agreementApi.updateAgreement(ea.getId(), tmfEdcMapper.toUpdate(ea));
              registerAgreementCompensation(
                  "revert cancelled agreement " + ea.getId(), ea.getId(), previousStatus);
            });
  }

  private void cancelProductOrder(QuoteVO quoteVO) {
    productOrderApi.findByQuoteId(quoteVO.getId()).stream()
        .filter(po -> po.getState() != ProductOrderStateTypeVO.CANCELLED)
        .forEach(
            po -> {
              ProductOrderStateTypeVO previousState = po.getState();
              ProductOrderUpdateVO poUpdate = tmfEdcMapper.toUpdate(po);
              poUpdate.setState(ProductOrderStateTypeVO.CANCELLED);
              productOrderApi.updateProductOrder(po.getId(), poUpdate);
              registerProductOrderCompensation(
                  "revert cancelled product order " + po.getId(), po.getId(), previousState);
            });
  }

  private Optional<ProductOrderVO> getProductOrder(String quoteId) {
    List<ProductOrderVO> productOrderVOS = productOrderApi.findByQuoteId(quoteId);
    if (productOrderVOS.isEmpty()) {
      return Optional.empty();
    }
    if (productOrderVOS.size() > 1) {
      throw new IllegalArgumentException("There should only be one order per quote.");
    }
    return Optional.ofNullable(productOrderVOS.getFirst());
  }

  private List<ExtendableQuoteVO> getQuotes(ContractNegotiation contractNegotiation) {
    return quoteApi.findByNegotiationId(contractNegotiation.getId());
  }

  private Optional<ExtendableQuoteVO> getActiveQuote(
      List<ExtendableQuoteVO> quotes,
      ContractNegotiation contractNegotiation,
      List<ContractNegotiationStates> possibleStates) {
    monitor.debug(
        "Find active quotes for "
            + contractNegotiation.getId()
            + " and states "
            + possibleStates.stream()
                .map(ContractNegotiationStates::name)
                .collect(Collectors.joining(",")));
    List<ExtendableQuoteVO> activeQuoteVOS =
        quotes.stream()
            .filter(
                quoteVO ->
                    possibleStates.contains(TMFEdcMapper.getContractNegotiationState(quoteVO)))
            .toList();
    if (activeQuoteVOS.size() > 1) {
      monitor.warning(
          "Multiple active quotes: "
              + activeQuoteVOS.stream()
                  .map(ExtendableQuoteVO::getId)
                  .collect(Collectors.joining(", ")));
      throw new IllegalArgumentException(
          "There cannot be more than one active quote per negotiation. Negotiation id was "
              + contractNegotiation.getId());
    }
    if (activeQuoteVOS.isEmpty()) {
      return Optional.empty();
    }
    return Optional.ofNullable(activeQuoteVOS.getFirst());
  }

  private void terminateQuote(
      ExtendableQuoteVO orginialQuote,
      ContractNegotiation contractNegotiation,
      QuoteStateTypeVO quoteState) {
    monitor.warning(
        "Terminate existing quote for negotiation "
            + contractNegotiation.getId()
            + " - state "
            + ContractNegotiationStates.from(contractNegotiation.getState()).name());
    boolean isConsumer = contractNegotiation.getType() == ContractNegotiation.Type.CONSUMER;

    // Snapshot previous state for compensation
    QuoteStateTypeVO previousQuoteState = orginialQuote.getState();
    ContractNegotiationState previousNegState = orginialQuote.getContractNegotiationState();

    ExtendableQuoteUpdateVO quoteUpdateVO = tmfEdcMapper.toUpdate(orginialQuote);
    quoteUpdateVO.setState(quoteState);
    // Null out relatedParty to prevent the PATCH from clearing existing entries.
    // See updateQuote for the detailed explanation.
    quoteUpdateVO.setRelatedParty(null);

    ContractNegotiationState contractNegotiationState =
        new ContractNegotiationState()
            .setControlplane(controlplane)
            .setPending(contractNegotiation.isPending())
            .setState(ContractNegotiationStates.TERMINATED.name())
            .setCorrelationId(contractNegotiation.getCorrelationId())
            .setCounterPartyAddress(contractNegotiation.getCounterPartyAddress());
    preserveLeaseFields(orginialQuote.getContractNegotiationState(), contractNegotiationState);

    quoteUpdateVO.setContractNegotiationState(contractNegotiationState);
    quoteUpdateVO.setExtendableQuoteItem(
        offersToQuoteItems(
            contractNegotiation.getContractOffers(), quoteState.getValue(), isConsumer));

    quoteApi.updateQuote(orginialQuote.getId(), quoteUpdateVO);

    registerQuoteCompensation(
        "revert terminated quote " + orginialQuote.getId(),
        orginialQuote.getId(),
        previousQuoteState,
        previousNegState);
  }

  private void createAgreement(ContractNegotiation contractNegotiation) {
    ExtendableAgreementVO agreementVO =
        tmfEdcMapper.toAgreement(
            contractNegotiation.getId(), contractNegotiation.getContractAgreement());
    // refer to the offering, as long as no product exists
    agreementVO.addAgreementItemItem(
        new AgreementItemVO()
            .addTermOrConditionItem(
                new AgreementTermOrConditionVO().description("Under negotiation")));
    agreementVO.setStatus(AgreementState.IN_PROCESS.getValue());
    ExtendableAgreementVO created =
        agreementApi.createAgreement(tmfEdcMapper.toCreate(agreementVO));

    if (created != null) {
      registerAgreementCompensation(
          "reject created agreement " + created.getId(),
          created.getId(),
          AgreementState.REJECTED.getValue());
    }
  }

  private void updateQuote(
      ExtendableQuoteVO originalQuote,
      ContractNegotiation contractNegotiation,
      QuoteStateTypeVO quoteState) {
    monitor.debug(
        "Update existing quote for negotiation "
            + contractNegotiation.getId()
            + " - state "
            + ContractNegotiationStates.from(contractNegotiation.getState()).name());
    boolean isConsumer = contractNegotiation.getType() == ContractNegotiation.Type.CONSUMER;

    // Snapshot previous state for compensation
    QuoteStateTypeVO previousQuoteState = originalQuote.getState();
    ContractNegotiationState previousNegState = originalQuote.getContractNegotiationState();

    ExtendableQuoteUpdateVO quoteUpdateVO = tmfEdcMapper.toUpdate(originalQuote);
    quoteUpdateVO.setState(quoteState);
    // Null out relatedParty so that the PATCH does not overwrite existing entries.
    // The generated QuoteVO initializes relatedParty to an empty ArrayList, so MapStruct
    // copies that empty list even when the TMF API response omitted the field. With
    // NON_NULL serialization, an empty list would be sent as "relatedParty": [], causing
    // the TMF API to clear the related parties that were set during quote creation.
    quoteUpdateVO.setRelatedParty(null);
    ContractNegotiationState contractNegotiationState =
        new ContractNegotiationState()
            .setControlplane(controlplane)
            .setPending(contractNegotiation.isPending())
            .setState(contractNegotiation.stateAsString())
            .setCorrelationId(contractNegotiation.getCorrelationId())
            .setCounterPartyAddress(contractNegotiation.getCounterPartyAddress());
    preserveLeaseFields(originalQuote.getContractNegotiationState(), contractNegotiationState);

    quoteUpdateVO.setContractNegotiationState(contractNegotiationState);
    quoteUpdateVO.setExtendableQuoteItem(
        offersToQuoteItems(
            contractNegotiation.getContractOffers(), quoteState.getValue(), isConsumer));

    quoteApi.updateQuote(originalQuote.getId(), quoteUpdateVO);

    registerQuoteCompensation(
        "revert quote " + originalQuote.getId(),
        originalQuote.getId(),
        previousQuoteState,
        previousNegState);
  }

  private List<ExtendableQuoteItemVO> offersToQuoteItems(
      List<ContractOffer> offers, String negotiationState, boolean isConsumer) {
    monitor.info(String.format("Got %s offers. Is a consumer: %s", offers.size(), isConsumer));
    if (offers.isEmpty()) {
      return List.of();
    }
    List<ExtendableQuoteItemVO> quoteItemVOS = new ArrayList<>();

    // copy the list to not manipulate the original one
    List<ContractOffer> workingOffers = new ArrayList<>(offers);

    // get active offer -> will be the last in the list, all previous are already outdated
    ContractOffer lastOffer = workingOffers.removeLast();

    if (isConsumer) {
      quoteItemVOS.add(tmfEdcMapper.fromConsumerContractOffer(lastOffer, negotiationState));
      workingOffers.stream()
          .map(
              co ->
                  tmfEdcMapper.fromConsumerContractOffer(co, QuoteStateTypeVO.REJECTED.getValue()))
          .forEach(quoteItemVOS::add);
    } else {
      Optional<String> tmfOfferId = getTmfOfferId(lastOffer);
      monitor.info("Offer id is " + tmfOfferId.orElse("empty"));
      quoteItemVOS.add(
          tmfEdcMapper.fromProviderContractOffer(lastOffer, negotiationState, tmfOfferId));
      workingOffers.stream()
          .map(
              co ->
                  tmfEdcMapper.fromProviderContractOffer(
                      co, QuoteStateTypeVO.REJECTED.getValue(), getTmfOfferId(co)))
          .forEach(quoteItemVOS::add);
    }

    return quoteItemVOS;
  }

  private Optional<String> getTmfOfferId(ContractOffer contractOffer) {

    return ContractOfferId.parseId(contractOffer.getId())
        .asOptional()
        .map(ContractOfferId::definitionPart)
        .flatMap(productCatalogApi::getProductOfferingByExternalId)
        .map(ExtendableProductOffering::getId);
  }

  private ExtendableQuoteVO createQuote(
      ContractNegotiation contractNegotiation, QuoteStateTypeVO quoteState) {
    monitor.debug("Create a quote for negotiation");

    ExtendableQuoteCreateVO quoteCreateVO = new ExtendableQuoteCreateVO();

    quoteCreateVO.setExternalId(contractNegotiation.getId());
    quoteCreateVO.setContractNegotiationState(
        new ContractNegotiationState()
            .setPending(contractNegotiation.isPending())
            .setControlplane(controlplane)
            .setState(contractNegotiation.stateAsString())
            .setCorrelationId(contractNegotiation.getCorrelationId())
            .setCounterPartyAddress(contractNegotiation.getCounterPartyAddress()));

    List<ExtendableQuoteItemVO> extendableQuoteItemVOS =
        switch (contractNegotiation.getType()) {
          case PROVIDER ->
              contractNegotiation.getContractOffers().stream()
                  .map(
                      co ->
                          tmfEdcMapper.fromProviderContractOffer(
                              co, quoteState.getValue(), getTmfOfferId(co)))
                  .toList();
          case CONSUMER ->
              contractNegotiation.getContractOffers().stream()
                  .map(co -> tmfEdcMapper.fromConsumerContractOffer(co, quoteState.getValue()))
                  .toList();
        };

    quoteCreateVO.setExtendableQuoteItem(extendableQuoteItemVOS);

    getFromNegotiation(contractNegotiation).stream()
        .map(p -> new RelatedPartyVO().id(p.partyId()).role(p.role()))
        .forEach(quoteCreateVO::addRelatedPartyItem);

    ExtendableQuoteVO created = quoteApi.createQuote(quoteCreateVO);

    if (created != null) {
      registerQuoteCompensation(
          "cancel created quote " + created.getId(),
          created.getId(),
          QuoteStateTypeVO.CANCELLED,
          null);
    }

    return created;
  }

  private List<PartyWithRole> getFromNegotiation(ContractNegotiation contractNegotiation) {
    if (roleFromNegotiation(contractNegotiation.getType()).equals(PROVIDER_ROLE)) {
      return List.of(
          new PartyWithRole(
              participantResolver.getTmfId(contractNegotiation.getCounterPartyId()), CONSUMER_ROLE),
          new PartyWithRole(participantResolver.getTmfId(participantId), PROVIDER_ROLE));
    } else {
      return List.of(
          new PartyWithRole(
              participantResolver.getTmfId(contractNegotiation.getCounterPartyId()), PROVIDER_ROLE),
          new PartyWithRole(participantResolver.getTmfId(participantId), CONSUMER_ROLE));
    }
  }

  public String roleFromNegotiation(ContractNegotiation.Type type) {
    return switch (type) {
      case CONSUMER -> CONSUMER_ROLE;
      case PROVIDER -> PROVIDER_ROLE;
    };
  }

  private void registerQuoteCompensation(
      String description,
      String quoteId,
      QuoteStateTypeVO revertToState,
      ContractNegotiationState revertToNegState) {
    SagaContext saga = transactionContext.currentSaga();
    if (saga == null) {
      return;
    }
    saga.addCompensation(
        description,
        () -> {
          ExtendableQuoteUpdateVO revert = new ExtendableQuoteUpdateVO();
          revert.setState(revertToState);
          if (revertToNegState != null) {
            revert.setContractNegotiationState(revertToNegState);
          }
          quoteApi.updateQuote(quoteId, revert);
        });
  }

  private void registerAgreementCompensation(
      String description, String agreementId, String revertToStatus) {
    SagaContext saga = transactionContext.currentSaga();
    if (saga == null) {
      return;
    }
    saga.addCompensation(
        description,
        () -> {
          ExtendableAgreementUpdateVO revert = new ExtendableAgreementUpdateVO();
          revert.setStatus(revertToStatus);
          nullAgreementListFields(revert);
          agreementApi.updateAgreement(agreementId, revert);
        });
  }

  private void registerProductOrderCompensation(
      String description, String orderId, ProductOrderStateTypeVO revertToState) {
    SagaContext saga = transactionContext.currentSaga();
    if (saga == null) {
      return;
    }
    saga.addCompensation(
        description,
        () -> {
          ProductOrderUpdateVO revert = new ProductOrderUpdateVO();
          revert.setState(revertToState);
          nullProductOrderListFields(revert);
          productOrderApi.updateProductOrder(orderId, revert);
        });
  }

  private void preserveLeaseFields(
      ContractNegotiationState source, ContractNegotiationState target) {
    if (source != null) {
      target.setLeased(source.isLeased());
      target.setLeasedBy(source.getLeasedBy());
      target.setLeaseExpiry(source.getLeaseExpiry());
    }
  }

  /**
   * Nulls out all list fields on an {@link ExtendableAgreementUpdateVO} so that Jackson's {@code
   * NON_NULL} serialization omits them from the PATCH. The generated {@code AgreementUpdateVO}
   * initializes these to {@code new ArrayList<>()}, which would be serialized as empty arrays and
   * cause the TMF API to clear existing data or return 400.
   */
  public static void nullAgreementListFields(ExtendableAgreementUpdateVO update) {
    update.setAgreementAuthorization(null);
    update.setAgreementItem(null);
    update.setAssociatedAgreement(null);
    update.setCharacteristic(null);
    update.setEngagedParty(null);
  }

  /**
   * Nulls out all list fields on a {@link ProductOrderUpdateVO}. Same reason as {@link
   * #nullAgreementListFields}.
   */
  static void nullProductOrderListFields(ProductOrderUpdateVO update) {
    update.setAgreement(null);
    update.setChannel(null);
    update.setNote(null);
    update.setOrderTotalPrice(null);
    update.setPayment(null);
    update.setProductOfferingQualification(null);
    update.setProductOrderItem(null);
    update.setQuote(null);
    update.setRelatedParty(null);
  }

  private record PartyWithRole(String partyId, String role) {}
}
