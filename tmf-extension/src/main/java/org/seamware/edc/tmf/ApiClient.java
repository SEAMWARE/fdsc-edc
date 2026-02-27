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
package org.seamware.edc.tmf;

import java.io.IOException;
import okhttp3.*;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.web.spi.exception.BadGatewayException;

public abstract class ApiClient {

  protected static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
  protected static final String OFFSET_PARAM = "offset";
  protected static final String LIMIT_PARAM = "limit";

  protected final Monitor monitor;
  protected final OkHttpClient okHttpClient;

  protected ApiClient(Monitor monitor, OkHttpClient okHttpClient) {
    this.monitor = monitor;
    this.okHttpClient = okHttpClient;
  }

  protected ResponseBody executeRequest(Request request) {
    try {
      Response response = okHttpClient.newCall(request).execute();
      if (response.isSuccessful()) {
        return response.body();
      } else {
        monitor.warning(
            String.format(
                "Was not able to get as successful response for %s. Was: %s",
                request.url(), response.code()));
        throw new BadGatewayException(
            String.format(
                "Was not able to get as successful response for %s. Was: %s",
                request.url(), response.code()));
      }
    } catch (IOException e) {
      monitor.warning(String.format("Was not able to get response for %s", request.url()), e);
      throw new BadGatewayException(
          String.format("Was not able to get response for %s", request.url()));
    }
  }
}
