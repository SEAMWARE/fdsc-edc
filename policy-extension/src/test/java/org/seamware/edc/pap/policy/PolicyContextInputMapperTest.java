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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.seamware.pap.model.GenericJsonInputVO;
import org.seamware.pap.model.TestRequestVO;

/**
 * Unit tests for {@link PolicyContextInputMapper}.
 *
 * <p>Verifies that {@link RequestPolicyContext} subtypes are mapped to {@link TestRequestVO} (HTTP
 * request evaluation) and other {@link PolicyContext} types are mapped to {@link
 * GenericJsonInputVO} (JSON payload evaluation).
 */
class PolicyContextInputMapperTest {

  private static final String TEST_COUNTER_PARTY_ADDRESS = "https://provider.example.com/dsp";
  private static final String TEST_COUNTER_PARTY_ID = "urn:connector:provider";

  private PolicyContextInputMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new PolicyContextInputMapper();
  }

  @Nested
  @DisplayName("HTTP request mapping (RequestPolicyContext subtypes)")
  class HttpRequestMapping {

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
    @DisplayName("Known RequestPolicyContext subtypes map to correct method and path")
    void toTestRequest_knownContextTypes(
        String label,
        Class<? extends RequestPolicyContext> contextClass,
        TestRequestVO.MethodEnum expectedMethod,
        String expectedPath) {
      RequestPolicyContext context = createMockRequestPolicyContext(contextClass);

      TestRequestVO result = mapper.toTestRequest(context);

      assertNotNull(result);
      assertEquals(expectedMethod, result.getMethod());
      assertEquals(expectedPath, result.getPath());
      assertEquals(PolicyContextInputMapper.DEFAULT_PROTOCOL, result.getProtocol());
      assertEquals(TEST_COUNTER_PARTY_ADDRESS, result.getHost());
    }

    @ParameterizedTest(name = "{0} context includes counter-party identity in authorization header")
    @MethodSource("knownContextTypes")
    @DisplayName("Known RequestPolicyContext subtypes include authorization header")
    void toTestRequest_setsAuthorizationHeader(
        String label,
        Class<? extends RequestPolicyContext> contextClass,
        TestRequestVO.MethodEnum expectedMethod,
        String expectedPath) {
      RequestPolicyContext context = createMockRequestPolicyContext(contextClass);

      TestRequestVO result = mapper.toTestRequest(context);

      assertNotNull(result.getHeaders());
      assertEquals(TEST_COUNTER_PARTY_ID, result.getHeaders().getAuthorization());
      assertEquals(
          PolicyContextInputMapper.JSON_LD_CONTENT_TYPE, result.getHeaders().getContentType());
    }

    @Test
    @DisplayName("Null counter-party address falls back to default host")
    void toTestRequest_nullCounterPartyAddress_usesDefaultHost() {
      RequestCatalogPolicyContext context =
          createMockRequestPolicyContextWithNullAddress(RequestCatalogPolicyContext.class);

      TestRequestVO result = mapper.toTestRequest(context);

      assertEquals(PolicyContextInputMapper.DEFAULT_HOST, result.getHost());
    }

    @Test
    @DisplayName("Null counter-party ID omits headers")
    void toTestRequest_nullCounterPartyId_noHeaders() {
      RequestCatalogPolicyContext context =
          createMockRequestPolicyContextWithNullId(RequestCatalogPolicyContext.class);

      TestRequestVO result = mapper.toTestRequest(context);

      assertNull(result.getHeaders());
    }
  }

  @Nested
  @DisplayName("JSON payload mapping (non-request PolicyContext types)")
  class JsonInputMapping {

    @Test
    @DisplayName("Non-RequestPolicyContext maps to GenericJsonInputVO with scope in payload")
    void toJsonInput_includesScopeInPayload() {
      String customScope = "contract.negotiation";
      PolicyContext context = mock(PolicyContext.class);
      when(context.scope()).thenReturn(customScope);

      GenericJsonInputVO result = mapper.toJsonInput(context);

      assertNotNull(result);
      assertNotNull(result.getPayload());
      assertEquals(customScope, result.getPayload().get(PolicyContextInputMapper.KEY_SCOPE));
    }

    @Test
    @DisplayName("JSON input payload contains no HTTP request fields")
    void toJsonInput_noHttpRequestFields() {
      PolicyContext context = mock(PolicyContext.class);
      when(context.scope()).thenReturn("custom.scope");

      GenericJsonInputVO result = mapper.toJsonInput(context);

      assertNull(result.getPayload().get("method"));
      assertNull(result.getPayload().get("path"));
      assertNull(result.getPayload().get("host"));
    }

    @Test
    @DisplayName("JSON input has no subject set")
    void toJsonInput_noSubject() {
      PolicyContext context = mock(PolicyContext.class);
      when(context.scope()).thenReturn("custom.scope");

      GenericJsonInputVO result = mapper.toJsonInput(context);

      assertTrue(
          result.getSubject() == null || result.getSubject().isEmpty(),
          "Subject should be absent for non-request context types");
    }
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
