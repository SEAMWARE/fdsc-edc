package org.seamware.edc.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.connector.controlplane.contract.spi.ContractOfferId;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractOffer;
import org.eclipse.edc.spi.entity.StatefulEntity;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.eclipse.edc.spi.persistence.Lease;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.types.domain.callback.CallbackAddress;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.seamware.edc.AutomaticUnlockingLockManager;
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

import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Comparator.comparingLong;
import static org.seamware.edc.tmf.ParticipantResolver.CONSUMER_ROLE;
import static org.seamware.edc.tmf.ParticipantResolver.PROVIDER_ROLE;

public class TMFBackedContractNegotiationStore implements ContractNegotiationStore {

    private static final Duration DEFAULT_LEASE_TIME = Duration.ofSeconds(60);

    private final Monitor monitor;
    private final ObjectMapper objectMapper;
    private final QuoteApiClient quoteApi;
    private final AgreementApiClient agreementApi;
    private final ProductOrderApiClient productOrderApi;
    private final ProductCatalogApiClient productCatalogApi;
    private final ProductInventoryApiClient productInventoryApi;
    private final ParticipantResolver participantResolver;
    private final Clock clock;
    private final TMFEdcMapper tmfEdcMapper;
    private final String participantId;
    private final String controlplane;

    private final CriterionOperatorRegistry criterionOperatorRegistry;

    private final String lockId;
    private final AutomaticUnlockingLockManager lockManager;
    private final Map<String, Lease> leases = new HashMap<>();

    public TMFBackedContractNegotiationStore(Monitor monitor, ObjectMapper objectMapper, QuoteApiClient quoteApi, AgreementApiClient agreementApi, ProductOrderApiClient productOrderApi, ProductCatalogApiClient productCatalogApi, ProductInventoryApiClient productInventoryApi, ParticipantResolver participantResolver, TMFEdcMapper tmfEdcMapper, String participantId, Clock clock, String controlplane, CriterionOperatorRegistry criterionOperatorRegistry) {
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
        this.clock = clock;
        this.controlplane = controlplane;
        this.criterionOperatorRegistry = criterionOperatorRegistry;
        this.lockId = UUID.randomUUID().toString();
        this.lockManager = new AutomaticUnlockingLockManager(new ReentrantReadWriteLock(true), 10500, monitor);
    }

    @Override
    public @Nullable ContractAgreement findContractAgreement(String s) {
        monitor.debug("Find agreement " + s);
        return agreementApi.findByContractId(s)
                // only "AGREED" agreements can be considered as agreement in the terms of DSP
                .filter(agreement -> agreement.getStatus() != null)
                .filter(agreement -> agreement.getStatus().equals(AgreementState.AGREED.getValue()))
                .map(tmfEdcMapper::toContractAgreement)
                .orElse(null);
    }


    @Override
    public StoreResult<Void> deleteById(String s) {
        throw new UnsupportedOperationException("Deletion is currently not supported.");
    }

    @Override
    public @NotNull Stream<ContractNegotiation> queryNegotiations(QuerySpec querySpec) {
        Map<String, List<ExtendableQuoteVO>> negotiationQuotes = new HashMap<>();

        quoteApi.getQuotes(querySpec.getOffset(), querySpec.getLimit())
                .stream()
                // only those that this controlplane is responsible for
                .filter(eqv -> eqv.getContractNegotiationState().getControlplane().equals(controlplane))
                .forEach(eq -> {
                    String negotiationId = eq.getExternalId();
                    if (negotiationQuotes.containsKey(negotiationId)) {
                        negotiationQuotes.get(negotiationId).add(eq);
                    } else {
                        List<ExtendableQuoteVO> quotes = new ArrayList<>();
                        quotes.add(eq);
                        negotiationQuotes.put(negotiationId, quotes);
                    }
                });
        return negotiationQuotes.entrySet()
                .stream()
                .map(e -> tmfEdcMapper.toContractNegotiation(e.getValue(), agreementApi, participantResolver, participantId));
    }

    @Override
    public @NotNull Stream<ContractAgreement> queryAgreements(QuerySpec querySpec) {
        monitor.debug("Query agreements " + querySpec.toString());
        return getFilteredStream(querySpec.getFilterExpression(), agreementApi.getAgreements(querySpec.getOffset(), querySpec.getLimit()))
                .skip(querySpec.getOffset())
                .limit(querySpec.getLimit());
    }

