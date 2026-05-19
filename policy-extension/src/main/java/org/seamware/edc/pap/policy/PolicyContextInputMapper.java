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

import java.util.HashMap;
import java.util.Map;
import org.eclipse.edc.policy.context.request.spi.RequestCatalogPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestContractNegotiationPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestTransferProcessPolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyContext;
import org.eclipse.edc.spi.iam.RequestContext;
import org.eclipse.edc.spi.types.domain.message.RemoteMessage;
import org.seamware.pap.model.GenericJsonInputVO;
import org.seamware.pap.model.HeadersVO;
import org.seamware.pap.model.TestRequestVO;

/**
 * Maps an EDC {@link PolicyContext} to the appropriate ODRL-PAP evaluation input.
 *
 * <p>The PAP's {@code POST /validate} endpoint accepts two mutually exclusive input formats:
 *
 * <ul>
 *   <li><b>HTTP request evaluation</b> ({@link TestRequestVO}) — used for {@link
 *       RequestPolicyContext} subtypes that represent incoming DSP protocol messages:
 *       <ul>
 *         <li>{@link RequestCatalogPolicyContext} &rarr; GET on the catalog path
 *         <li>{@link RequestContractNegotiationPolicyContext} &rarr; POST on the negotiations path
 *         <li>{@link RequestTransferProcessPolicyContext} &rarr; POST on the transfers path
 *       </ul>
 *   <li><b>JSON payload evaluation</b> ({@link GenericJsonInputVO}) — used for all other {@link
 *       PolicyContext} types (e.g. internal contract negotiation, transfer process, catalog policy
 *       evaluation), carrying the scope as payload
 * </ul>
 */
public class PolicyContextInputMapper {

  /** HTTP method for catalog requests. */
  static final TestRequestVO.MethodEnum CATALOG_HTTP_METHOD = TestRequestVO.MethodEnum.GET;

  /** HTTP method for contract negotiation requests. */
  static final TestRequestVO.MethodEnum NEGOTIATION_HTTP_METHOD = TestRequestVO.MethodEnum.POST;

  /** HTTP method for transfer process requests. */
  static final TestRequestVO.MethodEnum TRANSFER_HTTP_METHOD = TestRequestVO.MethodEnum.POST;

  /** Default HTTP method for unrecognized request context types. */
  static final TestRequestVO.MethodEnum DEFAULT_HTTP_METHOD = TestRequestVO.MethodEnum.POST;

  /** DSP catalog request path. */
  static final String CATALOG_PATH = "/catalog";

  /** DSP contract negotiation request path. */
  static final String NEGOTIATION_PATH = "/negotiations";

  /** DSP transfer process request path. */
  static final String TRANSFER_PATH = "/transfers";

  /** Default protocol for all HTTP requests. */
  static final TestRequestVO.ProtocolEnum DEFAULT_PROTOCOL = TestRequestVO.ProtocolEnum.HTTPS;

  /** Content type for JSON-LD payloads used in DSP protocol messages. */
  static final String JSON_LD_CONTENT_TYPE = "application/ld+json";

  /** Default host when no counter-party address is available. */
  static final String DEFAULT_HOST = "unknown";

  /** Payload key for the policy scope in JSON input. */
  static final String KEY_SCOPE = "scope";

  /**
   * Converts a {@link RequestPolicyContext} into a {@link TestRequestVO} for HTTP request
   * evaluation by the PAP.
   *
   * <p>The mapping extracts the counter-party address and identity from the underlying {@link
   * RemoteMessage} and determines the HTTP method and path from the concrete context type.
   *
   * @param context the request policy context to convert
   * @return a {@link TestRequestVO} representing the HTTP request for PAP evaluation
   */
  public TestRequestVO toTestRequest(RequestPolicyContext context) {
    TestRequestVO testRequest = new TestRequestVO();
    testRequest.protocol(DEFAULT_PROTOCOL);

    RequestContext requestContext = context.requestContext();
    RemoteMessage message = requestContext.getMessage();

    String host = message.getCounterPartyAddress();
    testRequest.host(host != null ? host : DEFAULT_HOST);

    String counterPartyId = message.getCounterPartyId();
    if (counterPartyId != null) {
      HeadersVO headers = new HeadersVO();
      headers.authorization(counterPartyId);
      headers.contentType(JSON_LD_CONTENT_TYPE);
      testRequest.headers(headers);
    }

    if (context instanceof RequestCatalogPolicyContext) {
      testRequest.method(CATALOG_HTTP_METHOD);
      testRequest.path(CATALOG_PATH);
    } else if (context instanceof RequestContractNegotiationPolicyContext) {
      testRequest.method(NEGOTIATION_HTTP_METHOD);
      testRequest.path(NEGOTIATION_PATH);
    } else if (context instanceof RequestTransferProcessPolicyContext) {
      testRequest.method(TRANSFER_HTTP_METHOD);
      testRequest.path(TRANSFER_PATH);
    } else {
      testRequest.method(DEFAULT_HTTP_METHOD);
      testRequest.path("/" + context.scope());
    }

    return testRequest;
  }

  /**
   * Converts a non-request {@link PolicyContext} into a {@link GenericJsonInputVO} for JSON payload
   * evaluation by the PAP.
   *
   * <p>The payload carries the context's scope, allowing the PAP to evaluate the policy against the
   * scope identifier rather than an HTTP request.
   *
   * @param context the policy context to convert
   * @return a {@link GenericJsonInputVO} representing the JSON input for PAP evaluation
   */
  public GenericJsonInputVO toJsonInput(PolicyContext context) {
    GenericJsonInputVO jsonInput = new GenericJsonInputVO();
    Map<String, Object> payload = new HashMap<>();
    payload.put(KEY_SCOPE, context.scope());
    jsonInput.payload(payload);
    return jsonInput;
  }
}
