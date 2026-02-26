package org.seamware.edc.store;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractOffer;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreFailure;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.util.concurrency.LockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.seamware.edc.SchemaBaseUriHolder;
import org.seamware.edc.domain.AgreementState;
import org.seamware.edc.domain.ContractNegotiationState;
import org.seamware.edc.domain.ExtendableAgreementVO;
import org.seamware.edc.domain.ExtendableQuoteVO;
import org.seamware.edc.tmf.*;
import org.seamware.tmforum.quote.model.QuoteStateTypeVO;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// abstract to not be executed twice
public abstract class TMFBackedContractNegotiationStoreTest extends AbstractStoreTest {

    protected static final String TEST_AGREEMENT_ID = "test-agreement";
    protected static final String TEST_PROVIDER_ID = "provider-id";
    protected static final String TMF_TEST_PROVIDER_ID = "tmf-provider-id";
    protected static final String TEST_CONSUMER_ID = "consumer-id";
    protected static final String TMF_TEST_CONSUMER_ID = "tmf-consumer-id";
    protected static final String TEST_ASSET_ID = "asset-id";
    protected static final String TEST_POLICY_ID = "policy-id";
    protected static final String TEST_COUNTER_PARTY_ID = "counter-party-id";
    protected static final String TEST_COUNTER_PARTY_ADDRESS = "http://counter.party";
    protected static final String TEST_PROTOCOL = "v1";
    protected static final String TEST_PARTICIPANT_ID = "test-participant";
    protected static final String TEST_CONTROL_PLANE_ID = "test-control-plane";

    protected QuoteApiClient quoteApiClient;
    protected AgreementApiClient agreementApiClient;
    protected ProductOrderApiClient productOrderApiClient;
    protected ProductCatalogApiClient productCatalogApiClient;
    protected ProductInventoryApiClient productInventoryApiClient;
    protected ParticipantResolver participantResolver;
    protected TMFEdcMapper tmfEdcMapper;
    protected CriterionOperatorRegistry criterionOperatorRegistry;
    protected LeaseHolder leaseHolder;
    protected LockManager lockManager;
    protected ObjectMapper objectMapper;
    protected TMFBackedContractNegotiationStore tmfBackedContractNegotiationStore;

    @BeforeEach
    public void setup() {

        SchemaBaseUriHolder.configure(URI.create("http://my-base.schema"));

        objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.addMixIn(Policy.Builder.class, UnknownPropertyMixin.class);
        objectMapper.registerModule(new JavaTimeModule());

        quoteApiClient = mock(QuoteApiClient.class);
        agreementApiClient = mock(AgreementApiClient.class);
        productOrderApiClient = mock(ProductOrderApiClient.class);
        productCatalogApiClient = mock(ProductCatalogApiClient.class);
        productInventoryApiClient = mock(ProductInventoryApiClient.class);
        participantResolver = mock(ParticipantResolver.class);
        tmfEdcMapper = mock(TMFEdcMapper.class);
        criterionOperatorRegistry = mock(CriterionOperatorRegistry.class);
        leaseHolder = mock(LeaseHolder.class);
        lockManager = mock(LockManager.class);

        tmfBackedContractNegotiationStore = new TMFBackedContractNegotiationStore(
                mock(Monitor.class), objectMapper,
                quoteApiClient, agreementApiClient,
                productOrderApiClient, productCatalogApiClient,
                productInventoryApiClient, participantResolver,
                tmfEdcMapper, TEST_PARTICIPANT_ID,
                TEST_CONTROL_PLANE_ID, criterionOperatorRegistry,
                leaseHolder, lockManager);
    }

    @Test
    public void testFindContractAgreement_success() {
        ExtendableAgreementVO extendableAgreementVO = new ExtendableAgreementVO()
                .setExternalId(TEST_AGREEMENT_ID);
        extendableAgreementVO.setStatus(AgreementState.AGREED.getValue());
        when(agreementApiClient.findByContractId(eq(TEST_AGREEMENT_ID))).thenReturn(Optional.of(extendableAgreementVO));
        when(tmfEdcMapper.toContractAgreement(eq(extendableAgreementVO))).thenReturn(getTestContractAgreement());

        assertEquals(getTestContractAgreement(), tmfBackedContractNegotiationStore.findContractAgreement(TEST_AGREEMENT_ID), "The correct agreement should be returned.");
    }

