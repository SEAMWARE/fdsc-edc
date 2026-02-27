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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;
import okhttp3.mockwebserver.RecordedRequest;
import org.eclipse.edc.web.spi.exception.BadGatewayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.seamware.tmforum.productorder.model.ProductOrderCreateVO;
import org.seamware.tmforum.productorder.model.ProductOrderUpdateVO;
import org.seamware.tmforum.productorder.model.ProductOrderVO;

public class ProductOrderApiClientTest extends AbstractApiTest {

  private static final String TEST_ORDER_ID = "test-order";
  private static final String TEST_QUOTE_ID = "test-quote";

  private ProductOrderApiClient productOrderApiClient;

  @Override
  public void setupConcreteClient(String baseUrl) {
    productOrderApiClient = new ProductOrderApiClient(monitor, okHttpClient, baseUrl, objectMapper);
  }

  @ParameterizedTest
  @MethodSource("getValidOrders")
  public void testFindByQuoteId_success(List<ProductOrderVO> orders) throws Exception {
    ProductOrderVO testOrder = getValidProductOrder();
    mockResponse(200, orders);

    assertEquals(
        new HashSet<>(orders),
        new HashSet<>(productOrderApiClient.findByQuoteId(TEST_QUOTE_ID)),
        "All quotes should have been returned");

    RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertEquals("/productOrder?quote.id=" + TEST_QUOTE_ID, recordedRequest.getPath());
  }

  private static Stream<Arguments> getValidOrders() {
    return Stream.of(
        Arguments.of(getOrders(3)), Arguments.of(getOrders(1)), Arguments.of(List.of()));
  }

  @Test
  public void testFindByQuoteId_invalid_content() throws Exception {

    mockResponse(200, "invalid");
    assertThrows(
        BadGatewayException.class,
        () -> productOrderApiClient.findByQuoteId(TEST_QUOTE_ID),
        "If the server returns something invalid, a BadGateWay should be thrown.");
  }

  @ParameterizedTest(name = "Failure code {0}")
  @ValueSource(ints = {400, 401, 403, 404, 500})
  public void testGFindByQuoteId_bad_response(int responseCode) throws Exception {
    mockResponse(responseCode);
    assertThrows(
        BadGatewayException.class,
        () -> productOrderApiClient.findByQuoteId(TEST_QUOTE_ID),
        "If the server returns something unsuccessful, a BadGateWay should be thrown.");
  }

  @Test
  public void testCreateProductOrder_success() throws Exception {
    ProductOrderCreateVO testCreate = getProductOrderCreate();
    ProductOrderVO testOrder = getValidProductOrder();
    mockResponse(200, testOrder);

    assertEquals(
        testOrder,
        productOrderApiClient.createProductOrder(testCreate),
        "The correct order should be returned.");

    RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertEquals("/productOrder", recordedRequest.getPath());
    ProductOrderCreateVO sentOrder =
        objectMapper.readValue(
            recordedRequest.getBody().readByteArray(), ProductOrderCreateVO.class);
    assertEquals(testCreate, sentOrder, "The order create should have been sent.");
  }

  @Test
  public void testCreateProductOrder_invalid_content() throws Exception {

    mockResponse(200, "invalid");
    assertThrows(
        BadGatewayException.class,
        () -> productOrderApiClient.createProductOrder(getProductOrderCreate()),
        "If the server returns something invalid, a BadGateWay should be thrown.");
  }

  @ParameterizedTest(name = "Failure code {0}")
  @ValueSource(ints = {400, 401, 403, 404, 500})
  public void testCreateProductOrder_bad_response(int responseCode) throws Exception {
    mockResponse(responseCode);
    assertThrows(
        BadGatewayException.class,
        () -> productOrderApiClient.createProductOrder(getProductOrderCreate()),
        "If the server returns something unsuccessful, a BadGateWay should be thrown.");
  }

  @Test
  public void testUpdateProductOrder_success() throws Exception {
    ProductOrderUpdateVO testUpdate = getProductOrderUpdate();
    ProductOrderVO testOrder = getValidProductOrder();
    mockResponse(200, testOrder);

    assertEquals(
        testOrder,
        productOrderApiClient.updateProductOrder(TEST_ORDER_ID, testUpdate),
        "The correct order should be returned.");

    RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertEquals("/productOrder/" + TEST_ORDER_ID, recordedRequest.getPath());
    ProductOrderUpdateVO sentOrder =
        objectMapper.readValue(
            recordedRequest.getBody().readByteArray(), ProductOrderUpdateVO.class);
    assertEquals(testUpdate, sentOrder, "The order create should have been sent.");
  }

  @Test
  public void testUpdateProductOrder_invalid_content() throws Exception {

    mockResponse(200, "invalid");

    assertThrows(
        BadGatewayException.class,
        () -> productOrderApiClient.updateProductOrder(TEST_ORDER_ID, getProductOrderUpdate()),
        "If the server returns something invalid, a BadGateWay should be thrown.");
  }

  @ParameterizedTest(name = "Failure code {0}")
  @ValueSource(ints = {400, 401, 403, 404, 500})
  public void testUpdateProductOrder_bad_response(int responseCode) throws Exception {
    mockResponse(responseCode);
    assertThrows(
        BadGatewayException.class,
        () -> productOrderApiClient.updateProductOrder(TEST_ORDER_ID, getProductOrderUpdate()),
        "If the server returns something unsuccessful, a BadGateWay should be thrown.");
  }

  private static List<ProductOrderVO> getOrders(int num) {
    List<ProductOrderVO> productOrderVOS = new ArrayList<>();
    for (int i = 0; i < num; i++) {
      productOrderVOS.add(getValidProductOrder());
    }
    return productOrderVOS;
  }

  private static ProductOrderVO getValidProductOrder() {
    return new ProductOrderVO();
  }

  private ProductOrderCreateVO getProductOrderCreate() {
    return new ProductOrderCreateVO();
  }

  private ProductOrderUpdateVO getProductOrderUpdate() {
    return new ProductOrderUpdateVO();
  }
}
