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

/**
 * Maps an EDC {@link PolicyContext} to the ODRL-PAP's {@link GenericJsonInputVO} format.
 *
 * <p>The PAP's {@code POST /validate} endpoint accepts a {@link GenericJsonInputVO} for evaluating
 * policies against arbitrary JSON payloads. This mapper inspects the concrete {@link PolicyContext}
 * subtype to build an appropriate payload and subject:
 *
 * <ul>
 *   <li>{@link RequestCatalogPolicyContext} &rarr; payload with GET method on the catalog path
 *   <li>{@link RequestContractNegotiationPolicyContext} &rarr; payload with POST method on the
 *       negotiations path
 *   <li>{@link RequestTransferProcessPolicyContext} &rarr; payload with POST method on the
 *       transfers path
 * </ul>
 *
 * <p>For unrecognized context types, a fallback payload is produced using the context's scope.
 */
public class PolicyContextInputMapper {

  /** HTTP method for catalog requests. */
  static final String CATALOG_HTTP_METHOD = "GET";

  /** HTTP method for contract negotiation requests. */
  static final String NEGOTIATION_HTTP_METHOD = "POST";

  /** HTTP method for transfer process requests. */
  static final String TRANSFER_HTTP_METHOD = "POST";

  /** Default HTTP method for unrecognized context types. */
  static final String DEFAULT_HTTP_METHOD = "POST";

  /** DSP catalog request path. */
  static final String CATALOG_PATH = "/catalog";

  /** DSP contract negotiation request path. */
  static final String NEGOTIATION_PATH = "/negotiations";

  /** DSP transfer process request path. */
  static final String TRANSFER_PATH = "/transfers";

  /** Default protocol for all requests. */
  static final String DEFAULT_PROTOCOL = "https";

  /** Content type for JSON-LD payloads used in DSP protocol messages. */
  static final String JSON_LD_CONTENT_TYPE = "application/ld+json";

  /** Default host when no counter-party address is available. */
  static final String DEFAULT_HOST = "unknown";

  /** Payload key for the HTTP method. */
  static final String KEY_METHOD = "method";

  /** Payload key for the request path. */
  static final String KEY_PATH = "path";

  /** Payload key for the target host. */
  static final String KEY_HOST = "host";

  /** Payload key for the protocol. */
  static final String KEY_PROTOCOL = "protocol";

  /** Payload key for the content type. */
  static final String KEY_CONTENT_TYPE = "contentType";

  /** Payload key for the policy scope. */
  static final String KEY_SCOPE = "scope";

  /** Subject key for the participant identity. */
  static final String KEY_IDENTITY = "identity";

  /**
   * Converts an EDC {@link PolicyContext} into a {@link GenericJsonInputVO} suitable for the
   * ODRL-PAP's validation endpoint.
   *
   * <p>The mapping inspects the concrete context type to determine the payload content. For {@link
   * RequestPolicyContext} subtypes, the payload includes HTTP method, path, host, and protocol
   * derived from the underlying {@link RemoteMessage}, and the subject carries the counter-party
   * identity. For other context types, the payload contains only the scope.
   *
   * @param context the EDC policy context to convert
   * @return a {@link GenericJsonInputVO} representing the input for PAP evaluation
   */
  public GenericJsonInputVO toJsonInput(PolicyContext context) {
    GenericJsonInputVO jsonInput = new GenericJsonInputVO();
    Map<String, Object> payload = new HashMap<>();

    if (context instanceof RequestPolicyContext requestPolicyContext) {
      mapRequestPolicyContext(jsonInput, payload, requestPolicyContext);
    } else {
      payload.put(KEY_SCOPE, context.scope());
    }

    jsonInput.payload(payload);
    return jsonInput;
  }

  /**
   * Populates the JSON input from a {@link RequestPolicyContext}, extracting host, identity, and
   * scope-specific HTTP method and path into the payload, and counter-party identity into the
   * subject.
   */
  private void mapRequestPolicyContext(
      GenericJsonInputVO jsonInput,
      Map<String, Object> payload,
      RequestPolicyContext requestPolicyContext) {
    RequestContext requestContext = requestPolicyContext.requestContext();
    RemoteMessage message = requestContext.getMessage();

    String host = message.getCounterPartyAddress();
    payload.put(KEY_HOST, host != null ? host : DEFAULT_HOST);
    payload.put(KEY_PROTOCOL, DEFAULT_PROTOCOL);

    if (requestPolicyContext instanceof RequestCatalogPolicyContext) {
      payload.put(KEY_METHOD, CATALOG_HTTP_METHOD);
      payload.put(KEY_PATH, CATALOG_PATH);
    } else if (requestPolicyContext instanceof RequestContractNegotiationPolicyContext) {
      payload.put(KEY_METHOD, NEGOTIATION_HTTP_METHOD);
      payload.put(KEY_PATH, NEGOTIATION_PATH);
    } else if (requestPolicyContext instanceof RequestTransferProcessPolicyContext) {
      payload.put(KEY_METHOD, TRANSFER_HTTP_METHOD);
      payload.put(KEY_PATH, TRANSFER_PATH);
    } else {
      payload.put(KEY_METHOD, DEFAULT_HTTP_METHOD);
      payload.put(KEY_PATH, "/" + requestPolicyContext.scope());
    }

    String counterPartyId = message.getCounterPartyId();
    if (counterPartyId != null) {
      payload.put(KEY_CONTENT_TYPE, JSON_LD_CONTENT_TYPE);
      Map<String, Object> subject = new HashMap<>();
      subject.put(KEY_IDENTITY, counterPartyId);
      jsonInput.subject(subject);
    }
  }
}