    private Stream<ContractAgreement> getFilteredStream(List<Criterion> criteria, List<ExtendableAgreementVO> agreementVOS) {
        monitor.debug("getFilteredStream");
        Predicate<ContractAgreement> filterPredicate = criteria.stream()
                .map(criterionOperatorRegistry::<ContractAgreement>toPredicate)
                .reduce(x -> true, Predicate::and);
        return agreementVOS
                .stream()
                // only "AGREED" agreements can be considered as agreement in the terms of DSP
                .filter(agreement -> agreement.getStatus() != null)
                .filter(agreement -> agreement.getStatus().equals(AgreementState.AGREED.getValue()))
                .map(tmfEdcMapper::toContractAgreement)
                .filter(filterPredicate);
    }

    @Override
    public @Nullable ContractNegotiation findById(String s) {
        List<ExtendableQuoteVO> contractNegotiations = quoteApi.findByNegotiationId(s)
                .stream()
                // only those that this controlplane is responsible for
                .filter(eqv -> eqv.getContractNegotiationState().getControlplane().equals(controlplane))
                .toList();
        return tmfEdcMapper.toContractNegotiation(new ArrayList<>(contractNegotiations), agreementApi, participantResolver, participantId);
    }

    @Override
    public @NotNull List<ContractNegotiation> nextNotLeased(int i, Criterion... criteria) {
        try {
            return lockManager.writeLock(() -> {
                Predicate<ContractNegotiation> filterPredicate = Arrays.stream(criteria)
                        .map(criterionOperatorRegistry::<ContractNegotiation>toPredicate)
                        .reduce(x -> true, Predicate::and);
                // we want no duplicates
                Set<String> negotiationIds = new HashSet<>();
                int offset = 0;
                boolean moreQuotesAvailable = true;
                int limit = 100;
                // a negotiation might consist of multiple quotes, thus first fetch the ids and then reduce
                // the multi calls need to be improved, current solution is bad performacne
                while (moreQuotesAvailable && negotiationIds.size() < i) {
                    List<ExtendableQuoteVO> extendableQuoteVOS = quoteApi.getQuotes(offset, limit);
                    extendableQuoteVOS
                            .stream()
                            // only those that this controlplane is responsible for
                            .filter(eqv -> eqv.getContractNegotiationState().getControlplane().equals(controlplane))
                            .map(ExtendableQuoteVO::getExternalId)
                            .forEach(negotiationIds::add);
                    moreQuotesAvailable = extendableQuoteVOS.size() == limit;
                    offset += limit;
                }
                List<ContractNegotiation> contractNegotiations;
                contractNegotiations = negotiationIds.stream()
                        .map(quoteApi::findByNegotiationId)
                        .map(extendableQuoteVOS -> tmfEdcMapper.toContractNegotiation(extendableQuoteVOS, agreementApi, participantResolver, participantId))
                        .filter(filterPredicate)
                        .filter(e -> !isLeased(e.getId()))
                        .map(ContractNegotiation.class::cast)
                        .sorted(comparingLong(StatefulEntity::getStateTimestamp))
                        .limit(i)
                        .toList();

                return contractNegotiations.stream()
                        .filter(cn -> {
                            try {
                                acquireLease(cn.getId());
                                return true;
                            } catch (Exception e) {
                                monitor.info(String.format("Was not able to lease %s", cn.getId()), e);
                                return false;
                            }
                        }).toList();
            });
        } catch (Exception e) {
            monitor.warning("Failed to get", e);
            throw new EdcPersistenceException(e);
        }
    }

    @Override
    public StoreResult<ContractNegotiation> findByIdAndLease(String s) {
        try {
            return lockManager.writeLock(() -> {
                monitor.info("Find by Id and Lease " + s);
                ContractNegotiation contractNegotiation = findById(s);
                if (contractNegotiation == null) {
                    return StoreResult.notFound(String.format("Negotiation %s does not exist.", s));
                }
                try {
                    acquireLease(s);
                    return StoreResult.success(contractNegotiation);
                } catch (IllegalStateException e) {
                    return StoreResult.alreadyLeased(String.format("%s is already leased.", s));
                }
            });
        } catch (Exception e) {
            monitor.warning("Failed to find", e);
            throw new EdcPersistenceException(e);
        }
    }

