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
package org.seamware.edc.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.seamware.edc.SchemaBaseUriHolder;

/** Jackson round-trip of the participant-context tags on the TMForum VOs. */
class ParticipantContextTagTest {

  private static final String SCHEMA_BASE = "http://base.schema/";
  private static final String CONTEXT_A = "did:web:dataspace-a";
  private static final String CONTEXT_B = "did:web:dataspace-b";
  private static final String CONTEXT_ID_PROPERTY = "participantContextId";
  private static final String CONTEXT_IDS_PROPERTY = "participantContextIds";
  private static final String ALL_CONTEXTS = "*";

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper()
          .setSerializationInclusion(JsonInclude.Include.NON_NULL)
          .registerModule(new JavaTimeModule());

  @BeforeAll
  static void configureSchemaBase() {
    SchemaBaseUriHolder.configure(URI.create(SCHEMA_BASE));
  }

  private record TaggedVo<T>(
      Class<T> type,
      Supplier<T> factory,
      BiConsumer<T, String> setter,
      Function<T, String> getter) {

    T tagged(String participantContextId) {
      T vo = factory.get();
      setter.accept(vo, participantContextId);
      return vo;
    }
  }

  private static <T> TaggedVo<T> vo(
      Class<T> type,
      Supplier<T> factory,
      BiConsumer<T, String> setter,
      Function<T, String> getter) {
    return new TaggedVo<>(type, factory, setter, getter);
  }

  private static final List<TaggedVo<?>> TAGGED_VOS =
      List.of(
          vo(
              ExtendableQuoteVO.class,
              ExtendableQuoteVO::new,
              ExtendableQuoteVO::setParticipantContextId,
              ExtendableQuoteVO::getParticipantContextId),
          vo(
              ExtendableQuoteCreateVO.class,
              ExtendableQuoteCreateVO::new,
              ExtendableQuoteCreateVO::setParticipantContextId,
              ExtendableQuoteCreateVO::getParticipantContextId),
          vo(
              ExtendableQuoteUpdateVO.class,
              ExtendableQuoteUpdateVO::new,
              ExtendableQuoteUpdateVO::setParticipantContextId,
              ExtendableQuoteUpdateVO::getParticipantContextId),
          vo(
              ExtendableQuoteItemVO.class,
              ExtendableQuoteItemVO::new,
              ExtendableQuoteItemVO::setParticipantContextId,
              ExtendableQuoteItemVO::getParticipantContextId),
          vo(
              ExtendableAgreementVO.class,
              ExtendableAgreementVO::new,
              ExtendableAgreementVO::setParticipantContextId,
              ExtendableAgreementVO::getParticipantContextId),
          vo(
              ExtendableAgreementCreateVO.class,
              ExtendableAgreementCreateVO::new,
              ExtendableAgreementCreateVO::setParticipantContextId,
              ExtendableAgreementCreateVO::getParticipantContextId),
          vo(
              ExtendableAgreementUpdateVO.class,
              ExtendableAgreementUpdateVO::new,
              ExtendableAgreementUpdateVO::setParticipantContextId,
              ExtendableAgreementUpdateVO::getParticipantContextId),
          vo(
              ExtendableUsageVO.class,
              ExtendableUsageVO::new,
              ExtendableUsageVO::setParticipantContextId,
              ExtendableUsageVO::getParticipantContextId),
          vo(
              ExtendableUsageCreateVO.class,
              ExtendableUsageCreateVO::new,
              ExtendableUsageCreateVO::setParticipantContextId,
              ExtendableUsageCreateVO::getParticipantContextId),
          vo(
              ExtendableUsageUpdateVO.class,
              ExtendableUsageUpdateVO::new,
              ExtendableUsageUpdateVO::setParticipantContextId,
              ExtendableUsageUpdateVO::getParticipantContextId));

  private static Stream<Arguments> taggedVosWithAndWithoutContext() {
    return TAGGED_VOS.stream()
        .flatMap(
            v ->
                Stream.of(
                    Arguments.of(v.type().getSimpleName(), v, CONTEXT_A),
                    Arguments.of(v.type().getSimpleName(), v, null)));
  }

  @ParameterizedTest(name = "{0} with context {2}")
  @MethodSource("taggedVosWithAndWithoutContext")
  <T> void participantContextId_roundTrips(String name, TaggedVo<T> v, String context)
      throws Exception {
    String json = OBJECT_MAPPER.writeValueAsString(v.tagged(context));

    JsonNode node = OBJECT_MAPPER.readTree(json);
    if (context == null) {
      assertFalse(node.has(CONTEXT_ID_PROPERTY), "An untagged entity must not carry the property.");
    } else {
      assertEquals(context, node.get(CONTEXT_ID_PROPERTY).asText());
    }
    assertEquals(context, v.getter().apply(OBJECT_MAPPER.readValue(json, v.type())));
  }

  private static Stream<Arguments> vosWithEquality() {
    return Stream.of(
        Arguments.of(TAGGED_VOS.get(0)),
        Arguments.of(TAGGED_VOS.get(4)),
        Arguments.of(TAGGED_VOS.get(7)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("vosWithEquality")
  <T> void participantContextId_isPartOfEquality(TaggedVo<T> v) {
    assertEquals(v.tagged(CONTEXT_A), v.tagged(CONTEXT_A));
    assertEquals(v.tagged(CONTEXT_A).hashCode(), v.tagged(CONTEXT_A).hashCode());
    assertNotEquals(v.tagged(CONTEXT_A), v.tagged(CONTEXT_B));
    assertNotEquals(v.tagged(CONTEXT_A), v.tagged(null));
  }

  private static Stream<Arguments> offeringContexts() {
    return Stream.of(
        Arguments.of("absent", null),
        Arguments.of("single", List.of(CONTEXT_A)),
        Arguments.of("several", List.of(CONTEXT_A, CONTEXT_B)),
        Arguments.of("all", List.of(ALL_CONTEXTS)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("offeringContexts")
  void offeringParticipantContextIds_roundTrip(String name, List<String> contexts)
      throws Exception {
    ExtendableProductOffering offering =
        new ExtendableProductOffering().setParticipantContextIds(contexts);

    String json = OBJECT_MAPPER.writeValueAsString(offering);
    ExtendableProductOffering read = OBJECT_MAPPER.readValue(json, ExtendableProductOffering.class);

    assertEquals(contexts, read.getParticipantContextIds());
    assertEquals(offering, read);
  }

  @Test
  void offeringParticipantContextIds_emptyListIsNotWritten() throws Exception {
    ExtendableProductOffering offering =
        new ExtendableProductOffering().setParticipantContextIds(new ArrayList<>());

    JsonNode node = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(offering));

    assertFalse(
        node.has(CONTEXT_IDS_PROPERTY), "TMForum reads an empty array back as [{}], omit it.");
  }

  @Test
  void offeringParticipantContextIds_isPartOfEquality() {
    assertNotEquals(
        new ExtendableProductOffering().setParticipantContextIds(List.of(CONTEXT_A)),
        new ExtendableProductOffering().setParticipantContextIds(List.of(CONTEXT_B)));
  }

  @Test
  void usageCreate_referencesUsageSchema() {
    assertEquals(
        URI.create(SCHEMA_BASE + "usage.json"),
        new ExtendableUsageCreateVO().getAtSchemaLocation());
  }

  @Test
  void untaggedOffering_hasNoContexts() {
    assertNull(new ExtendableProductOffering().getParticipantContextIds());
  }
}
