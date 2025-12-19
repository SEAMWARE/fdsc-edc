package org.seamware.edc.tmf;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.eclipse.edc.spi.monitor.Monitor;
import org.seamware.tmforum.party.model.CharacteristicVO;
import org.seamware.tmforum.party.model.OrganizationVO;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class OrganizationApiClient extends ApiClient {

    public static final String PARTY_CHARACTERISTIC_DID = "did";
    public static final String PARTY_CHARACTERISTIC_TCK_ADDRESS = "tckAddress";

    private static final String PARTY_CHARACTERISTIC_NAME = "partyCharacteristic.name";
    private static final String ORGANIZATION_PATH = "organization";


    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public OrganizationApiClient(Monitor monitor, OkHttpClient okHttpClient, String baseUrl, ObjectMapper objectMapper) {
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
            throw new IllegalArgumentException(String.format("Was not able to get organization for %s", tmfId), e);
        }
    }

    public OrganizationVO getByDid(String did) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        urlBuilder.addPathSegment(ORGANIZATION_PATH);
        urlBuilder.addQueryParameter(PARTY_CHARACTERISTIC_NAME, PARTY_CHARACTERISTIC_DID);
        Request request = new Request.Builder().url(urlBuilder.build()).build();
        try (ResponseBody responseBody = executeRequest(request)) {
            return objectMapper.readValue(responseBody.bytes(), new TypeReference<List<OrganizationVO>>() {
                    })
                    .stream()
                    .filter(organizationVO ->
                            Optional.ofNullable(organizationVO.getPartyCharacteristic())
                                    .orElse(List.of())
                                    .stream()
                                    .filter(characteristicVO -> characteristicVO.getName().equals(PARTY_CHARACTERISTIC_DID))
                                    .anyMatch(characteristicVO -> characteristicVO.getValue().equals(did))
                    )
                    .findAny()
                    .orElseThrow(() -> new IllegalArgumentException(String.format("Was not able to get Organization for %s", did)));
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Was not able to get Organization for %s", did), e);
        }
    }


}
