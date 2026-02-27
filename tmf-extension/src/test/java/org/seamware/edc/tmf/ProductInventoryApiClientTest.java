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

import java.net.URI;
import okhttp3.mockwebserver.RecordedRequest;
import org.eclipse.edc.web.spi.exception.BadGatewayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.seamware.edc.domain.ExtendableProduct;
import org.seamware.edc.domain.ExtendableProductCreate;

public class ProductInventoryApiClientTest extends AbstractApiTest {

  private static final String TEST_PRODUCT_ID = "test-product";

  private ProductInventoryApiClient productInventoryApiClient;

  @Override
  public void setupConcreteClient(String baseUrl) {
    productInventoryApiClient =
        new ProductInventoryApiClient(monitor, okHttpClient, baseUrl, objectMapper);
  }

  @Test
  public void testGetProductById_success() throws Exception {
    ExtendableProduct testProduct = getProduct();
    mockResponse(200, testProduct);

    assertEquals(
        testProduct,
        productInventoryApiClient.getProductById(TEST_PRODUCT_ID).get(),
        "The correct product should be returned.");

    RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertEquals("/product/" + TEST_PRODUCT_ID, recordedRequest.getPath());
  }

  @Test
  public void testGetProductById_invalid_content() throws Exception {
    mockResponse(200, "invalid");

    assertThrows(
        BadGatewayException.class,
        () -> productInventoryApiClient.getProductById(TEST_PRODUCT_ID),
        "If the server returns something invalid, a BadGateway should be returned.");
  }

  @ParameterizedTest(name = "Failure code {0}")
  @ValueSource(ints = {400, 401, 403, 404, 500})
  public void testGetProductById_bad_response(int responseCode) throws Exception {
    mockResponse(responseCode);
    assertThrows(
        BadGatewayException.class,
        () -> productInventoryApiClient.getProductById(TEST_PRODUCT_ID),
        "If the server returns something invalid, a BadGateway should be returned.");
  }

  @Test
  public void testCreateProduct_success() throws Exception {
    ExtendableProductCreate testCreate = getProductCreate();
    ExtendableProduct testProduct = getProduct();
    mockResponse(200, testProduct);

    assertEquals(
        testProduct,
        productInventoryApiClient.createProduct(testCreate),
        "The correct product should be returned.");

    RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertEquals("/product", recordedRequest.getPath());
    ExtendableProductCreate sentProduct =
        objectMapper.readValue(
            recordedRequest.getBody().readByteArray(), ExtendableProductCreate.class);
    assertEquals(testCreate, sentProduct, "The product create should have been sent.");
  }

  @Test
  public void testCreateAgreement_invalid_content() throws Exception {

    mockResponse(200, "invalid");

    assertThrows(
        BadGatewayException.class,
        () -> productInventoryApiClient.createProduct(getProductCreate()),
        "If the server returns something invalid, a BadGateWay should be thrown.");
  }

  @ParameterizedTest(name = "Failure code {0}")
  @ValueSource(ints = {400, 401, 403, 404, 500})
  public void testCreateAgreement_bad_response(int responseCode) throws Exception {
    mockResponse(responseCode);
    assertThrows(
        BadGatewayException.class,
        () -> productInventoryApiClient.createProduct(getProductCreate()),
        "If the server returns something unsuccessful, a BadGateWay should be thrown.");
  }

  private ExtendableProductCreate getProductCreate() {

    ExtendableProductCreate extendableProductCreate = new ExtendableProductCreate();
    extendableProductCreate.setAtSchemaLocation(URI.create("http://base.uri/external-id.json"));
    return extendableProductCreate;
  }

  private ExtendableProduct getProduct() {
    ExtendableProduct extendableProduct = new ExtendableProduct();
    extendableProduct.setAtSchemaLocation(URI.create("http://base.uri/external-id.json"));
    return extendableProduct;
  }
}
