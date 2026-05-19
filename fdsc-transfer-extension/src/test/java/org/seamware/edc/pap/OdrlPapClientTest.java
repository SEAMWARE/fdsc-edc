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

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.RecordedRequest;
import org.eclipse.edc.web.spi.exception.BadGatewayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.seamware.edc.AbstractApiClientTest;
import org.seamware.edc.HttpClientException;
import org.seamware.pap.model.MappingVO;
import org.seamware.pap.model.MappingsVO;
import org.seamware.pap.model.PolicyPathVO;
import org.seamware.pap.model.ServiceCreateVO;
import org.seamware.pap.model.TestRequestVO;
import org.seamware.pap.model.ValidationRequestVO;
import org.seamware.pap.model.ValidationResponseVO;

public class OdrlPapClientTest extends AbstractApiClientTest {

  private static final String TEST_SERVICE_ID = "service-id";
  private static final String TEST_POLICY_PATH = "policy-path";

  private OdrlPapClient odrlPapClient;

  @Override
  public void setupConcreteClient(String baseUrl) {
    odrlPapClient = new OdrlPapClient(monitor, okHttpClient, baseUrl, objectMapper);
  }

  @Test
  public void testCreatePolicy_success() throws Exception {
    Map<String, Object> testPolicy = getPolicy();
    mockResponse(204);

    assertDoesNotThrow(
        () -> odrlPapClient.createPolicy(TEST_SERVICE_ID, testPolicy),
        "The correct service should be returned.");

    RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertEquals("/service/" + TEST_SERVICE_ID + "/policy", recordedRequest.getPath());
    Map<String, Object> sentPolicy =
        objectMapper.readValue(
            recordedRequest.getBody().readByteArray(), new TypeReference<Map<String, Object>>() {});
    assertEquals(testPolicy, sentPolicy, "The policy create should have been sent.");
  }

  @ParameterizedTest(name = "Failure code {0}")
  @ValueSource(ints = {400, 401, 403, 404, 500})
  public void testCreatePolicy_bad_response(int responseCode) throws Exception {
    mockResponse(responseCode);
    assertThrows(
        HttpClientException.class,
        () -> odrlPapClient.createPolicy(TEST_SERVICE_ID, getPolicy()),
        "If the server returns something unsuccessful, a BadGateWay should be thrown.");
  }

  @Test
  public void testCreateService_success() throws Exception {
    ServiceCreateVO testCreate = getServiceCreate();
    PolicyPathVO testPath = getPolicyPath();
    mockResponse(200, testPath);

    assertEquals(
        testPath,
        odrlPapClient.createService(testCreate),
        "The correct service should be returned.");

    RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertEquals("/service", recordedRequest.getPath());
    ServiceCreateVO sendAgreement =
        objectMapper.readValue(recordedRequest.getBody().readByteArray(), ServiceCreateVO.class);
    assertEquals(testCreate, sendAgreement, "The service create should have been sent.");
  }

  @Test
  public void testCreateService_invalid_content() throws Exception {

    mockResponse(200, "invalid");

    assertThrows(
        BadGatewayException.class,
        () -> odrlPapClient.createService(getServiceCreate()),
        "If the server returns something invalid, a BadGateWay should be thrown.");
  }

  @ParameterizedTest(name = "Failure code {0}")
  @ValueSource(ints = {400, 401, 403, 404, 500})
  public void testCreateService_bad_response(int responseCode) throws Exception {
    mockResponse(responseCode);
    assertThrows(
        BadGatewayException.class,
        () -> odrlPapClient.createService(getServiceCreate()),
        "If the server returns something unsuccessful, a BadGateWay should be thrown.");
  }

  @Test
  public void testDeleteService_success() throws Exception {
    mockResponse(200);
    odrlPapClient.deleteService(TEST_SERVICE_ID);

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
            () -> odrlPapClient.deleteService(TEST_SERVICE_ID),
            "If the server returns something unsuccessful, a HttpClientException should be thrown.");

