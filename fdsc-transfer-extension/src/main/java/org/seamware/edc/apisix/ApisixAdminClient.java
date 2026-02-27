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
package org.seamware.edc.apisix;

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
import java.io.IOException;
import java.util.Objects;
import okhttp3.*;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.web.spi.exception.BadGatewayException;
import org.seamware.edc.BaseClient;
import org.seamware.edc.HttpClientException;

public class ApisixAdminClient extends BaseClient {

  private static final String ROUTES_PATH = "apisix/admin/routes";
  private static final String ADMIN_TOKEN_HEADER = "X-API-KEY";

  private final String adminToken;

  public ApisixAdminClient(
      Monitor monitor,
      OkHttpClient okHttpClient,
      String baseUrl,
      ObjectMapper objectMapper,
      String adminToken) {
    super(monitor, okHttpClient, baseUrl, objectMapper);
    this.adminToken = adminToken;
  }

  public Route addRoute(Route route) {
    HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
    urlBuilder.addPathSegment(ROUTES_PATH);
    String routeString = "";
    try {
      routeString = objectMapper.writeValueAsString(route);
    } catch (JsonProcessingException e) {
      monitor.warning("Was not able to parse route.", e);
      throw new BadGatewayException("Was not able to parse route.");
    }

    Request request =
        new Request.Builder()
            .url(urlBuilder.build())
            .header(ADMIN_TOKEN_HEADER, adminToken)
            .put(RequestBody.create(routeString, JSON))
            .build();
    try (ResponseBody responseBody = executeRequestWithResponse(request)) {
      return objectMapper.readValue(responseBody.bytes(), Route.class);
    } catch (IOException e) {
      monitor.warning(String.format("Was not able to create route: %s.", routeString), e);
      throw new BadGatewayException(
          String.format("Was not able to create route: %s.", routeString));
    }
  }

  public void deleteRoute(String routeId) {
    HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(baseUrl)).newBuilder();
    urlBuilder.addPathSegment(ROUTES_PATH);
    urlBuilder.addPathSegment(routeId);
    Response response =
        executeRequest(
            new Request.Builder()
                .url(urlBuilder.build())
                .header(ADMIN_TOKEN_HEADER, adminToken)
                .delete()
                .build());
    if (!response.isSuccessful()) {
      throw new HttpClientException("Was not able to delete route.", response.code());
    }
  }
}
