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

import java.util.stream.Stream;
import org.eclipse.edc.spi.result.Result;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class ContractOfferIdParserTest {

  @ParameterizedTest
  @MethodSource("getValidOfferIds")
  public void testParse(
      String offerId, String expectedDefinition, String expectedAsset, String expectedUuid) {
    Result<ContractOfferIdParser.ContractOfferWithUid> contractOfferWithUidResult =
        ContractOfferIdParser.parseId(offerId);

    assertTrue(
        contractOfferWithUidResult.succeeded(), "The offer should have successfully been parsed.");
    ContractOfferIdParser.ContractOfferWithUid contractOfferWithUid =
        contractOfferWithUidResult.getContent();
    assertEquals(
        expectedDefinition,
        contractOfferWithUid.contractOfferId().definitionPart(),
        "The definition should have been correctly extracted.");
    assertEquals(
        expectedAsset,
        contractOfferWithUid.contractOfferId().assetIdPart(),
        "The definition should have been correctly extracted.");
    assertEquals(
        expectedUuid,
        contractOfferWithUid.uuid(),
        "The uuid should have been correctly extracted.");
  }

  @ParameterizedTest
  @MethodSource("getInvalidOfferIds")
  public void testParseInvalid(String offerId) {
    Result<ContractOfferIdParser.ContractOfferWithUid> contractOfferWithUidResult =
        ContractOfferIdParser.parseId(offerId);

    assertTrue(contractOfferWithUidResult.failed(), "The offer should not have been parsed.");
  }

  private static Stream<Arguments> getValidOfferIds() {
    return Stream.of(
        Arguments.of("offer-1:asset-1:uuid", "offer-1", "asset-1", "uuid"),
        Arguments.of("offer-2:asset-2:test-id", "offer-2", "asset-2", "test-id"));
  }

  private static Stream<Arguments> getInvalidOfferIds() {
    return Stream.of(
        Arguments.of("offer-1:asset-1"),
        Arguments.of("offer-2"),
        Arguments.of("offer-2:definition-2:uuid:something-else"));
  }
}
