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

import static org.seamware.edc.tmf.OrganizationApiClient.PARTY_CHARACTERISTIC_DID;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import okhttp3.OkHttpClient;
import org.eclipse.edc.spi.monitor.Monitor;
import org.seamware.tmforum.party.model.CharacteristicVO;
import org.seamware.tmforum.party.model.OrganizationCreateVO;
import org.seamware.tmforum.party.model.OrganizationVO;

public class ParticipantResolver {

  public static final String PROVIDER_ROLE = "Provider";
  public static final String CONSUMER_ROLE = "Consumer";

  private final OrganizationApiClient organizationApi;
  private final Monitor monitor;

  public ParticipantResolver(
      Monitor monitor,
      OkHttpClient okHttpClient,
      String organizationApiBaseUrl,
      ObjectMapper objectMapper) {
    this(
        monitor,
        new OrganizationApiClient(monitor, okHttpClient, organizationApiBaseUrl, objectMapper));
  }

  public ParticipantResolver(Monitor monitor, OrganizationApiClient organizationApiClient) {
    this.monitor = monitor;
    this.organizationApi = organizationApiClient;
  }

  public Optional<OrganizationVO> getOrganization(String tmfId) {
    try {
      return Optional.ofNullable(organizationApi.getOrganization(tmfId));
    } catch (RuntimeException iae) {
      monitor.warning("Was not able to get organization.", iae);
      return Optional.empty();
    }
  }

  public String getTmfId(String did) {
    return organizationApi.getByDid(did).orElse(createOrganization(did)).getId();
  }

  private OrganizationVO createOrganization(String did) {
    CharacteristicVO didCharacteristic =
        new CharacteristicVO().name(PARTY_CHARACTERISTIC_DID).value(did);
    OrganizationCreateVO organizationCreateVO =
        new OrganizationCreateVO().partyCharacteristic(List.of(didCharacteristic));
    return organizationApi.createOrganization(organizationCreateVO);
  }

  public static Optional<String> getDidFromOrganization(OrganizationVO organizationVO) {
    return Optional.ofNullable(organizationVO.getPartyCharacteristic()).orElse(List.of()).stream()
        .filter(cvo -> cvo.getName().equals(PARTY_CHARACTERISTIC_DID))
        .map(CharacteristicVO::getValue)
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .findAny();
  }
}
