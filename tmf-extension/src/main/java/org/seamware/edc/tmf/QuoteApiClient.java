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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import okhttp3.*;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.web.spi.exception.BadGatewayException;
import org.seamware.edc.domain.ExtendableQuoteUpdateVO;
import org.seamware.edc.domain.ExtendableQuoteVO;
import org.seamware.tmforum.quote.model.QuoteCreateVO;

/** Client implementation to interact with the TMForum Usage API */
public class QuoteApiClient extends ApiClient {

  private static final String QUOTE_PATH = "quote";
  public static final String CONTROL_PLANE_ID_PARAM = "contractNegotiation.controlplane";

  private final String controlPlaneId;
  private final String baseUrl;
  private final ObjectMapper objectMapper;

  public QuoteApiClient(
      Monitor monitor,
      OkHttpClient okHttpClient,
      String controlPlaneId,
      String baseUrl,
      ObjectMapper objectMapper) {
    super(monitor, okHttpClient);
    this.controlPlaneId = controlPlaneId;
    this.baseUrl = baseUrl;
    this.objectMapper = objectMapper.copy().setSerializationInclusion(JsonInclude.Include.NON_NULL);
  }

  /** Returns a list of quotes, supporting pagination */
  public List<ExtendableQuoteVO> getQuotes(int offset, int limit) {
    HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
    urlBuilder.addPathSegment(QUOTE_PATH);
    urlBuilder.addQueryParameter(OFFSET_PARAM, String.valueOf(offset));
    urlBuilder.addQueryParameter(LIMIT_PARAM, String.valueOf(limit));
    // only get quotes that we are responsible for
    urlBuilder.addQueryParameter(CONTROL_PLANE_ID_PARAM, controlPlaneId);
    Request request = new Request.Builder().url(urlBuilder.build()).build();
    try (ResponseBody responseBody = executeRequest(request)) {
      return objectMapper.readValue(responseBody.bytes(), new TypeReference<>() {});
    } catch (Exception e) {
      monitor.warning("Was not able to get quotes.", e);
      throw new BadGatewayException("Was not able to get quotes.");
    }
  }

  /** Updates the given quote */
  public ExtendableQuoteVO updateQuote(String id, ExtendableQuoteUpdateVO quoteUpdateVO) {

    HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
    urlBuilder.addPathSegment(QUOTE_PATH);
    urlBuilder.addPathSegment(id);
    RequestBody requestBody = null;
    try {
      String qc = objectMapper.writeValueAsString(quoteUpdateVO);
      requestBody = RequestBody.create(qc, JSON);
    } catch (JsonProcessingException e) {
      monitor.warning("Was not able to serialize quote update.", e);
      throw new BadGatewayException("Was not able to serialize quote update.");
    }
    Request request = new Request.Builder().url(urlBuilder.build()).patch(requestBody).build();
    try (ResponseBody responseBody = executeRequest(request)) {
      return objectMapper.readValue(responseBody.bytes(), ExtendableQuoteVO.class);
    } catch (IOException e) {
      monitor.warning("Was not able to read quote creation.", e);
      throw new BadGatewayException("Was not able to read quote creation response.");
    }
  }

  /** Creates the given quote */
  public ExtendableQuoteVO createQuote(QuoteCreateVO quoteCreateVO) {

    HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
    urlBuilder.addPathSegment(QUOTE_PATH);
    RequestBody requestBody = null;
    try {
      requestBody = RequestBody.create(objectMapper.writeValueAsString(quoteCreateVO), JSON);
    } catch (JsonProcessingException e) {
      monitor.warning("Was not able to serialize quote.", e);
      throw new BadGatewayException("Was not able to serialize quote.");
    }
    Request request = new Request.Builder().url(urlBuilder.build()).post(requestBody).build();
    try (ResponseBody responseBody = executeRequest(request)) {
      return objectMapper.readValue(responseBody.bytes(), ExtendableQuoteVO.class);
    } catch (IOException e) {
      monitor.warning("Was not able to read quote creation response.", e);
      throw new BadGatewayException("Was not able to read quote creation response.");
    }
  }

  /** Returns all quotes corresponding to the given negotiationId */
  public List<ExtendableQuoteVO> findByNegotiationId(String negotiationId) {
    HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
    urlBuilder.addPathSegment(QUOTE_PATH);
    urlBuilder.addQueryParameter("externalId", negotiationId);
    Request request = new Request.Builder().url(urlBuilder.build()).build();
    try (ResponseBody responseBody = executeRequest(request)) {
      return objectMapper.readValue(responseBody.bytes(), new TypeReference<>() {});
    } catch (IOException e) {
      monitor.warning(
          String.format("Was not able to get quotes for negotiation %s", negotiationId), e);
      throw new BadGatewayException(
          String.format("Was not able to get quotes for negotiation %s", negotiationId));
    }
  }
}
