package org.seamware.edc.store;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.catalog.spi.DataService;
import org.eclipse.edc.connector.controlplane.catalog.spi.Dataset;
import org.eclipse.edc.connector.controlplane.catalog.spi.Distribution;
import org.eclipse.edc.connector.controlplane.contract.spi.ContractOfferId;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractDefinition;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractOffer;
import org.eclipse.edc.connector.controlplane.contract.spi.validation.ValidatableConsumerOffer;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.policy.model.*;
import org.eclipse.edc.protocol.dsp.spi.type.Dsp2025Constants;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.seamware.edc.SchemaBaseUriHolder;
import org.seamware.edc.domain.*;
import org.seamware.edc.tmf.AgreementApiClient;
import org.seamware.edc.tmf.ParticipantResolver;
import org.seamware.edc.util.NoopMonitor;
import org.seamware.tmforum.agreement.model.AgreementVO;
import org.seamware.tmforum.agreement.model.CharacteristicVO;
import org.seamware.tmforum.agreement.model.RelatedPartyVO;
import org.seamware.tmforum.party.model.OrganizationVO;
import org.seamware.tmforum.productcatalog.model.CharacteristicValueSpecificationVO;
import org.seamware.tmforum.productcatalog.model.ProductOfferingTermVO;
import org.seamware.tmforum.productcatalog.model.ProductSpecificationCharacteristicVO;
import org.seamware.tmforum.quote.model.ProductOfferingRefVO;
import org.seamware.tmforum.quote.model.QuoteStateTypeVO;

