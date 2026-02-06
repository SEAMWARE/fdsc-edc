package org.seamware.edc.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.participant.spi.ParticipantIdMapper;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.protocol.dsp.spi.type.Dsp2025Constants;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.seamware.edc.domain.*;
import org.seamware.edc.tmf.AgreementApiClient;
import org.seamware.edc.tmf.ParticipantResolver;
import org.seamware.tmforum.agreement.model.CharacteristicVO;
import org.seamware.tmforum.agreement.model.RelatedPartyVO;
import org.seamware.tmforum.party.model.OrganizationVO;
import org.seamware.tmforum.productcatalog.model.ProductSpecificationCharacteristicVO;
import org.seamware.tmforum.productorder.model.ProductOrderUpdateVO;
import org.seamware.tmforum.productorder.model.ProductOrderVO;
import org.seamware.tmforum.quote.model.ProductOfferingRefVO;
import org.seamware.tmforum.usage.model.RatedProductUsageVO;
import org.seamware.tmforum.usage.model.UsageCharacteristicVO;
import org.seamware.tmforum.usage.model.UsageStatusTypeVO;

import java.io.StringReader;
import java.util.*;

import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess.Type.CONSUMER;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess.Type.PROVIDER;
import static org.seamware.edc.tmf.ParticipantResolver.CONSUMER_ROLE;
import static org.seamware.edc.tmf.ParticipantResolver.PROVIDER_ROLE;

/**
 * Mapper between TMForum and EDC entities
 */
public class TMFEdcMapper {

    public static final String POT_NAME_CONTRACT_DEFINITION = "edc:contractDefinition";
    public static final String CONTRACT_POLICY_KEY = "contractPolicy";
    public static final String ACCESS_POLICY_KEY = "accessPolicy";
    public static final String PRODUCT_SPEC_CHAR_KEY = "productSpecCharacteristic";
    public static final String ENDPOINT_URL_KEY = "endpointUrl";
    public static final String ENDPOINT_DESCRIPTION_KEY = "endpointDescription";
    public static final String UPSTREAM_ADDRESS_KEY = "upstreamAddress";
    public static final String TMF_ID_KEY = "tmfId";
    public static final String PRODUCT_SPEC_CHAR_VALUE = "productSpecCharacteristicValue";
    public static final String UID_KEY = "http://www.w3.org/ns/odrl/2/uid";
    public static final String USAGE_CHARACTERISTIC_ASSET_ID = "asset-id";
    public static final String USAGE_CHARACTERISTIC_CORRELATION_ID = "correlation-id";
    public static final String USAGE_CHARACTERISTIC_PROTOCOL = "protocol";
    public static final String USAGE_CHARACTERISTIC_COUNTER_PARTY_ADDRESS = "counter-party-address";
    public static final String USAGE_CHARACTERISTIC_TRANSFER_TYPE = "transfer-type";
    public static final String USAGE_CHARACTERISTIC_TYPE = "type";
    public static final String USAGE_CHARACTERISTIC_CONTRACT_ID = "contract-id";
    public static final String USAGE_CHARACTERISTIC_RESOURCE_MANIFEST = "resource-manifest";
    public static final String USAGE_CHARACTERISTIC_DATAPLANE_ID = "dataplane-id";
    public static final String USAGE_CHARACTERISTIC_CONTENT_DATA_ADDRESS = "content-data-address";
    public static final String USAGE_TYPE_DSP_TRANSFER = "dspTransfer";

    public static final String AGREEMENT_CHARACTERISTIC_SIGNING_DATE = "signing-date";
    public static final String AGREEMENT_CHARACTERISTIC_PROVIDER_ID = "provider-id";
    public static final String AGREEMENT_CHARACTERISTIC_CONSUMER_ID = "consumer-id";
    public static final String AGREEMENT_CHARACTERISTIC_ASSET_ID = "asset-id";
    public static final String AGREEMENT_CHARACTERISTIC_POLICY = "policy";
    public static final String AGREEMENT_TYPE_DSP = "dspContract";

    private static final String DEFINITIONS_KEY = "definition";
    private static final String DEFINITION_ASSET_ID_KEY = "assetId";
    private static final String DEFINITION_ID_KEY = "id";
    private static final String DEFINITION_TRANSFER_PROCESS_ID_KEY = "transferProcessId";
    public static final String QUOTE_ITEM_ADD_ACTION = "add";
    public static final String FDSC_DATA_ADDRESS_TYPE = "FDSC";

    private final Monitor monitor;
    private final ObjectMapper objectMapper;
    private final TMFObjectMapper tmfObjectMapper;
    private final ParticipantResolver participantResolver;
    private final TypeTransformerRegistry typeTransformerRegistry;
    private final JsonLd jsonLd;