    @Override
    public void save(ContractNegotiation contractNegotiation) {
        persistContractNegotiation(contractNegotiation);
    }

    private void persistContractNegotiation(ContractNegotiation contractNegotiation) {
        monitor.severe("save " + ContractNegotiationStates.from(contractNegotiation.getState()).name() + " pending " + contractNegotiation.isPending() + " " + contractNegotiation.getType() + " id " + contractNegotiation);
        try {
            acquireLease(contractNegotiation.getId());
            ContractNegotiationStates negotiationState = ContractNegotiationStates.from(contractNegotiation.getState());
            switch (negotiationState) {
                case VERIFIED, VERIFYING -> {

                    Optional<ExtendableQuoteVO> extendableQuoteVO = getActiveQuote(getQuotes(contractNegotiation), contractNegotiation, List.of(ContractNegotiationStates.AGREED, ContractNegotiationStates.VERIFYING, ContractNegotiationStates.VERIFIED));

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
                    getFromNegotiation(contractNegotiation)
                            .stream()
                            .map(p -> new org.seamware.tmforum.productorder.model.RelatedPartyVO().id(p.partyId()).role(p.role()))
                            .forEach(pr -> {
                                if (pr.getRole().equals(CONSUMER_ROLE)) {
                                    productOrderCreateVO.addRelatedPartyItem(pr);
                                    // Customer is the role expected by the Contract-Management
                                    productOrderCreateVO.addRelatedPartyItem(new org.seamware.tmforum.productorder.model.RelatedPartyVO().id(pr.getId()).role("Customer"));
                                } else {
                                    productOrderCreateVO.addRelatedPartyItem(pr);
                                }
                            });
                    productOrderCreateVO.quote(List.of(new QuoteRefVO().id(quoteVO.getId())));

                    productOrderApi.createProductOrder(productOrderCreateVO);
                    updateQuote(quoteVO, contractNegotiation, quoteVO.getState());
                }
                case FINALIZING, FINALIZED -> {
                    Optional<ExtendableQuoteVO> extendableQuoteVO = getActiveQuote(getQuotes(contractNegotiation), contractNegotiation, List.of(ContractNegotiationStates.VERIFIED, ContractNegotiationStates.FINALIZING, ContractNegotiationStates.FINALIZED));

                    if (extendableQuoteVO.isEmpty()) {
                        throw new IllegalArgumentException("In state verified, an accepted Quote needs to exist.");
                    }
                    finalizeOrder(contractNegotiation, extendableQuoteVO.get());

                }
                case ACCEPTED, ACCEPTING -> {
                    List<ExtendableQuoteVO> quotes = getQuotes(contractNegotiation);
                    Optional<ExtendableQuoteVO> activeQuote = getActiveQuote(quotes, contractNegotiation, List.of(ContractNegotiationStates.OFFERED, ContractNegotiationStates.ACCEPTING, ContractNegotiationStates.ACCEPTED));
                    Optional<ExtendableQuoteVO> terminatingQuote = quotes.stream().filter(q -> tmfEdcMapper.getContractNegotiationState(q) == ContractNegotiationStates.TERMINATING).findAny();
                    if (activeQuote.isEmpty() && terminatingQuote.isEmpty()) {
                        monitor.info("Create quote in accepted - existing quotes " + objectMapper.writeValueAsString(quotes));
                        // if the first offer is directly accepted, no quote might exist
                        ExtendableQuoteVO quoteVO = createQuote(contractNegotiation, QuoteStateTypeVO.ACCEPTED);
                        // we need to create and update, since state changes are only able by patch
                        updateQuote(quoteVO, contractNegotiation, QuoteStateTypeVO.ACCEPTED);
                    } else if (activeQuote.isPresent()) {
                        updateQuote(activeQuote.get(), contractNegotiation, QuoteStateTypeVO.ACCEPTED);
                    } else {
                        // negotiation is currently in terminating, dont do anything.
                    }
                }
                case AGREEING, AGREED -> {
                    Optional<ExtendableQuoteVO> activeQuote = getActiveQuote(getQuotes(contractNegotiation), contractNegotiation, List.of(ContractNegotiationStates.AGREEING, ContractNegotiationStates.AGREED, ContractNegotiationStates.ACCEPTED, ContractNegotiationStates.REQUESTED));
                    if (activeQuote.isEmpty()) {
                        // we cannot throw something here, since it somehow kills an internal edc worker...
                        monitor.warning(String.format("Cannot save transition to %s for %s.", negotiationState.name(), contractNegotiation.getId()));
                    } else {
                        updateQuote(activeQuote.get(), contractNegotiation, QuoteStateTypeVO.ACCEPTED);
                        if (contractNegotiation.getContractAgreement() != null) {
                            createAgreement(contractNegotiation);
                        }
                    }
                }
                case OFFERING, OFFERED -> {
                    Optional<ExtendableQuoteVO> activeQuote = getActiveQuote(getQuotes(contractNegotiation), contractNegotiation, List.of(ContractNegotiationStates.REQUESTED, ContractNegotiationStates.INITIAL, ContractNegotiationStates.OFFERING, ContractNegotiationStates.OFFERED));
                    if (activeQuote.isEmpty()) {
                        monitor.info("Create quote in offered");
                        ExtendableQuoteVO quoteVO = createQuote(contractNegotiation, QuoteStateTypeVO.APPROVED);
                        // we need to create and update, since state changes are only able by patch
                        updateQuote(quoteVO, contractNegotiation, QuoteStateTypeVO.APPROVED);
                    } else {
                        updateQuote(activeQuote.get(), contractNegotiation, QuoteStateTypeVO.APPROVED);
                    }
                }
                case REQUESTED -> {
                    List<ExtendableQuoteVO> quotes = getQuotes(contractNegotiation);
                    Optional<ExtendableQuoteVO> activeQuote = getActiveQuote(quotes, contractNegotiation, List.of(ContractNegotiationStates.INITIAL, ContractNegotiationStates.OFFERED, ContractNegotiationStates.REQUESTED, ContractNegotiationStates.REQUESTING));
                    if (activeQuote.isEmpty()) {
                        monitor.info("Create quote in requested - existing quotes " + objectMapper.writeValueAsString(quotes));
                        // in case of counter-offers, we will have a quote in approved or accepted and need to cancel them
                        getActiveQuote(quotes, contractNegotiation, List.of(ContractNegotiationStates.OFFERED))
                                .ifPresent(q -> terminateQuote(q, contractNegotiation, QuoteStateTypeVO.CANCELLED));
                        ExtendableQuoteVO quoteVO = createQuote(contractNegotiation, QuoteStateTypeVO.IN_PROGRESS);
                        // we need to create and update, since state changes are only able by patch
                        updateQuote(quoteVO, contractNegotiation, QuoteStateTypeVO.APPROVED);
                    } else {
                        updateQuote(activeQuote.get(), contractNegotiation, QuoteStateTypeVO.APPROVED);
                    }
                }
                case INITIAL, REQUESTING -> {

                    List<ExtendableQuoteVO> quotes = getQuotes(contractNegotiation);
                    Optional<ExtendableQuoteVO> activeQuote = getActiveQuote(quotes, contractNegotiation, List.of(ContractNegotiationStates.INITIAL, ContractNegotiationStates.REQUESTING));
                    if (activeQuote.isEmpty()) {
                        // in case of counter-offers, we will have a quote in approved or accepted and need to cancel them
                        getActiveQuote(quotes, contractNegotiation, List.of(ContractNegotiationStates.OFFERING, ContractNegotiationStates.OFFERED))
                                .ifPresent(q -> terminateQuote(q, contractNegotiation, QuoteStateTypeVO.CANCELLED));
                        // create the new quote
                        createQuote(contractNegotiation, QuoteStateTypeVO.IN_PROGRESS);
                    } else {
                        updateQuote(activeQuote.get(), contractNegotiation, QuoteStateTypeVO.IN_PROGRESS);
                    }
                }
                case TERMINATED, TERMINATING -> {
                    quoteApi.findByNegotiationId(contractNegotiation.getId())
                            .forEach(cn -> {
                                updateQuote(cn, contractNegotiation, QuoteStateTypeVO.CANCELLED);
                                cancelProductOrder(cn);
                            });
                    // TODO: check to reject product order
                }
                default -> monitor.warning("State not yet supported " + negotiationState);
            }
        } catch (Exception e) {
            monitor.warning("Failed to save", e);
            throw new EdcPersistenceException(String.format("Failed to save negotiation %s.", contractNegotiation.getId()), e);
        } finally {
            // always give up the lock
            freeLease(contractNegotiation.getId(), "Finally saved.");
        }
    }


