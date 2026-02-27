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
package org.seamware.edc.tir;

/*-
 * #%L
 * dcp-extension
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
import java.io.IOException;
import java.util.Optional;
import okhttp3.*;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.web.spi.exception.BadGatewayException;
import org.seamware.til.model.TrustedIssuerVO;

public class TirClient extends BaseClient {

  private static final String ISSUER_PATH = "issuer";

  public TirClient(
      Monitor monitor, OkHttpClient okHttpClient, String baseUrl, ObjectMapper objectMapper) {
    super(monitor, okHttpClient, baseUrl, objectMapper);
  }

  public Optional<TrustedIssuerVO> getIssuer(String did) {

    HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
    urlBuilder.addPathSegment(ISSUER_PATH);
    urlBuilder.addPathSegment(did);
    Request request = new Request.Builder().url(urlBuilder.build()).get().build();
    try (Response r = executeRequest(request)) {
      if (r.code() == 404) {
        return Optional.empty();
      } else if (r.isSuccessful()) {
        return Optional.of(objectMapper.readValue(r.body().string(), TrustedIssuerVO.class));
      }
      throw new BadGatewayException(String.format("Was not able to get issuer from %s", baseUrl));
    } catch (IOException e) {
      monitor.warning("Was not able to read the issuer.", e);
      throw new BadGatewayException("Was not able to read the issuer.");
    }
  }

  public void putIssuer(TrustedIssuerVO trustedIssuerVO) {
    HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
    urlBuilder.addPathSegment(ISSUER_PATH);
    String serviceString = "";
    try {
      serviceString = objectMapper.writeValueAsString(trustedIssuerVO);
    } catch (JsonProcessingException e) {
      monitor.warning("Was not able to parse issuer.", e);
      throw new IllegalArgumentException("Was not able to parse issuer.", e);
    }

    Request request =
        new Request.Builder()
            .url(urlBuilder.build())
            .put(RequestBody.create(serviceString, JSON))
            .build();

    try (Response r = executeRequest(request)) {
      if (!r.isSuccessful()) {
        throw new BadGatewayException(
            String.format("Was not able to update issuer at til %s", baseUrl));
      }
    }
  }

  public void createIssuer(TrustedIssuerVO trustedIssuerVO) {
    HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
    urlBuilder.addPathSegment(ISSUER_PATH);
    String serviceString = "";
    try {
      serviceString = objectMapper.writeValueAsString(trustedIssuerVO);
    } catch (JsonProcessingException e) {
      monitor.warning("Was not able to parse issuer.", e);
      throw new IllegalArgumentException("Was not able to parse issuer.", e);
    }

    Request request =
        new Request.Builder()
            .url(urlBuilder.build())
            .post(RequestBody.create(serviceString, JSON))
            .build();

    try (Response r = executeRequest(request)) {
      if (!r.isSuccessful()) {
        throw new BadGatewayException(
            String.format("Was not able to create issuer at til %s", baseUrl));
      }
    }
  }
}
