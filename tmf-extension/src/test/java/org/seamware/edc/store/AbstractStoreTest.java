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

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.StringReader;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractOffer;
import org.eclipse.edc.policy.model.*;
import org.eclipse.edc.protocol.dsp.spi.type.Dsp2025Constants;
import org.seamware.edc.domain.*;
import org.seamware.tmforum.agreement.model.CharacteristicVO;
import org.seamware.tmforum.agreement.model.RelatedPartyVO;
import org.seamware.tmforum.productcatalog.model.CharacteristicValueSpecificationVO;
import org.seamware.tmforum.productcatalog.model.ProductSpecificationCharacteristicVO;
import org.seamware.tmforum.quote.model.QuoteStateTypeVO;

public abstract class AbstractStoreTest {

  protected static final String TEST_CONSUMER_DID = "did:web:test-consumer.io";
  protected static final String TEST_PROVIDER_DID = "did:web:test-provider.io";
  protected static final String TEST_CONSUMER_TMF_ID = "tmf-consumer";
  protected static final String TEST_PROVIDER_TMF_ID = "tmf-provider";

  protected static final String TEST_ASSET_ID = "asset-id";
  protected static final String TEST_AGREEMENT_ID = "agreement-id";
  protected static final String TEST_NEGOTIATION_ID = "negotiation-id";
  protected static final String TEST_CORRELATION_ID = "correlation-id";
  protected static final String TEST_OFFER_ID = "offer-id";

  protected static final String TEST_COUNTER_PARTY_ADDRESS = "http://counter.party";
  protected static final String TEST_CONTROL_PLANE = "test-control-plane";

  protected static final String TEST_DESCRIPTION = "Description";
  protected static final String TEST_VERSION = "v1.0.0";
  protected static final String TEST_NAME = "name";
  protected static final String TEST_SPEC_DESCRIPTION = "Spec Description";

  protected static ExtendableProductOffering getNonCDProductOffering() {
    ExtendableProductOffering extendableProductOffering =
        new ExtendableProductOffering().setExternalId(TEST_OFFER_ID);
    return extendableProductOffering;
  }

  protected static ExtendableProductOffering getExtendableProductOffering(
      String offerId,
      Optional<Map<String, Object>> accessPolicy,
      Optional<Map<String, Object>> contractPolicy) {

    ExtendableProductOfferingTerm extendableProductOfferingTerm =
        new ExtendableProductOfferingTerm();
    accessPolicy.ifPresent(
        ap -> extendableProductOfferingTerm.setAdditionalProperties("accessPolicy", ap));
    contractPolicy.ifPresent(
        cp -> extendableProductOfferingTerm.setAdditionalProperties("contractPolicy", cp));
    extendableProductOfferingTerm.name("edc:contractDefinition");
    ExtendableProductOffering extendableProductOffering =
        new ExtendableProductOffering().setExternalId(offerId);
    extendableProductOffering.setId(UUID.randomUUID().toString());
    extendableProductOffering.setExtendableProductOfferingTerm(
        List.of(extendableProductOfferingTerm));
    return extendableProductOffering;
  }

  protected static ExtendableProductOffering getExtendableProductOffering() {
    return getExtendableProductOffering(
        TEST_OFFER_ID, Optional.empty(), Optional.of(getTestOdrlPolicy()));
  }

  protected record Endpoint(String id, String url) {}

  protected static ExtendableProductSpecification getTestProductSpec(
      List<Endpoint> endpoints,
      Optional<String> externalId,
      Optional<String> version,
      Optional<String> name,
      Optional<String> description,
      Optional<String> upstreamAddress) {
    ExtendableProductSpecification extendableProductSpecification =
        new ExtendableProductSpecification();
    externalId.ifPresent(extendableProductSpecification::setExternalId);
    version.ifPresent(extendableProductSpecification::setVersion);
    name.ifPresent(extendableProductSpecification::setName);
    description.ifPresent(extendableProductSpecification::setDescription);
    upstreamAddress.ifPresent(
        ua -> {
          extendableProductSpecification.addProductSpecCharacteristicItem(
              new ProductSpecificationCharacteristicVO()
                  .id("upstreamAddress")
                  .valueType("upstreamAddress"));
        });
    endpoints.stream()
        .map(
            endpoint ->
                new ProductSpecificationCharacteristicVO()
                    .id(endpoint.id())
                    .valueType("endpointUrl")
                    .productSpecCharacteristicValue(
                        List.of(
                            new CharacteristicValueSpecificationVO()
                                .value(endpoint.url())
                                .valueType("endpointUrl")
                                .isDefault(true))))
        .forEach(extendableProductSpecification::addProductSpecCharacteristicItem);
    extendableProductSpecification.addProductSpecCharacteristicItem(
        new ProductSpecificationCharacteristicVO()
            .id("endpointDescription")
            .valueType("endpointDescription")
            .addProductSpecCharacteristicValueItem(
                new CharacteristicValueSpecificationVO().value(TEST_DESCRIPTION)));
    return extendableProductSpecification;
  }

