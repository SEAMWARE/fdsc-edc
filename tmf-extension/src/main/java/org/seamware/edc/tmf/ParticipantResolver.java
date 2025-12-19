package org.seamware.edc.tmf;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import org.eclipse.edc.spi.monitor.Monitor;
import org.seamware.tmforum.party.model.CharacteristicVO;
import org.seamware.tmforum.party.model.OrganizationVO;

import java.util.List;
import java.util.Optional;

import static org.seamware.edc.tmf.OrganizationApiClient.PARTY_CHARACTERISTIC_DID;
import static org.seamware.edc.tmf.OrganizationApiClient.PARTY_CHARACTERISTIC_TCK_ADDRESS;

public class ParticipantResolver {

    public static final String PROVIDER_ROLE = "provider";
    public static final String CONSUMER_ROLE = "consumer";

    private final OrganizationApiClient organizationApi;

    public ParticipantResolver(Monitor monitor, OkHttpClient okHttpClient, String organizationApiBaseUrl, ObjectMapper objectMapper) {
        this.organizationApi = new OrganizationApiClient(monitor, okHttpClient, organizationApiBaseUrl, objectMapper);
    }

    public OrganizationVO getOrganization(String tmfId) {
        return organizationApi.getOrganization(tmfId);
    }

    public String getDid(String tmfId) {
        return getDidFromOrganization(organizationApi.getOrganization(tmfId));
    }

    public String getTmfId(String did) {
        return organizationApi.getByDid(did)
                .getId();
    }

    public static String getDidFromOrganization(OrganizationVO organizationVO) {
        return Optional.ofNullable(organizationVO
                        .getPartyCharacteristic())
                .orElse(List.of())
                .stream()
                .filter(cvo -> cvo.getName().equals(PARTY_CHARACTERISTIC_DID))
                .map(CharacteristicVO::getValue)
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException(String.format("The organization %s does not contain a did.", organizationVO.getId())));
    }

    public static String getAddressFromOrganization(OrganizationVO organizationVO) {
        return Optional.ofNullable(organizationVO
                        .getPartyCharacteristic())
                .orElse(List.of())
                .stream()
                .filter(cvo -> cvo.getName().equals(PARTY_CHARACTERISTIC_TCK_ADDRESS))
                .map(CharacteristicVO::getValue)
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException(String.format("The organization %s does not contain a tckAddress.", organizationVO.getId())));
    }
}
