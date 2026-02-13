package org.seamware.edc.store;

import org.eclipse.edc.spi.result.Result;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ContractOfferIdParserTest {

    @ParameterizedTest
    @MethodSource("getValidOfferIds")
    public void testParse(String offerId, String expectedDefinition, String expectedAsset, String expectedUuid) {
        Result<ContractOfferIdParser.ContractOfferWithUid> contractOfferWithUidResult = ContractOfferIdParser.parseId(offerId);

        assertTrue(contractOfferWithUidResult.succeeded(), "The offer should have successfully been parsed.");
        ContractOfferIdParser.ContractOfferWithUid contractOfferWithUid = contractOfferWithUidResult.getContent();
        assertEquals(expectedDefinition, contractOfferWithUid.contractOfferId().definitionPart(), "The definition should have been correctly extracted.");
        assertEquals(expectedAsset, contractOfferWithUid.contractOfferId().assetIdPart(), "The definition should have been correctly extracted.");
        assertEquals(expectedUuid, contractOfferWithUid.uuid(), "The uuid should have been correctly extracted.");
    }

    @ParameterizedTest
    @MethodSource("getInvalidOfferIds")
    public void testParseInvalid(String offerId) {
        Result<ContractOfferIdParser.ContractOfferWithUid> contractOfferWithUidResult = ContractOfferIdParser.parseId(offerId);

        assertTrue(contractOfferWithUidResult.failed(), "The offer should not have been parsed.");
    }

    private static Stream<Arguments> getValidOfferIds() {
        return Stream.of(
                Arguments.of("offer-1:asset-1:uuid", "offer-1", "asset-1", "uuid"),
                Arguments.of("offer-2:asset-2:test-id", "offer-2", "asset-2", "test-id")
        );
    }

    private static Stream<Arguments> getInvalidOfferIds() {
        return Stream.of(
                Arguments.of("offer-1:asset-1"),
                Arguments.of("offer-2"),
                Arguments.of("offer-2:definition-2:uuid:something-else")
        );
    }

}