  protected static ExtendableProductSpecification getTestProductSpec(List<Endpoint> endpoints) {
    return getTestProductSpec(
        endpoints,
        Optional.of(TEST_ASSET_ID),
        Optional.of(TEST_VERSION),
        Optional.of(TEST_NAME),
        Optional.of(TEST_SPEC_DESCRIPTION),
        Optional.of(TEST_SPEC_DESCRIPTION));
  }

  protected static ExtendableAgreementVO getTestAgreement() {
    ExtendableAgreementVO agreementVO =
        new ExtendableAgreementVO()
            .setExternalId(TEST_AGREEMENT_ID)
            .setNegotiationId(TEST_NEGOTIATION_ID);
    agreementVO
        .agreementType("dspContract")
        .name(
            String.format(
                "DSP Contract between %s - %s for %s.",
                TEST_PROVIDER_DID, TEST_CONSUMER_DID, TEST_ASSET_ID))
        .addEngagedPartyItem(new RelatedPartyVO().role("Consumer").id(TEST_CONSUMER_TMF_ID))
        .addEngagedPartyItem(new RelatedPartyVO().role("Provider").id(TEST_PROVIDER_TMF_ID))
        .addCharacteristicItem(new CharacteristicVO().name("asset-id").value(TEST_ASSET_ID))
        .addCharacteristicItem(new CharacteristicVO().name("provider-id").value(TEST_PROVIDER_DID))
        .addCharacteristicItem(new CharacteristicVO().name("policy").value(getTestOdrlPolicy()))
        .addCharacteristicItem(new CharacteristicVO().name("consumer-id").value(TEST_CONSUMER_DID))
        .addCharacteristicItem(new CharacteristicVO().name("signing-date").value(1));
    return agreementVO;
  }

  protected static ContractAgreement getValidContractAgreement() {
    return ContractAgreement.Builder.newInstance()
        .id(TEST_AGREEMENT_ID)
        .assetId(TEST_ASSET_ID)
        .consumerId(TEST_CONSUMER_DID)
        .providerId(TEST_PROVIDER_DID)
        .contractSigningDate(1)
        .policy(getTestPolicy())
        .build();
  }

  protected static Action getUse() {
    return Action.Builder.newInstance().type("use").build();
  }

  protected static Constraint getTestConstraint() {
    return AtomicConstraint.Builder.newInstance()
        .leftExpression(new LiteralExpression("odrl:dayOfWeek"))
        .operator(Operator.EQ)
        .rightExpression(new LiteralExpression(6))
        .build();
  }

  protected static Permission getTestPermission() {
    return Permission.Builder.newInstance()
        .action(getUse())
        .constraint(getTestConstraint())
        .build();
  }

  protected static Policy getTestPolicy(String id) {
    return Policy.Builder.newInstance()
        .type(PolicyType.CONTRACT)
        .assigner("assigner")
        .assignee("assignee")
        .permission(getTestPermission())
        .extensibleProperty("http://www.w3.org/ns/odrl/2/uid", id)
        .build();
  }

  protected static Policy getTestPolicy() {
    return getTestPolicy("uid");
  }

  protected static JsonObject getEmptyOdrlContract() {
    String emptyOdrl = "{}";
    try (JsonReader reader = Json.createReader(new StringReader(emptyOdrl))) {
      return reader.readObject();
    }
  }

  protected static JsonObject getIdedPolicy(String id) {
    String odrlPlaceholder = String.format("{\"id\": \"%s\"}", id);
    try (JsonReader reader = Json.createReader(new StringReader(odrlPlaceholder))) {
      return reader.readObject();
    }
  }

