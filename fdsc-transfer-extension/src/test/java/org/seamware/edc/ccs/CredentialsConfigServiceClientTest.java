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

import static org.junit.jupiter.api.Assertions.*;

import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.seamware.credentials.model.ServiceVO;
import org.seamware.edc.AbstractApiClientTest;
import org.seamware.edc.HttpClientException;

public class CredentialsConfigServiceClientTest extends AbstractApiClientTest {

  private static final String TEST_SERVICE_ID = "test-service";

  private CredentialsConfigServiceClient credentialsConfigServiceClient;

  @Override
  public void setupConcreteClient(String baseUrl) {
    credentialsConfigServiceClient =
        new CredentialsConfigServiceClient(monitor, okHttpClient, baseUrl, objectMapper);
  }

  @Test
  public void testCreateService_success() throws Exception {

    ServiceVO testService = getService();
    mockResponse(200);

    assertDoesNotThrow(
        () -> credentialsConfigServiceClient.createService(testService),
        "The service should be created.");

    RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertEquals("/service", recordedRequest.getPath());
    ServiceVO sentService =
        objectMapper.readValue(recordedRequest.getBody().readByteArray(), ServiceVO.class);
    assertEquals(testService, sentService, "The service create should have been sent.");
  }

  @ParameterizedTest(name = "Failure code {0}")
  @ValueSource(ints = {400, 401, 403, 404, 500})
  public void testCreateService_bad_response(int responseCode) throws Exception {
    mockResponse(responseCode);
    HttpClientException exception =
        assertThrows(
            HttpClientException.class,
            () -> credentialsConfigServiceClient.createService(getService()),
            "If the server returns something unsuccessful, a HttpClientException should be thrown.");

    assertEquals(
        responseCode, exception.getStatusCode().get(), "The correct status should be propagated.");
  }

  @Test
  public void testDeleteService_success() throws Exception {
    mockResponse(200);
    credentialsConfigServiceClient.deleteService(TEST_SERVICE_ID);

    RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertEquals(
        "/service/" + TEST_SERVICE_ID,
        recordedRequest.getPath(),
        "The correct service should have been requested for deletion.");
  }

  @ParameterizedTest(name = "Failure code {0}")
  @ValueSource(ints = {400, 401, 403, 404, 500})
  public void testDeleteService_bad_response(int responseCode) throws Exception {
    mockResponse(responseCode);
    HttpClientException exception =
        assertThrows(
            HttpClientException.class,
            () -> credentialsConfigServiceClient.deleteService(TEST_SERVICE_ID),
            "If the server returns something unsuccessful, a HttpClientException should be thrown.");

    assertEquals(
        responseCode, exception.getStatusCode().get(), "The correct status should be propagated.");
  }

  private ServiceVO getService() {
    return new ServiceVO().id(TEST_SERVICE_ID);
  }
}
