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
package org.seamware.edc.ccs;

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
import java.util.Optional;
import okhttp3.*;
import org.eclipse.edc.spi.monitor.Monitor;
import org.seamware.credentials.model.ServiceVO;
import org.seamware.edc.BaseClient;

public class CredentialsConfigServiceClient extends BaseClient {

  private static final String SERVICE_PATH = "service";

  public CredentialsConfigServiceClient(
      Monitor monitor, OkHttpClient okHttpClient, String baseUrl, ObjectMapper objectMapper) {
    super(monitor, okHttpClient, baseUrl, objectMapper);
  }

  public void createService(ServiceVO serviceVO) {
    HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(baseUrl)).newBuilder();
    urlBuilder.addPathSegment(SERVICE_PATH);
    String serviceString = "";
    try {
      serviceString = objectMapper.writeValueAsString(serviceVO);
    } catch (JsonProcessingException e) {
      monitor.warning("Was not able to parse service.", e);
      throw new IllegalArgumentException("Was not able to parse service.", e);
    }

    Request request =
        new Request.Builder()
            .url(urlBuilder.build())
            .post(RequestBody.create(serviceString, JSON))
            .build();
    Optional.ofNullable(executeRequest(request).body()).ifPresent(ResponseBody::close);
  }

  public void deleteService(String serviceId) {
    HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(baseUrl)).newBuilder();
    urlBuilder.addPathSegment(SERVICE_PATH);
    urlBuilder.addPathSegment(serviceId);
    Optional.ofNullable(
            executeRequest(new Request.Builder().url(urlBuilder.build()).delete().build()).body())
        .ifPresent(ResponseBody::close);
  }
}