  protected static JsonObject getTestOdrlContract() {
    String odrl =
        String.format(
            "{"
                + "\"@type\": \"contract\", "
                + "\"assigner\": \"assigner\", "
                + "\"assignee\": \"assignee\", "
                + "\"permission\": {"
                + "\"target\":\"%s\", "
                + "\"action\":\"use\", "
                + "\"constraint\": ["
                + "{"
                + "\"leftOperand\":\"dayOfWeek\", "
                + "\"operator\":\"eq\", "
                + "\"rightOperand\":6"
                + "}"
                + "]"
                + "}"
                + "}",
            TEST_ASSET_ID);
    try (JsonReader reader = Json.createReader(new StringReader(odrl))) {
      return reader.readObject();
    }
  }

  protected static Map<String, Object> getTestOffer() {
    return new LinkedHashMap<>(
        Map.of(
            "@type", "offer",
            "assigner", "assigner",
            "assignee", "assignee",
            "permission",
                Map.of(
                    "target",
                    TEST_ASSET_ID,
                    "action",
                    "use",
                    "constraint",
                    List.of(
                        Map.of("leftOperand", "dayOfWeek", "operator", "eq", "rightOperand", 6)))));
  }

  protected static Map<String, Object> getTestOdrlPolicy() {
    return new LinkedHashMap<>(
        Map.of(
            "@type", "contract",
            "assigner", "assigner",
            "assignee", "assignee",
            "permission",
                Map.of(
                    "target",
                    TEST_ASSET_ID,
                    "action",
                    "use",
                    "constraint",
                    List.of(
                        Map.of("leftOperand", "dayOfWeek", "operator", "eq", "rightOperand", 6)))));
  }

  protected static ExtendableQuoteVO getExtendableQuoteVo(
      ContractNegotiationState contractNegotiationState,
      QuoteStateTypeVO quoteState,
      String consumerId,
      String providerId,
      long quoteDate,
      List<String> offers) {
    ExtendableQuoteVO quoteVO = new ExtendableQuoteVO();
    quoteVO.setContractNegotiationState(contractNegotiationState);
    quoteVO.setExternalId(TEST_NEGOTIATION_ID);
    quoteVO.setState(quoteState);
    quoteVO.setQuoteDate(
        OffsetDateTime.ofInstant(
            Instant.ofEpochSecond(quoteDate), TimeZone.getDefault().toZoneId()));
    quoteVO.setRelatedParty(
        List.of(
            new org.seamware.tmforum.quote.model.RelatedPartyVO().id(consumerId).role("Consumer"),
            new org.seamware.tmforum.quote.model.RelatedPartyVO().id("someoneElse").role("Manager"),
            new org.seamware.tmforum.quote.model.RelatedPartyVO().id(providerId).role("Provider")));

    quoteVO.setExtendableQuoteItem(
        offers.stream()
            .map(
                offer ->
                    new ExtendableQuoteItemVO()
                        .setDatasetId(TEST_ASSET_ID)
                        .setExternalId(offer)
                        .setPolicy(getTestOffer()))
            .toList());
    return quoteVO;
  }

  protected static ContractOffer getOffer(String id) {
    return ContractOffer.Builder.newInstance()
        .id(id)
        .assetId(TEST_ASSET_ID)
        .policy(getTestPolicy())
        .build();
  }

  protected static ContractNegotiation getNegotiation(
      ContractNegotiation.Type type,
      ContractNegotiationStates state,
      boolean pending,
      String counterParty,
      List<ContractOffer> offers,
      Optional<ContractAgreement> contractAgreement) {
    ContractNegotiation.Builder builder =
        ContractNegotiation.Builder.newInstance()
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

  protected static ContractNegotiation getNegotiation(
      ContractNegotiation.Type type,
      ContractNegotiationStates state,
      boolean pending,
      String counterParty,
      List<ContractOffer> offers) {
    return getNegotiation(type, state, pending, counterParty, offers, Optional.empty());
  }

  protected static ContractNegotiationState negotiationState(String state, boolean pending) {
    return new ContractNegotiationState()
        .setCounterPartyAddress(TEST_COUNTER_PARTY_ADDRESS)
        .setState(state)
        .setCorrelationId(TEST_CORRELATION_ID)
        .setLeased(false)
        .setControlplane(TEST_CONTROL_PLANE)
        .setPending(pending);
  }
}
