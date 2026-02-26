package org.seamware.edc.tmf;

import okhttp3.mockwebserver.RecordedRequest;
import org.eclipse.edc.web.spi.exception.BadGatewayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.seamware.edc.domain.ExtendableAgreementCreateVO;
import org.seamware.edc.domain.ExtendableAgreementUpdateVO;
import org.seamware.edc.domain.ExtendableAgreementVO;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


public class AgreementApiClientTest extends AbstractApiTest {

    private static final String TEST_AGREEMENT_ID = "test-agreement";
    private static final String TEST_NEGOTIATION_ID = "test-negotiation";
    private static final String TEST_CONTRACT_ID = "test-contract";

    private AgreementApiClient agreementApiClient;


    @Override
    public void setupConcreteClient(String baseUrl) {
        agreementApiClient = new AgreementApiClient(monitor, okHttpClient, baseUrl, objectMapper);
    }

    @Test
    public void testGetAgreement_success() throws Exception {
        ExtendableAgreementVO testAgreement = getValidAgreementVO();
        mockResponse(200, testAgreement);

        assertEquals(testAgreement, agreementApiClient.getAgreement(TEST_AGREEMENT_ID), "The correct agreement should be returned.");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("/agreement/" + TEST_AGREEMENT_ID, recordedRequest.getPath());
    }

    @Test
    public void testGetAgreement_invalid_content() throws Exception {
        mockResponse(200, "invalid");

        assertThrows(BadGatewayException.class, () -> agreementApiClient.getAgreement(TEST_AGREEMENT_ID), "If the server returns something invalid, a BadGateWay should be thrown.");
    }

    @ParameterizedTest(name = "Failure code {0}")
    @ValueSource(ints = {400, 401, 403, 404, 500})
    public void testGetAgreement_bad_response(int responseCode) throws Exception {
        mockResponse(responseCode);
        assertThrows(BadGatewayException.class, () -> agreementApiClient.getAgreement(TEST_AGREEMENT_ID), "If the server returns something unsuccessful, a BadGateWay should be thrown.");

    }

    @Test
    public void testGetAgreements_success() throws Exception {
        List<ExtendableAgreementVO> testAgreements = getValidAgreements(5);
        mockResponse(200, testAgreements);

        assertEquals(testAgreements, agreementApiClient.getAgreements(0, 5), "The correct agreements should be returned.");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("/agreement?offset=0&limit=5", recordedRequest.getPath());
    }

    @Test
    public void testGetAgreements_invalid_content() throws Exception {
        mockResponse(200, "invalid");

        assertThrows(BadGatewayException.class, () -> agreementApiClient.getAgreements(0, 5), "If the server returns something invalid, a BadGateWay should be thrown.");
    }

    @ParameterizedTest(name = "Failure code {0}")
    @ValueSource(ints = {400, 401, 403, 404, 500})
    public void testGetAgreements_bad_response(int responseCode) throws Exception {
        mockResponse(responseCode);
        assertThrows(BadGatewayException.class, () -> agreementApiClient.getAgreements(0, 5), "If the server returns something unsuccessful, a BadGateWay should be thrown.");

    }

    @Test
    public void testCreateAgreement_success() throws Exception {
        ExtendableAgreementCreateVO testCreate = getValidAgreementCreateVO();
        ExtendableAgreementVO testAgreement = getValidAgreementVO();
        mockResponse(200, testAgreement);

        assertEquals(testAgreement, agreementApiClient.createAgreement(testCreate), "The correct agreement should be returned.");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("/agreement", recordedRequest.getPath());
        ExtendableAgreementCreateVO sendAgreement = objectMapper.readValue(recordedRequest.getBody().readByteArray(), ExtendableAgreementCreateVO.class);
        assertEquals(testCreate, sendAgreement, "The agreement create should have been sent.");
    }

    @Test
    public void testCreateAgreement_invalid_content() throws Exception {

        mockResponse(200, "invalid");

        assertThrows(BadGatewayException.class, () -> agreementApiClient.createAgreement(getValidAgreementCreateVO()),
                "If the server returns something invalid, a BadGateWay should be thrown.");
    }

    @ParameterizedTest(name = "Failure code {0}")
    @ValueSource(ints = {400, 401, 403, 404, 500})
    public void testCreateAgreement_bad_response(int responseCode) throws Exception {
        mockResponse(responseCode);
        assertThrows(BadGatewayException.class, () -> agreementApiClient.createAgreement(getValidAgreementCreateVO()), "If the server returns something unsuccessful, a BadGateWay should be thrown.");
    }

    @Test
    public void testUpdateAgreement_success() throws Exception {
        ExtendableAgreementUpdateVO testUpdate = getValidAgreementUpdateVO();
        ExtendableAgreementVO testAgreement = getValidAgreementVO();
        mockResponse(200, testAgreement);

        assertEquals(testAgreement, agreementApiClient.updateAgreement(TEST_AGREEMENT_ID, testUpdate), "The correct agreement should be returned.");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("/agreement/" + TEST_AGREEMENT_ID, recordedRequest.getPath());
        ExtendableAgreementUpdateVO sendAgreement = objectMapper.readValue(recordedRequest.getBody().readByteArray(), ExtendableAgreementUpdateVO.class);
        assertEquals(testUpdate, sendAgreement, "The agreement create should have been sent.");
    }

    @Test
    public void testUpdateAgreement_invalid_content() throws Exception {

        mockResponse(200, "invalid");

        assertThrows(BadGatewayException.class, () -> agreementApiClient.updateAgreement(TEST_AGREEMENT_ID, getValidAgreementUpdateVO()),
                "If the server returns something invalid, a BadGateWay should be thrown.");
    }

