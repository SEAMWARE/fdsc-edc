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

import org.eclipse.edc.policy.context.request.spi.RequestCatalogPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestContractNegotiationPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestTransferProcessPolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyContext;
import org.eclipse.edc.spi.iam.RequestContext;
import org.eclipse.edc.spi.types.domain.message.RemoteMessage;
import org.seamware.pap.model.HeadersVO;
import org.seamware.pap.model.TestRequestVO;

/**
 * Maps an EDC {@link PolicyContext} to the ODRL-PAP's {@link TestRequestVO} format.
 *
 * <p>The PAP's {@code POST /validate} endpoint expects a {@link TestRequestVO} describing the HTTP
 * request being evaluated. This mapper inspects the concrete {@link PolicyContext} subtype to
 * determine the appropriate HTTP method, path, and participant identity:
 *
 * <ul>
 *   <li>{@link RequestCatalogPolicyContext} &rarr; GET request on the catalog path
 *   <li>{@link RequestContractNegotiationPolicyContext} &rarr; POST request on the negotiations
 *       path
 *   <li>{@link RequestTransferProcessPolicyContext} &rarr; POST request on the transfers path
 * </ul>
 *
 * <p>For unrecognized context types, a fallback POST request is produced using the context's scope
 * as the path.
 */
public class PolicyContextRequestMapper {

  /** HTTP method for catalog requests. */
  static final TestRequestVO.MethodEnum CATALOG_HTTP_METHOD = TestRequestVO.MethodEnum.GET;

  /** HTTP method for contract negotiation requests. */
  static final TestRequestVO.MethodEnum NEGOTIATION_HTTP_METHOD = TestRequestVO.MethodEnum.POST;

  /** HTTP method for transfer process requests. */
  static final TestRequestVO.MethodEnum TRANSFER_HTTP_METHOD = TestRequestVO.MethodEnum.POST;

  /** Default HTTP method for unrecognized context types. */
  static final TestRequestVO.MethodEnum DEFAULT_HTTP_METHOD = TestRequestVO.MethodEnum.POST;

  /** DSP catalog request path. */
  static final String CATALOG_PATH = "/catalog";

  /** DSP contract negotiation request path. */
  static final String NEGOTIATION_PATH = "/negotiations";

  /** DSP transfer process request path. */
  static final String TRANSFER_PATH = "/transfers";

  /** Default protocol for all requests. */
  static final TestRequestVO.ProtocolEnum DEFAULT_PROTOCOL = TestRequestVO.ProtocolEnum.HTTPS;

  /** Content type for JSON-LD payloads used in DSP protocol messages. */
  static final String JSON_LD_CONTENT_TYPE = "application/ld+json";

  /** Default host when no counter-party address is available. */
  static final String DEFAULT_HOST = "unknown";

  /**
   * Converts an EDC {@link PolicyContext} into a {@link TestRequestVO} suitable for the ODRL-PAP's
   * validation endpoint.
   *
   * <p>The mapping extracts the counter-party address and identity from the underlying {@link
   * RemoteMessage} (when the context is a {@link RequestPolicyContext}), and determines the HTTP
   * method and path from the concrete context type.
   *
   * @param context the EDC policy context to convert
   * @return a {@link TestRequestVO} representing the request for PAP evaluation
   */
  public TestRequestVO toTestRequest(PolicyContext context) {
    TestRequestVO testRequest = new TestRequestVO();
    testRequest.protocol(DEFAULT_PROTOCOL);

    if (context instanceof RequestPolicyContext requestPolicyContext) {
      mapRequestPolicyContext(testRequest, requestPolicyContext);
    } else {
      testRequest.method(DEFAULT_HTTP_METHOD);
      testRequest.host(DEFAULT_HOST);
      testRequest.path("/" + context.scope());
    }

    return testRequest;
  }

  /**
   * Populates the test request from a {@link RequestPolicyContext}, extracting host, identity, and
   * scope-specific HTTP method and path.
   */
  private void mapRequestPolicyContext(
      TestRequestVO testRequest, RequestPolicyContext requestPolicyContext) {
    RequestContext requestContext = requestPolicyContext.requestContext();
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

    if (requestPolicyContext instanceof RequestCatalogPolicyContext) {
      testRequest.method(CATALOG_HTTP_METHOD);
      testRequest.path(CATALOG_PATH);
    } else if (requestPolicyContext instanceof RequestContractNegotiationPolicyContext) {
      testRequest.method(NEGOTIATION_HTTP_METHOD);
      testRequest.path(NEGOTIATION_PATH);
    } else if (requestPolicyContext instanceof RequestTransferProcessPolicyContext) {
      testRequest.method(TRANSFER_HTTP_METHOD);
      testRequest.path(TRANSFER_PATH);
    } else {
      testRequest.method(DEFAULT_HTTP_METHOD);
      testRequest.path("/" + requestPolicyContext.scope());
    }
  }
}