    assertEquals(
        responseCode, exception.getStatusCode().get(), "The correct status should be propagated.");
  }

  @Test
  public void testValidate_allow() throws Exception {
    ValidationResponseVO expectedResponse =
        new ValidationResponseVO().allow(true).explanation(List.of());
    mockResponse(200, expectedResponse);
    ValidationRequestVO request = getValidationRequest();

    ValidationResponseVO result = odrlPapClient.validate(request);

    assertEquals(expectedResponse, result, "The validation response should indicate allow=true.");
    RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertEquals("/validate", recordedRequest.getPath());
    ValidationRequestVO sentRequest =
        objectMapper.readValue(
            recordedRequest.getBody().readByteArray(), ValidationRequestVO.class);
    assertEquals(request, sentRequest, "The validation request should have been sent correctly.");
  }

  @Test
  public void testValidate_deny() throws Exception {
    ValidationResponseVO expectedResponse =
        new ValidationResponseVO()
            .allow(false)
            .explanation(List.of("Policy constraint not satisfied", "Access denied"));
    mockResponse(200, expectedResponse);

    ValidationResponseVO result = odrlPapClient.validate(getValidationRequest());

    assertEquals(expectedResponse, result, "The validation response should indicate allow=false.");
    assertFalse(result.getAllow(), "The allow field should be false.");
    assertEquals(
        2, result.getExplanation().size(), "The explanation should contain two denial reasons.");
  }

  @Test
  public void testValidate_invalidContent() throws Exception {
    mockResponse(200, "invalid");

    assertThrows(
        BadGatewayException.class,
        () -> odrlPapClient.validate(getValidationRequest()),
        "If the server returns invalid content, a BadGatewayException should be thrown.");
  }

  @ParameterizedTest(name = "Failure code {0}")
  @ValueSource(ints = {400, 401, 403, 404, 500})
  public void testValidate_badResponse(int responseCode) throws Exception {
    mockResponse(responseCode);
    assertThrows(
        HttpClientException.class,
        () -> odrlPapClient.validate(getValidationRequest()),
        "If the server returns something unsuccessful, an HttpClientException should be thrown.");
  }

  @Test
  public void testGetMappings_success() throws Exception {
    MappingsVO expectedMappings =
        new MappingsVO()
            .actions(List.of(new MappingVO().name("use").description("Use the asset")))
            .operators(List.of(new MappingVO().name("eq").description("Equals")));
    mockResponse(200, expectedMappings);

    MappingsVO result = odrlPapClient.getMappings();

    assertEquals(expectedMappings, result, "The correct mappings should be returned.");
    RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertEquals("/mappings", recordedRequest.getPath());
    assertEquals("GET", recordedRequest.getMethod());
  }

  @Test
  public void testGetMappings_invalidContent() throws Exception {
    mockResponse(200, "invalid");

    assertThrows(
        BadGatewayException.class,
        () -> odrlPapClient.getMappings(),
        "If the server returns invalid content, a BadGatewayException should be thrown.");
  }

  @ParameterizedTest(name = "Failure code {0}")
  @ValueSource(ints = {400, 401, 403, 404, 500})
  public void testGetMappings_badResponse(int responseCode) throws Exception {
    mockResponse(responseCode);
    assertThrows(
        HttpClientException.class,
        () -> odrlPapClient.getMappings(),
        "If the server returns something unsuccessful, an HttpClientException should be thrown.");
  }

  private ServiceCreateVO getServiceCreate() {
    return new ServiceCreateVO();
  }

  private Map<String, Object> getPolicy() {
    return Map.of("test", "test");
  }

  private PolicyPathVO getPolicyPath() {
    return new PolicyPathVO().policyPath(TEST_POLICY_PATH);
  }

  private ValidationRequestVO getValidationRequest() {
    TestRequestVO testRequest =
        new TestRequestVO()
            .method(TestRequestVO.MethodEnum.GET)
            .host("localhost")
            .path("/api/catalog")
            .protocol(TestRequestVO.ProtocolEnum.HTTPS);
    return new ValidationRequestVO()
        .policy(Map.of("@type", "odrl:Set", "@id", "policy-1"))
        .testRequest(testRequest);
  }
}
