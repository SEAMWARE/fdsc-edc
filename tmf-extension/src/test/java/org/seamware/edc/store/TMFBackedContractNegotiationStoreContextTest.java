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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.seamware.edc.store.TMFBackedContractNegotiationStoreTest.TEST_POLICY_ID;
import static org.seamware.edc.store.TMFBackedContractNegotiationStoreTest.TEST_PROTOCOL;
import static org.seamware.edc.store.TMFBackedContractNegotiationStoreTest.TEST_PROVIDER_ID;
import static org.seamware.edc.tmf.ParticipantResolver.CONSUMER_ROLE;
import static org.seamware.edc.tmf.ParticipantResolver.PROVIDER_ROLE;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.edc.connector.controlplane.contract.spi.ContractOfferId;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractOffer;
import org.eclipse.edc.participantcontext.spi.identity.ParticipantIdentityResolver;
import org.eclipse.edc.query.CriterionOperatorRegistryImpl;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.seamware.edc.SchemaBaseUriHolder;
import org.seamware.edc.domain.AgreementState;
import org.seamware.edc.domain.ContractNegotiationState;
import org.seamware.edc.domain.ExtendableAgreementVO;
import org.seamware.edc.domain.ExtendableProductOffering;
import org.seamware.edc.domain.ExtendableQuoteCreateVO;
import org.seamware.edc.domain.ExtendableQuoteItemVO;
import org.seamware.edc.domain.ExtendableQuoteVO;
import org.seamware.edc.tmf.AgreementApiClient;
import org.seamware.edc.tmf.ParticipantResolver;
import org.seamware.edc.tmf.ProductCatalogApiClient;
import org.seamware.edc.tmf.ProductInventoryApiClient;
import org.seamware.edc.tmf.ProductOrderApiClient;
import org.seamware.edc.tmf.QuoteApiClient;
import org.seamware.tmforum.quote.model.RelatedPartyVO;

/** Negotiation and agreement store behaviour when one runtime serves several contexts. */
class TMFBackedContractNegotiationStoreContextTest extends AbstractStoreTest {

  private static final String CONTEXT_A = "dataspace-a";
  private static final String CONTEXT_B = "dataspace-b";
  private static final String DEFAULT_CONTEXT = "default-context";
  private static final String DID_A = "did:web:provider.dataspace-a";
  private static final String DID_B = "did:web:provider.dataspace-b";
  private static final String DEFAULT_DID = "did:web:provider.default";
  private static final String COUNTER_PARTY_DID = "did:web:consumer";
  private static final String TMF_ID_PREFIX = "tmf-";
  private static final String CONTROL_PLANE = "test-control-plane";
  private static final String NEGOTIATION_A = "negotiation-a";
  private static final String NEGOTIATION_B = "negotiation-b";
  private static final String LEGACY_NEGOTIATION = "negotiation-legacy";
  private static final String AGREEMENT_ID = "agreement-id";
  private static final String TMF_OFFERING_ID = "urn:ngsi-ld:product-offering:shared";
  private static final String COUNTER_PARTY_ADDRESS = "http://consumer.dataspace";
  private static final int PAGE_SIZE = 100;

  private QuoteApiClient quoteApiClient;
  private AgreementApiClient agreementApiClient;
  private ProductCatalogApiClient productCatalogApiClient;
  private TMFEdcMapper tmfEdcMapper;
  private LeaseHolder leaseHolder;
  private TMFBackedContractNegotiationStore store;

