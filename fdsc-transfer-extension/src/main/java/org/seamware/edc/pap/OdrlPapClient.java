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
package org.seamware.edc.pap;

/*-
 * #%L
 * fdsc-transfer-extension
 * %%
 * Copyright (C) 2025 - 2026 Seamless Middleware Technologies S.L
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import okhttp3.*;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.web.spi.exception.BadGatewayException;
import org.seamware.edc.BaseClient;
import org.seamware.edc.HttpClientException;
import org.seamware.pap.model.MappingsVO;
import org.seamware.pap.model.PolicyPathVO;
import org.seamware.pap.model.ServiceCreateVO;
import org.seamware.pap.model.ValidationRequestVO;
import org.seamware.pap.model.ValidationResponseVO;

/**
 * HTTP client for the ODRL-PAP (Policy Administration Point) API.
 *
 * <p>Provides methods for service and policy CRUD operations, as well as policy validation and
 * supported-mappings retrieval against the PAP REST API.
 */
public class OdrlPapClient extends BaseClient {

  private static final String SERVICE_PATH = "service";
  private static final String POLICY_PATH = "policy";
  private static final String VALIDATE_PATH = "validate";
  private static final String MAPPINGS_PATH = "mappings";

  public OdrlPapClient(
      Monitor monitor, OkHttpClient okHttpClient, String baseUrl, ObjectMapper objectMapper) {
    super(monitor, okHttpClient, baseUrl, objectMapper);
  }

  public PolicyPathVO createService(ServiceCreateVO serviceCreate) {
    HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(baseUrl)).newBuilder();
    urlBuilder.addPathSegment(SERVICE_PATH);
    RequestBody requestBody = null;
    try {
      String br = objectMapper.writeValueAsString(serviceCreate);
      requestBody = RequestBody.create(br, JSON);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Was not able to serialize agreement.", e);
    }
    Request request = new Request.Builder().url(urlBuilder.build()).post(requestBody).build();
    try (ResponseBody responseBody = executeRequest(request).body()) {
      return objectMapper.readValue(responseBody.bytes(), PolicyPathVO.class);
    } catch (Exception e) {
      monitor.warning("Was not able to read agreement creation response.", e);
      throw new BadGatewayException("Was not able to read agreement creation response.");
    }
  }

  public void createPolicy(String serviceId, Object policy) {
    HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(baseUrl)).newBuilder();
    urlBuilder.addPathSegment(SERVICE_PATH);
    urlBuilder.addPathSegment(serviceId);
    urlBuilder.addPathSegment(POLICY_PATH);
    String policyString = "";
    try {
      policyString = objectMapper.writeValueAsString(policy);
    } catch (JsonProcessingException e) {
      monitor.warning("Was not able to parse policy.", e);
      throw new IllegalArgumentException("Was not able to parse policy.", e);
    }
    HttpUrl url = urlBuilder.build();
    Request request =
        new Request.Builder().url(url).post(RequestBody.create(policyString, JSON)).build();

    executeRequest(request).close();
  }

  public void deleteService(String serviceId) {
    HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(baseUrl)).newBuilder();
    urlBuilder.addPathSegment(SERVICE_PATH);
    urlBuilder.addPathSegment(serviceId);

    executeRequest(new Request.Builder().url(urlBuilder.build()).delete().build()).close();
  }

  /**
   * Validates an ODRL policy against a test request via the PAP's {@code POST /validate} endpoint.
   *
   * @param request the validation request containing the ODRL policy and a test HTTP request
   * @return the validation response indicating whether the policy allows the request, with
   *     explanations on denial
   * @throws HttpClientException if the PAP returns a non-2xx response
   * @throws BadGatewayException if the response body cannot be deserialized
   */
  public ValidationResponseVO validate(ValidationRequestVO request) {
    HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(baseUrl)).newBuilder();
    urlBuilder.addPathSegment(VALIDATE_PATH);
    RequestBody requestBody;
    try {
      String body = objectMapper.writeValueAsString(request);
      requestBody = RequestBody.create(body, JSON);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Was not able to serialize validation request.", e);
    }
    Request httpRequest = new Request.Builder().url(urlBuilder.build()).post(requestBody).build();
    try (ResponseBody responseBody = executeRequest(httpRequest).body()) {
      return objectMapper.readValue(responseBody.bytes(), ValidationResponseVO.class);
    } catch (HttpClientException e) {
      throw e;
    } catch (Exception e) {
      monitor.warning("Was not able to read validation response.", e);
      throw new BadGatewayException("Was not able to read validation response.");
    }
  }

  /**
   * Retrieves the supported policy mappings from the PAP via the {@code GET /mappings} endpoint.
   *
   * <p>Mappings describe the available actions, operators, operands, and other ODRL constructs that
   * the PAP supports for policy construction.
   *
   * @return the supported mappings
   * @throws HttpClientException if the PAP returns a non-2xx response
   * @throws BadGatewayException if the response body cannot be deserialized
   */
  public MappingsVO getMappings() {
    HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(baseUrl)).newBuilder();
    urlBuilder.addPathSegment(MAPPINGS_PATH);
    Request httpRequest = new Request.Builder().url(urlBuilder.build()).get().build();
    try (ResponseBody responseBody = executeRequest(httpRequest).body()) {
      return objectMapper.readValue(responseBody.bytes(), MappingsVO.class);
    } catch (HttpClientException e) {
      throw e;
    } catch (Exception e) {
      monitor.warning("Was not able to read mappings response.", e);
      throw new BadGatewayException("Was not able to read mappings response.");
    }
  }
}