    public TMFEdcMapper(Monitor monitor, ObjectMapper objectMapper, ParticipantResolver participantResolver, TypeTransformerRegistry typeTransformerRegistry, JsonLd jsonLd) {
        this.monitor = monitor;
        this.objectMapper = objectMapper;
        this.participantResolver = participantResolver;
        this.typeTransformerRegistry = typeTransformerRegistry;
        this.jsonLd = jsonLd;
        this.tmfObjectMapper = new TMFObjectMapperImpl();
    }

    public ExtendableAgreementVO toAgreement(String negotiationId, ContractAgreement contractAgreement) {
        ExtendableAgreementVO agreementVO = new ExtendableAgreementVO();
        agreementVO.setExternalId(contractAgreement.getId());
        agreementVO.setNegotiationId(negotiationId);
        agreementVO.agreementType(AGREEMENT_TYPE_DSP);
        agreementVO.name(String.format("DSP Contract between %s - %s for %s.", contractAgreement.getProviderId(), contractAgreement.getConsumerId(), contractAgreement.getAssetId()));

        String consumerId = participantResolver.getTmfId(contractAgreement.getConsumerId());
        String providerId = participantResolver.getTmfId(contractAgreement.getProviderId());
        agreementVO.addEngagedPartyItem(new RelatedPartyVO().id(consumerId).role(CONSUMER_ROLE))
                .addEngagedPartyItem(new RelatedPartyVO().id(providerId).role(PROVIDER_ROLE));
        agreementVO.addCharacteristicItem(toAgreementCharacteristic(AGREEMENT_CHARACTERISTIC_ASSET_ID, contractAgreement.getAssetId()))
                .addCharacteristicItem(toAgreementCharacteristic(AGREEMENT_CHARACTERISTIC_PROVIDER_ID, contractAgreement.getProviderId()))
                .addCharacteristicItem(toAgreementCharacteristic(AGREEMENT_CHARACTERISTIC_POLICY, fromEdcPolicy(contractAgreement.getPolicy())))
                .addCharacteristicItem(toAgreementCharacteristic(AGREEMENT_CHARACTERISTIC_CONSUMER_ID, contractAgreement.getConsumerId()))
                .addCharacteristicItem(toAgreementCharacteristic(AGREEMENT_CHARACTERISTIC_SIGNING_DATE, contractAgreement.getContractSigningDate()));
        return agreementVO;
    }

    private CharacteristicVO toAgreementCharacteristic(String key, Object value) {
        return new CharacteristicVO().name(key).value(value);
    }

    public ContractAgreement toContractAgreement(ExtendableAgreementVO agreementVO) {
        ContractAgreement.Builder agreementBuilder = ContractAgreement.Builder.newInstance();
        agreementVO.getCharacteristic()
                .forEach(c -> {
                    switch (c.getName()) {
                        case AGREEMENT_CHARACTERISTIC_SIGNING_DATE -> agreementBuilder.contractSigningDate(((Number) c.getValue()).longValue());
                        case AGREEMENT_CHARACTERISTIC_PROVIDER_ID -> agreementBuilder.providerId((String) c.getValue());
                        case AGREEMENT_CHARACTERISTIC_CONSUMER_ID -> agreementBuilder.consumerId((String) c.getValue());
                        case AGREEMENT_CHARACTERISTIC_ASSET_ID -> agreementBuilder.assetId((String) c.getValue());
                        case AGREEMENT_CHARACTERISTIC_POLICY -> agreementBuilder.policy(fromOdrl(c.getValue()));
                    }
                });
        agreementBuilder.id(agreementVO.getExternalId());
        return agreementBuilder.build();
    }

    public Dataset datasetFromProductOffering(ExtendableProductOffering extendableProductOffering, Optional<ExtendableProductSpecification> productSpecification) {
        Policy contractPolicy = getContractPolicyFromOfferTerm(getContractDefinitionTerm(extendableProductOffering));


        Dataset.Builder datasetBuilder = Dataset.Builder.newInstance()
                .offer(extendableProductOffering.getExternalId(), contractPolicy);
        productSpecification.ifPresent(pS -> datasetBuilder.id(pS.getExternalId()));
        
        getDataService(productSpecification)
                .stream()
                .map(ds -> Distribution.Builder.newInstance().format("http").dataService(ds).build())
                .forEach(datasetBuilder::distribution);
        return datasetBuilder.build();
    }

    private record DataServiceChar(String id, String endpointUrl) {
    }

    ;