  @BeforeEach
  void setup() {
    SchemaBaseUriHolder.configure(URI.create("http://my-base.schema"));
    quoteApiClient = mock(QuoteApiClient.class);
    agreementApiClient = mock(AgreementApiClient.class);
    productCatalogApiClient = mock(ProductCatalogApiClient.class);
    tmfEdcMapper = mock(TMFEdcMapper.class);
    leaseHolder = mock(LeaseHolder.class);
    when(tmfEdcMapper.getDefaultParticipantContextId()).thenReturn(DEFAULT_CONTEXT);

    ParticipantResolver participantResolver = mock(ParticipantResolver.class);
    when(participantResolver.getTmfId(anyString()))
        .thenAnswer(invocation -> TMF_ID_PREFIX + invocation.getArgument(0));

    ParticipantIdentityResolver identityResolver = mock(ParticipantIdentityResolver.class);
    Map.of(CONTEXT_A, DID_A, CONTEXT_B, DID_B, DEFAULT_CONTEXT, DEFAULT_DID)
        .forEach(
            (context, did) ->
                when(identityResolver.getParticipantId(eq(context), any())).thenReturn(did));

    LockManager lockManager = mock(LockManager.class);
    when(lockManager.writeLock(any()))
        .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());

    store =
        new TMFBackedContractNegotiationStore(
            mock(Monitor.class),
            new ObjectMapper(),
            quoteApiClient,
            agreementApiClient,
            mock(ProductOrderApiClient.class),
            productCatalogApiClient,
            mock(ProductInventoryApiClient.class),
            participantResolver,
            tmfEdcMapper,
            new OwnParticipantIdentity(identityResolver, TEST_PROVIDER_ID),
            CONTROL_PLANE,
            CriterionOperatorRegistryImpl.ofDefaults(),
            leaseHolder,
            new TMFTransactionContext(mock(Monitor.class)),
            lockManager);
  }

  private static Stream<Arguments> ownDidPerContext() {
    return Stream.of(
        Arguments.of(CONTEXT_A, DID_A),
        Arguments.of(CONTEXT_B, DID_B),
        Arguments.of(DEFAULT_CONTEXT, DEFAULT_DID));
  }

  @ParameterizedTest(name = "context {0} -> own party {1}")
  @MethodSource("ownDidPerContext")
  void save_createsTheQuoteInTheNegotiationsContextWithItsOwnDid(String context, String ownDid) {
    when(quoteApiClient.findByNegotiationId(anyString())).thenReturn(List.of());
    when(tmfEdcMapper.fromProviderContractOffer(any(), any(), any()))
        .thenAnswer(invocation -> new ExtendableQuoteItemVO().setExternalId("item"));

    store.save(
        providerNegotiation(
            NEGOTIATION_A, context, ContractOfferId.create(TEST_OFFER_ID, TEST_ASSET_ID)));

    ArgumentCaptor<ExtendableQuoteCreateVO> quoteCaptor =
        ArgumentCaptor.forClass(ExtendableQuoteCreateVO.class);
    verify(quoteApiClient).createQuote(quoteCaptor.capture());
    ExtendableQuoteCreateVO quote = quoteCaptor.getValue();
    assertEquals(context, quote.getParticipantContextId(), "The quote carries the context.");
    assertTrue(
        quote.getExtendableQuoteItem().stream()
            .allMatch(item -> context.equals(item.getParticipantContextId())),
        "Every quote item carries the negotiation's context.");
    assertEquals(
        Map.of(
            PROVIDER_ROLE, TMF_ID_PREFIX + ownDid,
            CONSUMER_ROLE, TMF_ID_PREFIX + COUNTER_PARTY_DID),
        quote.getRelatedParty().stream()
            .collect(Collectors.toMap(RelatedPartyVO::getRole, RelatedPartyVO::getId)),
        "The own party is the context's own identity.");
  }

  @Test
  void save_resolvesAContextQualifiedOfferToTheSharedOffering() {
    List<String> offeredIn = List.of(CONTEXT_A, CONTEXT_B);
    ExtendableProductOffering offering =
        getExtendableProductOffering().setParticipantContextIds(offeredIn);
    offering.setId(TMF_OFFERING_ID);
    when(productCatalogApiClient.getProductOfferingByExternalId(eq(TEST_OFFER_ID)))
        .thenReturn(Optional.of(offering));
    // a plain id cannot address an offering shared by several contexts
    when(tmfEdcMapper.contextForId(eq(offeredIn), isNull())).thenReturn(Optional.empty());
    when(tmfEdcMapper.contextForId(eq(offeredIn), eq(CONTEXT_A)))
        .thenReturn(Optional.of(CONTEXT_A));
    when(quoteApiClient.findByNegotiationId(anyString())).thenReturn(List.of());
    when(tmfEdcMapper.fromProviderContractOffer(any(), any(), any()))
        .thenAnswer(invocation -> new ExtendableQuoteItemVO().setExternalId("item"));

    store.save(
        providerNegotiation(
            NEGOTIATION_A,
            CONTEXT_A,
            ContractOfferId.create(
                ContextQualifiedId.qualify(TEST_OFFER_ID, CONTEXT_A), TEST_ASSET_ID)));

    verify(tmfEdcMapper).fromProviderContractOffer(any(), any(), eq(Optional.of(TMF_OFFERING_ID)));
  }

  private static Stream<Arguments> negotiationsPerContext() {
    return Stream.of(
        Arguments.of(CONTEXT_A, NEGOTIATION_A),
        Arguments.of(CONTEXT_B, NEGOTIATION_B),
        Arguments.of(DEFAULT_CONTEXT, LEGACY_NEGOTIATION));
  }

  @ParameterizedTest(name = "context {0} sees only {1}")
  @MethodSource("negotiationsPerContext")
  void queryNegotiations_returnsOnlyTheRequestedContext(String context, String expectedId) {
    givenNegotiationsInAllContexts();

    List<String> found =
        store.queryNegotiations(contextQuery(context)).map(ContractNegotiation::getId).toList();

    assertEquals(List.of(expectedId), found);
    if (DEFAULT_CONTEXT.equals(context)) {
      // untagged legacy quotes belong to the default context, so it is read unfiltered
      verify(quoteApiClient).getQuotes(0, PAGE_SIZE);
    } else {
      verify(quoteApiClient).getQuotes(0, PAGE_SIZE, context);
    }
  }

  @ParameterizedTest(name = "context {0} leases only {1}")
  @MethodSource("negotiationsPerContext")
  void nextNotLeased_leasesOnlyTheRequestedContextByEntityId(String context, String expectedId) {
    givenNegotiationsInAllContexts();

    List<String> leased =
        store.nextNotLeased(PAGE_SIZE, contextCriterion(context)).stream()
            .map(ContractNegotiation::getId)
            .toList();

    assertEquals(List.of(expectedId), leased);
    // the lease key is the entity id alone, which is unique across contexts
    verify(leaseHolder).acquireLease(eq(expectedId), any());
    Stream.of(NEGOTIATION_A, NEGOTIATION_B, LEGACY_NEGOTIATION)
        .filter(id -> !id.equals(expectedId))
        .forEach(id -> verify(leaseHolder, never()).acquireLease(eq(id), any()));
  }

  private static Stream<Arguments> agreementContexts() {
    return Stream.of(
        Arguments.of(CONTEXT_A), Arguments.of(CONTEXT_B), Arguments.of(DEFAULT_CONTEXT));
  }

  @ParameterizedTest(name = "agreement in context {0}")
  @MethodSource("agreementContexts")
  void queryAgreements_matchesIdAndContext(String context) {
    List<ExtendableAgreementVO> agreements =
        Stream.of(CONTEXT_A, CONTEXT_B, null).map(this::agreedAgreement).toList();
    when(agreementApiClient.getAgreements(anyInt(), anyInt())).thenReturn(agreements);
    when(agreementApiClient.getAgreements(anyInt(), anyInt(), anyString())).thenReturn(agreements);
    when(tmfEdcMapper.toContractAgreement(any()))
        .thenAnswer(invocation -> toAgreement(invocation.<ExtendableAgreementVO>getArgument(0)));

    List<ContractAgreement> found =
        store
            .queryAgreements(
                QuerySpec.Builder.newInstance()
                    .filter(
                        List.of(
                            Criterion.criterion("id", "=", AGREEMENT_ID),
                            contextCriterion(context)))
                    .build())
            .toList();

    assertEquals(1, found.size(), "Exactly the agreement of the requested context matches.");
    assertEquals(context, found.getFirst().getParticipantContextId());
  }

  private void givenNegotiationsInAllContexts() {
    List<ExtendableQuoteVO> quotes =
        List.of(
            quote(NEGOTIATION_A, CONTEXT_A),
            quote(NEGOTIATION_B, CONTEXT_B),
            quote(LEGACY_NEGOTIATION, null));
    // TMForum may ignore the context filter, so the stores must filter in memory as well
    when(quoteApiClient.getQuotes(anyInt(), anyInt())).thenReturn(quotes);
    when(quoteApiClient.getQuotes(anyInt(), anyInt(), anyString())).thenReturn(quotes);
    when(quoteApiClient.findByNegotiationId(anyString()))
        .thenAnswer(
            invocation ->
                quotes.stream()
                    .filter(q -> q.getExternalId().equals(invocation.getArgument(0)))
                    .toList());
    when(tmfEdcMapper.toContractNegotiation(any(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              ExtendableQuoteVO quote = invocation.<List<ExtendableQuoteVO>>getArgument(0).get(0);
              return providerNegotiation(
                  quote.getExternalId(),
                  contextOrDefault(quote.getParticipantContextId()),
                  ContractOfferId.create(TEST_OFFER_ID, TEST_ASSET_ID));
            });
  }

  private static ExtendableQuoteVO quote(String negotiationId, String context) {
    ExtendableQuoteVO quote = new ExtendableQuoteVO();
    quote.setExternalId(negotiationId);
    quote.setParticipantContextId(context);
    quote.setContractNegotiationState(
        new ContractNegotiationState().setControlplane(CONTROL_PLANE));
    return quote;
  }

  private ExtendableAgreementVO agreedAgreement(String context) {
    ExtendableAgreementVO agreement = new ExtendableAgreementVO().setExternalId(AGREEMENT_ID);
    agreement.setStatus(AgreementState.AGREED.getValue());
    agreement.setParticipantContextId(context);
    return agreement;
  }

  private static ContractAgreement toAgreement(ExtendableAgreementVO agreementVO) {
    return ContractAgreement.Builder.newInstance()
        .id(agreementVO.getExternalId())
        .providerId(TEST_PROVIDER_ID)
        .consumerId(COUNTER_PARTY_DID)
        .assetId(TEST_ASSET_ID)
        .policy(getTestPolicy(TEST_POLICY_ID))
        .participantContextId(contextOrDefault(agreementVO.getParticipantContextId()))
        .build();
  }

  private static String contextOrDefault(String context) {
    return context == null ? DEFAULT_CONTEXT : context;
  }

  private static ContractNegotiation providerNegotiation(
      String negotiationId, String context, ContractOfferId offerId) {
    ContractOffer offer = getOffer(offerId.toString());
    return ContractNegotiation.Builder.newInstance()
        .id(negotiationId)
        .type(ContractNegotiation.Type.PROVIDER)
        .state(ContractNegotiationStates.INITIAL.code())
        .clock(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
        .participantContextId(context)
        .counterPartyId(COUNTER_PARTY_DID)
        .counterPartyAddress(COUNTER_PARTY_ADDRESS)
        .protocol(TEST_PROTOCOL)
        .contractOffers(List.of(offer))
        .build();
  }

  private static Criterion contextCriterion(String context) {
    return Criterion.criterion(
        ContextCriteria.PARTICIPANT_CONTEXT_ID_PROPERTY, ContextCriteria.EQUALS_OPERATOR, context);
  }

  private static QuerySpec contextQuery(String context) {
    return QuerySpec.Builder.newInstance().filter(List.of(contextCriterion(context))).build();
  }
}
