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
import java.util.Optional;
import okhttp3.*;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.web.spi.exception.BadGatewayException;
import org.seamware.edc.domain.*;
import org.seamware.edc.store.TMFBackedContractNegotiationStore;

public class AgreementApiClient extends ApiClient {

  private static final String AGREEMENT_PATH = "agreement";

  private final String baseUrl;
  private final ObjectMapper objectMapper;

  public AgreementApiClient(
      Monitor monitor, OkHttpClient okHttpClient, String baseUrl, ObjectMapper objectMapper) {
    super(monitor, okHttpClient);
    this.baseUrl = baseUrl;
    this.objectMapper = objectMapper;
  }

  public ExtendableAgreementVO getAgreement(String agreementId) {
    HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
    urlBuilder.addPathSegment(AGREEMENT_PATH);
    urlBuilder.addPathSegment(agreementId);
    Request request = new Request.Builder().url(urlBuilder.build()).build();
    try (ResponseBody responseBody = executeRequest(request)) {
      return objectMapper.readValue(responseBody.bytes(), ExtendableAgreementVO.class);
    } catch (IOException e) {
      monitor.warning("Was not able to get agreement.", e);
      throw new BadGatewayException(
          String.format("Was not able to get agreement %s.", agreementId));
    }
  }

  public List<ExtendableAgreementVO> getAgreements(int offset, int limit) {
    HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
    urlBuilder.addPathSegment(AGREEMENT_PATH);
    urlBuilder.addQueryParameter(OFFSET_PARAM, String.valueOf(offset));
    urlBuilder.addQueryParameter(LIMIT_PARAM, String.valueOf(limit));
    Request request = new Request.Builder().url(urlBuilder.build()).build();
    try (ResponseBody responseBody = executeRequest(request)) {
      return objectMapper.readValue(responseBody.bytes(), new TypeReference<>() {});
    } catch (IOException e) {
      monitor.warning("Was not able to get agreements.", e);
      throw new BadGatewayException("Was not able to get agreements.");
    }
  }

  public ExtendableAgreementVO createAgreement(
      ExtendableAgreementCreateVO extendableAgreementCreateVO) {

    HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
    urlBuilder.addPathSegment(AGREEMENT_PATH);
    RequestBody requestBody = null;
    try {
      requestBody =
          RequestBody.create(objectMapper.writeValueAsString(extendableAgreementCreateVO), JSON);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Was not able to serialize agreement.", e);
    }
    Request request = new Request.Builder().url(urlBuilder.build()).post(requestBody).build();
    try (ResponseBody responseBody = executeRequest(request)) {
      return objectMapper.readValue(responseBody.bytes(), ExtendableAgreementVO.class);
    } catch (IOException e) {
      monitor.warning("Was not able to read agreement creation response.", e);
      throw new BadGatewayException("Was not able to read agreement creation response.");
    }
  }

  /** Updates the given quote */
  public ExtendableAgreementVO updateAgreement(
      String id, ExtendableAgreementUpdateVO agreementUpdateVO) {

    HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
    urlBuilder.addPathSegment(AGREEMENT_PATH);
    urlBuilder.addPathSegment(id);
    RequestBody requestBody = null;
    try {
      String qc = objectMapper.writeValueAsString(agreementUpdateVO);
      requestBody = RequestBody.create(qc, JSON);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Was not able to serialize agreement update.", e);
    }
    Request request = new Request.Builder().url(urlBuilder.build()).patch(requestBody).build();
    try (ResponseBody responseBody = executeRequest(request)) {
      return objectMapper.readValue(responseBody.bytes(), ExtendableAgreementVO.class);
    } catch (IOException e) {
      monitor.severe("Was not able to read agreement creation.", e);
      throw new BadGatewayException("Was not able to read agreement creation response.");
    }
  }

  public Optional<ExtendableAgreementVO> findByNegotiationId(String negotiationId) {
    HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
    urlBuilder.addPathSegment(AGREEMENT_PATH);
    urlBuilder.addQueryParameter("negotiationId", negotiationId);
    Request request = new Request.Builder().url(urlBuilder.build()).build();
    try (ResponseBody responseBody = executeRequest(request)) {
      List<ExtendableAgreementVO> extendableAgreementVOS =
          objectMapper.readValue(
              responseBody.bytes(), new TypeReference<List<ExtendableAgreementVO>>() {});
      if (extendableAgreementVOS.size() > 1) {
        monitor.warning(
            String.format(
                "Found %d agreements for negotiation %s, expected at most 1. Rejecting duplicates.",
                extendableAgreementVOS.size(), negotiationId));
        rejectDuplicateAgreements(extendableAgreementVOS);
      }
      if (extendableAgreementVOS.isEmpty()) {
        return Optional.empty();
      }
      return Optional.ofNullable(extendableAgreementVOS.getFirst());
    } catch (IOException e) {
      monitor.warning(
          String.format("Was not able to get agreements for negotiationId %s", negotiationId), e);
      throw new BadGatewayException(
          String.format("Was not able to get agreements for negotiationId %s", negotiationId));
    }
  }

  public Optional<ExtendableAgreementVO> findByContractId(String contractId) {
    HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
    urlBuilder.addPathSegment(AGREEMENT_PATH);
    urlBuilder.addQueryParameter("externalId", contractId);
    Request request = new Request.Builder().url(urlBuilder.build()).build();
    try (ResponseBody responseBody = executeRequest(request)) {
      List<ExtendableAgreementVO> extendableAgreementVOS =
          objectMapper.readValue(
              responseBody.bytes(), new TypeReference<List<ExtendableAgreementVO>>() {});
      if (extendableAgreementVOS.size() > 1) {
        throw new BadGatewayException(
            String.format(
                "There cannot be more than one agreement per contract id. Found multiple for %s.",
                contractId));
      }
      if (extendableAgreementVOS.isEmpty()) {
        return Optional.empty();
      }
      return Optional.ofNullable(extendableAgreementVOS.getFirst());
    } catch (IOException e) {
      monitor.warning(
          String.format("Was not able to get agreements for contractId %s", contractId), e);
      throw new BadGatewayException(
          String.format("Was not able to get agreements for contractId %s", contractId));
    }
  }

  /**
   * Rejects all but the first agreement in the list. This cleans up duplicates that can arise when
   * concurrent DSP message handlers both create an agreement before either sees the other's.
   */
  private void rejectDuplicateAgreements(List<ExtendableAgreementVO> agreements) {
    for (int i = 1; i < agreements.size(); i++) {
      String duplicateId = agreements.get(i).getId();
      try {
        ExtendableAgreementUpdateVO rejectUpdate = new ExtendableAgreementUpdateVO();
        rejectUpdate.setStatus(AgreementState.REJECTED.getValue());
        TMFBackedContractNegotiationStore.nullAgreementListFields(rejectUpdate);
        updateAgreement(duplicateId, rejectUpdate);
        monitor.info(String.format("Rejected duplicate agreement %s.", duplicateId));
      } catch (RuntimeException e) {
        monitor.warning(String.format("Failed to reject duplicate agreement %s.", duplicateId), e);
      }
    }
  }
}