    public List<DataService> getDataService(Optional<ExtendableProductSpecification> productSpecification) {
        DataService.Builder defaultDataserviceBuilder = DataService.Builder.newInstance();

        if (productSpecification.isEmpty()) {
            return List.of();
        }
        List<DataServiceChar> endpoints = new ArrayList<>();
        productSpecification.map(ExtendableProductSpecification::getProductSpecCharacteristic)
                .orElse(List.of())
                .forEach(spec -> {
                    switch (spec.getValueType()) {
                        case ENDPOINT_URL_KEY -> {
                            getValue(spec.getProductSpecCharacteristicValue()).map(endpointUrl -> new DataServiceChar(spec.getId(), endpointUrl)).ifPresent(endpoints::add);
                        }
                        case ENDPOINT_DESCRIPTION_KEY -> getValue(spec.getProductSpecCharacteristicValue()).ifPresent(defaultDataserviceBuilder::endpointDescription);
                    }
                });
        String endpointDescription = defaultDataserviceBuilder.build().getEndpointDescription();

        return endpoints.stream()
                .map(endpoint -> DataService.Builder.newInstance()
                        .endpointUrl(endpoint.endpointUrl())
                        .endpointDescription(endpointDescription)
                        .id(endpoint.id())
                        .build())
                .toList();
    }

    private List<CharValue> getSpecCharValue(Map<String, Object> specChar) {
        if (specChar.containsKey(PRODUCT_SPEC_CHAR_VALUE)) {
            if (specChar.get(PRODUCT_SPEC_CHAR_VALUE) instanceof List<?>) {
                return objectMapper.convertValue(specChar.get(PRODUCT_SPEC_CHAR_VALUE), new TypeReference<List<CharValue>>() {
                });
            } else {
                return List.of(objectMapper.convertValue(specChar.get(PRODUCT_SPEC_CHAR_VALUE), new TypeReference<CharValue>() {
                }));
            }
        }
        return List.of();
    }

    private Optional<String> getStringValueFromChars(List<CharValue> charValues) {
        if (charValues == null || charValues.isEmpty()) {
            return Optional.empty();
        }
        for (CharValue charValue : charValues) {
            if (charValue.isDefault() && charValue.getTmfValue() instanceof String stringValue) {
                return Optional.of(stringValue);
            }
        }
        // if no default available, just pick the first one
        Object firstValue = charValues.getFirst().getTmfValue();
        if (firstValue instanceof String stringValue) {
            return Optional.of(stringValue);
        }
        return Optional.empty();
    }

    public ContractNegotiation toContractNegotiation(List<ExtendableQuoteVO> quoteVOs, AgreementApiClient agreementApiClient, ParticipantResolver participantResolver, String participantId) {

        ContractNegotiation.Builder contractNegotiationBuilder = ContractNegotiation.Builder.newInstance();

        if (quoteVOs.isEmpty()) {
            monitor.warning("Tried to map an empty list to a contract negotiation.");
            return null;
        }

        // the newest one represents the current state of the negotiation
        ExtendableQuoteVO newestQuoteVo = getNewest(quoteVOs);
        ContractNegotiationStates cnState = getContractNegotiationState(newestQuoteVo);

        contractNegotiationBuilder.id(newestQuoteVo.getExternalId());
        contractNegotiationBuilder.state(cnState.code());

        NegotiationParticipants negotiationParticipants = new NegotiationParticipants();
        Optional.ofNullable(newestQuoteVo.getRelatedParty())
                .orElse(List.of())
                .forEach(rp -> {
                    OrganizationVO organizationVO = participantResolver.getOrganization(rp.getId());
                    String did = ParticipantResolver.getDidFromOrganization(organizationVO);
                    String role = rp.getRole();
                    if (role == null) {
                        monitor.warning("Received a related party without a role.");
                        return;
                    }
                    if (did.equals(participantId)) {
                        switch (role) {
                            case PROVIDER_ROLE -> {
                                contractNegotiationBuilder.type(ContractNegotiation.Type.PROVIDER);
                                negotiationParticipants
                                        .providerId(did);
                            }
                            case CONSUMER_ROLE -> {
                                contractNegotiationBuilder.type(ContractNegotiation.Type.CONSUMER);
                                negotiationParticipants
                                        .consumerId(did);
                            }
                        }
                    } else {
                        switch (role) {
                            case PROVIDER_ROLE -> negotiationParticipants.providerId(did);
                            case CONSUMER_ROLE -> negotiationParticipants.consumerId(did);
                        }
                        if (role.equals(PROVIDER_ROLE) || role.equals(CONSUMER_ROLE)) {
                            contractNegotiationBuilder.protocol(Dsp2025Constants.DATASPACE_PROTOCOL_HTTP_V_2025_1);
                            contractNegotiationBuilder.counterPartyId(did);
                            contractNegotiationBuilder.counterPartyAddress(newestQuoteVo.getContractNegotiationState().getCounterPartyAddress());
                        }
                    }
                });
        ContractNegotiationState contractNegotiationState = newestQuoteVo.getContractNegotiationState();
        contractNegotiationBuilder.pending(contractNegotiationState.isPending());
        contractNegotiationBuilder.correlationId(contractNegotiationState.getCorrelationId());
        quoteVOs.stream()
                .map(ExtendableQuoteVO::getExtendableQuoteItem)
                .flatMap(List::stream)
                .map(this::fromQuoteItem)
                .forEach(contractNegotiationBuilder::contractOffer);
        if (cnState.code() >= ContractNegotiationStates.AGREEING.code() && cnState.code() < ContractNegotiationStates.TERMINATING.code()) {
            agreementApiClient.findByNegotiationId(newestQuoteVo.getExternalId())
                    .map(this::toContractAgreement)
                    .ifPresent(contractNegotiationBuilder::contractAgreement);

        }
        return contractNegotiationBuilder.build();

    }

