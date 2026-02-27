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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import okhttp3.*;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.web.spi.exception.BadGatewayException;
import org.seamware.tmforum.productorder.model.ProductOrderCreateVO;
import org.seamware.tmforum.productorder.model.ProductOrderUpdateVO;
import org.seamware.tmforum.productorder.model.ProductOrderVO;

/** Client implementation to interact with the TMForum Product Order API */
public class ProductOrderApiClient extends ApiClient {

  private static final String PRODUCT_ORDER_PATH = "productOrder";

  private final String baseUrl;
  private final ObjectMapper objectMapper;

  public ProductOrderApiClient(
      Monitor monitor, OkHttpClient okHttpClient, String baseUrl, ObjectMapper objectMapper) {
    super(monitor, okHttpClient);
    this.baseUrl = baseUrl;
    this.objectMapper = objectMapper;
  }

  /** Returns all product orders that reference the given quote id */
  public List<ProductOrderVO> findByQuoteId(String quoteId) {
    HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
    urlBuilder.addPathSegment(PRODUCT_ORDER_PATH);
    urlBuilder.addQueryParameter("quote.id", quoteId);
    Request request = new Request.Builder().url(urlBuilder.build()).build();
    try (ResponseBody responseBody = executeRequest(request)) {
      return objectMapper.readValue(responseBody.bytes(), new TypeReference<>() {});
    } catch (IOException e) {
      monitor.warning(String.format("Was not able to get product orders for quote %s", quoteId), e);
      throw new BadGatewayException(
          String.format("Was not able to get product orders for quote %s", quoteId));
    }
  }

  /** Creates the given product order */
  public ProductOrderVO createProductOrder(ProductOrderCreateVO productOrderCreateVO) {

    HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
    urlBuilder.addPathSegment(PRODUCT_ORDER_PATH);
    RequestBody requestBody = null;
    try {
      requestBody = RequestBody.create(objectMapper.writeValueAsString(productOrderCreateVO), JSON);
    } catch (JsonProcessingException e) {
      monitor.warning("Was not able to serialize product order.", e);
      throw new BadGatewayException("Was not able to serialize product order.");
    }
    Request request = new Request.Builder().url(urlBuilder.build()).post(requestBody).build();
    try (ResponseBody responseBody = executeRequest(request)) {
      return objectMapper.readValue(responseBody.bytes(), ProductOrderVO.class);
    } catch (IOException e) {
      monitor.warning("Was not able to read product order creation response.", e);
      throw new BadGatewayException("Was not able to read product order creation response.");
    }
  }

  /** Updates the given product order */
  public ProductOrderVO updateProductOrder(String id, ProductOrderUpdateVO productOrderUpdateVO) {

    HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
    urlBuilder.addPathSegment(PRODUCT_ORDER_PATH);
    urlBuilder.addPathSegment(id);
    RequestBody requestBody = null;
    try {
      String qc = objectMapper.writeValueAsString(productOrderUpdateVO);
      requestBody = RequestBody.create(qc, JSON);
    } catch (JsonProcessingException e) {
      monitor.warning("Was not able to serialize product order update.", e);
      throw new BadGatewayException("Was not able to serialize product order update.");
    }
    Request request = new Request.Builder().url(urlBuilder.build()).patch(requestBody).build();
    try (ResponseBody responseBody = executeRequest(request)) {
      return objectMapper.readValue(responseBody.bytes(), ProductOrderVO.class);
    } catch (IOException e) {
      monitor.warning("Was not able to read product order update response.", e);
      throw new BadGatewayException("Was not able to read product order update response.");
    }
  }
}
