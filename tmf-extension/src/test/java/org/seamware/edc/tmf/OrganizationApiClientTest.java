package org.seamware.edc.tmf;

import okhttp3.mockwebserver.RecordedRequest;
import org.eclipse.edc.web.spi.exception.BadGatewayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.seamware.edc.domain.ExtendableAgreementUpdateVO;
import org.seamware.tmforum.party.model.CharacteristicVO;
import org.seamware.tmforum.party.model.OrganizationCreateVO;
import org.seamware.tmforum.party.model.OrganizationVO;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class OrganizationApiClientTest extends AbstractApiTest {

    private static final String TEST_ORGANIZATION_ID = "test-organization";
    private static final String TEST_ORGANIZATION_DID = "did:web:test-organization";

    private OrganizationApiClient organizationApiClient;

    @Override
    public void setupConcreteClient(String baseUrl) {
        organizationApiClient = new OrganizationApiClient(monitor, okHttpClient, baseUrl, objectMapper);
    }

    @Test
    public void testGetOrganization_success() throws Exception {
        OrganizationVO testOrganization = getValidOrganization();
        mockResponse(200, testOrganization);

        assertEquals(testOrganization, organizationApiClient.getOrganization(TEST_ORGANIZATION_ID), "The correct organization should be returned.");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("/organization/" + TEST_ORGANIZATION_ID, recordedRequest.getPath());
    }


    @Test
    public void testGetOrganization_invalid_content() throws Exception {
        mockResponse(200, "invalid");

        assertThrows(BadGatewayException.class, () -> organizationApiClient.getOrganization(TEST_ORGANIZATION_ID), "If the server returns something invalid, a BadGateWay should be thrown.");
    }

    @ParameterizedTest(name = "Failure code {0}")
    @ValueSource(ints = {400, 401, 403, 404, 500})
    public void testGetOrganization_bad_response(int responseCode) throws Exception {
        mockResponse(responseCode);
        assertThrows(BadGatewayException.class, () -> organizationApiClient.getOrganization(TEST_ORGANIZATION_ID), "If the server returns something unsuccessful, a BadGateWay should be thrown.");

    }

    @Test
    public void testGetByDid_success() throws Exception {
        OrganizationVO testOrganization = getValidOrganization(TEST_ORGANIZATION_DID);
        mockResponse(200, List.of(testOrganization));

        assertEquals(testOrganization, organizationApiClient.getByDid(TEST_ORGANIZATION_DID).get(), "The correct organization should be returned.");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("/organization?partyCharacteristic.name=did", recordedRequest.getPath());
    }

    @Test
    public void testGetByDid_success_multiple_orgs() throws Exception {
        OrganizationVO testOrganization = getValidOrganization(TEST_ORGANIZATION_DID);
        mockResponse(200, List.of(testOrganization, getValidOrganization("did:web:someone-else")));

        assertEquals(testOrganization, organizationApiClient.getByDid(TEST_ORGANIZATION_DID).get(), "The correct organization should be returned.");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("/organization?partyCharacteristic.name=did", recordedRequest.getPath());
    }

    @Test
    public void testGetByDid_success_no_org() throws Exception {
        mockResponse(200, List.of());

        assertTrue(organizationApiClient.getByDid(TEST_ORGANIZATION_DID).isEmpty(), "If the organization does not exist, it should not be returned.");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("/organization?partyCharacteristic.name=did", recordedRequest.getPath());
    }

    @Test
    public void testGetByDid_success_other_orgs() throws Exception {
        mockResponse(200, List.of(getValidOrganization("did:web:someone-else")));

        assertTrue(organizationApiClient.getByDid(TEST_ORGANIZATION_DID).isEmpty(), "If the organization does not exist, it should not be returned.");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("/organization?partyCharacteristic.name=did", recordedRequest.getPath());
    }

    @Test
    public void testGetByDid_invalid_content() throws Exception {
        mockResponse(200, "invalid");

        assertThrows(BadGatewayException.class, () -> organizationApiClient.getByDid(TEST_ORGANIZATION_DID), "If the server returns something invalid, a BadGateWay should be thrown.");
    }

    @ParameterizedTest(name = "Failure code {0}")
    @ValueSource(ints = {400, 401, 403, 404, 500})
    public void testGetByDid_bad_response(int responseCode) throws Exception {
        mockResponse(responseCode);
        assertThrows(BadGatewayException.class, () -> organizationApiClient.getByDid(TEST_ORGANIZATION_DID), "If the server returns something unsuccessful, a BadGateWay should be thrown.");

    }

    @Test
    public void testCreateOrganization_success() throws Exception {
        OrganizationCreateVO testCreate = getOrganizationCreate();
        OrganizationVO testOrganization = getValidOrganization();
        mockResponse(200, testOrganization);

        assertEquals(testOrganization, organizationApiClient.createOrganization(testCreate), "The correct organization should be returned.");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("/organization", recordedRequest.getPath());

        OrganizationCreateVO sentOrganization = objectMapper.readValue(recordedRequest.getBody().readByteArray(), OrganizationCreateVO.class);
        assertEquals(testCreate, sentOrganization, "The organization create should have been sent.");
    }

    @Test
    public void testCreateOrganization_invalid_content() throws Exception {
        mockResponse(200, "invalid");

        assertThrows(BadGatewayException.class, () -> organizationApiClient.createOrganization(getOrganizationCreate()), "If the server returns something invalid, a BadGateWay should be thrown.");
    }

    @ParameterizedTest(name = "Failure code {0}")
    @ValueSource(ints = {400, 401, 403, 404, 500})
    public void testCreateOrganization_bad_response(int responseCode) throws Exception {
        mockResponse(responseCode);
        assertThrows(BadGatewayException.class, () -> organizationApiClient.createOrganization(getOrganizationCreate()), "If the server returns something unsuccessful, a BadGateWay should be thrown.");

    }

    private OrganizationVO getValidOrganization() {
        return new OrganizationVO();
    }

    private OrganizationCreateVO getOrganizationCreate() {
        return new OrganizationCreateVO();
    }

    private OrganizationVO getValidOrganization(String did) {
        return new OrganizationVO()
                .partyCharacteristic(List.of(new CharacteristicVO().name("did").value(did)));
    }

}