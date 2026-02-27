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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.web.spi.exception.BadGatewayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.seamware.til.model.TrustedIssuerVO;

public class TirClientTest {

  private static final String TEST_ISSUER_DID = "did:web:issuer.test";

  private MockWebServer mockWebServer;
  private ObjectMapper objectMapper;
  private TirClient tirClient;

  @BeforeEach
  public void setup() throws Exception {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
    objectMapper = new ObjectMapper();

    tirClient =
        new TirClient(
            mock(Monitor.class),
            new OkHttpClient(),
            mockWebServer.url("").toString(),
            objectMapper);
  }

  @Test
  public void testGetIssuer_success() throws Exception {
    TrustedIssuerVO testIssuer = getIssuer(TEST_ISSUER_DID);
    mockResponse(200, testIssuer);

    assertEquals(
        testIssuer,
        tirClient.getIssuer(TEST_ISSUER_DID).get(),
        "The correct issuer should be returned.");

    RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertEquals("/issuer/" + TEST_ISSUER_DID, recordedRequest.getPath());
  }

  @Test
  public void testGetIssuer_not_found() throws Exception {
    mockResponse(404);

    assertTrue(
        tirClient.getIssuer(TEST_ISSUER_DID).isEmpty(),
        "If the issuer does not exist, nothing should be returned");

    RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertEquals("/issuer/" + TEST_ISSUER_DID, recordedRequest.getPath());
  }

  @Test
  public void testGetIssuer_invalid_content() throws Exception {
    mockResponse(200, "invalid");

    assertThrows(
        BadGatewayException.class,
        () -> tirClient.getIssuer(TEST_ISSUER_DID),
        "If the server returns something invalid, a BadGateWay should be thrown.");
  }

  @ParameterizedTest(name = "Failure code {0}")
  @ValueSource(ints = {400, 401, 403, 500})
  public void testGetIssuer_bad_response(int responseCode) throws Exception {
    mockResponse(responseCode);
    assertThrows(
        BadGatewayException.class,
        () -> tirClient.getIssuer(TEST_ISSUER_DID),
        "If the server returns something unsuccessful, a BadGateWay should be thrown.");
  }

  private TrustedIssuerVO getIssuer(String did) {
    return new TrustedIssuerVO().did(did);
  }

  private void mockResponse(int statusCode) {
    mockWebServer.enqueue(new MockResponse().setResponseCode(statusCode));
  }

  private <T> void mockResponse(int statusCode, T response) throws Exception {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(statusCode)
            .setBody(objectMapper.writeValueAsString(response)));
  }
}
