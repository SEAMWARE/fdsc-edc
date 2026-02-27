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
import org.seamware.pap.model.PolicyPathVO;
import org.seamware.pap.model.ServiceCreateVO;

public class OdrlPapClient extends BaseClient {

  private static final String SERVICE_PATH = "service";
  private static final String POLICY_PATH = "policy";

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

    executeRequest(request);
  }

  public void deleteService(String serviceId) {
    HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(baseUrl)).newBuilder();
    urlBuilder.addPathSegment(SERVICE_PATH);
    urlBuilder.addPathSegment(serviceId);

    executeRequest(new Request.Builder().url(urlBuilder.build()).delete().build());
  }
}