    public void finalizeOrder(ContractNegotiation contractNegotiation, ExtendableQuoteVO finalQuote) {
        try {
            monitor.warning("FINALIZING Negotiation " + objectMapper.writeValueAsString(contractNegotiation));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        Optional<ProductOrderVO> orderVO = getProductOrder(finalQuote.getId());
        if (orderVO.isEmpty()) {
            throw new IllegalArgumentException("When finalizing, an order should already exist.");
        }

        updateQuote(finalQuote, contractNegotiation, QuoteStateTypeVO.ACCEPTED);
        ProductOrderUpdateVO productOrderUpdateVO = tmfEdcMapper.toUpdate(orderVO.get());
        productOrderUpdateVO.setState(ProductOrderStateTypeVO.COMPLETED);

        ExtendableProductCreate productCreateVO = new ExtendableProductCreate();
        String providerId = participantResolver.getTmfId(contractNegotiation.getContractAgreement().getProviderId());
        String consumerId = participantResolver.getTmfId(contractNegotiation.getContractAgreement().getConsumerId());
        productCreateVO
                .setExternalId(contractNegotiation.getContractAgreement().getAssetId())
                .status(ProductStatusTypeVO.ACTIVE)
                .addRelatedPartyItem(new org.seamware.tmforum.productinventory.model.RelatedPartyVO().id(providerId).role(PROVIDER_ROLE))
                .addRelatedPartyItem(new org.seamware.tmforum.productinventory.model.RelatedPartyVO().id(consumerId).role(CONSUMER_ROLE));
        // product will be the reference for the agreement, the asset can only be linked in the provider side
        if (contractNegotiation.getType() == ContractNegotiation.Type.PROVIDER) {
            String productOfferingId = Optional.ofNullable(
                            finalQuote.getExtendableQuoteItem().getLast()
                                    .getProductOffering())
                    .map(org.seamware.tmforum.quote.model.ProductOfferingRefVO::getId)
                    .orElseThrow(() -> new IllegalArgumentException("On the provider side, the quote should have a reference to a product offering."));
            productCreateVO.productOffering(new ProductOfferingRefVO().id(productOfferingId));
        }
        ProductVO product = productInventoryApi.createProduct(productCreateVO);

        ExtendableAgreementVO extendableAgreementVO = agreementApi.findByContractId(contractNegotiation.getContractAgreement().getId())
                .orElseThrow(() -> new IllegalArgumentException("An agreement needs to be present at that stage."));
        extendableAgreementVO.setStatus(AgreementState.AGREED.getValue());
        AgreementItemVO agreementItemVO = new AgreementItemVO()
                .addProductItem(new ProductRefVO().id(product.getId()));
        // throw away the old ref, irrelevant by now
        extendableAgreementVO.agreementItem(List.of(agreementItemVO));

        agreementApi.updateAgreement(extendableAgreementVO.getId(), tmfEdcMapper.toUpdate(extendableAgreementVO));
        productOrderApi.updateProductOrder(orderVO.get().getId(), productOrderUpdateVO);
    }

    private void cancelProductOrder(QuoteVO quoteVO) {
        productOrderApi.findByQuoteId(quoteVO.getId())
                .forEach(po -> {
                    ProductOrderUpdateVO poUpdate = tmfEdcMapper.toUpdate(po);
                    poUpdate.setState(ProductOrderStateTypeVO.CANCELLED);
                    productOrderApi.updateProductOrder(po.getId(), poUpdate);
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

    private Optional<ExtendableQuoteVO> getActiveQuote(List<ExtendableQuoteVO> quotes, ContractNegotiation contractNegotiation, List<ContractNegotiationStates> possibleStates) {
        monitor.info("Find active quotes for " + contractNegotiation.getId() + " and states " + possibleStates.stream().map(ContractNegotiationStates::name).collect(Collectors.joining(",")));
        List<ExtendableQuoteVO> activeQuoteVOS = quotes
                .stream()
                .filter(quoteVO -> possibleStates.contains(tmfEdcMapper.getContractNegotiationState(quoteVO)))
                .toList();
        if (activeQuoteVOS.size() > 1) {
            try {
                monitor.warning("Multiple active quotes: " + objectMapper.writeValueAsString(activeQuoteVOS));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            throw new IllegalArgumentException("There cannot be more than one active quote per negotiation. Negotiation id was " + contractNegotiation.getId());
        }
        if (activeQuoteVOS.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(activeQuoteVOS.getFirst());
    }

    private void terminateQuote(ExtendableQuoteVO orginialQuote, ContractNegotiation contractNegotiation, QuoteStateTypeVO quoteState) {
        monitor.warning("Terminate existing quote for negotiation " + contractNegotiation.getId() + " - state " + ContractNegotiationStates.from(contractNegotiation.getState()).name());
        boolean isConsumer = contractNegotiation.getType() == ContractNegotiation.Type.CONSUMER;

        ExtendableQuoteUpdateVO quoteUpdateVO = tmfEdcMapper.toUpdate(orginialQuote);
        quoteUpdateVO.setState(quoteState);

        ContractNegotiationState contractNegotiationState = new ContractNegotiationState()
                .setControlplane(controlplane)
                .setPending(contractNegotiation.isPending())
                .setState(ContractNegotiationStates.TERMINATED.name())
                .setCorrelationId(contractNegotiation.getCorrelationId())
                .setCounterPartyAddress(contractNegotiation.getCounterPartyAddress());

        quoteUpdateVO.setContractNegotiationState(contractNegotiationState);
        quoteUpdateVO.setExtendableQuoteItem(offersToQuoteItems(contractNegotiation.getContractOffers(), quoteState.getValue(), isConsumer));

        quoteApi.updateQuote(orginialQuote.getId(), quoteUpdateVO);
    }

    private void createAgreement(ContractNegotiation contractNegotiation) {
        ExtendableAgreementVO agreementVO = tmfEdcMapper.toAgreement(contractNegotiation.getId(), contractNegotiation.getContractAgreement());
        // refer to the offering, as long as no product exists
        agreementVO.addAgreementItemItem(new AgreementItemVO().addTermOrConditionItem(new AgreementTermOrConditionVO()
                .description("Under negotiation")));
        agreementVO.setStatus(AgreementState.IN_PROCESS.getValue());
        agreementApi.createAgreement(tmfEdcMapper.toCreate(agreementVO));
    }

    private void updateQuote(ExtendableQuoteVO originalQuote, ContractNegotiation contractNegotiation, QuoteStateTypeVO quoteState) {
        monitor.warning("Update existing quote for negotiation " + contractNegotiation.getId() + " - state " + ContractNegotiationStates.from(contractNegotiation.getState()).name());
        boolean isConsumer = contractNegotiation.getType() == ContractNegotiation.Type.CONSUMER;

        ExtendableQuoteUpdateVO quoteUpdateVO = tmfEdcMapper.toUpdate(originalQuote);
        quoteUpdateVO.setState(quoteState);
        ContractNegotiationState contractNegotiationState = new ContractNegotiationState()
                .setControlplane(controlplane)
                .setPending(contractNegotiation.isPending())
                .setState(contractNegotiation.stateAsString())
                .setCorrelationId(contractNegotiation.getCorrelationId())
                .setCounterPartyAddress(contractNegotiation.getCounterPartyAddress());

        quoteUpdateVO.setContractNegotiationState(contractNegotiationState);
        quoteUpdateVO.setExtendableQuoteItem(offersToQuoteItems(contractNegotiation.getContractOffers(), quoteState.getValue(), isConsumer));

        quoteApi.updateQuote(originalQuote.getId(), quoteUpdateVO);
    }

    private List<ExtendableQuoteItemVO> offersToQuoteItems(List<ContractOffer> offers, String negotiationState, boolean isConsumer) {
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
                    .map(co -> tmfEdcMapper.fromConsumerContractOffer(co, QuoteStateTypeVO.REJECTED.getValue()))
                    .forEach(quoteItemVOS::add);
        } else {
            Optional<String> tmfOfferId = getTmfOfferId(lastOffer);
            monitor.info("Offer id is " + tmfOfferId.orElse("empty"));
            quoteItemVOS.add(tmfEdcMapper.fromProviderContractOffer(lastOffer, negotiationState, tmfOfferId));
            workingOffers.stream()
                    .map(co -> tmfEdcMapper.fromProviderContractOffer(co, QuoteStateTypeVO.REJECTED.getValue(), getTmfOfferId(co)))
                    .forEach(quoteItemVOS::add);
        }

        return quoteItemVOS;
    }

    private Optional<String> getTmfOfferId(ContractOffer contractOffer) {

        return ContractOfferIdParser.parseId(contractOffer.getId())
                .asOptional()
                .map(ContractOfferIdParser.ContractOfferWithUid::contractOfferId)
                .map(ContractOfferId::definitionPart)
                .flatMap(productCatalogApi::getProductOfferingByExternalId)
                .map(ExtendableProductOffering::getId);
    }

    private ExtendableQuoteVO createQuote(ContractNegotiation contractNegotiation, QuoteStateTypeVO quoteState) {
        monitor.warning("Create a quote for negotiation");

        ExtendableQuoteCreateVO quoteCreateVO = new ExtendableQuoteCreateVO();

        quoteCreateVO.setExternalId(contractNegotiation.getId());
        quoteCreateVO.setContractNegotiationState(new ContractNegotiationState().setPending(contractNegotiation.isPending())
                .setControlplane(controlplane)
                .setState(contractNegotiation.stateAsString())
                .setCorrelationId(contractNegotiation.getCorrelationId())
                .setCounterPartyAddress(contractNegotiation.getCounterPartyAddress()));

        List<ExtendableQuoteItemVO> extendableQuoteItemVOS =
                switch (contractNegotiation.getType()) {
                    case PROVIDER -> contractNegotiation.getContractOffers()
                            .stream().map(co -> tmfEdcMapper.fromProviderContractOffer(co, quoteState.getValue(), getTmfOfferId(co)))
                            .toList();
                    case CONSUMER -> contractNegotiation.getContractOffers()
                            .stream().map(co -> tmfEdcMapper.fromConsumerContractOffer(co, quoteState.getValue()))
                            .toList();
                };

        quoteCreateVO.setExtendableQuoteItem(extendableQuoteItemVOS);

        getFromNegotiation(contractNegotiation)
                .stream()
                .map(p -> new RelatedPartyVO().id(p.partyId()).role(p.role()))
                .forEach(quoteCreateVO::addRelatedPartyItem);
        try {
            monitor.warning("Create quote " + objectMapper.writeValueAsString(quoteCreateVO));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return quoteApi.createQuote(quoteCreateVO);
    }

    private List<PartyWithRole> getFromNegotiation(ContractNegotiation contractNegotiation) {
        if (roleFromNegotiation(contractNegotiation.getType()).equals(PROVIDER_ROLE)) {
            return List.of(
                    new PartyWithRole(participantResolver.getTmfId(contractNegotiation.getCounterPartyId()), CONSUMER_ROLE),
                    new PartyWithRole(participantResolver.getTmfId(participantId), PROVIDER_ROLE));
        } else {
            return List.of(
                    new PartyWithRole(participantResolver.getTmfId(contractNegotiation.getCounterPartyId()), PROVIDER_ROLE),
                    new PartyWithRole(participantResolver.getTmfId(participantId), CONSUMER_ROLE));
        }
    }

    public String roleFromNegotiation(ContractNegotiation.Type type) {
        return switch (type) {
            case CONSUMER -> CONSUMER_ROLE;
            case PROVIDER -> PROVIDER_ROLE;
        };
    }

    public void acquireLease(String id, String lockId, Duration leaseTime) {
        if (!isLeased(id) || isLeasedBy(id, lockId)) {
            monitor.info("Acquire lease " + id + " - " + lockId);
            leases.put(id, new Lease(lockId, clock.millis(), leaseTime.toMillis()));
        } else {
            throw new IllegalStateException("Cannot acquire lease, is already leased by someone else!");
        }
    }

    public boolean isLeasedBy(String id, String lockId) {
        synchronized (leases) {
            return isLeased(id) && leases.get(id).getLeasedBy().equals(lockId);
        }
    }

    private void freeLease(String id, String reason) {
        synchronized (leases) {
            monitor.info("Free lease " + id + " because " + reason);
            leases.remove(id);
        }
    }

    private void acquireLease(String id) {
        acquireLease(id, lockId, DEFAULT_LEASE_TIME);
    }

    private boolean isLeased(String id) {
        synchronized (leases) {
            return leases.containsKey(id) && !leases.get(id).isExpired(clock.millis());
        }
    }

    private record PartyWithRole(String partyId, String role) {
    }

}