    @Test
    public void testFindContractAgreement_not_agreed() {
        ExtendableAgreementVO extendableAgreementVO = new ExtendableAgreementVO()
                .setExternalId(TEST_AGREEMENT_ID);
        extendableAgreementVO.setStatus(AgreementState.IN_PROCESS.getValue());
        when(agreementApiClient.findByContractId(eq(TEST_AGREEMENT_ID))).thenReturn(Optional.of(extendableAgreementVO));

        assertNull(tmfBackedContractNegotiationStore.findContractAgreement(TEST_AGREEMENT_ID), "If the agreement is not agreed, no contract agreement should be returned.");
    }

    @Test
    public void testFindContractAgreement_no_agreement() {
        when(agreementApiClient.findByContractId(eq(TEST_AGREEMENT_ID))).thenReturn(Optional.empty());

        assertNull(tmfBackedContractNegotiationStore.findContractAgreement(TEST_AGREEMENT_ID), "If no agreement is returned, no contract agreement should be returned.");
    }

    @Test
    public void testFindContractAgreement_unmappable_agreement() {
        ExtendableAgreementVO extendableAgreementVO = new ExtendableAgreementVO()
                .setExternalId(TEST_AGREEMENT_ID);
        extendableAgreementVO.setStatus(AgreementState.AGREED.getValue());
        when(agreementApiClient.findByContractId(eq(TEST_AGREEMENT_ID))).thenReturn(Optional.of(extendableAgreementVO));
        when(tmfEdcMapper.toContractAgreement(eq(extendableAgreementVO))).thenThrow(new RuntimeException("Was not able to map the agreement."));

        assertNull(tmfBackedContractNegotiationStore.findContractAgreement(TEST_AGREEMENT_ID), "If the agreement is not mappable, no contract agreement should be returned.");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("getValidNegotiations")
    public void testQueryNegotiations_success(String name, QuerySpec querySpec, List<ExtendableQuoteVO> negotiationQuotes, int numberOfNegotiations) {

        stubQuotes(negotiationQuotes);
        when(tmfEdcMapper.toContractNegotiation(any(), any(), any(), any()))
                .thenReturn(getValidNegotiation());
        assertEquals(numberOfNegotiations, tmfBackedContractNegotiationStore.queryNegotiations(querySpec).toList().size());
    }

    protected void stubQuotes(List<ExtendableQuoteVO> quotes) {
        when(quoteApiClient.getQuotes(anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    int offset = invocation.getArgument(0);
                    int limit = invocation.getArgument(1);

                    int fromIndex = Math.min(offset, quotes.size());
                    int toIndex = Math.min(offset + limit, quotes.size());

                    return quotes.subList(fromIndex, toIndex);
                });
    }

    protected static ContractNegotiation getValidNegotiationWithIdOfferAndType(String id, List<ContractOffer> offers, ContractNegotiation.Type type, ContractNegotiationStates state, String counterPartyId) {
        return ContractNegotiation.Builder
                .newInstance()
                .type(type)
                .state(state.code())
                .clock(Clock.fixed(Instant.EPOCH, TimeZone.getDefault().toZoneId()))
                .id(id)
                .counterPartyAddress(TEST_COUNTER_PARTY_ADDRESS)
                .counterPartyId(counterPartyId)
                .protocol(TEST_PROTOCOL)
                .contractOffers(offers)
                .build();
    }

    protected static ContractNegotiation getValidNegotiationWithIdInState(String id, ContractNegotiationStates state) {
        return ContractNegotiation.Builder
                .newInstance()
                .type(ContractNegotiation.Type.PROVIDER)
                .state(state.code())
                .clock(Clock.fixed(Instant.EPOCH, TimeZone.getDefault().toZoneId()))
                .id(id)
                .counterPartyAddress(TEST_COUNTER_PARTY_ADDRESS)
                .counterPartyId(TEST_COUNTER_PARTY_ID)
                .protocol(TEST_PROTOCOL)
                .build();
    }

    protected static ContractNegotiation getValidNegotiationWithIdOfferAndType(String id, List<ContractOffer> offers, ContractNegotiation.Type type, ContractNegotiationStates state) {
        return getValidNegotiationWithIdOfferAndType(id, offers, type, state, TEST_COUNTER_PARTY_ID);
    }

    protected static ContractNegotiation addAgreement(ContractNegotiation contractNegotiation) {
        ContractAgreement contractAgreement = ContractAgreement.Builder.newInstance()
                .providerId(TEST_PROVIDER_ID)
                .consumerId(TEST_CONSUMER_ID)
                .assetId(TEST_ASSET_ID)
                .policy(getTestPolicy())
                .build();
        contractNegotiation.setContractAgreement(contractAgreement);
        return contractNegotiation;
    }

    protected static ContractNegotiation getValidNegotiationWithId(String id) {
        return ContractNegotiation.Builder
                .newInstance()
                .clock(Clock.fixed(Instant.EPOCH, TimeZone.getDefault().toZoneId()))
                .id(id)
                .counterPartyAddress(TEST_COUNTER_PARTY_ADDRESS)
                .counterPartyId(TEST_COUNTER_PARTY_ID)
                .protocol(TEST_PROTOCOL)
                .build();
    }

    protected static ContractNegotiation getValidNegotiation() {
        return ContractNegotiation.Builder
                .newInstance()
                .clock(Clock.fixed(Instant.EPOCH, TimeZone.getDefault().toZoneId()))
                .counterPartyAddress(TEST_COUNTER_PARTY_ADDRESS)
                .counterPartyId(TEST_COUNTER_PARTY_ID)
                .protocol(TEST_PROTOCOL)
                .build();
    }

    @Test
    public void testQueryNegotiations_no_quotes() {

        when(quoteApiClient.getQuotes(anyInt(), anyInt())).thenReturn(List.of());
        assertEquals(0,
                tmfBackedContractNegotiationStore.queryNegotiations(QuerySpec.Builder.newInstance().offset(0).limit(100).build()).count(),
                "An empty stream should be returned if no quotes are returned.");
    }

    @Test
    public void testQueryNegotiations_skip_failing_quotes() {

        List<ExtendableQuoteVO> workingQuotes = getQuotes("negotiation-1", TEST_CONTROL_PLANE_ID, 3);
        List<ExtendableQuoteVO> failingQuotes = getQuotes("negotiation-2", TEST_CONTROL_PLANE_ID, 3);

        when(quoteApiClient.getQuotes(anyInt(), anyInt())).thenReturn(Stream.concat(workingQuotes.stream(), failingQuotes.stream()).toList());
        when(tmfEdcMapper.toContractNegotiation(eq(workingQuotes), any(), any(), any())).thenReturn(getValidNegotiation());
        when(tmfEdcMapper.toContractNegotiation(eq(failingQuotes), any(), any(), any())).thenThrow(new RuntimeException("Unmappable"));
        assertEquals(1,
                tmfBackedContractNegotiationStore.queryNegotiations(QuerySpec.Builder.newInstance().offset(0).limit(100).build()).count(),
                "Only the successfully mapped negotiations should be returned.");
    }

    @ParameterizedTest
    @MethodSource("getValidAgreements")
    public void testQueryAgreements_success(String name, List<AgreementHolder> agreementHolders, QuerySpec querySpec, int expectedAgs) {
        List<ExtendableAgreementVO> agreementVOS = agreementHolders.stream()
                .map(AgreementHolder::agreementVO)
                .toList();
        when(agreementApiClient.getAgreements(anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    int offset = invocation.getArgument(0);
                    int limit = invocation.getArgument(1);

                    int fromIndex = Math.min(offset, agreementVOS.size());
                    int toIndex = Math.min(offset + limit, agreementVOS.size());

                    return agreementVOS.subList(fromIndex, toIndex);
                });

        agreementHolders.forEach(ah -> when(tmfEdcMapper.toContractAgreement(eq(ah.agreementVO()))).thenReturn(ah.contractAgreement()));
        assertEquals(expectedAgs, tmfBackedContractNegotiationStore.queryAgreements(querySpec).count(), name);
    }

    @Test
    public void testQueryAgreements_empty_stream_if_no_agreement() {
        when(agreementApiClient.getAgreements(anyInt(), anyInt())).thenReturn(List.of());

        assertEquals(0, tmfBackedContractNegotiationStore.queryAgreements(QuerySpec.max()).count(), "If no agreements exist, an empty stream should be returned.");
    }

    @Test
    public void testQueryAgreements_ignore_failing_agreements() {
        when(agreementApiClient.getAgreements(anyInt(), anyInt())).thenReturn(getAgreements(10, "agg", AgreementState.AGREED.getValue()).stream().map(AgreementHolder::agreementVO).toList());
        when(tmfEdcMapper.toContractAgreement(any()))
                .thenThrow(new RuntimeException("Unmappable"))
                .thenReturn(getTestContractAgreement());
        assertEquals(9, tmfBackedContractNegotiationStore.queryAgreements(QuerySpec.max()).count(), "If an agreement cannot be mapped, it should be ignored.");
    }

    @Test
    public void testFindById_success() {
        String negotiationId = "negotiation-id";
        ContractNegotiation negotiation = getValidNegotiation();
        List<ExtendableQuoteVO> extendableQuoteVOS = getQuotes(negotiationId, TEST_CONTROL_PLANE_ID, 10);

        when(quoteApiClient.findByNegotiationId(eq(negotiationId))).thenReturn(extendableQuoteVOS);
        when(tmfEdcMapper.toContractNegotiation(eq(extendableQuoteVOS), any(), any(), any())).thenReturn(negotiation);
        assertEquals(negotiation, tmfBackedContractNegotiationStore.findById(negotiationId), "The correct negotiation should be returned.");
    }

    @Test
    public void testFindById_no_negotiation() {
        when(quoteApiClient.findByNegotiationId(any())).thenReturn(List.of());

        assertNull(tmfBackedContractNegotiationStore.findById("negotiation"), "No negotiation should be returned.");
    }

    @Test
    public void testFindById_no_negotiation_for_this_plane() {
        List<ExtendableQuoteVO> extendableQuoteVOS = getQuotes("negotiation", "other-control-plane", 10);

        when(quoteApiClient.findByNegotiationId(eq("negotiation"))).thenReturn(extendableQuoteVOS);
        assertNull(tmfBackedContractNegotiationStore.findById("negotiation"), "No negotiation should be returned.");
    }

    @Test
    public void testFindById_mapping_error() {
        List<ExtendableQuoteVO> extendableQuoteVOS = getQuotes("negotiation", TEST_CONTROL_PLANE_ID, 10);

        when(quoteApiClient.findByNegotiationId(any())).thenReturn(extendableQuoteVOS);
        when(tmfEdcMapper.toContractNegotiation(eq(extendableQuoteVOS), any(), any(), any())).thenThrow(new RuntimeException("Failed to map."));
        assertThrows(RuntimeException.class, () -> tmfBackedContractNegotiationStore.findById("negotiation"), "The exception should bubble.");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("getLeasableNegotiations")
    public void testNextNotLeased_success(String name, List<ExtendableQuoteVO> negotiationQuotes, List<NegotiationLease> negotiationLeases, int max, List<ContractNegotiation> expectedNegotiations) {

        // trigger invocation
        when(lockManager.writeLock(any())).thenAnswer(invocationOnMock -> {
                    Supplier<List<ContractNegotiation>> work = invocationOnMock.getArgument(0);
                    return work.get();
                }
        );

        stubQuotes(negotiationQuotes);

        Map<String, NegotiationLease> negMap = new HashMap<>();
        negotiationLeases.forEach(nl -> {
            negMap.put(nl.contractNegotiation().getId(), nl);
            if (nl.acquireError()) {
                doThrow(new IllegalStateException()).when(leaseHolder).acquireLease(eq(nl.contractNegotiation().getId()), any());
            }
        });

        when(leaseHolder.isLeased(any()))
                .thenAnswer(invocationOnMock -> {
                    String id = invocationOnMock.getArgument(0);
                    return negMap.get(id).isLeased();
                });
        when(tmfEdcMapper.toContractNegotiation(any(), any(), any(), any()))
                .thenAnswer(invocationOnMock -> {
                    List<ExtendableQuoteVO> quotes = invocationOnMock.getArgument(0);
                    return negMap.get(quotes.getFirst().getExternalId()).contractNegotiation();
                });
        List<ContractNegotiation> negotiations = tmfBackedContractNegotiationStore.nextNotLeased(max);
        // remove the order by putting into a set
        assertEquals(new HashSet<>(expectedNegotiations), new HashSet<>(negotiations), name);
    }

    @Test
    public void testNextNotLeased_fail_to_lock() {
        when(lockManager.writeLock(any())).thenThrow(new LockException("Was not able to get lock"));
        assertThrows(EdcPersistenceException.class, () -> tmfBackedContractNegotiationStore.nextNotLeased(3), "And EdcPersistenceException should be thrown if no lock can be acquired.");
    }

    @Test
    public void testNextNotLeased_api_failure() {
        // trigger invocation
        when(lockManager.writeLock(any())).thenAnswer(invocationOnMock -> {
                    Supplier<List<ContractNegotiation>> work = invocationOnMock.getArgument(0);
                    return work.get();
                }
        );
        when(quoteApiClient.getQuotes(anyInt(), anyInt())).thenThrow(new RuntimeException("Something happend."));
        assertThrows(EdcPersistenceException.class, () -> tmfBackedContractNegotiationStore.nextNotLeased(3), "And EdcPersistenceException should be thrown if an API failure happens.");
    }

    @Test
    public void testFindByIdAndLease_success() {
        // trigger invocation
        when(lockManager.writeLock(any())).thenAnswer(invocationOnMock -> {
                    Supplier<List<ContractNegotiation>> work = invocationOnMock.getArgument(0);
                    return work.get();
                }
        );
        String negotiationId = "negotiation-id";
        ContractNegotiation negotiation = getValidNegotiation();
        List<ExtendableQuoteVO> extendableQuoteVOS = getQuotes(negotiationId, TEST_CONTROL_PLANE_ID, 10);

        when(leaseHolder.isLeased(eq(negotiationId))).thenReturn(false);
        when(quoteApiClient.findByNegotiationId(eq(negotiationId))).thenReturn(extendableQuoteVOS);
        when(tmfEdcMapper.toContractNegotiation(eq(extendableQuoteVOS), any(), any(), any())).thenReturn(negotiation);

        StoreResult<ContractNegotiation> storeResult = tmfBackedContractNegotiationStore.findByIdAndLease(negotiationId);

        assertTrue(storeResult.succeeded(), "The negotiation should have been leased.");
        assertEquals(negotiation, storeResult.getContent(), "The correct negotiation should be returned.");

    }

    @Test
    public void testFindByIdAndLease_already_leased() {
        // trigger invocation
        when(lockManager.writeLock(any())).thenAnswer(invocationOnMock -> {
                    Supplier<List<ContractNegotiation>> work = invocationOnMock.getArgument(0);
                    return work.get();
                }
        );
        String negotiationId = "negotiation-id";
        ContractNegotiation negotiation = getValidNegotiation();
        List<ExtendableQuoteVO> extendableQuoteVOS = getQuotes(negotiationId, TEST_CONTROL_PLANE_ID, 10);

        doThrow(new IllegalStateException()).when(leaseHolder).acquireLease(eq(negotiationId), any());
        when(quoteApiClient.findByNegotiationId(eq(negotiationId))).thenReturn(extendableQuoteVOS);
        when(tmfEdcMapper.toContractNegotiation(eq(extendableQuoteVOS), any(), any(), any())).thenReturn(negotiation);
        StoreResult<ContractNegotiation> storeResult = tmfBackedContractNegotiationStore.findByIdAndLease(negotiationId);
        assertTrue(storeResult.failed(), "The result should be a failure if the negotiation is already leased.");
        assertEquals(StoreFailure.Reason.ALREADY_LEASED, storeResult.reason(), "The correct reason should be returned.");
    }

    @Test
    public void testFindByIdAndLease_lock_failure() {
        when(lockManager.writeLock(any())).thenThrow(new RuntimeException("No lock."));
        StoreResult<ContractNegotiation> storeResult = tmfBackedContractNegotiationStore.findByIdAndLease("negotiationId");
        assertTrue(storeResult.failed(), "If the lock fails, the result should indicate failure.");
        assertEquals(StoreFailure.Reason.GENERAL_ERROR, storeResult.reason(), "The correct reason for the failure should be provided.");
    }

    protected record NegotiationLease(ContractNegotiation contractNegotiation, boolean isLeased, boolean acquireError) {
        public NegotiationLease(ContractNegotiation contractNegotiation, boolean isLeased) {
            this(contractNegotiation, isLeased, false);
        }
    }

    protected static Stream<Arguments> getLeasableNegotiations() {
        return Stream.of(
                Arguments.of("The unleased negotiation should be returned.",
                        getNegotitations(1),
                        List.of(new NegotiationLease(getValidNegotiationWithId("neg-0"), false)),
                        3,
                        List.of(getValidNegotiationWithId("neg-0"))),
                Arguments.of("All unleased negotiations should be returned.",
                        getNegotitations(3),
                        List.of(
                                new NegotiationLease(getValidNegotiationWithId("neg-0"), false),
                                new NegotiationLease(getValidNegotiationWithId("neg-1"), false),
                                new NegotiationLease(getValidNegotiationWithId("neg-2"), false)
                        ),
                        5,
                        List.of(getValidNegotiationWithId("neg-0"), getValidNegotiationWithId("neg-1"), getValidNegotiationWithId("neg-2"))),
                Arguments.of("Only unleased negotiations should be returned.",
                        getNegotitations(3),
                        List.of(
                                new NegotiationLease(getValidNegotiationWithId("neg-0"), false),
                                new NegotiationLease(getValidNegotiationWithId("neg-1"), true),
                                new NegotiationLease(getValidNegotiationWithId("neg-2"), false)
                        ),
                        5,
                        List.of(getValidNegotiationWithId("neg-0"), getValidNegotiationWithId("neg-2"))),
                Arguments.of("Only the maximum unleased negotiations should be returned.",
                        getNegotitations(6),
                        List.of(
                                new NegotiationLease(getValidNegotiationWithId("neg-0"), false),
                                new NegotiationLease(getValidNegotiationWithId("neg-1"), true),
                                new NegotiationLease(getValidNegotiationWithId("neg-2"), false),
                                new NegotiationLease(getValidNegotiationWithId("neg-3"), false),
                                new NegotiationLease(getValidNegotiationWithId("neg-4"), false),
                                new NegotiationLease(getValidNegotiationWithId("neg-5"), false)
                        ),
                        3,
                        // order is reversed, due to the equal timestamps, thus the last 3 should be returned.
                        List.of(getValidNegotiationWithId("neg-2"), getValidNegotiationWithId("neg-4"), getValidNegotiationWithId("neg-5"))),
                Arguments.of("If no unleased negotiations are available, nothing should be returend.",
                        getNegotitations(6),
                        List.of(
                                new NegotiationLease(getValidNegotiationWithId("neg-0"), true),
                                new NegotiationLease(getValidNegotiationWithId("neg-1"), true),
                                new NegotiationLease(getValidNegotiationWithId("neg-2"), true),
                                new NegotiationLease(getValidNegotiationWithId("neg-3"), true),
                                new NegotiationLease(getValidNegotiationWithId("neg-4"), true),
                                new NegotiationLease(getValidNegotiationWithId("neg-5"), true)
                        ),
                        3,
                        List.of()),
                Arguments.of("Negotitation where acquiring the lease fails, should not be returned.",
                        getNegotitations(6),
                        List.of(
                                new NegotiationLease(getValidNegotiationWithId("neg-0"), false),
                                new NegotiationLease(getValidNegotiationWithId("neg-1"), true),
                                new NegotiationLease(getValidNegotiationWithId("neg-2"), false),
                                new NegotiationLease(getValidNegotiationWithId("neg-3"), false),
                                new NegotiationLease(getValidNegotiationWithId("neg-4"), false),
                                new NegotiationLease(getValidNegotiationWithId("neg-5"), false, true)
                        ),
                        5,
                        List.of(getValidNegotiationWithId("neg-0"), getValidNegotiationWithId("neg-2"), getValidNegotiationWithId("neg-3"), getValidNegotiationWithId("neg-4")))
        );
    }

    protected static Stream<Arguments> getValidAgreements() {
        return Stream.of(
                Arguments.of("All agreements should have been returned.",
                        getAgreements(10, "agg", AgreementState.AGREED.getValue()),
                        QuerySpec.Builder.newInstance().offset(0).limit(100).build(), 10),
                Arguments.of("Agreements should have been returned, respecting limits.",
                        getAgreements(10, "agg", AgreementState.AGREED.getValue()),
                        QuerySpec.Builder.newInstance().offset(0).limit(5).build(), 5),
                Arguments.of("Agreements should have been returned, respecting the offset.",
                        getAgreements(10, "agg", AgreementState.AGREED.getValue()),
                        QuerySpec.Builder.newInstance().offset(5).limit(10).build(), 5),
                Arguments.of("Only agreed agreements should have been returned, respecting the offset.",
                        Stream.concat(getAgreements(10, "agg", AgreementState.AGREED.getValue()).stream(), getAgreements(5, "in-process", AgreementState.IN_PROCESS.getValue()).stream()).toList(),
                        QuerySpec.Builder.newInstance().offset(0).limit(10).build(), 10)
        );
    }

    protected static List<AgreementHolder> getAgreements(int numAgs, String idPrefix, String agreementState) {
        List<AgreementHolder> agreementHolders = new ArrayList<>();
        for (int i = 0; i < numAgs; i++) {
            ContractAgreement contractAgreement = ContractAgreement.Builder.newInstance()
                    .id(String.format("%s-%s", idPrefix, i))
                    .policy(getTestPolicy())
                    .assetId(TEST_ASSET_ID)
                    .consumerId(TEST_CONSUMER_ID)
                    .providerId(TEST_PROVIDER_ID)
                    .build();
            ExtendableAgreementVO extendableAgreementVO = new ExtendableAgreementVO()
                    .setExternalId(String.format("%s-%s", idPrefix, i));
            extendableAgreementVO.setStatus(agreementState);

            agreementHolders.add(new AgreementHolder(extendableAgreementVO, contractAgreement));
        }
        return agreementHolders;
    }

    protected static Stream<Arguments> getValidNegotiations() {
        return Stream.of(
                Arguments.of("Get one negotiation from the quotes.",
                        QuerySpec.Builder.newInstance().offset(0).limit(100).build(),
                        getQuotes("negotiation-1", TEST_CONTROL_PLANE_ID, 3),
                        1),
                Arguments.of("Get negotiations from the quotes with limit.",
                        QuerySpec.Builder.newInstance().offset(0).limit(100).build(),
                        getNegotitations(150),
                        100),
                Arguments.of("Get negotiations from the quotes with limit.",
                        QuerySpec.Builder.newInstance().offset(0).limit(50).build(),
                        getNegotitations(150),
                        50),
                Arguments.of("Get negotiations from the quotes with offset and limit.",
                        QuerySpec.Builder.newInstance().offset(100).limit(100).build(),
                        getNegotitations(150),
                        50),
                Arguments.of("Get the negotiations from the quotes.",
                        QuerySpec.Builder.newInstance().offset(0).limit(100).build(),
                        Stream.concat(getQuotes("negotiation-1", TEST_CONTROL_PLANE_ID, 3).stream(), getQuotes("negotiation-2", TEST_CONTROL_PLANE_ID, 1).stream()).toList(),
                        2),
                Arguments.of("Get only the negotiations for this controlplane.",
                        QuerySpec.Builder.newInstance().offset(0).limit(100).build(),
                        Stream.concat(
                                Stream.concat(
                                        getQuotes("negotiation-1", TEST_CONTROL_PLANE_ID, 3).stream(),
                                        getQuotes("negotiation-2", TEST_CONTROL_PLANE_ID, 1).stream()),
                                getQuotes("negotiation-2", "the-other-plane", 1).stream()).toList(),
                        2)
        );
    }

    protected static List<ExtendableQuoteVO> getNegotitations(int numNegs) {
        List<ExtendableQuoteVO> quoteVOS = new ArrayList<>();
        for (int i = 0; i < numNegs; i++) {
            quoteVOS.addAll(getQuotes(String.format("neg-%s", i), TEST_CONTROL_PLANE_ID, 3));
        }
        return quoteVOS;
    }

    protected static List<ExtendableQuoteVO> getQuotes(String negotiationId, String controlPlane, int numQuotes) {

        List<ExtendableQuoteVO> extendableQuoteVOS = new ArrayList<>();
        for (int i = 0; i < numQuotes; i++) {
            ExtendableQuoteVO extendableQuoteVO = new ExtendableQuoteVO();
            extendableQuoteVO.setExternalId(negotiationId);
            extendableQuoteVO.setContractNegotiationState(
                    new ContractNegotiationState()
                            .setControlplane(controlPlane));
            extendableQuoteVOS.add(extendableQuoteVO);
        }

        return extendableQuoteVOS;
    }


    protected static List<ExtendableQuoteVO> getQuotes(String negotiationId, String controlPlane, int numQuotes, QuoteStateTypeVO state) {
        List<ExtendableQuoteVO> extendableQuoteVOS = new ArrayList<>();
        for (int i = 0; i < numQuotes; i++) {
            extendableQuoteVOS.add(getQuote("id-" + i, negotiationId, controlPlane, state));
        }

        return extendableQuoteVOS;
    }

    protected static ExtendableQuoteVO getQuote(String quoteId, String negotiationId, String controlPlane, QuoteStateTypeVO state) {
        return getQuote(quoteId, negotiationId, controlPlane, state, ContractNegotiationStates.INITIAL);
    }

    protected static ExtendableQuoteVO getQuote(String quoteId, String negotiationId, String controlPlane, QuoteStateTypeVO state, ContractNegotiationStates negotiationState) {
        ExtendableQuoteVO extendableQuoteVO = new ExtendableQuoteVO();
        extendableQuoteVO.setId(quoteId);
        extendableQuoteVO.setExternalId(negotiationId);
        extendableQuoteVO.setState(state);
        extendableQuoteVO.setContractNegotiationState(
                new ContractNegotiationState()
                        .setState(negotiationState.name())
                        .setControlplane(controlPlane));
        return extendableQuoteVO;
    }


    protected record AgreementHolder(ExtendableAgreementVO agreementVO, ContractAgreement contractAgreement) {
    }

    protected static ContractAgreement getTestContractAgreement() {
        return ContractAgreement.Builder.newInstance()
                .id(TEST_AGREEMENT_ID)
                .providerId(TEST_PROVIDER_ID)
                .consumerId(TEST_CONSUMER_ID)
                .assetId(TEST_ASSET_ID)
                .policy(getTestPolicy())
                .build();
    }


    protected static Policy getTestPolicy() {
        return Policy.Builder
                .newInstance()
                .extensibleProperty("http://www.w3.org/ns/odrl/2/uid", TEST_POLICY_ID)
                .build();
    }

}