    private ExtendableQuoteVO getNewest(List<ExtendableQuoteVO> quoteVOs) {
        quoteVOs.sort(new Comparator<ExtendableQuoteVO>() {
            @Override
            public int compare(ExtendableQuoteVO quoteVO, ExtendableQuoteVO t1) {
                if (quoteVO.getQuoteDate().isBefore(t1.getQuoteDate())) {
                    return -1;
                } else {
                    return 1;
                }
            }
        });
        return quoteVOs.getLast();
    }

    public ContractNegotiationStates toState(String state) {
        return tmfObjectMapper.mapNegotiationState(state);
    }

    public ContractNegotiationStates getContractNegotiationState(ExtendableQuoteVO quoteVO) {
        return toState(quoteVO.getContractNegotiationState().getState());
    }

    public ContractOffer fromQuoteItem(ExtendableQuoteItemVO quoteItemVO) {
        ContractOffer.Builder contractOfferBuilder = ContractOffer.Builder.newInstance();
        if (quoteItemVO.getPolicy() != null) {
            contractOfferBuilder.policy(fromOdrl(quoteItemVO.getPolicy()));
        }

        return contractOfferBuilder
                .assetId(quoteItemVO.getDatasetId())
                .id(quoteItemVO.getExternalId())
                .build();
    }

    public ExtendableQuoteItemVO fromConsumerContractOffer(ContractOffer contractOffer, String negotiationState) {


        String itemUid = ContractOfferIdParser.parseId(contractOffer.getId())
                .asOptional()
                .map(ContractOfferIdParser.ContractOfferWithUid::uuid)
                .orElse(UUID.randomUUID().toString());

        ExtendableQuoteItemVO extendableQuoteItemVO = new ExtendableQuoteItemVO();
        extendableQuoteItemVO.setId(itemUid);
        extendableQuoteItemVO.setDatasetId(contractOffer.getAssetId());
        extendableQuoteItemVO.setExternalId(contractOffer.getId());
        extendableQuoteItemVO.setPolicy(fromEdcPolicy(contractOffer.getPolicy()));
        extendableQuoteItemVO.setState(negotiationState);
        extendableQuoteItemVO.setAction(QUOTE_ITEM_ADD_ACTION);
        return extendableQuoteItemVO;
    }

    public ExtendableQuoteItemVO fromProviderContractOffer(ContractOffer contractOffer, String negotiationState, Optional<String> offerTmfId) {

        ContractOfferIdParser.ContractOfferWithUid contractOfferId = ContractOfferIdParser.parseId(contractOffer.getId())
                .orElseThrow(f -> new IllegalArgumentException(f.getFailureDetail() + " id was " + contractOffer.getId()));

        monitor.info("Offer uid is " + contractOfferId.uuid());

        ExtendableQuoteItemVO extendableQuoteItemVO = new ExtendableQuoteItemVO();
        extendableQuoteItemVO.setId(contractOfferId.uuid());
        extendableQuoteItemVO.setDatasetId(contractOffer.getAssetId());
        extendableQuoteItemVO.setExternalId(contractOfferId.asDecoded());
        extendableQuoteItemVO.setAction(QUOTE_ITEM_ADD_ACTION);
        offerTmfId.ifPresent(offerId -> extendableQuoteItemVO.productOffering(new ProductOfferingRefVO().id(offerId)));
        extendableQuoteItemVO.setPolicy(fromEdcPolicy(contractOffer.getPolicy()));
        extendableQuoteItemVO.setState(negotiationState);
        return extendableQuoteItemVO;
    }

