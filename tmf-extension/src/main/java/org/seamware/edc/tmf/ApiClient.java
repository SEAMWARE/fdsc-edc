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

  /** Filter on the context tag of Quotes, Agreements and Usages; never matches an offering. */
  protected static final String PARTICIPANT_CONTEXT_ID_QUERY_PARAM = "participantContextId";

  /** Array-contains filter on a ProductOffering's context tags. */
  protected static final String PARTICIPANT_CONTEXT_IDS_QUERY_PARAM = "participantContextIds";

  /** Separates alternatives within one TMForum query value, which the server ORs. */
  protected static final String QUERY_VALUE_OR_SEPARATOR = ",";

  /** Characters TMForum splits a query value on; a context filter value must not contain them. */
  private static final String QUERY_VALUE_SEPARATORS = ",;";

  protected final Monitor monitor;
  protected final OkHttpClient okHttpClient;

  protected ApiClient(Monitor monitor, OkHttpClient okHttpClient) {
    this.monitor = monitor;
    this.okHttpClient = okHttpClient;
  }

  /**
   * Rejects a context id TMForum would split into several alternatives, which would widen the
   * filter to other contexts.
   */
  protected static String requireFilterableContextId(String participantContextId) {
    if (participantContextId.chars().anyMatch(c -> QUERY_VALUE_SEPARATORS.indexOf(c) >= 0)) {
      throw new IllegalArgumentException(
          String.format(
              "Participant context id '%s' must not contain any of '%s'.",
              participantContextId, QUERY_VALUE_SEPARATORS));
    }
    return participantContextId;
  }

  protected ResponseBody executeRequest(Request request) {
    try {
      Response response = okHttpClient.newCall(request).execute();
      if (response.isSuccessful()) {
        return response.body();
      } else {
        int code = response.code();
        response.close();
        monitor.warning(
            String.format(
                "Was not able to get as successful response for %s. Was: %s", request.url(), code));
        throw new BadGatewayException(
            String.format(
                "Was not able to get as successful response for %s. Was: %s", request.url(), code));
      }
    } catch (IOException e) {
      monitor.warning(String.format("Was not able to get response for %s", request.url()), e);
      throw new BadGatewayException(
          String.format("Was not able to get response for %s", request.url()));
    }
  }
}
