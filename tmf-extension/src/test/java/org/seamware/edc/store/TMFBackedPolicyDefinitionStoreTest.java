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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentMatchers;
import org.seamware.edc.TMFConfig;
import org.seamware.edc.tmf.DuplicateIdException;
import org.seamware.edc.tmf.OfferingPolicy;
import org.seamware.edc.tmf.ParticipantResolver;
import org.seamware.edc.tmf.ProductCatalogApiClient;

public class TMFBackedPolicyDefinitionStoreTest {

  private static final String TEST_ID = "test-id";
  private static final String DEFAULT_CONTEXT = "default-context";
  private static final String CONTEXT_A = "context-a";
  private static final String CONTEXT_B = "context-b";
  private static final String CONTEXT_C = "context-c";

  private Monitor monitor;
  private ProductCatalogApiClient productCatalogApiClient;
  private TMFBackedPolicyDefinitionStore tmfBackedPolicyDefinitionStore;

  @BeforeEach
  public void setup() {
    monitor = mock(Monitor.class);
    productCatalogApiClient = mock(ProductCatalogApiClient.class);
    TMFEdcMapper tmfEdcMapper =
        new TMFEdcMapper(
            monitor,
            new ObjectMapper(),
            mock(ParticipantResolver.class),
            mock(TypeTransformerRegistry.class),
            mock(JsonLd.class),
            Clock.systemUTC(),
            DEFAULT_CONTEXT);

    tmfBackedPolicyDefinitionStore =
        new TMFBackedPolicyDefinitionStore(monitor, productCatalogApiClient, tmfEdcMapper);
  }

