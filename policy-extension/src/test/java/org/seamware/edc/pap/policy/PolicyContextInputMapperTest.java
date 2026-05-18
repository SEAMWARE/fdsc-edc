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
package org.seamware.edc.pap.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.stream.Stream;
import org.eclipse.edc.policy.context.request.spi.RequestCatalogPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestContractNegotiationPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestTransferProcessPolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyContext;
import org.eclipse.edc.spi.iam.RequestContext;
import org.eclipse.edc.spi.types.domain.message.RemoteMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.seamware.pap.model.GenericJsonInputVO;

/**
 * Unit tests for {@link PolicyContextInputMapper}.
 *
 * <p>Verifies that each EDC {@link PolicyContext} subtype is correctly mapped to the expected
 * {@link GenericJsonInputVO} with the appropriate payload fields and subject identity.
 */
class PolicyContextInputMapperTest {

  private static final String TEST_COUNTER_PARTY_ADDRESS = "https://provider.example.com/dsp";
  private static final String TEST_COUNTER_PARTY_ID = "urn:connector:provider";

  private PolicyContextInputMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new PolicyContextInputMapper();
  }

  /**
   * Provides test arguments for each known {@link RequestPolicyContext} subtype, mapping each to
   * its expected HTTP method and path.
   */
  static Stream<Arguments> knownContextTypes() {
    return Stream.of(
        Arguments.of(
            "Catalog",
            RequestCatalogPolicyContext.class,
            PolicyContextInputMapper.CATALOG_HTTP_METHOD,
            PolicyContextInputMapper.CATALOG_PATH),
        Arguments.of(
            "ContractNegotiation",
            RequestContractNegotiationPolicyContext.class,
            PolicyContextInputMapper.NEGOTIATION_HTTP_METHOD,
            PolicyContextInputMapper.NEGOTIATION_PATH),
        Arguments.of(
            "TransferProcess",
            RequestTransferProcessPolicyContext.class,
            PolicyContextInputMapper.TRANSFER_HTTP_METHOD,
            PolicyContextInputMapper.TRANSFER_PATH));
  }

  @ParameterizedTest(name = "{0} context maps to {2} {3}")
  @MethodSource("knownContextTypes")
  @DisplayName("Known RequestPolicyContext subtypes map to correct payload fields")
  void toJsonInput_knownContextTypes(
      String label,
      Class<? extends RequestPolicyContext> contextClass,
      String expectedMethod,
      String expectedPath) {
    RequestPolicyContext context = createMockRequestPolicyContext(contextClass);

    GenericJsonInputVO result = mapper.toJsonInput(context);

    assertNotNull(result);
    Map<String, Object> payload = result.getPayload();
    assertEquals(expectedMethod, payload.get(PolicyContextInputMapper.KEY_METHOD));
    assertEquals(expectedPath, payload.get(PolicyContextInputMapper.KEY_PATH));
    assertEquals(
        PolicyContextInputMapper.DEFAULT_PROTOCOL,
        payload.get(PolicyContextInputMapper.KEY_PROTOCOL));
    assertEquals(TEST_COUNTER_PARTY_ADDRESS, payload.get(PolicyContextInputMapper.KEY_HOST));
  }

  @ParameterizedTest(name = "{0} context includes counter-party identity in subject")
  @MethodSource("knownContextTypes")
  @DisplayName("Known RequestPolicyContext subtypes include counter-party identity in subject")
  void toJsonInput_setsSubjectIdentity(
      String label,
      Class<? extends RequestPolicyContext> contextClass,
      String expectedMethod,
      String expectedPath) {
    RequestPolicyContext context = createMockRequestPolicyContext(contextClass);

    GenericJsonInputVO result = mapper.toJsonInput(context);

    assertNotNull(result.getSubject());
    assertEquals(
        TEST_COUNTER_PARTY_ID, result.getSubject().get(PolicyContextInputMapper.KEY_IDENTITY));
    assertEquals(
        PolicyContextInputMapper.JSON_LD_CONTENT_TYPE,
        result.getPayload().get(PolicyContextInputMapper.KEY_CONTENT_TYPE));
  }

  @Test
  @DisplayName("Non-RequestPolicyContext falls back to scope-only payload")
  void toJsonInput_unknownContextType_usesDefaults() {
    String customScope = "custom.scope";
    PolicyContext context = mock(PolicyContext.class);
    when(context.scope()).thenReturn(customScope);

    GenericJsonInputVO result = mapper.toJsonInput(context);

    assertNotNull(result);
    Map<String, Object> payload = result.getPayload();
    assertEquals(customScope, payload.get(PolicyContextInputMapper.KEY_SCOPE));
    assertNull(payload.get(PolicyContextInputMapper.KEY_METHOD));
    assertTrue(
        result.getSubject() == null || result.getSubject().isEmpty(),
        "Subject should be absent for non-request context types");
  }

  @Test
  @DisplayName("Null counter-party address falls back to default host")
  void toJsonInput_nullCounterPartyAddress_usesDefaultHost() {
    RequestCatalogPolicyContext context =
        createMockRequestPolicyContextWithNullAddress(RequestCatalogPolicyContext.class);

    GenericJsonInputVO result = mapper.toJsonInput(context);

    assertEquals(
        PolicyContextInputMapper.DEFAULT_HOST,
        result.getPayload().get(PolicyContextInputMapper.KEY_HOST));
  }

  @Test
  @DisplayName("Null counter-party ID omits subject")
  void toJsonInput_nullCounterPartyId_noSubject() {
    RequestCatalogPolicyContext context =
        createMockRequestPolicyContextWithNullId(RequestCatalogPolicyContext.class);

    GenericJsonInputVO result = mapper.toJsonInput(context);

    assertTrue(
        result.getSubject() == null || result.getSubject().isEmpty(),
        "Subject should be absent when counter-party ID is null");
  }

  /**
   * Creates a mock {@link RequestPolicyContext} of the given subtype with standard test values for
   * counter-party address and identity.
   */
  private <T extends RequestPolicyContext> T createMockRequestPolicyContext(Class<T> contextClass) {
    T context = mock(contextClass);
    RequestContext requestContext = mock(RequestContext.class);
    RemoteMessage message = mock(RemoteMessage.class);

    when(context.requestContext()).thenReturn(requestContext);
    when(requestContext.getMessage()).thenReturn(message);
    when(message.getCounterPartyAddress()).thenReturn(TEST_COUNTER_PARTY_ADDRESS);
    when(message.getCounterPartyId()).thenReturn(TEST_COUNTER_PARTY_ID);

    return context;
  }

  /** Creates a mock {@link RequestPolicyContext} with a null counter-party address. */
  private <T extends RequestPolicyContext> T createMockRequestPolicyContextWithNullAddress(
      Class<T> contextClass) {
    T context = mock(contextClass);
    RequestContext requestContext = mock(RequestContext.class);
    RemoteMessage message = mock(RemoteMessage.class);

    when(context.requestContext()).thenReturn(requestContext);
    when(requestContext.getMessage()).thenReturn(message);
    when(message.getCounterPartyAddress()).thenReturn(null);
    when(message.getCounterPartyId()).thenReturn(TEST_COUNTER_PARTY_ID);

    return context;
  }

  /** Creates a mock {@link RequestPolicyContext} with a null counter-party ID. */
  private <T extends RequestPolicyContext> T createMockRequestPolicyContextWithNullId(
      Class<T> contextClass) {
    T context = mock(contextClass);
    RequestContext requestContext = mock(RequestContext.class);
    RemoteMessage message = mock(RemoteMessage.class);

    when(context.requestContext()).thenReturn(requestContext);
    when(requestContext.getMessage()).thenReturn(message);
    when(message.getCounterPartyAddress()).thenReturn(TEST_COUNTER_PARTY_ADDRESS);
    when(message.getCounterPartyId()).thenReturn(null);

    return context;
  }
}
