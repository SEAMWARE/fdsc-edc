package org.seamware.edc.tmf;

import okhttp3.Call;
import okhttp3.mockwebserver.RecordedRequest;
import org.eclipse.edc.web.spi.exception.BadGatewayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.shadow.com.univocity.parsers.annotations.helpers.AnnotationRegistry;
import org.seamware.edc.domain.ExtendableAgreementVO;
import org.seamware.edc.domain.ExtendableQuoteCreateVO;
import org.seamware.edc.domain.ExtendableQuoteUpdateVO;
import org.seamware.edc.domain.ExtendableQuoteVO;
import org.seamware.tmforum.productorder.model.ProductOrderCreateVO;
import org.seamware.tmforum.productorder.model.ProductOrderUpdateVO;
import org.seamware.tmforum.productorder.model.ProductOrderVO;
import org.seamware.tmforum.quote.model.QuoteVO;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class QuoteApiClientTest extends AbstractApiTest {

    private static final String CONTROLPLANE_ID = "test-controlplane";
    private static final String TEST_QUOTE_ID = "test-quote";

    private QuoteApiClient quoteApiClient;

    @Override
    public void setupConcreteClient(String baseUrl) {
        quoteApiClient = new QuoteApiClient(monitor, okHttpClient, CONTROLPLANE_ID, baseUrl, objectMapper);
    }

    @Test
    public void testGetQuotes_success() throws Exception {
        List<ExtendableQuoteVO> testQuotes = getQuotes(5);
        mockResponse(200, testQuotes);

        assertEquals(testQuotes, quoteApiClient.getQuotes(0, 5), "The correct quotes should be returned.");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("/quote?offset=0&limit=5&contractNegotiation.controlplane=" + CONTROLPLANE_ID, recordedRequest.getPath());
    }


    @Test
    public void testGetQuotes_invalid_content() throws Exception {
        mockResponse(200, "invalid");

        assertThrows(BadGatewayException.class, () -> quoteApiClient.getQuotes(0, 5), "If the server returns something invalid, a BadGateWay should be thrown.");
    }

    @ParameterizedTest(name = "Failure code {0}")
    @ValueSource(ints = {400, 401, 403, 404, 500})
    public void testGetQuotes_bad_response(int responseCode) throws Exception {
        mockResponse(responseCode);
        assertThrows(BadGatewayException.class, () -> quoteApiClient.getQuotes(0, 5), "If the server returns something unsuccessful, a BadGateWay should be thrown.");

    }

    @Test
    public void testUpdateQuote_success() throws Exception {
        ExtendableQuoteUpdateVO testUpdate = getQuoteUpdate();
        ExtendableQuoteVO testQuote = getQuote();
        mockResponse(200, testQuote);

        assertEquals(testQuote, quoteApiClient.updateQuote(TEST_QUOTE_ID, testUpdate), "The correct quote should be returned.");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("/quote/" + TEST_QUOTE_ID, recordedRequest.getPath());
        ExtendableQuoteUpdateVO sentQuote = objectMapper.readValue(recordedRequest.getBody().readByteArray(), ExtendableQuoteUpdateVO.class);
        assertEquals(testUpdate, sentQuote, "The quote create should have been sent.");
    }

    @Test
    public void testUpdateQuote_invalid_content() throws Exception {

        mockResponse(200, "invalid");

        assertThrows(BadGatewayException.class, () -> quoteApiClient.updateQuote(TEST_QUOTE_ID, getQuoteUpdate()),
                "If the server returns something invalid, a BadGateWay should be thrown.");
    }

    @ParameterizedTest(name = "Failure code {0}")
    @ValueSource(ints = {400, 401, 403, 404, 500})
    public void testUpdateQuote_bad_response(int responseCode) throws Exception {
        mockResponse(responseCode);
        assertThrows(BadGatewayException.class, () -> quoteApiClient.updateQuote(TEST_QUOTE_ID, getQuoteUpdate()),
                "If the server returns something unsuccessful, a BadGateWay should be thrown.");
    }

    @Test
    public void testCreateQuote_success() throws Exception {
        ExtendableQuoteCreateVO testCreate = getQuoteCreate();
        ExtendableQuoteVO testQuote = getQuote();
        mockResponse(200, testQuote);

        assertEquals(testQuote, quoteApiClient.createQuote(testCreate), "The correct quote should be returned.");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("/quote", recordedRequest.getPath());
        ExtendableQuoteCreateVO sentQuote = objectMapper.readValue(recordedRequest.getBody().readByteArray(), ExtendableQuoteCreateVO.class);
        assertEquals(testCreate, sentQuote, "The quote create should have been sent.");
    }

    @Test
    public void testCreateQuote_invalid_content() throws Exception {

        mockResponse(200, "invalid");
        assertThrows(BadGatewayException.class, () -> quoteApiClient.createQuote(getQuoteCreate()),
                "If the server returns something invalid, a BadGateWay should be thrown.");
    }

    @ParameterizedTest(name = "Failure code {0}")
    @ValueSource(ints = {400, 401, 403, 404, 500})
    public void testCreateQuote_bad_response(int responseCode) throws Exception {
        mockResponse(responseCode);
        assertThrows(BadGatewayException.class, () -> quoteApiClient.createQuote(getQuoteCreate()),
                "If the server returns something unsuccessful, a BadGateWay should be thrown.");
    }

    @ParameterizedTest
    @MethodSource("getValidQuotes")
    public void testFindByNegotiationId_success(List<ExtendableQuoteVO> quoteVOS) throws Exception {
        mockResponse(200, quoteVOS);

        assertEquals(new HashSet<>(quoteVOS), new HashSet<>(quoteApiClient.findByNegotiationId(TEST_QUOTE_ID)), "The correct quotes should be returned.");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("/quote?externalId=" + TEST_QUOTE_ID, recordedRequest.getPath());
    }

    private static Stream<Arguments> getValidQuotes() {
        return Stream.of(
                Arguments.of(getQuotes(1)),
                Arguments.of(getQuotes(3)),
                Arguments.of(List.of())
        );
    }

    @Test
    public void testFindByNegotiationId_invalid_content() throws Exception {

        mockResponse(200, "invalid");

        assertThrows(BadGatewayException.class, () -> quoteApiClient.findByNegotiationId(TEST_QUOTE_ID), "If the server returns something invalid, a BadGateWay should be thrown.");
    }

    @ParameterizedTest(name = "Failure code {0}")
    @ValueSource(ints = {400, 401, 403, 404, 500})
    public void testGFindByNegotiationId_bad_response(int responseCode) throws Exception {
        mockResponse(responseCode);
        assertThrows(BadGatewayException.class, () -> quoteApiClient.findByNegotiationId(TEST_QUOTE_ID), "If the server returns something unsuccessful, a BadGateWay should be thrown.");

    }


    private static List<ExtendableQuoteVO> getQuotes(int num) {
        List<ExtendableQuoteVO> extendableQuoteVOS = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            extendableQuoteVOS.add(getQuote());
        }
        return extendableQuoteVOS;
    }

    private static ExtendableQuoteVO getQuote() {
        ExtendableQuoteVO extendableQuoteVO = new ExtendableQuoteVO();
        extendableQuoteVO.setAtSchemaLocation(URI.create("http://base.uri/contract-negotiation.json"));
        return extendableQuoteVO;
    }

    private static ExtendableQuoteUpdateVO getQuoteUpdate() {
        ExtendableQuoteUpdateVO extendableQuoteUpdateVO = new ExtendableQuoteUpdateVO();
        extendableQuoteUpdateVO.setAtSchemaLocation(URI.create("http://base.uri/contract-negotiation.json"));
        return extendableQuoteUpdateVO;
    }

    private static ExtendableQuoteCreateVO getQuoteCreate() {
        ExtendableQuoteCreateVO extendableQuoteCreateVO = new ExtendableQuoteCreateVO();
        extendableQuoteCreateVO.setAtSchemaLocation(URI.create("http://base.uri/contract-negotiation.json"));
        return extendableQuoteCreateVO;
    }

    private static ExtendableQuoteVO getQuote(String externalId) {
        ExtendableQuoteVO extendableQuoteVO = new ExtendableQuoteVO();
        extendableQuoteVO.setAtSchemaLocation(URI.create("http://base.uri/contract-negotiation.json"));
        extendableQuoteVO.setExternalId(externalId);
        return extendableQuoteVO;
    }
}