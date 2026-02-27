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
import org.seamware.tmforum.party.model.OrganizationCreateVO;
import org.seamware.tmforum.party.model.OrganizationVO;

public class OrganizationApiClient extends ApiClient {

  public static final String PARTY_CHARACTERISTIC_DID = "did";
  public static final String PARTY_CHARACTERISTIC_TCK_ADDRESS = "tckAddress";

  private static final String PARTY_CHARACTERISTIC_NAME = "partyCharacteristic.name";
  private static final String ORGANIZATION_PATH = "organization";

  private final String baseUrl;
  private final ObjectMapper objectMapper;

  public OrganizationApiClient(
      Monitor monitor, OkHttpClient okHttpClient, String baseUrl, ObjectMapper objectMapper) {
    super(monitor, okHttpClient);
    this.baseUrl = baseUrl;
    this.objectMapper = objectMapper;
  }

  public OrganizationVO getOrganization(String tmfId) {
    HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
    urlBuilder.addPathSegment(ORGANIZATION_PATH);
    urlBuilder.addPathSegment(tmfId);
    Request request = new Request.Builder().url(urlBuilder.build()).build();
    try (ResponseBody responseBody = executeRequest(request)) {
      return objectMapper.readValue(responseBody.bytes(), OrganizationVO.class);
    } catch (IOException e) {
      monitor.warning(String.format("Was not able to get organization for %s", tmfId), e);
      throw new BadGatewayException(
          String.format("Was not able to get organization for %s", tmfId));
    }
  }

  public Optional<OrganizationVO> getByDid(String did) {
    HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
    urlBuilder.addPathSegment(ORGANIZATION_PATH);
    urlBuilder.addQueryParameter(PARTY_CHARACTERISTIC_NAME, PARTY_CHARACTERISTIC_DID);
    Request request = new Request.Builder().url(urlBuilder.build()).build();
    try (ResponseBody responseBody = executeRequest(request)) {
      return objectMapper
          .readValue(responseBody.bytes(), new TypeReference<List<OrganizationVO>>() {})
          .stream()
          .filter(
              organizationVO ->
                  Optional.ofNullable(organizationVO.getPartyCharacteristic())
                      .orElse(List.of())
                      .stream()
                      .filter(
                          characteristicVO ->
                              characteristicVO.getName().equals(PARTY_CHARACTERISTIC_DID))
                      .anyMatch(characteristicVO -> characteristicVO.getValue().equals(did)))
          .findAny();
    } catch (IOException e) {
      monitor.warning("Was not able to get the organization by did.", e);
      throw new BadGatewayException("Was not able to get the organization by did.");
    }
  }

  /** Creates the given organization */
  public OrganizationVO createOrganization(OrganizationCreateVO organizationCreateVO) {

    HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
    urlBuilder.addPathSegment(ORGANIZATION_PATH);
    RequestBody requestBody = null;
    try {
      requestBody = RequestBody.create(objectMapper.writeValueAsString(organizationCreateVO), JSON);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Was not able to serialize organization.", e);
    }
    Request request = new Request.Builder().url(urlBuilder.build()).post(requestBody).build();
    try (ResponseBody responseBody = executeRequest(request)) {
      return objectMapper.readValue(responseBody.bytes(), OrganizationVO.class);
    } catch (IOException e) {
      monitor.warning("Was not able to read organization creation response.", e);
      throw new BadGatewayException("Was not able to read organization creation response.");
    }
  }
}