  /** Owning offering's contexts, requested id, expected definition id and context (null: none). */
  private static Stream<Arguments> policyLookups() {
    String qualifiedA = ContextQualifiedId.qualify(TEST_ID, CONTEXT_A);
    String qualifiedB = ContextQualifiedId.qualify(TEST_ID, CONTEXT_B);
    String qualifiedC = ContextQualifiedId.qualify(TEST_ID, CONTEXT_C);
    List<String> ab = List.of(CONTEXT_A, CONTEXT_B);
    List<String> all = List.of(TMFConfig.ALL_PARTICIPANT_CONTEXTS);
    return Stream.of(
        Arguments.of("legacy, plain id", null, TEST_ID, TEST_ID, DEFAULT_CONTEXT),
        Arguments.of("legacy, qualified A", null, qualifiedA, qualifiedA, CONTEXT_A),
        Arguments.of(
            "legacy, qualified default",
            null,
            ContextQualifiedId.qualify(TEST_ID, DEFAULT_CONTEXT),
            null,
            null),
        Arguments.of("context A, plain id", List.of(CONTEXT_A), TEST_ID, TEST_ID, CONTEXT_A),
        Arguments.of("context B, plain id", List.of(CONTEXT_B), TEST_ID, TEST_ID, CONTEXT_B),
        Arguments.of("single context, qualified id", List.of(CONTEXT_A), qualifiedA, null, null),
        Arguments.of("A and B, qualified A", ab, qualifiedA, qualifiedA, CONTEXT_A),
        Arguments.of("A and B, qualified B", ab, qualifiedB, qualifiedB, CONTEXT_B),
        Arguments.of("A and B, qualified C", ab, qualifiedC, null, null),
        Arguments.of("A and B, plain id", ab, TEST_ID, null, null),
        Arguments.of("all, qualified A", all, qualifiedA, qualifiedA, CONTEXT_A),
        Arguments.of("all, qualified C", all, qualifiedC, qualifiedC, CONTEXT_C),
        Arguments.of("all, plain id", all, TEST_ID, null, null));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("policyLookups")
  public void testFindById_stampsTheOwningOfferingsContext(
      String name,
      List<String> offeringContexts,
      String requestedId,
      String expectedId,
      String expectedContext) {
    Policy testPolicy = getTestPolicy(TEST_ID);
    when(productCatalogApiClient.getByPolicyId(eq(TEST_ID)))
        .thenReturn(Optional.of(new OfferingPolicy(testPolicy, offeringContexts)));

    PolicyDefinition definition = tmfBackedPolicyDefinitionStore.findById(requestedId);

    if (expectedId == null) {
      assertNull(definition, "The id must not resolve.");
      return;
    }
    assertEquals(
        PolicyDefinition.Builder.newInstance()
            .policy(testPolicy)
            .id(expectedId)
            .participantContextId(expectedContext)
            .build(),
        definition);
  }

  @Test
  public void testFindById_policyIdContainingTheSeparatorResolvesAsPlainId() {
    String idWithSeparator = "urn:policy@v1";
    Policy testPolicy = getTestPolicy(idWithSeparator);
    when(productCatalogApiClient.getByPolicyId(eq(idWithSeparator)))
        .thenReturn(Optional.of(new OfferingPolicy(testPolicy, null)));

    PolicyDefinition definition = tmfBackedPolicyDefinitionStore.findById(idWithSeparator);

    assertEquals(idWithSeparator, definition.getId());
    assertEquals(DEFAULT_CONTEXT, definition.getParticipantContextId());
  }

  @Test
  public void testFindById_ambiguousIdResolvesToNullAndIsLoggedSevere() {
    String qualifiedA = ContextQualifiedId.qualify(TEST_ID, CONTEXT_A);
    when(productCatalogApiClient.getByPolicyId(eq(qualifiedA)))
        .thenReturn(Optional.of(new OfferingPolicy(getTestPolicy(qualifiedA), List.of(CONTEXT_A))));
    when(productCatalogApiClient.getByPolicyId(eq(TEST_ID)))
        .thenReturn(
            Optional.of(
                new OfferingPolicy(getTestPolicy(TEST_ID), Arrays.asList(CONTEXT_A, CONTEXT_B))));

    assertNull(tmfBackedPolicyDefinitionStore.findById(qualifiedA));
    verify(monitor).severe(ArgumentMatchers.<String>argThat(m -> m.contains(qualifiedA)));
  }

  private static Stream<Arguments> unaddressablePlainHits() {
    return Stream.of(
        Arguments.of(List.of(CONTEXT_A, CONTEXT_B)),
        Arguments.of(List.of(TMFConfig.ALL_PARTICIPANT_CONTEXTS)));
  }

  @ParameterizedTest(name = "plain hit tagged {0}")
  @MethodSource("unaddressablePlainHits")
  public void testFindById_unaddressablePlainHitFallsBackToTheQualifiedId(
      List<String> plainHitContexts) {
    String qualifiedA = ContextQualifiedId.qualify(TEST_ID, CONTEXT_A);
    when(productCatalogApiClient.getByPolicyId(eq(qualifiedA)))
        .thenReturn(Optional.of(new OfferingPolicy(getTestPolicy(qualifiedA), plainHitContexts)));
    when(productCatalogApiClient.getByPolicyId(eq(TEST_ID)))
        .thenReturn(
            Optional.of(new OfferingPolicy(getTestPolicy(TEST_ID), List.of(CONTEXT_A, CONTEXT_B))));

    PolicyDefinition definition = tmfBackedPolicyDefinitionStore.findById(qualifiedA);

    assertNotNull(definition, "The qualified id of the multi-context offering must resolve.");
    assertEquals(qualifiedA, definition.getId());
    assertEquals(CONTEXT_A, definition.getParticipantContextId());
  }

  @Test
  public void testFindById_failsForAPolicyIdUsedByOfferingsInTwoContexts() {
    when(productCatalogApiClient.getByPolicyId(eq(TEST_ID)))
        .thenThrow(new DuplicateIdException("Policy test-id is used by multiple offerings."));

    assertThrows(
        EdcPersistenceException.class, () -> tmfBackedPolicyDefinitionStore.findById(TEST_ID));
  }

  @Test
  public void testFindById_no_spec() {
    when(productCatalogApiClient.getByPolicyId(eq(TEST_ID))).thenReturn(Optional.empty());

    assertNull(
        tmfBackedPolicyDefinitionStore.findById(TEST_ID),
        "If no corresponding spec exists, null should be returned.");
  }

  @Test
  public void testFindById_api_error() {
    when(productCatalogApiClient.getByPolicyId(eq(TEST_ID)))
        .thenThrow(new RuntimeException("Something bad happened"));

    assertThrows(
        EdcPersistenceException.class,
        () -> tmfBackedPolicyDefinitionStore.findById(TEST_ID),
        "If an error happens, an EdcPersistence Exception should be thrown.");
  }

  private static Policy getTestPolicy(String uid) {
    return Policy.Builder.newInstance().extensibleProperty(TMFEdcMapper.UID_KEY, uid).build();
  }
}
