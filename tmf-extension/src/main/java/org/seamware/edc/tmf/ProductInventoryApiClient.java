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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Optional;
import okhttp3.*;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.web.spi.exception.BadGatewayException;
import org.seamware.edc.domain.ExtendableProduct;
import org.seamware.edc.domain.ExtendableProductCreate;

/** Client implementation to interact with the TMForum Product Inventory API */
public class ProductInventoryApiClient extends ApiClient {

  private static final String PRODUCT_PATH = "product";

  private final String baseUrl;
  private final ObjectMapper objectMapper;

  public ProductInventoryApiClient(
      Monitor monitor, OkHttpClient okHttpClient, String baseUrl, ObjectMapper objectMapper) {
    super(monitor, okHttpClient);
    this.baseUrl = baseUrl;
    this.objectMapper = objectMapper;
  }

  /** Creates the given product */
  public ExtendableProduct createProduct(ExtendableProductCreate productCreateVO) {

    HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
    urlBuilder.addPathSegment(PRODUCT_PATH);
    RequestBody requestBody = null;
    try {
      requestBody = RequestBody.create(objectMapper.writeValueAsString(productCreateVO), JSON);
    } catch (JsonProcessingException e) {
      monitor.warning("Was not able to serialize product.", e);
      throw new BadGatewayException("Was not able to serialize product.");
    }
    Request request = new Request.Builder().url(urlBuilder.build()).post(requestBody).build();
    try (ResponseBody responseBody = executeRequest(request)) {
      return objectMapper.readValue(responseBody.bytes(), ExtendableProduct.class);
    } catch (IOException e) {
      monitor.warning("Was not able to read product creation response.", e);
      throw new BadGatewayException("Was not able to read product creation response.");
    }
  }

  /** Returns the product by its id */
  public Optional<ExtendableProduct> getProductById(String id) {
    HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
    urlBuilder.addPathSegment(PRODUCT_PATH);
    urlBuilder.addPathSegment(id);
    Request request = new Request.Builder().url(urlBuilder.build()).build();
    try (ResponseBody responseBody = executeRequest(request)) {
      return Optional.ofNullable(
          objectMapper.readValue(responseBody.bytes(), ExtendableProduct.class));
    } catch (IOException e) {
      monitor.warning(String.format("Was not able to get product %s.", id), e);
      throw new BadGatewayException(String.format("Was not able to get product %s.", id));
    }
  }
}
