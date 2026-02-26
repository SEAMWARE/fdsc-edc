package org.seamware.edc.tmf;

import okhttp3.mockwebserver.RecordedRequest;
import org.eclipse.edc.web.spi.exception.BadGatewayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.seamware.edc.domain.ExtendableProduct;
import org.seamware.edc.domain.ExtendableProductCreate;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ProductInventoryApiClientTest extends AbstractApiTest {

    private static final String TEST_PRODUCT_ID = "test-product";

    private ProductInventoryApiClient productInventoryApiClient;

    @Override
    public void setupConcreteClient(String baseUrl) {
        productInventoryApiClient = new ProductInventoryApiClient(monitor, okHttpClient, baseUrl, objectMapper);
    }

    @Test
    public void testGetProductById_success() throws Exception {
        ExtendableProduct testProduct = getProduct();
        mockResponse(200, testProduct);

        assertEquals(testProduct, productInventoryApiClient.getProductById(TEST_PRODUCT_ID).get(), "The correct product should be returned.");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("/product/" + TEST_PRODUCT_ID, recordedRequest.getPath());
    }

    @Test
    public void testGetProductById_invalid_content() throws Exception {
        mockResponse(200, "invalid");

        assertThrows(BadGatewayException.class, () -> productInventoryApiClient.getProductById(TEST_PRODUCT_ID), "If the server returns something invalid, a BadGateway should be returned.");
    }

    @ParameterizedTest(name = "Failure code {0}")
    @ValueSource(ints = {400, 401, 403, 404, 500})
    public void testGetProductById_bad_response(int responseCode) throws Exception {
        mockResponse(responseCode);
        assertThrows(BadGatewayException.class, () -> productInventoryApiClient.getProductById(TEST_PRODUCT_ID), "If the server returns something invalid, a BadGateway should be returned.");
    }

    @Test
    public void testCreateProduct_success() throws Exception {
        ExtendableProductCreate testCreate = getProductCreate();
        ExtendableProduct testProduct = getProduct();
        mockResponse(200, testProduct);

        assertEquals(testProduct, productInventoryApiClient.createProduct(testCreate), "The correct product should be returned.");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("/product", recordedRequest.getPath());
        ExtendableProductCreate sentProduct = objectMapper.readValue(recordedRequest.getBody().readByteArray(), ExtendableProductCreate.class);
        assertEquals(testCreate, sentProduct, "The product create should have been sent.");
    }

    @Test
    public void testCreateAgreement_invalid_content() throws Exception {

        mockResponse(200, "invalid");

        assertThrows(BadGatewayException.class, () -> productInventoryApiClient.createProduct(getProductCreate()),
                "If the server returns something invalid, a BadGateWay should be thrown.");
    }

    @ParameterizedTest(name = "Failure code {0}")
    @ValueSource(ints = {400, 401, 403, 404, 500})
    public void testCreateAgreement_bad_response(int responseCode) throws Exception {
        mockResponse(responseCode);
        assertThrows(BadGatewayException.class, () -> productInventoryApiClient.createProduct(getProductCreate()), "If the server returns something unsuccessful, a BadGateWay should be thrown.");

    }

    private ExtendableProductCreate getProductCreate() {

        ExtendableProductCreate extendableProductCreate = new ExtendableProductCreate();
        extendableProductCreate.setAtSchemaLocation(URI.create("http://base.uri/external-id.json"));
        return extendableProductCreate;
    }

    private ExtendableProduct getProduct() {
        ExtendableProduct extendableProduct = new ExtendableProduct();
        extendableProduct.setAtSchemaLocation(URI.create("http://base.uri/external-id.json"));
        return extendableProduct;
    }

}