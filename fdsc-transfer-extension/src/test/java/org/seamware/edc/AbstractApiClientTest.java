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

import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.api.BeforeEach;

public abstract class AbstractApiClientTest {

  protected Monitor monitor;
  protected OkHttpClient okHttpClient;
  protected MockWebServer mockWebServer;
  protected ObjectMapper objectMapper;

  @BeforeEach
  public void setup() throws Exception {
    setupApiClient();
    setupConcreteClient(mockWebServer.url("").toString());
  }

  protected void setupApiClient() throws Exception {
    mockWebServer = new MockWebServer();
    mockWebServer.start();

    SchemaBaseUriHolder.configure(URI.create("http://base.uri"));

    objectMapper = new ObjectMapper();

    monitor = mock(Monitor.class);
    okHttpClient = new OkHttpClient();
  }

  public abstract void setupConcreteClient(String baseUrl);

  protected void mockResponse(int statusCode) {
    mockWebServer.enqueue(new MockResponse().setResponseCode(statusCode));
  }

  protected <T> void mockResponse(int statusCode, T response) throws Exception {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(statusCode)
            .setBody(objectMapper.writeValueAsString(response)));
  }
}
