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
package org.seamware.edc;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import okhttp3.*;
import org.eclipse.edc.spi.monitor.Monitor;

/** Base http client */
public abstract class BaseClient {

  protected static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

  protected final Monitor monitor;
  protected final OkHttpClient okHttpClient;
  protected final String baseUrl;
  protected final ObjectMapper objectMapper;

  protected BaseClient(
      Monitor monitor, OkHttpClient okHttpClient, String baseUrl, ObjectMapper objectMapper) {
    this.monitor = monitor;
    this.okHttpClient = okHttpClient;
    this.baseUrl = baseUrl;
    this.objectMapper = objectMapper;
  }

  protected ResponseBody executeRequestWithResponse(Request request) {
    return executeRequest(request).body();
  }

  protected Response executeRequest(Request request) {
    try {
      Response response = okHttpClient.newCall(request).execute();
      if (response.isSuccessful()) {
        return response;
      } else {
        monitor.warning(
            String.format(
                "Was not able to get as successful response for %s. Was: %s - %s",
                request.url(), response.code(), response.body().string()));
        throw new HttpClientException(
            String.format(
                "Was not able to get as successful response for %s. Was: %s",
                request.url(), response.code()),
            response.code());
      }
    } catch (IOException e) {
      monitor.warning(String.format("Was not able to get response for %s", request.url()));
      throw new HttpClientException(
          String.format("Was not able to get response for %s", request.url()), e);
    }
  }
}