import java.io.StringReader;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TMFEdcMapperTest {

    private static final String TEST_CONSUMER_DID = "did:web:test-consumer.io";
    private static final String TEST_PROVIDER_DID = "did:web:test-provider.io";
    private static final String TEST_CONSUMER_TMF_ID = "tmf-consumer";
    private static final String TEST_PROVIDER_TMF_ID = "tmf-provider";

    private static final String TEST_ASSET_ID = "asset-id";
    private static final String TEST_AGREEMENT_ID = "agreement-id";
    private static final String TEST_NEGOTIATION_ID = "negotiation-id";
    private static final String TEST_CORRELATION_ID = "correlation-id";
    private static final String TEST_OFFER_ID = "offer-id";

    private static final String TEST_COUNTER_PARTY_ADDRESS = "http://counter.party";
    private static final String TEST_CONTROL_PLANE = "test-control-plane";

    private static final String TEST_DESCRIPTION = "Description";
    private static final String TEST_VERSION = "v1.0.0";
    private static final String TEST_NAME = "name";
    private static final String TEST_SPEC_DESCRIPTION = "Spec Description";

    private static final ObjectMapper SORTED_MAPPER = JsonMapper.builder()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .build();


    public TMFEdcMapper tmfEdcMapper;
    public ParticipantResolver participantResolver;
    public TypeTransformerRegistry typeTransformerRegistry;
    public JsonLd jsonLd;
    public ObjectMapper objectMapper;
    public static Clock clock = Clock.fixed(Instant.EPOCH, TimeZone.getDefault().toZoneId());

    @BeforeEach
    public void setup() {
        SchemaBaseUriHolder.configure(URI.create("http://base.schema"));

        objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.addMixIn(Policy.Builder.class, UnknownPropertyMixin.class);
        objectMapper.registerModule(new JavaTimeModule());

        Monitor monitor = new NoopMonitor();
        participantResolver = mock(ParticipantResolver.class);
        typeTransformerRegistry = mock(TypeTransformerRegistry.class);
        jsonLd = mock(JsonLd.class);

        tmfEdcMapper = new TMFEdcMapper(monitor, objectMapper, participantResolver, typeTransformerRegistry, jsonLd, clock);
    }

    // ---- TO AGREEMENT ----

    @Test
    public void toAgreement_success() throws JsonProcessingException {

        when(participantResolver.getTmfId(eq(TEST_CONSUMER_DID))).thenReturn(TEST_CONSUMER_TMF_ID);
        when(participantResolver.getTmfId(eq(TEST_PROVIDER_DID))).thenReturn(TEST_PROVIDER_TMF_ID);

        ContractAgreement contractAgreement = getValidContractAgreement();
        AgreementVO extendableAgreementVO = getTestAgreement();

        when(typeTransformerRegistry.transform(any(), eq(JsonObject.class))).thenReturn(Result.success(getEmptyOdrlContract()));
        when(jsonLd.expand(any())).thenReturn(Result.success(getTestOdrlContract()));

        assertEquals(SORTED_MAPPER.writeValueAsString(extendableAgreementVO),
                SORTED_MAPPER.writeValueAsString(tmfEdcMapper.toAgreement(TEST_NEGOTIATION_ID, contractAgreement)));
        verify(jsonLd, times(1)).expand(any());
    }

    @Test
    public void toAgreement_fail_no_consumer_in_tmf() {
        when(participantResolver.getTmfId(eq(TEST_CONSUMER_DID))).thenThrow(new RuntimeException("Something bad happend"));
        when(participantResolver.getTmfId(eq(TEST_PROVIDER_DID))).thenReturn(TEST_PROVIDER_TMF_ID);

        assertThrows(RuntimeException.class, () -> tmfEdcMapper.toAgreement(TEST_NEGOTIATION_ID, getValidContractAgreement()), "In case the participant cant be resolved, the exception should bubble.");
    }

    @Test
    public void toAgreement_fail_no_provider_in_tmf() {
        when(participantResolver.getTmfId(eq(TEST_PROVIDER_DID))).thenThrow(new RuntimeException("Something bad happend"));
        when(participantResolver.getTmfId(eq(TEST_CONSUMER_DID))).thenReturn(TEST_CONSUMER_TMF_ID);

        assertThrows(RuntimeException.class, () -> tmfEdcMapper.toAgreement(TEST_NEGOTIATION_ID, getValidContractAgreement()), "In case the participant cant be resolved, the exception should bubble.");
    }

    @Test
    public void toAgreement_fail_on_policy_transform() {
        when(participantResolver.getTmfId(eq(TEST_PROVIDER_DID))).thenReturn(TEST_PROVIDER_TMF_ID);
        when(participantResolver.getTmfId(eq(TEST_CONSUMER_DID))).thenReturn(TEST_CONSUMER_TMF_ID);
        when(typeTransformerRegistry.transform(any(), any())).thenThrow(new RuntimeException("Was not able to transform."));

        assertThrows(RuntimeException.class, () -> tmfEdcMapper.toAgreement(TEST_NEGOTIATION_ID, getValidContractAgreement()), "In case the transformation to odrl does not work, the exception should bubble.");
    }

    @Test
    public void toAgreement_fail_on_jsonld_expansion_exception() {
        when(participantResolver.getTmfId(eq(TEST_PROVIDER_DID))).thenReturn(TEST_PROVIDER_TMF_ID);
        when(participantResolver.getTmfId(eq(TEST_CONSUMER_DID))).thenReturn(TEST_CONSUMER_TMF_ID);
        when(typeTransformerRegistry.transform(any(), eq(JsonObject.class))).thenReturn(Result.success(getEmptyOdrlContract()));
        when(jsonLd.expand(any())).thenThrow(new RuntimeException("Cannot expand"));
        assertThrows(RuntimeException.class, () -> tmfEdcMapper.toAgreement(TEST_NEGOTIATION_ID, getValidContractAgreement()), "In case the expansion of jsonld does not work, the exception should bubble.");
    }

    @Test
    public void toAgreement_fail_on_jsonld_expansion_failure() {
        when(participantResolver.getTmfId(eq(TEST_PROVIDER_DID))).thenReturn(TEST_PROVIDER_TMF_ID);
        when(participantResolver.getTmfId(eq(TEST_CONSUMER_DID))).thenReturn(TEST_CONSUMER_TMF_ID);
        when(typeTransformerRegistry.transform(any(), eq(JsonObject.class))).thenReturn(Result.success(getEmptyOdrlContract()));
        when(jsonLd.expand(any())).thenReturn(Result.failure("Failed to expand"));
        assertThrows(IllegalArgumentException.class, () -> tmfEdcMapper.toAgreement(TEST_NEGOTIATION_ID, getValidContractAgreement()), "If the expansion returns a failure, an exception should be thrown.");
    }

    // ---- TO CONTRACT AGREEMENT ----

    @Test
    public void toContractAgreement_success() throws JsonProcessingException {
        ContractAgreement expectedAgreement = ContractAgreement.Builder.newInstance()
                .id(TEST_AGREEMENT_ID)
                .policy(getTestPolicy())
                .providerId(TEST_PROVIDER_DID)
                .consumerId(TEST_CONSUMER_DID)
                .contractSigningDate(1)
                .assetId(TEST_ASSET_ID).build();
        when(jsonLd.expand(any())).thenReturn(Result.success(getTestOdrlContract()));
        when(typeTransformerRegistry.transform(any(), eq(Policy.class))).thenReturn(Result.success(getTestPolicy()));

        assertEquals(
                SORTED_MAPPER.writeValueAsString(expectedAgreement),
                SORTED_MAPPER.writeValueAsString(tmfEdcMapper.toContractAgreement((ExtendableAgreementVO) getTestAgreement())));
    }

    @Test
    public void toContractAgreement_fail_on_expand() {
        when(jsonLd.expand(any())).thenReturn(Result.failure("Expansion failed."));
        assertThrows(IllegalArgumentException.class,
                () -> tmfEdcMapper.toContractAgreement((ExtendableAgreementVO) getTestAgreement()), "If the expansion fails, an exception should be thrown.");
    }

    @Test
    public void toContractAgreement_fail_on_transform() {
        when(jsonLd.expand(any())).thenReturn(Result.success(getEmptyOdrlContract()));
        when(typeTransformerRegistry.transform(any(), any())).thenReturn(Result.failure("Failed to transform."));
        assertThrows(IllegalArgumentException.class,
                () -> tmfEdcMapper.toContractAgreement((ExtendableAgreementVO) getTestAgreement()), "If the transformation fails, an exception should be thrown.");
    }

    // ---- DATASET FROM PRODUCT OFFERING ----
    @ParameterizedTest
    @MethodSource("getValidProductOfferings")
    public void datasetFromProductOffering_successs(ExtendableProductOffering inputOffering, Optional<ExtendableProductSpecification> inputSpec, Dataset expectedDataSet) throws JsonProcessingException {

        when(jsonLd.expand(any())).thenReturn(Result.success(getTestOdrlContract()));
        when(typeTransformerRegistry.transform(any(), eq(Policy.class))).thenReturn(Result.success(getTestPolicy()));
        Optional<Dataset> optionalDataset = tmfEdcMapper.datasetFromProductOffering(inputOffering, inputSpec);
        assertTrue(optionalDataset.isPresent(), "The dataset should have been mapped.");
        Map<String, Object> expectedMap = SORTED_MAPPER.convertValue(expectedDataSet, new TypeReference<Map<String, Object>>() {
        });
        Map<String, Object> datasetMap = SORTED_MAPPER.convertValue(optionalDataset.get(), new TypeReference<Map<String, Object>>() {
        });

        if (inputSpec.isEmpty()) {
            assertTrue(datasetMap.containsKey("id"), "In case of a missing inputSpec, the id should be generated");
            datasetMap.remove("id");
            expectedMap.remove("id");
        }

        assertEquals(expectedMap, datasetMap, "The dataset should correctly be mapped.");
    }

    @ParameterizedTest
    @MethodSource("getInvalidOfferings")
    public void datasetFromProductOffering_unsupported_offering(ExtendableProductOffering inputOffering) {
        assertFalse(tmfEdcMapper.datasetFromProductOffering(inputOffering, Optional.empty()).isPresent(), "Invalid offerings should not be mapped to data sets.");
    }

    @ParameterizedTest
    @MethodSource("getInvalidSpecs")
    public void datasetFromProductOffering_unsupported_offering(ExtendableProductSpecification inputSpec) {
        assertFalse(tmfEdcMapper.datasetFromProductOffering(getExtendableProductOffering(), Optional.of(inputSpec)).isPresent(), "Invalid offerings should not be mapped to data sets.");
    }

    // ---- TO CONTRACT NEGOTIATIONS ----

    @ParameterizedTest(name = "{0}")
    @MethodSource("getNegotiations")
    public void toContractNegotiation_success(String message, List<ExtendableQuoteVO> inputQuotes, Optional<ExtendableAgreementVO> inputAgreement, String participantInput, ContractNegotiation expectedNegotiation) throws JsonProcessingException {

        when(jsonLd.expand(any())).thenReturn(Result.success(getTestOdrlContract()));
        when(typeTransformerRegistry.transform(any(), eq(Policy.class))).thenReturn(Result.success(getTestPolicy()));
        when(participantResolver.getOrganization(eq(TEST_CONSUMER_TMF_ID)))
                .thenReturn(Optional.of(new OrganizationVO().partyCharacteristic(List.of(new org.seamware.tmforum.party.model.CharacteristicVO().name("did").value(TEST_CONSUMER_DID)))));
        when(participantResolver.getOrganization(eq(TEST_PROVIDER_TMF_ID)))
                .thenReturn(Optional.of(new OrganizationVO().partyCharacteristic(List.of(new org.seamware.tmforum.party.model.CharacteristicVO().name("did").value(TEST_PROVIDER_DID)))));

        AgreementApiClient agreementApiClient = mock(AgreementApiClient.class);
        when(agreementApiClient.findByNegotiationId(any())).thenReturn(inputAgreement);
        ContractNegotiation contractNegotiation = tmfEdcMapper.toContractNegotiation(new ArrayList<>(inputQuotes), agreementApiClient, participantResolver, participantInput);
        assertEquals(SORTED_MAPPER.writeValueAsString(expectedNegotiation), SORTED_MAPPER.writeValueAsString(contractNegotiation), "The negotiation should have correctly been extracted.");
    }

    @Test
    public void toContractNegotiation_failure_invalid_state() {
        AgreementApiClient agreementApiClient = mock(AgreementApiClient.class);
        ExtendableQuoteVO failingQuote = getExtendableQuoteVo(negotiationState("INVALID", false), QuoteStateTypeVO.IN_PROGRESS, TEST_CONSUMER_TMF_ID, TEST_PROVIDER_TMF_ID, 1, List.of(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1")));
        assertThrows(IllegalArgumentException.class, () -> tmfEdcMapper.toContractNegotiation(new ArrayList<>(List.of(failingQuote)), agreementApiClient, participantResolver, TEST_CONSUMER_DID), "If the negotiation state does not contain a valid state, an exception should be thrown.");
    }

    @Test
    public void toContractNegotiation_failure_no_state() {
        AgreementApiClient agreementApiClient = mock(AgreementApiClient.class);
        ExtendableQuoteVO failingQuote = getExtendableQuoteVo(null, QuoteStateTypeVO.IN_PROGRESS, TEST_CONSUMER_TMF_ID, TEST_PROVIDER_TMF_ID, 1, List.of(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1")));
        assertThrows(IllegalArgumentException.class, () -> tmfEdcMapper.toContractNegotiation(new ArrayList<>(List.of(failingQuote)), agreementApiClient, participantResolver, TEST_CONSUMER_DID), "If no negotiation state is contained, an exception should be thrown.");
    }

    // ---- FROM CONTRACT OFFERS ----

    @ParameterizedTest
    @MethodSource("getValidConsumerOffers")
    public void fromConsumerContractOffer_success(ContractOffer contractOffer, String negotiationState, ExtendableQuoteItemVO expectedItem) {

        when(typeTransformerRegistry.transform(any(), eq(JsonObject.class))).thenReturn(Result.success(getEmptyOdrlContract()));
        when(jsonLd.expand(any())).thenReturn(Result.success(getEmptyOdrlContract()));

        assertEquals(tmfEdcMapper.fromConsumerContractOffer(contractOffer, negotiationState), expectedItem);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "offer-1", "offer:asset", "offer:asset:my:id"})
    public void fromContractOffer_failure_invalid_id(String offerId) {
        assertThrows(IllegalArgumentException.class, () -> tmfEdcMapper.fromConsumerContractOffer(getOffer(offerId), "INITIAL"), "If an invalid id is provided, an exception should be thrown.");
        assertThrows(IllegalArgumentException.class, () -> tmfEdcMapper.fromProviderContractOffer(getOffer(offerId), "INITIAL", Optional.empty()), "If an invalid id is provided, an exception should be thrown.");
    }

    @ParameterizedTest
    @MethodSource("getValidProviderOffers")
    public void fromProviderContract_success(ContractOffer contractOffer, String negotiationState, Optional<String> offeringId, ExtendableQuoteItemVO expectedItem) {

        when(typeTransformerRegistry.transform(any(), eq(JsonObject.class))).thenReturn(Result.success(getEmptyOdrlContract()));
        when(jsonLd.expand(any())).thenReturn(Result.success(getEmptyOdrlContract()));

        assertEquals(tmfEdcMapper.fromProviderContractOffer(contractOffer, negotiationState, offeringId), expectedItem);
    }

    // ---- ASSET FROM PRODUCT SPEC ----

    @ParameterizedTest
    @MethodSource("getValidProductSpecs")
    public void assetFromProductSpec_success(ExtendableProductSpecification productSpecification, Asset expectedAsset) throws JsonProcessingException {
        Optional<Asset> asset = tmfEdcMapper.assetFromProductSpec(productSpecification);
        assertTrue(asset.isPresent(), "The asset should have been extracted from the product spec.");

        assertEquals(SORTED_MAPPER.writeValueAsString(expectedAsset), SORTED_MAPPER.writeValueAsString(asset.get()),
                "The asset should have been correctly extracted.");
    }

    @Test
    public void assetFromProductSpec_failure_no_upstream() {
        assertTrue(
                tmfEdcMapper.assetFromProductSpec(getTestProductSpec(
                        List.of(new Endpoint("endpoint", "http://end.point")),
                        Optional.of(TEST_ASSET_ID),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty())).isEmpty(), "Without an upstream, the product cannot be supported.");
    }

    @Test
    public void assetFromProductSpec_failure_no_asset_id() {
        assertTrue(
                tmfEdcMapper.assetFromProductSpec(getTestProductSpec(
                        List.of(new Endpoint("endpoint", "http://end.point")),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of("upstream"))).isEmpty(), "Without an external Id, the product cannot be supported.");
    }

    // ---- ID FROM POLICY ----
    @Test
    public void getIdFromPolicy_success() {
        assertEquals("uid", TMFEdcMapper.getIdFromPolicy(getTestPolicy()));
    }

    @Test
    public void getIdFromPolicy_failure_no_id() {
        Policy testPolicy = getTestPolicy();
        testPolicy.getExtensibleProperties().remove("http://www.w3.org/ns/odrl/2/uid");
        assertThrows(IllegalArgumentException.class, () -> TMFEdcMapper.getIdFromPolicy(testPolicy), "No policy without an Id should exist.");
    }

    // ---- CONTRACT FROM PRODUCT OFFER ----

    @Test
    public void fromProductOffer_success() throws JsonProcessingException {

        when(typeTransformerRegistry.transform(any(), eq(Policy.class)))
                .thenReturn(Result.success(getTestPolicy("contractPolicy")))
                .thenReturn(Result.success(getTestPolicy("accessPolicy")));
        when(jsonLd.expand(any())).thenReturn(Result.success(getEmptyOdrlContract()));
        assertEquals(
                SORTED_MAPPER.writeValueAsString(getContractDefintion(TEST_OFFER_ID, "accessPolicy", "contractPolicy")),
                SORTED_MAPPER.writeValueAsString(
                        tmfEdcMapper.fromProductOffer(getExtendableProductOffering(TEST_OFFER_ID, Optional.of(getTestOdrlPolicy()), Optional.of(getTestOdrlPolicy()))).get()));
    }

    @Test
    public void fromProductOffer_failure_no_contract_policy() throws JsonProcessingException {

        when(typeTransformerRegistry.transform(any(), eq(Policy.class)))
                .thenReturn(Result.success(getTestPolicy("contractPolicy")))
                .thenReturn(Result.success(getTestPolicy("accessPolicy")));
        when(jsonLd.expand(any())).thenReturn(Result.success(getEmptyOdrlContract()));
        assertTrue(tmfEdcMapper.fromProductOffer(getExtendableProductOffering(TEST_OFFER_ID, Optional.of(getTestOdrlPolicy()), Optional.empty())).isEmpty(),
                "A contract policy is required.");
    }

    @Test
    public void fromProductOffer_failure_no_access_policy() throws JsonProcessingException {

        when(typeTransformerRegistry.transform(any(), eq(Policy.class)))
                .thenReturn(Result.success(getTestPolicy("contractPolicy")))
                .thenReturn(Result.success(getTestPolicy("accessPolicy")));
        when(jsonLd.expand(any())).thenReturn(Result.success(getEmptyOdrlContract()));
        assertTrue(tmfEdcMapper.fromProductOffer(getExtendableProductOffering(TEST_OFFER_ID, Optional.empty(), Optional.of(getTestOdrlPolicy()))).isEmpty(),
                "A contract policy is required.");
    }

    @Test
    public void fromProductOffer_failure_no_external_id() throws JsonProcessingException {

        when(typeTransformerRegistry.transform(any(), eq(Policy.class)))
                .thenReturn(Result.success(getTestPolicy("contractPolicy")))
                .thenReturn(Result.success(getTestPolicy("accessPolicy")));
        when(jsonLd.expand(any())).thenReturn(Result.success(getEmptyOdrlContract()));
        assertTrue(tmfEdcMapper.fromProductOffer(getExtendableProductOffering(null, Optional.of(getTestOdrlPolicy()), Optional.of(getTestOdrlPolicy()))).isEmpty(),
                "An external id is required.");
    }


    // ---- CONSUMER OFFER FROM PRODUCT OFFER ----

    @Test
    public void consumerOfferFromProductOffering_success() throws JsonProcessingException {
        when(typeTransformerRegistry.transform(any(), eq(Policy.class)))
                .thenReturn(Result.success(getTestPolicy("contractPolicy")))
                .thenReturn(Result.success(getTestPolicy("accessPolicy")))
                .thenReturn(Result.success(getTestPolicy("accessPolicy")))
                .thenReturn(Result.success(getTestPolicy("contractPolicy")));
        when(jsonLd.expand(any())).thenReturn(Result.success(getEmptyOdrlContract()));

        ContractOfferId contractOfferId = ContractOfferId.create(TEST_OFFER_ID, TEST_ASSET_ID);
        Optional<ValidatableConsumerOffer> optionalValidatableConsumerOffer = tmfEdcMapper.consumerOfferFromProductOffering(getExtendableProductOffering(TEST_OFFER_ID, Optional.of(getTestOdrlPolicy()), Optional.of(getTestOdrlPolicy())), contractOfferId);
        assertTrue(optionalValidatableConsumerOffer.isPresent(), "The offer should have been returned");

        ValidatableConsumerOffer expectedOffer = ValidatableConsumerOffer.Builder.newInstance()
                .offerId(contractOfferId)
                .accessPolicy(getTestPolicy("accessPolicy"))
                .contractPolicy(getTestPolicy("contractPolicy"))
                .contractDefinition(getContractDefintion(TEST_OFFER_ID, "accessPolicy", "contractPolicy"))
                .build();

        assertEquals(
                SORTED_MAPPER.writeValueAsString(expectedOffer.getAccessPolicy()),
                SORTED_MAPPER.writeValueAsString(optionalValidatableConsumerOffer.get().getAccessPolicy()),
                "The correct access policy should have been included.");
        assertEquals(
                SORTED_MAPPER.writeValueAsString(expectedOffer.getContractPolicy()),
                SORTED_MAPPER.writeValueAsString(optionalValidatableConsumerOffer.get().getContractPolicy()),
                "The correct contract policy should have been included.");
        assertEquals(
                SORTED_MAPPER.writeValueAsString(expectedOffer.getContractDefinition()),
                SORTED_MAPPER.writeValueAsString(optionalValidatableConsumerOffer.get().getContractDefinition()),
                "The correct contract definition should have been included.");
        assertEquals(
                expectedOffer.getOfferId(),
                optionalValidatableConsumerOffer.get().getOfferId(),
                "The correct offer id should have been included.");
    }

    @Test
    public void consumerOfferFromProductOffering_failure_no_definition_term() throws JsonProcessingException {
        when(typeTransformerRegistry.transform(any(), eq(Policy.class)))
                .thenReturn(Result.success(getTestPolicy("contractPolicy")))
                .thenReturn(Result.success(getTestPolicy("accessPolicy")))
                .thenReturn(Result.success(getTestPolicy("accessPolicy")))
                .thenReturn(Result.success(getTestPolicy("contractPolicy")));
        when(jsonLd.expand(any())).thenReturn(Result.success(getEmptyOdrlContract()));

        assertTrue(tmfEdcMapper.consumerOfferFromProductOffering(getNonCDProductOffering(), ContractOfferId.create(TEST_OFFER_ID, TEST_ASSET_ID)).isEmpty(),
                "With out a term containing the contract definition, no offering should be returned");
    }

    @Test
    public void consumerOfferFromProductOffering_failure_no_access_policy() throws JsonProcessingException {
        when(typeTransformerRegistry.transform(any(), eq(Policy.class)))
                .thenReturn(Result.success(getTestPolicy("contractPolicy")))
                .thenReturn(Result.success(getTestPolicy("accessPolicy")))
                .thenReturn(Result.success(getTestPolicy("accessPolicy")))
                .thenReturn(Result.success(getTestPolicy("contractPolicy")));
        when(jsonLd.expand(any())).thenReturn(Result.success(getEmptyOdrlContract()));

        assertTrue(tmfEdcMapper.consumerOfferFromProductOffering(
                        getExtendableProductOffering(TEST_OFFER_ID, Optional.empty(), Optional.of(getTestOdrlPolicy())),
                        ContractOfferId.create(TEST_OFFER_ID, TEST_ASSET_ID)).isEmpty(),
                "With out an access policy, no offering should be returned");
    }

    @Test
    public void consumerOfferFromProductOffering_failure_no_contract_policy() throws JsonProcessingException {
        when(typeTransformerRegistry.transform(any(), eq(Policy.class)))
                .thenReturn(Result.success(getTestPolicy("contractPolicy")))
                .thenReturn(Result.success(getTestPolicy("accessPolicy")))
                .thenReturn(Result.success(getTestPolicy("accessPolicy")))
                .thenReturn(Result.success(getTestPolicy("contractPolicy")));
        when(jsonLd.expand(any())).thenReturn(Result.success(getEmptyOdrlContract()));

        assertTrue(tmfEdcMapper.consumerOfferFromProductOffering(
                        getExtendableProductOffering(TEST_OFFER_ID, Optional.of(getTestOdrlPolicy()), Optional.empty()),
                        ContractOfferId.create(TEST_OFFER_ID, TEST_ASSET_ID)).isEmpty(),
                "With out a contract policy, no offering should be returned");
    }

    @Test
    public void consumerOfferFromProductOffering_failure_no_valid_policy() throws JsonProcessingException {
        when(typeTransformerRegistry.transform(any(), eq(Policy.class)))
                .thenReturn(Result.success(getTestPolicy("contractPolicy")))
                .thenReturn(Result.success(getTestPolicy("accessPolicy")))
                .thenReturn(Result.failure("invalid policy"));
        when(jsonLd.expand(any())).thenReturn(Result.success(getEmptyOdrlContract()));

        assertTrue(tmfEdcMapper.consumerOfferFromProductOffering(
                        getExtendableProductOffering(TEST_OFFER_ID, Optional.of(getTestOdrlPolicy()), Optional.empty()),
                        ContractOfferId.create(TEST_OFFER_ID, TEST_ASSET_ID)).isEmpty(),
                "With out a valid policy, no offering should be returned");
    }

    private static ContractDefinition getContractDefintion(String id, String accessPolicyId, String contractPolicyId) {
        return ContractDefinition.Builder.newInstance()
                .clock(clock)
                .accessPolicyId(accessPolicyId)
                .contractPolicyId(contractPolicyId)
                .id(id)
                .build();
    }

    private static Stream<Arguments> getValidProductSpecs() {
        return Stream.of(
                Arguments.of(
                        getTestProductSpec(List.of(new Endpoint("endpoint-1", "http://end.point"))),
                        getAsset(TEST_ASSET_ID, TEST_VERSION, TEST_NAME, TEST_SPEC_DESCRIPTION, getDataAddress("http://end.point", TEST_DESCRIPTION))),
                Arguments.of(
                        getTestProductSpec(List.of(new Endpoint("endpoint-1", "http://end.point")), Optional.of(TEST_ASSET_ID), Optional.of("version"), Optional.empty(), Optional.empty(), Optional.of("http://upstream.svc.local")),
                        getAsset(TEST_ASSET_ID, "version", null, null, getDataAddress("http://end.point", TEST_DESCRIPTION))),
                Arguments.of(
                        getTestProductSpec(List.of(new Endpoint("endpoint-1", "http://end.point")), Optional.of(TEST_ASSET_ID), Optional.of("version"), Optional.of("name"), Optional.empty(), Optional.of("http://upstream.svc.local")),
                        getAsset(TEST_ASSET_ID, "version", "name", null, getDataAddress("http://end.point", TEST_DESCRIPTION))),
                Arguments.of(
                        getTestProductSpec(List.of(new Endpoint("endpoint-1", "http://end.point")), Optional.of(TEST_ASSET_ID), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of("http://upstream.svc.local")),
                        getAsset(TEST_ASSET_ID, null, null, null, getDataAddress("http://end.point", TEST_DESCRIPTION)))
        );
    }

    private static DataAddress getDataAddress(String url, String description) {
        return DataAddress.Builder.newInstance()
                .type("FDSC")
                .property("endpointUrl", url)
                .property("endpointDescription", description)
                .build();
    }

    private static Asset getAsset(String assetId, String assetVersion, String name, String description, DataAddress dataAddress) {
        return Asset.Builder.newInstance()
                .clock(clock)
                .id(assetId)
                .version(assetVersion)
                .name(name)
                .description(description)
                .dataAddress(dataAddress)
                .build();
    }

    private static Stream<Arguments> getValidProviderOffers() {
        return Stream.of(
                Arguments.of(getOffer("offer:asset:1"), "INITIAL", Optional.empty(), getExtendableQuoteItem("1", "asset", "offer:asset:1", "INITIAL")),
                Arguments.of(getOffer("offer-2:asset-2:1"), "VALIDATED", Optional.empty(), getExtendableQuoteItem("1", "asset-2", "offer-2:asset-2:1", "VALIDATED")),
                Arguments.of(getOffer("offer-2:asset-2:1"), "VALIDATED", Optional.of("urn:ngsi-ld:product-offering:1"), getExtendableQuoteItem("1", "asset-2", "offer-2:asset-2:1", "VALIDATED", Optional.of("urn:ngsi-ld:product-offering:1")))
        );
    }

    private static Stream<Arguments> getValidConsumerOffers() {
        return Stream.of(
                Arguments.of(getOffer("offer:asset:1"), "INITIAL", getExtendableQuoteItem("1", "asset", "offer:asset:1", "INITIAL")),
                Arguments.of(getOffer("offer-2:asset-2:1"), "VALIDATED", getExtendableQuoteItem("1", "asset-2", "offer-2:asset-2:1", "VALIDATED"))
        );
    }

    private static ExtendableQuoteItemVO getExtendableQuoteItem(String id, String assetId, String offerId, String negotiationState, Optional<String> productOfferId) {
        ExtendableQuoteItemVO extendableQuoteItemVO = new ExtendableQuoteItemVO()
                .setDatasetId(assetId)
                .setExternalId(offerId);
        extendableQuoteItemVO.state(negotiationState)
                .action("add")
                .id(id);
        productOfferId.ifPresent(pofId -> extendableQuoteItemVO.productOffering(new ProductOfferingRefVO().id(pofId)));
        return extendableQuoteItemVO;
    }

    private static ExtendableQuoteItemVO getExtendableQuoteItem(String id, String assetId, String offerId, String negotiationState) {
        return getExtendableQuoteItem(id, assetId, offerId, negotiationState, Optional.empty());
    }

    private static Stream<Arguments> getNegotiations() {
        List<Arguments> arguments = new ArrayList<>();

        arguments.add(
                Arguments.of(
                        "Initial consumer negotiation in pending should be created.",
                        List.of(getExtendableQuoteVo(negotiationState("INITIAL", true), QuoteStateTypeVO.IN_PROGRESS, TEST_CONSUMER_TMF_ID, TEST_PROVIDER_TMF_ID, 1, List.of(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1")))),
                        Optional.empty(),
                        TEST_CONSUMER_DID,
                        getNegotiation(ContractNegotiation.Type.CONSUMER, ContractNegotiationStates.INITIAL, true, TEST_PROVIDER_DID, List.of(getOffer(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1"))))));
        arguments.add(
                Arguments.of(
                        "Initial consumer negotiation in pending false should be created.",
                        List.of(getExtendableQuoteVo(negotiationState("INITIAL", false), QuoteStateTypeVO.IN_PROGRESS, TEST_CONSUMER_TMF_ID, TEST_PROVIDER_TMF_ID, 1, List.of(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1")))),
                        Optional.empty(),
                        TEST_CONSUMER_DID,
                        getNegotiation(ContractNegotiation.Type.CONSUMER, ContractNegotiationStates.INITIAL, false, TEST_PROVIDER_DID, List.of(getOffer(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1"))))));
        arguments.add(
                Arguments.of(
                        "Initial provider negotiation in pending false should be created.",
                        List.of(getExtendableQuoteVo(negotiationState("INITIAL", false), QuoteStateTypeVO.IN_PROGRESS, TEST_CONSUMER_TMF_ID, TEST_PROVIDER_TMF_ID, 1, List.of(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1")))),
                        Optional.empty(),
                        TEST_PROVIDER_DID,
                        getNegotiation(ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.INITIAL, false, TEST_CONSUMER_DID, List.of(getOffer(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1"))))));
        arguments.add(
                Arguments.of(
                        "Initial provider negotiation in pending false should be created. Terminated quote should be ignored.",
                        List.of(
                                getExtendableQuoteVo(negotiationState("TERMINATED", false), QuoteStateTypeVO.CANCELLED, TEST_CONSUMER_TMF_ID, TEST_PROVIDER_TMF_ID, 1, List.of(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1"))),
                                getExtendableQuoteVo(negotiationState("INITIAL", false), QuoteStateTypeVO.IN_PROGRESS, TEST_CONSUMER_TMF_ID, TEST_PROVIDER_TMF_ID, 10, List.of(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "2")))
                        ),
                        Optional.empty(),
                        TEST_PROVIDER_DID,
                        getNegotiation(ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.INITIAL, false, TEST_CONSUMER_DID, List.of(
                                getOffer(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1")),
                                getOffer(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "2"))))));
        arguments.add(
                Arguments.of(
                        "Initial provider negotiation in pending false should be created. Old quotes should be ignored.",
                        List.of(
                                getExtendableQuoteVo(negotiationState("TERMINATED", false), QuoteStateTypeVO.ACCEPTED, TEST_CONSUMER_TMF_ID, TEST_PROVIDER_TMF_ID, 5, List.of(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "2"))),
                                getExtendableQuoteVo(negotiationState("TERMINATED", false), QuoteStateTypeVO.CANCELLED, TEST_CONSUMER_TMF_ID, TEST_PROVIDER_TMF_ID, 1, List.of(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1"))),
                                getExtendableQuoteVo(negotiationState("INITIAL", false), QuoteStateTypeVO.IN_PROGRESS, TEST_CONSUMER_TMF_ID, TEST_PROVIDER_TMF_ID, 10, List.of(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "3")))
                        ),
                        Optional.empty(),
                        TEST_PROVIDER_DID,
                        getNegotiation(ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.INITIAL, false, TEST_CONSUMER_DID, List.of(
                                getOffer(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1")),
                                getOffer(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "2")),
                                getOffer(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "3"))))));
        arguments.add(
                Arguments.of(
                        "Initial provider negotiation in pending false should be created. Old quotes should be ignored.",
                        List.of(
                                getExtendableQuoteVo(negotiationState("TERMINATED", false), QuoteStateTypeVO.CANCELLED, TEST_CONSUMER_TMF_ID, TEST_PROVIDER_TMF_ID, 1, List.of(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1"))),
                                getExtendableQuoteVo(negotiationState("TERMINATED", false), QuoteStateTypeVO.ACCEPTED, TEST_CONSUMER_TMF_ID, TEST_PROVIDER_TMF_ID, 1, List.of(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "2"))),
                                getExtendableQuoteVo(negotiationState("INITIAL", false), QuoteStateTypeVO.IN_PROGRESS, TEST_CONSUMER_TMF_ID, TEST_PROVIDER_TMF_ID, 10, List.of(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "3")))
                        ),
                        Optional.empty(),
                        TEST_PROVIDER_DID,
                        getNegotiation(ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.INITIAL, false, TEST_CONSUMER_DID, List.of(
                                getOffer(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1")),
                                getOffer(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "2")),
                                getOffer(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "3"))))));
        arguments.add(
                Arguments.of(
                        "Requesting provider negotiation in pending false should be created.",
                        List.of(
                                getExtendableQuoteVo(negotiationState("REQUESTING", false), QuoteStateTypeVO.IN_PROGRESS, TEST_CONSUMER_TMF_ID, TEST_PROVIDER_TMF_ID, 10, List.of(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1")))
                        ),
                        Optional.empty(),
                        TEST_PROVIDER_DID,
                        getNegotiation(ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.REQUESTING, false, TEST_CONSUMER_DID, List.of(getOffer(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1"))))));
        arguments.add(
                Arguments.of(
                        "Requesting provider negotiation in pending false should be created.",
                        List.of(
                                getExtendableQuoteVo(negotiationState("REQUESTING", false), QuoteStateTypeVO.IN_PROGRESS, TEST_CONSUMER_TMF_ID, TEST_PROVIDER_TMF_ID, 10, List.of(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1")))
                        ),
                        Optional.empty(),
                        TEST_CONSUMER_DID,
                        getNegotiation(ContractNegotiation.Type.CONSUMER, ContractNegotiationStates.REQUESTING, false, TEST_PROVIDER_DID, List.of(getOffer(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1"))))));
        arguments.add(
                Arguments.of(
                        "Requested provider negotiation in pending false should be created.",
                        List.of(
                                getExtendableQuoteVo(negotiationState("REQUESTED", false), QuoteStateTypeVO.APPROVED, TEST_CONSUMER_TMF_ID, TEST_PROVIDER_TMF_ID, 10, List.of(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1")))
                        ),
                        Optional.empty(),
                        TEST_PROVIDER_DID,
                        getNegotiation(ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.REQUESTED, false, TEST_CONSUMER_DID, List.of(getOffer(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1"))))));
        arguments.add(
                Arguments.of(
                        "Requested consumer negotiation in pending false should be created.",
                        List.of(
                                getExtendableQuoteVo(negotiationState("REQUESTED", false), QuoteStateTypeVO.APPROVED, TEST_CONSUMER_TMF_ID, TEST_PROVIDER_TMF_ID, 10, List.of(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1")))
                        ),
                        Optional.empty(),
                        TEST_CONSUMER_DID,
                        getNegotiation(ContractNegotiation.Type.CONSUMER, ContractNegotiationStates.REQUESTED, false, TEST_PROVIDER_DID, List.of(getOffer(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1"))))));
        arguments.add(
                Arguments.of(
                        "Requested consumer negotiation in pending false should be created.",
                        List.of(
                                getExtendableQuoteVo(negotiationState("REQUESTED", false), QuoteStateTypeVO.APPROVED, TEST_CONSUMER_TMF_ID, TEST_PROVIDER_TMF_ID, 10, List.of(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1")))
                        ),
                        Optional.empty(),
                        TEST_CONSUMER_DID,
                        getNegotiation(ContractNegotiation.Type.CONSUMER, ContractNegotiationStates.REQUESTED, false, TEST_PROVIDER_DID, List.of(getOffer(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1"))))));
        arguments.add(
                Arguments.of(
                        "Offering consumer negotiation in pending false should be created.",
                        List.of(
                                getExtendableQuoteVo(negotiationState("OFFERING", false), QuoteStateTypeVO.APPROVED, TEST_CONSUMER_TMF_ID, TEST_PROVIDER_TMF_ID, 10, List.of(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1")))
                        ),
                        Optional.empty(),
                        TEST_CONSUMER_DID,
                        getNegotiation(ContractNegotiation.Type.CONSUMER, ContractNegotiationStates.OFFERING, false, TEST_PROVIDER_DID, List.of(getOffer(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1"))))));
        arguments.add(
                Arguments.of(
                        "Offered consumer negotiation in pending false should be created.",
                        List.of(
                                getExtendableQuoteVo(negotiationState("OFFERED", false), QuoteStateTypeVO.APPROVED, TEST_CONSUMER_TMF_ID, TEST_PROVIDER_TMF_ID, 10, List.of(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1")))
                        ),
                        Optional.empty(),
                        TEST_CONSUMER_DID,
                        getNegotiation(ContractNegotiation.Type.CONSUMER, ContractNegotiationStates.OFFERED, false, TEST_PROVIDER_DID, List.of(getOffer(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1"))))));
        arguments.add(
                Arguments.of(
                        "Accepting consumer negotiation in pending false should be created.",
                        List.of(
                                getExtendableQuoteVo(negotiationState("ACCEPTING", false), QuoteStateTypeVO.ACCEPTED, TEST_CONSUMER_TMF_ID, TEST_PROVIDER_TMF_ID, 10, List.of(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1")))
                        ),
                        Optional.empty(),
                        TEST_CONSUMER_DID,
                        getNegotiation(ContractNegotiation.Type.CONSUMER, ContractNegotiationStates.ACCEPTING, false, TEST_PROVIDER_DID, List.of(getOffer(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1"))))));
        arguments.add(
                Arguments.of(
                        "Accepted consumer negotiation in pending false should be created.",
                        List.of(
                                getExtendableQuoteVo(negotiationState("ACCEPTED", false), QuoteStateTypeVO.ACCEPTED, TEST_CONSUMER_TMF_ID, TEST_PROVIDER_TMF_ID, 10, List.of(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1")))
                        ),
                        Optional.empty(),
                        TEST_CONSUMER_DID,
                        getNegotiation(ContractNegotiation.Type.CONSUMER, ContractNegotiationStates.ACCEPTED, false, TEST_PROVIDER_DID, List.of(getOffer(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1"))))));
        arguments.add(
                Arguments.of(
                        "Agreeing consumer negotiation in pending false should be created.",
                        List.of(
                                getExtendableQuoteVo(negotiationState("AGREEING", false), QuoteStateTypeVO.ACCEPTED, TEST_CONSUMER_TMF_ID, TEST_PROVIDER_TMF_ID, 10, List.of(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1")))
                        ),
                        Optional.of(getTestAgreement()),
                        TEST_CONSUMER_DID,
                        getNegotiation(ContractNegotiation.Type.CONSUMER, ContractNegotiationStates.AGREEING, false, TEST_PROVIDER_DID, List.of(getOffer(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1"))), Optional.of(getValidContractAgreement()))));
        arguments.add(
                Arguments.of(
                        "Agreeing consumer negotiation in pending false should be created.",
                        List.of(
                                getExtendableQuoteVo(negotiationState("AGREED", false), QuoteStateTypeVO.ACCEPTED, TEST_CONSUMER_TMF_ID, TEST_PROVIDER_TMF_ID, 10, List.of(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1")))
                        ),
                        Optional.of(getTestAgreement()),
                        TEST_CONSUMER_DID,
                        getNegotiation(ContractNegotiation.Type.CONSUMER, ContractNegotiationStates.AGREED, false, TEST_PROVIDER_DID, List.of(getOffer(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1"))), Optional.of(getValidContractAgreement()))));
        arguments.add(
                Arguments.of(
                        "Verifying consumer negotiation in pending false should be created.",
                        List.of(
                                getExtendableQuoteVo(negotiationState("VERIFYING", false), QuoteStateTypeVO.ACCEPTED, TEST_CONSUMER_TMF_ID, TEST_PROVIDER_TMF_ID, 10, List.of(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1")))
                        ),
                        Optional.of(getTestAgreement()),
                        TEST_CONSUMER_DID,
                        getNegotiation(ContractNegotiation.Type.CONSUMER, ContractNegotiationStates.VERIFYING, false, TEST_PROVIDER_DID, List.of(getOffer(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1"))), Optional.of(getValidContractAgreement()))));
        arguments.add(
                Arguments.of(
                        "Verified consumer negotiation in pending false should be created.",
                        List.of(
                                getExtendableQuoteVo(negotiationState("VERIFIED", false), QuoteStateTypeVO.ACCEPTED, TEST_CONSUMER_TMF_ID, TEST_PROVIDER_TMF_ID, 10, List.of(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1")))
                        ),
                        Optional.of(getTestAgreement()),
                        TEST_CONSUMER_DID,
                        getNegotiation(ContractNegotiation.Type.CONSUMER, ContractNegotiationStates.VERIFIED, false, TEST_PROVIDER_DID, List.of(getOffer(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1"))), Optional.of(getValidContractAgreement()))));
        arguments.add(
                Arguments.of(
                        "Finalizing consumer negotiation in pending false should be created.",
                        List.of(
                                getExtendableQuoteVo(negotiationState("FINALIZING", false), QuoteStateTypeVO.ACCEPTED, TEST_CONSUMER_TMF_ID, TEST_PROVIDER_TMF_ID, 10, List.of(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1")))
                        ),
                        Optional.of(getTestAgreement()),
                        TEST_CONSUMER_DID,
                        getNegotiation(ContractNegotiation.Type.CONSUMER, ContractNegotiationStates.FINALIZING, false, TEST_PROVIDER_DID, List.of(getOffer(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1"))), Optional.of(getValidContractAgreement()))));
        arguments.add(
                Arguments.of(
                        "Finalized consumer negotiation in pending false should be created.",
                        List.of(
                                getExtendableQuoteVo(negotiationState("FINALIZED", false), QuoteStateTypeVO.ACCEPTED, TEST_CONSUMER_TMF_ID, TEST_PROVIDER_TMF_ID, 10, List.of(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1")))
                        ),
                        Optional.of(getTestAgreement()),
                        TEST_CONSUMER_DID,
                        getNegotiation(ContractNegotiation.Type.CONSUMER, ContractNegotiationStates.FINALIZED, false, TEST_PROVIDER_DID, List.of(getOffer(String.format("%s:%s:%s", TEST_OFFER_ID, TEST_ASSET_ID, "1"))), Optional.of(getValidContractAgreement()))));

        return arguments.stream();
    }

    private static ExtendableQuoteVO getExtendableQuoteVo(ContractNegotiationState contractNegotiationState, QuoteStateTypeVO quoteState, String consumerId, String providerId, long quoteDate, List<String> offers) {
        ExtendableQuoteVO quoteVO = new ExtendableQuoteVO();
        quoteVO.setContractNegotiationState(contractNegotiationState);
        quoteVO.setExternalId(TEST_NEGOTIATION_ID);
        quoteVO.setState(quoteState);
        quoteVO.setQuoteDate(OffsetDateTime.ofInstant(Instant.ofEpochSecond(quoteDate), TimeZone.getDefault().toZoneId()));
        quoteVO.setRelatedParty(
                List.of(new org.seamware.tmforum.quote.model.RelatedPartyVO()
                                .id(consumerId)
                                .role("Consumer"),
                        new org.seamware.tmforum.quote.model.RelatedPartyVO()
                                .id("someoneElse")
                                .role("Manager"),
                        new org.seamware.tmforum.quote.model.RelatedPartyVO()
                                .id(providerId)
                                .role("Provider")));

        quoteVO.setExtendableQuoteItem(offers.stream()
                .map(offer -> new ExtendableQuoteItemVO()
                        .setDatasetId(TEST_ASSET_ID)
                        .setExternalId(offer)
                        .setPolicy(getTestOffer()))
                .toList());
        return quoteVO;
    }

    private static ContractOffer getOffer(String id) {
        return ContractOffer.Builder.newInstance()
                .id(id)
                .assetId(TEST_ASSET_ID)
                .policy(getTestPolicy())
                .build();
    }

    private static ContractNegotiation getNegotiation(ContractNegotiation.Type type, ContractNegotiationStates state, boolean pending, String counterParty, List<ContractOffer> offers, Optional<ContractAgreement> contractAgreement) {
        ContractNegotiation.Builder builder = ContractNegotiation.Builder.newInstance()
                .id(TEST_NEGOTIATION_ID)
                .type(type)
                .counterPartyAddress(TEST_COUNTER_PARTY_ADDRESS)
                .correlationId(TEST_CORRELATION_ID)
                .state(state.code())
                .pending(pending)
                .counterPartyId(counterParty)
                .protocol(Dsp2025Constants.DATASPACE_PROTOCOL_HTTP_V_2025_1)
                .clock(Clock.fixed(Instant.EPOCH, TimeZone.getDefault().toZoneId()));
        offers.forEach(builder::contractOffer);
        contractAgreement.ifPresent(builder::contractAgreement);
        return builder.build();
    }

    private static ContractNegotiation getNegotiation(ContractNegotiation.Type type, ContractNegotiationStates state, boolean pending, String counterParty, List<ContractOffer> offers) {
        return getNegotiation(type, state, pending, counterParty, offers, Optional.empty());
    }

    private static ContractNegotiationState negotiationState(String state, boolean pending) {
        return new ContractNegotiationState()
                .setCounterPartyAddress(TEST_COUNTER_PARTY_ADDRESS)
                .setState(state)
                .setCorrelationId(TEST_CORRELATION_ID)
                .setLeased(false)
                .setControlplane(TEST_CONTROL_PLANE)
                .setPending(pending);
    }

    private static Stream<Arguments> getInvalidSpecs() {
        List<Arguments> arguments = new ArrayList<>();
        arguments.add(Arguments.of(new ExtendableProductSpecification()));
        arguments.add(Arguments.of(new ExtendableProductSpecification().setExternalId(TEST_ASSET_ID)));
        arguments.add(Arguments.of(getTestProductSpec(List.of())));

        return arguments.stream();
    }

    private static Stream<Arguments> getInvalidOfferings() {

        ExtendableProductOffering extendableProductOfferingWrongPolicy = new ExtendableProductOffering()
                .setExternalId(TEST_ASSET_ID);
        ProductOfferingTermVO extendableProductOfferingTermWrongPolicy = new ExtendableProductOfferingTerm()
                .additionalProperties(Map.of("wrongPolicy", getTestOdrlPolicy()))
                .name("edc:contractDefinition");
        extendableProductOfferingWrongPolicy.addProductOfferingTermItem(extendableProductOfferingTermWrongPolicy);

        ExtendableProductOffering extendableProductOfferingWrongName = new ExtendableProductOffering()
                .setExternalId(TEST_ASSET_ID);
        ProductOfferingTermVO extendableProductOfferingTermWrongName = new ExtendableProductOfferingTerm()
                .additionalProperties(Map.of("contractPolicy", getTestOdrlPolicy()))
                .name("wrongDefinition");
        extendableProductOfferingWrongName.addProductOfferingTermItem(extendableProductOfferingTermWrongName);

        return Stream.of(
                Arguments.of(new ExtendableProductOffering()),
                Arguments.of(new ExtendableProductOffering().setExternalId(TEST_ASSET_ID)),
                Arguments.of(extendableProductOfferingWrongPolicy),
                Arguments.of(extendableProductOfferingWrongName)
        );
    }

    private static Stream<Arguments> getValidProductOfferings() {
        List<Arguments> arguments = new ArrayList<>();

        ExtendableProductOffering extendableProductOffering1 = getExtendableProductOffering();

        Dataset expectedDataset1 = Dataset.Builder.newInstance()
                .offers(Map.of(TEST_OFFER_ID, getTestPolicy()))
                .build();

        arguments.add(Arguments.of(extendableProductOffering1, Optional.empty(), expectedDataset1));

        ProductOfferingTermVO extendableProductOfferingTerm2 = new ExtendableProductOfferingTerm()
                .additionalProperties(Map.of("contractPolicy", getTestOdrlPolicy()))
                .name("edc:contractDefinition");
        ExtendableProductOffering extendableProductOffering2 = new ExtendableProductOffering()
                .setExternalId(TEST_OFFER_ID);
        extendableProductOffering2.setExtendableProductOfferingTerm(List.of((ExtendableProductOfferingTerm) extendableProductOfferingTerm2));

        Dataset expectedDataset2 = Dataset.Builder.newInstance()
                .id(TEST_ASSET_ID)
                .offers(Map.of(TEST_OFFER_ID, getTestPolicy()))
                .distribution(Distribution.Builder.newInstance()
                        .format("http")
                        .dataService(DataService.Builder.newInstance()
                                .id("test-1")
                                .endpointUrl("http://end.point")
                                .endpointDescription("Description")
                                .build())
                        .build())
                .build();

        arguments.add(
                Arguments.of(extendableProductOffering2,
                        Optional.of(getTestProductSpec(List.of(new Endpoint("test-1", "http://end.point")))),
                        expectedDataset2));

        ProductOfferingTermVO extendableProductOfferingTerm3 = new ExtendableProductOfferingTerm()
                .additionalProperties(Map.of("contractPolicy", getTestOdrlPolicy()))
                .name("edc:contractDefinition");
        ExtendableProductOffering extendableProductOffering3 = new ExtendableProductOffering()
                .setExternalId(TEST_OFFER_ID);
        extendableProductOffering3.setExtendableProductOfferingTerm(List.of((ExtendableProductOfferingTerm) extendableProductOfferingTerm3));

        Dataset expectedDataset3 = Dataset.Builder.newInstance()
                .id(TEST_ASSET_ID)
                .offers(Map.of(TEST_OFFER_ID, getTestPolicy()))
                .distribution(Distribution.Builder.newInstance()
                        .format("http")
                        .dataService(DataService.Builder.newInstance()
                                .id("test-1")
                                .endpointUrl("http://end.point")
                                .endpointDescription("Description")
                                .build())
                        .build())
                .distribution(Distribution.Builder.newInstance()
                        .format("http")
                        .dataService(DataService.Builder.newInstance()
                                .id("test-2")
                                .endpointUrl("http://other.point")
                                .endpointDescription("Description")
                                .build())
                        .build())
                .build();

        arguments.add(
                Arguments.of(extendableProductOffering3,
                        Optional.of(getTestProductSpec(List.of(new Endpoint("test-1", "http://end.point"), new Endpoint("test-2", "http://other.point")))),
                        expectedDataset3));

        return arguments.stream();
    }

    private static ExtendableProductOffering getNonCDProductOffering() {
        ExtendableProductOffering extendableProductOffering = new ExtendableProductOffering()
                .setExternalId(TEST_OFFER_ID);
        return extendableProductOffering;
    }

    private static ExtendableProductOffering getExtendableProductOffering(String offerId, Optional<Map<String, Object>> accessPolicy, Optional<Map<String, Object>> contractPolicy) {

        ExtendableProductOfferingTerm extendableProductOfferingTerm = new ExtendableProductOfferingTerm();
        accessPolicy.ifPresent(ap -> extendableProductOfferingTerm.setAdditionalProperties("accessPolicy", ap));
        contractPolicy.ifPresent(cp -> extendableProductOfferingTerm.setAdditionalProperties("contractPolicy", cp));
        extendableProductOfferingTerm.name("edc:contractDefinition");
        ExtendableProductOffering extendableProductOffering = new ExtendableProductOffering()
                .setExternalId(offerId);
        extendableProductOffering.setExtendableProductOfferingTerm(List.of(extendableProductOfferingTerm));
        return extendableProductOffering;
    }

    private static ExtendableProductOffering getExtendableProductOffering() {
        return getExtendableProductOffering(TEST_OFFER_ID, Optional.empty(), Optional.of(getTestOdrlPolicy()));
    }

    private record Endpoint(String id, String url) {
    }

    private static ExtendableProductSpecification getTestProductSpec
            (List<Endpoint> endpoints, Optional<String> externalId, Optional<String> version, Optional<String> name, Optional<String> description, Optional<String> upstreamAddress) {
        ExtendableProductSpecification extendableProductSpecification = new ExtendableProductSpecification();
        externalId.ifPresent(extendableProductSpecification::setExternalId);
        version.ifPresent(extendableProductSpecification::setVersion);
        name.ifPresent(extendableProductSpecification::setName);
        description.ifPresent(extendableProductSpecification::setDescription);
        upstreamAddress.ifPresent(ua -> {
            extendableProductSpecification.addProductSpecCharacteristicItem(new ProductSpecificationCharacteristicVO()
                    .id("upstreamAddress")
                    .valueType("upstreamAddress"));
        });
        endpoints.stream()
                .map(endpoint ->
                        new ProductSpecificationCharacteristicVO()
                                .id(endpoint.id())
                                .valueType("endpointUrl")
                                .productSpecCharacteristicValue(List.of(new CharacteristicValueSpecificationVO()
                                        .value(endpoint.url())
                                        .valueType("endpointUrl")
                                        .isDefault(true))))
                .forEach(extendableProductSpecification::addProductSpecCharacteristicItem);
        extendableProductSpecification.addProductSpecCharacteristicItem(
                new ProductSpecificationCharacteristicVO()
                        .id("endpointDescription")
                        .valueType("endpointDescription")
                        .addProductSpecCharacteristicValueItem(new CharacteristicValueSpecificationVO()
                                .value(TEST_DESCRIPTION)));
        return extendableProductSpecification;
    }

    private static ExtendableProductSpecification getTestProductSpec(List<Endpoint> endpoints) {
        return getTestProductSpec(endpoints,
                Optional.of(TEST_ASSET_ID),
                Optional.of(TEST_VERSION),
                Optional.of(TEST_NAME),
                Optional.of(TEST_SPEC_DESCRIPTION),
                Optional.of(TEST_SPEC_DESCRIPTION));
    }

    private static ExtendableAgreementVO getTestAgreement() {
        ExtendableAgreementVO agreementVO = new ExtendableAgreementVO()
                .setExternalId(TEST_AGREEMENT_ID)
                .setNegotiationId(TEST_NEGOTIATION_ID);
        agreementVO.agreementType("dspContract")
                .name(String.format("DSP Contract between %s - %s for %s.", TEST_PROVIDER_DID, TEST_CONSUMER_DID, TEST_ASSET_ID))
                .addEngagedPartyItem(new RelatedPartyVO().role("Consumer").id(TEST_CONSUMER_TMF_ID))
                .addEngagedPartyItem(new RelatedPartyVO().role("Provider").id(TEST_PROVIDER_TMF_ID))
                .addCharacteristicItem(new CharacteristicVO().name("asset-id").value(TEST_ASSET_ID))
                .addCharacteristicItem(new CharacteristicVO().name("provider-id").value(TEST_PROVIDER_DID))
                .addCharacteristicItem(new CharacteristicVO().name("policy").value(getTestOdrlPolicy()))
                .addCharacteristicItem(new CharacteristicVO().name("consumer-id").value(TEST_CONSUMER_DID))
                .addCharacteristicItem(new CharacteristicVO().name("signing-date").value(1));
        return agreementVO;
    }

    private static ContractAgreement getValidContractAgreement() {
        return ContractAgreement.Builder.newInstance()
                .id(TEST_AGREEMENT_ID)
                .assetId(TEST_ASSET_ID)
                .consumerId(TEST_CONSUMER_DID)
                .providerId(TEST_PROVIDER_DID)
                .contractSigningDate(1)
                .policy(getTestPolicy())
                .build();
    }

    private static Action getUse() {
        return Action.Builder.newInstance().type("use").build();
    }

    private static Constraint getTestConstraint() {
        return AtomicConstraint.Builder.newInstance()
                .leftExpression(new LiteralExpression("odrl:dayOfWeek"))
                .operator(Operator.EQ)
                .rightExpression(new LiteralExpression(6))
                .build();
    }

    private static Permission getTestPermission() {
        return Permission.Builder.newInstance()
                .action(getUse())
                .constraint(getTestConstraint())
                .build();
    }

    private static Policy getTestPolicy(String id) {
        return Policy.Builder.newInstance()
                .type(PolicyType.CONTRACT)
                .assigner("assigner")
                .assignee("assignee")
                .permission(getTestPermission())
                .extensibleProperty("http://www.w3.org/ns/odrl/2/uid", id)
                .build();
    }

    private static Policy getTestPolicy() {
        return getTestPolicy("uid");
    }

    private static JsonObject getEmptyOdrlContract() {
        String emptyOdrl = "{}";
        try (JsonReader reader = Json.createReader(new StringReader(emptyOdrl))) {
            return reader.readObject();
        }
    }

    private static JsonObject getIdedPolicy(String id) {
        String odrlPlaceholder = String.format("{\"id\": \"%s\"}", id);
        try (JsonReader reader = Json.createReader(new StringReader(odrlPlaceholder))) {
            return reader.readObject();
        }
    }

    private static JsonObject getTestOdrlContract() {
        String odrl = String.format("{" +
                "\"@type\": \"contract\", " +
                "\"assigner\": \"assigner\", " +
                "\"assignee\": \"assignee\", " +
                "\"permission\": {" +
                "\"target\":\"%s\", " +
                "\"action\":\"use\", " +
                "\"constraint\": [" +
                "{" +
                "\"leftOperand\":\"dayOfWeek\", " +
                "\"operator\":\"eq\", " +
                "\"rightOperand\":6" +
                "}" +
                "]" +
                "}" +
                "}", TEST_ASSET_ID);
        try (JsonReader reader = Json.createReader(new StringReader(odrl))) {
            return reader.readObject();
        }
    }

    private static Map<String, Object> getTestOffer() {
        return new LinkedHashMap<>(Map.of(
                "@type", "offer",
                "assigner", "assigner",
                "assignee", "assignee",
                "permission", Map.of(
                        "target", TEST_ASSET_ID,
                        "action", "use",
                        "constraint", List.of(
                                Map.of("leftOperand", "dayOfWeek",
                                        "operator", "eq",
                                        "rightOperand", 6)
                        ))));
    }

    private static Map<String, Object> getTestOdrlPolicy() {
        return new LinkedHashMap<>(Map.of(
                "@type", "contract",
                "assigner", "assigner",
                "assignee", "assignee",
                "permission", Map.of(
                        "target", TEST_ASSET_ID,
                        "action", "use",
                        "constraint", List.of(
                                Map.of("leftOperand", "dayOfWeek",
                                        "operator", "eq",
                                        "rightOperand", 6)
                        ))));
    }

}