    @ParameterizedTest(name = "Failure code {0}")
    @ValueSource(ints = {400, 401, 403, 404, 500})
    public void testUpdateAgreement_bad_response(int responseCode) throws Exception {
        mockResponse(responseCode);
        assertThrows(BadGatewayException.class, () -> agreementApiClient.updateAgreement(TEST_AGREEMENT_ID, getValidAgreementUpdateVO()), "If the server returns something unsuccessful, a BadGateWay should be thrown.");

    }

    @Test
    public void testFindByNegotiationId_success() throws Exception {
        ExtendableAgreementVO testAgreement = getValidAgreementVO();
        mockResponse(200, List.of(testAgreement));

        assertEquals(testAgreement, agreementApiClient.findByNegotiationId(TEST_NEGOTIATION_ID).get(), "The correct agreement should be returned.");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("/agreement?negotiationId=" + TEST_NEGOTIATION_ID, recordedRequest.getPath());
    }

    @Test
    public void testFindByNegotiationId_success_no_negotiation() throws Exception {
        mockResponse(200, List.of());

        assertTrue(agreementApiClient.findByNegotiationId(TEST_NEGOTIATION_ID).isEmpty(), "No agreement should have been returned.");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("/agreement?negotiationId=" + TEST_NEGOTIATION_ID, recordedRequest.getPath());
    }

    @Test
    public void testFindByNegotiationId_failure_to_many_agreements() throws Exception {
        mockResponse(200, getValidAgreements(2));
        assertThrows(BadGatewayException.class, () -> agreementApiClient.findByNegotiationId(TEST_NEGOTIATION_ID), "If the server returns something invalid, a BadGateWay should be thrown.");
    }


    @Test
    public void testFindByNegotiationId_invalid_content() throws Exception {

        mockResponse(200, "invalid");

        assertThrows(BadGatewayException.class, () -> agreementApiClient.findByNegotiationId(TEST_NEGOTIATION_ID), "If the server returns something invalid, a BadGateWay should be thrown.");
    }

    @ParameterizedTest(name = "Failure code {0}")
    @ValueSource(ints = {400, 401, 403, 404, 500})
    public void testFindByNegotiationId_bad_response(int responseCode) throws Exception {
        mockResponse(responseCode);
        assertThrows(BadGatewayException.class, () -> agreementApiClient.findByNegotiationId(TEST_NEGOTIATION_ID), "If the server returns something unsuccessful, a BadGateWay should be thrown.");

    }

    @Test
    public void testFindByContractId_success() throws Exception {
        ExtendableAgreementVO testAgreement = getValidAgreementVO();
        mockResponse(200, List.of(testAgreement));

        assertEquals(testAgreement, agreementApiClient.findByContractId(TEST_CONTRACT_ID).get(), "The correct agreement should be returned.");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("/agreement?externalId=" + TEST_CONTRACT_ID, recordedRequest.getPath());
    }

    @Test
    public void testFindByContractId_success_no_negotiation() throws Exception {
        mockResponse(200, List.of());

        assertTrue(agreementApiClient.findByContractId(TEST_CONTRACT_ID).isEmpty(), "No agreement should have been returned.");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("/agreement?externalId=" + TEST_CONTRACT_ID, recordedRequest.getPath());
    }

    @Test
    public void testFindByContractId_failure_to_many_agreements() throws Exception {
        mockResponse(200, getValidAgreements(2));

        assertThrows(BadGatewayException.class, () -> agreementApiClient.findByContractId(TEST_CONTRACT_ID), "If the server returns something invalid, a BadGateWay should be thrown.");
    }


    @Test
    public void testFindByContractId_invalid_content() throws Exception {

        mockResponse(200, "invalid");
        assertThrows(BadGatewayException.class, () -> agreementApiClient.findByContractId(TEST_CONTRACT_ID), "If the server returns something invalid, a BadGateWay should be thrown.");
    }

    @ParameterizedTest(name = "Failure code {0}")
    @ValueSource(ints = {400, 401, 403, 404, 500})
    public void testGFindByContractId_bad_response(int responseCode) throws Exception {
        mockResponse(responseCode);
        assertThrows(BadGatewayException.class, () -> agreementApiClient.findByContractId(TEST_CONTRACT_ID), "If the server returns something unsuccessful, a BadGateWay should be thrown.");

    }

    private ExtendableAgreementVO getValidAgreementVO() {
        return getValidAgreementVO("test");
    }

    private ExtendableAgreementVO getValidAgreementVO(String id) {
        ExtendableAgreementVO agreementVO = new ExtendableAgreementVO();
        agreementVO.setId(id);
        agreementVO.setAtSchemaLocation(URI.create("http://base.uri/agreement.json"));
        return agreementVO;
    }

    private ExtendableAgreementCreateVO getValidAgreementCreateVO() {
        ExtendableAgreementCreateVO agreementVO = new ExtendableAgreementCreateVO();
        agreementVO.setAtSchemaLocation(URI.create("http://base.uri/agreement.json"));
        return agreementVO;
    }

    private ExtendableAgreementUpdateVO getValidAgreementUpdateVO() {
        ExtendableAgreementUpdateVO agreementVO = new ExtendableAgreementUpdateVO();
        agreementVO.setAtSchemaLocation(URI.create("http://base.uri/agreement.json"));
        return agreementVO;
    }

    private List<ExtendableAgreementVO> getValidAgreements(int num) {
        List<ExtendableAgreementVO> extendableAgreementVOS = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            extendableAgreementVOS.add(getValidAgreementVO(String.valueOf(i)));
        }
        return extendableAgreementVOS;
    }
}