    public Optional<String> getValue(List<org.seamware.tmforum.productcatalog.model.CharacteristicValueSpecificationVO> characteristicValueSpecificationVOS) {
        if (characteristicValueSpecificationVOS == null || characteristicValueSpecificationVOS.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> defaultValue = characteristicValueSpecificationVOS.stream()
                .filter(cvs -> Optional.ofNullable(cvs.getIsDefault()).orElse(false))
                .map(org.seamware.tmforum.productcatalog.model.CharacteristicValueSpecificationVO::getValue)
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .findAny();
        if (defaultValue.isPresent()) {
            return defaultValue;
        }
        return characteristicValueSpecificationVOS.stream()
                .map(org.seamware.tmforum.productcatalog.model.CharacteristicValueSpecificationVO::getValue)
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .findFirst();
    }

    public Optional<Asset> assetFromProductSpec(ExtendableProductSpecification productSpecification) {
        DataAddress.Builder dataAddressBuilder = DataAddress.Builder.newInstance()
                .type(FDSC_DATA_ADDRESS_TYPE);
        List<ProductSpecificationCharacteristicVO> specChars = productSpecification.getProductSpecCharacteristic();
        Optional<String> upstreamAddressKey = specChars.stream()
                .map(ProductSpecificationCharacteristicVO::getId)
                .filter(UPSTREAM_ADDRESS_KEY::equals)
                .findAny();
        if (upstreamAddressKey.isEmpty()) {
            monitor.info("The given product specification cannot be used for DSP, since it does not contain an upstreamAddress.");
            return Optional.empty();
        }
        specChars
                .forEach(spec -> {
                    switch (spec.getId()) {
                        case ENDPOINT_URL_KEY -> getValue(spec.getProductSpecCharacteristicValue()).ifPresent(url -> dataAddressBuilder.property(ENDPOINT_URL_KEY, url));
                        case ENDPOINT_DESCRIPTION_KEY -> getValue(spec.getProductSpecCharacteristicValue()).ifPresent(desc -> dataAddressBuilder.property(ENDPOINT_DESCRIPTION_KEY, desc));
                    }
                });

        return Optional.of(Asset.Builder.newInstance()
                .id(productSpecification.getExternalId())
                .version(productSpecification.getVersion())
                .name(productSpecification.getName())
                .description(productSpecification.getDescription())
                .dataAddress(dataAddressBuilder.build())
                .build());
    }

    public Optional<Asset> assetFromProductOffering(ExtendableProductOffering productOffering, Optional<ExtendableProductSpecification> productSpecification) {
        Optional<ContractOfferId> optionalContractOfferId = ContractOfferId.parseId(productOffering.getExternalId()).asOptional();

        DataAddress.Builder dataAddressBuilder = DataAddress.Builder.newInstance()
                .type(FDSC_DATA_ADDRESS_TYPE);


        List<ProductSpecificationCharacteristicVO> specChars = productSpecification.map(ExtendableProductSpecification::getProductSpecCharacteristic)
                .orElse(List.of());
        Optional<String> upstreamAddressKey = specChars.stream()
                .map(ProductSpecificationCharacteristicVO::getId)
                .filter(UPSTREAM_ADDRESS_KEY::equals)
                .findAny();
        if (upstreamAddressKey.isEmpty()) {
            monitor.info("The given product specification cannot be used for DSP, since it does not contain an upstreamAddress.");
            return Optional.empty();
        }

        specChars
                .forEach(spec -> {
                    switch (spec.getId()) {
                        case ENDPOINT_URL_KEY -> getValue(spec.getProductSpecCharacteristicValue()).ifPresent(url -> dataAddressBuilder.property(ENDPOINT_URL_KEY, url));
                        case ENDPOINT_DESCRIPTION_KEY -> getValue(spec.getProductSpecCharacteristicValue()).ifPresent(desc -> dataAddressBuilder.property(ENDPOINT_DESCRIPTION_KEY, desc));
                    }
                });

        return optionalContractOfferId.map(contractOfferId -> Asset.Builder.newInstance()
                .id(contractOfferId.assetIdPart())
                .name(productOffering.getName())
                .description(productOffering.getDescription())
                .version(productOffering.getVersion())
                .dataAddress(dataAddressBuilder.build())
                .build());
    }

    public JsonObject jsonObjectFromEdcPolicy(Policy policy) {
        JsonObject jsonObject = typeTransformerRegistry.transform(policy, JsonObject.class)
                .orElseThrow(f -> new IllegalArgumentException(String.format("Was not able to transform the policy. Failure: %s.", f.getFailureDetail())));

        return jsonLd.expand(jsonObject).orElseThrow(f -> new IllegalArgumentException(String.format("Was not able to expand the policy. Failure: %s", f.getFailureDetail())));
    }

    public Map<String, Object> fromEdcPolicy(Policy policy) {
        try {
            return objectMapper.readValue(jsonObjectFromEdcPolicy(policy).toString(), new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Did not receive a valid edc policy.", e);
        }
    }

    public Policy fromOdrl(Object policy) {
        // convert to string
        try {
            String jsonString = objectMapper.writeValueAsString(policy);
            try (JsonReader reader = Json.createReader(new StringReader(jsonString))) {
                JsonObject expandedInput = jsonLd.expand(reader.readObject())
                        .orElseThrow(f -> new IllegalArgumentException(String.format("Was not able to expand the json-ld input. Failure: %s.", f.getFailureDetail())));

                return typeTransformerRegistry.transform(expandedInput, Policy.class)
                        .orElseThrow(f -> new IllegalArgumentException(String.format("Was not able to transform input to a policy. Failure: %s.", f.getFailureDetail())));
            }
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Contract definition does not contain a contract policy.");
        }

    }

    private Policy getContractPolicyFromOfferTerm(ExtendableProductOfferingTerm extendableProductOfferingTerm) {

        Map<String, Object> additionalProperties = extendableProductOfferingTerm.getAdditionalProperties();

        if (additionalProperties.containsKey(CONTRACT_POLICY_KEY)) {
            return fromOdrl(additionalProperties.get(CONTRACT_POLICY_KEY));
        } else {
            throw new IllegalArgumentException("Contract definition does not contain a contract policy.");
        }
    }

    private static class NoopParticipantIdMapper implements ParticipantIdMapper {

        @Override
        public String toIri(String s) {
            return s;
        }

        @Override
        public String fromIri(String s) {
            return s;
        }
    }

    private Policy getAccessPolicyFromOfferTerm(ExtendableProductOfferingTerm extendableProductOfferingTerm) {

        Map<String, Object> additionalProperties = extendableProductOfferingTerm.getAdditionalProperties();

        if (additionalProperties.containsKey(ACCESS_POLICY_KEY)) {
            return fromOdrl(additionalProperties.get(ACCESS_POLICY_KEY));

        } else {
            throw new IllegalArgumentException("Contract definition does not contain an access policy.");
        }
    }

    private ExtendableProductOfferingTerm getContractDefinitionTerm(ExtendableProductOffering extendableProductOffering) {

        return extendableProductOffering.getExtendableProductOfferingTerm()
                .stream()
                .filter(pot -> pot.getName().equals(POT_NAME_CONTRACT_DEFINITION))
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("Offering does not contain a contract definition."));
    }

    public static String getIdFromPolicy(Policy policy) {
        return Optional.ofNullable(policy.getExtensibleProperties().get(UID_KEY))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .orElseThrow(() -> new IllegalArgumentException("Policy does not contain a uid."));
    }

    public ContractDefinition fromProductOffer(ExtendableProductOffering productOfferingVO) {

        ContractDefinition.Builder contractDefinitionBuilder = ContractDefinition.Builder
                .newInstance()
                .id(productOfferingVO.getId());

        ExtendableProductOfferingTerm extendableProductOfferingTerm = getContractDefinitionTerm(productOfferingVO);

        Policy contractPolicy = getContractPolicyFromOfferTerm(extendableProductOfferingTerm);
        contractDefinitionBuilder.contractPolicyId(getIdFromPolicy(contractPolicy));

        Policy accessPolicy = getAccessPolicyFromOfferTerm(extendableProductOfferingTerm);
        contractDefinitionBuilder.accessPolicyId(getIdFromPolicy(accessPolicy));

        return contractDefinitionBuilder.build();
    }

    public ValidatableConsumerOffer consumerOfferFromProductOffering(ExtendableProductOffering productOfferingVO, ContractOfferId offerId) {
        ValidatableConsumerOffer.Builder consumerOfferBuilder = ValidatableConsumerOffer.Builder.newInstance();

        ContractDefinition contractDefinition = fromProductOffer(productOfferingVO);

        ExtendableProductOfferingTerm extendableProductOfferingTerm = getContractDefinitionTerm(productOfferingVO);
        Policy accessPolicy = getAccessPolicyFromOfferTerm(extendableProductOfferingTerm);
        Policy contractPolicy = getContractPolicyFromOfferTerm(extendableProductOfferingTerm);

        return consumerOfferBuilder
                .offerId(offerId)
                .contractPolicy(contractPolicy)
                .accessPolicy(accessPolicy)
                .contractDefinition(contractDefinition)
                .build();
    }

    public ExtendableAgreementCreateVO toCreate(ExtendableAgreementVO extendableAgreementVO) {
        return tmfObjectMapper.map(extendableAgreementVO);
    }

    public ExtendableAgreementUpdateVO toUpdate(ExtendableAgreementVO extendableAgreementVO) {
        return tmfObjectMapper.mapToUpdate(extendableAgreementVO);
    }

    public ExtendableQuoteUpdateVO toUpdate(ExtendableQuoteVO extendableQuoteVO) {
        return tmfObjectMapper.map(extendableQuoteVO);
    }

    public ProductOrderUpdateVO toUpdate(ProductOrderVO productOrderVO) {
        return tmfObjectMapper.map(productOrderVO);
    }

    public ExtendableUsageUpdateVO toUpdate(ExtendableUsageVO extendableUsageVO) {
        return tmfObjectMapper.map(extendableUsageVO);
    }

    public ExtendableUsageCreateVO toCreate(ExtendableUsageVO extendableUsageVO) {
        ExtendableUsageCreateVO extendableUsageCreateVO = tmfObjectMapper.mapToCreate(extendableUsageVO);
        try {
            monitor.info("To create " + objectMapper.writeValueAsString(extendableUsageCreateVO));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return extendableUsageCreateVO;
    }


    private class NegotiationParticipants {
        private String consumerId;
        private String providerId;

        public boolean participantsAvailable() {
            return consumerId != null && providerId != null;
        }

        public String getConsumerId() {
            return consumerId;
        }

        public NegotiationParticipants consumerId(String consumerId) {
            this.consumerId = consumerId;
            return this;
        }

        public String getProviderId() {
            return providerId;
        }

        public NegotiationParticipants providerId(String providerId) {
            this.providerId = providerId;
            return this;
        }

    }

//    public TransferProcess fromUsage(ExtendableUsageVO extendableUsageVO) {
//
//        TransferProcess.Builder transferProcessBuilder = TransferProcess.Builder.newInstance()
//                .id(extendableUsageVO.getExternalId())
//                .state(tmfObjectMapper.mapTransferState(extendableUsageVO.getTransferState()).code())
//                .type(fromCharacteristics(extendableUsageVO.getUsageCharacteristic(), USAGE_CHARACTERISTIC_TYPE, String.class)
//                        .map(this::fromName)
//                        .orElseThrow(() -> new IllegalArgumentException("Usage needs to contain its type.")))
//                .transferType(fromCharacteristics(extendableUsageVO.getUsageCharacteristic(), USAGE_CHARACTERISTIC_TRANSFER_TYPE, String.class)
//                        .orElseThrow(() -> new IllegalArgumentException("Usage needs to contain its transfer type.")))
//                .assetId(fromCharacteristics(extendableUsageVO.getUsageCharacteristic(), USAGE_CHARACTERISTIC_ASSET_ID, String.class)
//                        .orElseThrow(() -> new IllegalArgumentException("Usage needs to contain its asset id.")))
//                .counterPartyAddress(fromCharacteristics(extendableUsageVO.getUsageCharacteristic(), USAGE_CHARACTERISTIC_COUNTER_PARTY_ADDRESS, String.class)
//                        .orElseThrow(() -> new IllegalArgumentException("Usage needs to contain its counter party address.")))
//                .contractId(fromCharacteristics(extendableUsageVO.getUsageCharacteristic(), USAGE_CHARACTERISTIC_CONTRACT_ID, String.class)
//                        .orElseThrow(() -> new IllegalArgumentException("Usage needs to contain its contract id.")))
//                .protocol(fromCharacteristics(extendableUsageVO.getUsageCharacteristic(), USAGE_CHARACTERISTIC_PROTOCOL, String.class)
//                        .orElseThrow(() -> new IllegalArgumentException("Usage needs to contain its protocol.")));
//        fromCharacteristics(extendableUsageVO.getUsageCharacteristic(), USAGE_CHARACTERISTIC_CONTENT_DATA_ADDRESS, Map.class)
//                .map(m -> {
//                    return objectMapper.convertValue(m, DataAddress.class);
//                })
//                .ifPresent(transferProcessBuilder::contentDataAddress);
//        fromCharacteristics(extendableUsageVO.getUsageCharacteristic(), USAGE_CHARACTERISTIC_RESOURCE_MANIFEST, Map.class)
//                .map(this::toResourceManifest)
//                .ifPresent(transferProcessBuilder::resourceManifest);
//        fromCharacteristics(extendableUsageVO.getUsageCharacteristic(), USAGE_CHARACTERISTIC_CORRELATION_ID, String.class)
//                .ifPresent(transferProcessBuilder::correlationId);
//        fromCharacteristics(extendableUsageVO.getUsageCharacteristic(), USAGE_CHARACTERISTIC_DATAPLANE_ID, String.class)
//                .ifPresent(transferProcessBuilder::dataPlaneId);
//        return transferProcessBuilder.build();
//    }

//    private ResourceManifest toResourceManifest(Map<String, Object> manifestMap) {
//        ResourceManifest.Builder manifestBuilder = ResourceManifest.Builder.newInstance();
//
//        List<ResourceDefinition> resourceDefinitions = Optional.ofNullable(manifestMap.get(DEFINITIONS_KEY))
//                .map(definitionsObject -> objectMapper.convertValue(definitionsObject, new TypeReference<List<Map<String, Object>>>() {
//                }))
//                .orElse(List.of())
//                .stream()
//                .map(this::toResourceDefinition)
//                .toList();
//        manifestBuilder.definitions(resourceDefinitions);
//        return manifestBuilder.build();
//    }
//
//    private ResourceDefinition toResourceDefinition(Map<String, Object> definitionMap) {
//        FDSCProviderResourceDefinition.Builder definitionBuilder = FDSCProviderResourceDefinition.Builder.newInstance();
//        Optional.ofNullable(definitionMap.get(DEFINITION_ASSET_ID_KEY))
//                .filter(String.class::isInstance)
//                .map(String.class::cast).ifPresent(definitionBuilder::assetId);
//        Optional.ofNullable(definitionMap.get(DEFINITION_ID_KEY))
//                .filter(String.class::isInstance)
//                .map(String.class::cast).ifPresent(definitionBuilder::id);
//        Optional.ofNullable(definitionMap.get(DEFINITION_TRANSFER_PROCESS_ID_KEY))
//                .filter(String.class::isInstance)
//                .map(String.class::cast).ifPresent(definitionBuilder::transferProcessId);
//        return definitionBuilder.build();
//    }

    private TransferProcess.Type fromName(String name) {
        return switch (name) {
            case "CONSUMER" -> CONSUMER;
            case "PROVIDER" -> PROVIDER;
            default -> throw new IllegalArgumentException(String.format("Type %s is not supported.", name));
        };
    }

    public ExtendableUsageVO fromTransferProcess(TransferProcess transferProcess, String assetId, String productId) {
        ExtendableUsageVO usageVO = new ExtendableUsageVO();
        usageVO
                .setTransferState(transferProcess.stateAsString())
                .setExternalId(transferProcess.getId())
                .addUsageCharacteristicItem(toUsageCharacteristic(USAGE_CHARACTERISTIC_ASSET_ID, assetId))
                .addUsageCharacteristicItem(toUsageCharacteristic(USAGE_CHARACTERISTIC_COUNTER_PARTY_ADDRESS, transferProcess.getCounterPartyAddress()))
                .addUsageCharacteristicItem(toUsageCharacteristic(USAGE_CHARACTERISTIC_CONTRACT_ID, transferProcess.getContractId()))
                .addUsageCharacteristicItem(toUsageCharacteristic(USAGE_CHARACTERISTIC_PROTOCOL, transferProcess.getProtocol()))
                .addUsageCharacteristicItem(toUsageCharacteristic(USAGE_CHARACTERISTIC_TYPE, transferProcess.getType().name()))
                .addUsageCharacteristicItem(toUsageCharacteristic(USAGE_CHARACTERISTIC_TRANSFER_TYPE, transferProcess.getTransferType()))
                .usageType(USAGE_TYPE_DSP_TRANSFER);
        Optional.ofNullable(transferProcess.getContentDataAddress())
                .ifPresent(cda -> {
                    usageVO.addUsageCharacteristicItem(toUsageCharacteristic(USAGE_CHARACTERISTIC_CONTENT_DATA_ADDRESS, cda));
                });
        Optional.ofNullable(transferProcess.getDataPlaneId())
                .ifPresent(did -> usageVO.addUsageCharacteristicItem(toUsageCharacteristic(USAGE_CHARACTERISTIC_DATAPLANE_ID, did)));

        Optional.ofNullable(transferProcess.getCorrelationId())
                .ifPresent(cid -> usageVO.addUsageCharacteristicItem(toUsageCharacteristic(USAGE_CHARACTERISTIC_CORRELATION_ID, cid)));
        if (transferProcess.getState() < TransferProcessStates.STARTED.code()) {
            usageVO.status(UsageStatusTypeVO.RECEIVED);
        } else {
            usageVO.status(UsageStatusTypeVO.RATED);
            usageVO.addRatedProductUsageItem(new RatedProductUsageVO()
                    .productRef(new org.seamware.tmforum.usage.model.ProductRefVO().id(productId)));
        }

        if (transferProcess.getResourceManifest() != null) {
            usageVO.addUsageCharacteristicItem(toUsageCharacteristic(USAGE_CHARACTERISTIC_RESOURCE_MANIFEST, transferProcess.getResourceManifest()));
        }

        return usageVO;
    }

    public <T> Optional<T> fromCharacteristics(List<UsageCharacteristicVO> characteristicVOS, String key, Class<T> valueType) {
        return characteristicVOS.stream()
                .filter(characteristicVO -> characteristicVO.getName().equals(key))
                .map(UsageCharacteristicVO::getValue)
                .filter(valueType::isInstance)
                .map(valueType::cast)
                .findAny();
    }

    private UsageCharacteristicVO toUsageCharacteristic(String name, Object value) {
        return new UsageCharacteristicVO().name(name)
                .value(value);
    }
}
