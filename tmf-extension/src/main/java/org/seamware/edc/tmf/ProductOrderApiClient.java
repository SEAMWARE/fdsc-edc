package org.seamware.edc.tmf;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.eclipse.edc.spi.monitor.Monitor;
import org.seamware.tmforum.productorder.model.ProductOrderCreateVO;
import org.seamware.tmforum.productorder.model.ProductOrderUpdateVO;
import org.seamware.tmforum.productorder.model.ProductOrderVO;

import java.io.IOException;
import java.util.List;

/**
 * Client implementation to interact with the TMForum Product Order API
 */
public class ProductOrderApiClient extends ApiClient {

    private static final String PRODUCT_ORDER_PATH = "productOrder";

    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public ProductOrderApiClient(Monitor monitor, OkHttpClient okHttpClient, String baseUrl, ObjectMapper objectMapper) {
        super(monitor, okHttpClient);
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns all product orders that reference the given quote id
     */
    public List<ProductOrderVO> findByQuoteId(String quoteId) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        urlBuilder.addPathSegment(PRODUCT_ORDER_PATH);
        urlBuilder.addQueryParameter("quote.id", quoteId);
        Request request = new Request.Builder().url(urlBuilder.build()).build();
        try (ResponseBody responseBody = executeRequest(request)) {
            return objectMapper.readValue(responseBody.bytes(), new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Was not able to get product orders for quote %s", quoteId), e);
        }
    }

    /**
     * Creates the given product order
     */
    public ProductOrderVO createProductOrder(ProductOrderCreateVO productOrderCreateVO) {

        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        urlBuilder.addPathSegment(PRODUCT_ORDER_PATH);
        RequestBody requestBody = null;
        try {
            requestBody = RequestBody.create(objectMapper.writeValueAsString(productOrderCreateVO), JSON);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Was not able to serialize product order.", e);
        }
        Request request = new Request.Builder().url(urlBuilder.build()).post(requestBody).build();
        try (ResponseBody responseBody = executeRequest(request)) {
            return objectMapper.readValue(responseBody.bytes(), ProductOrderVO.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Was not able to read product order creation response.", e);
        }
    }

    /**
     * Updates the given product order
     */
    public ProductOrderVO updateProductOrder(String id, ProductOrderUpdateVO productOrderUpdateVO) {

        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        urlBuilder.addPathSegment(PRODUCT_ORDER_PATH);
        urlBuilder.addPathSegment(id);
        RequestBody requestBody = null;
        try {
            String qc = objectMapper.writeValueAsString(productOrderUpdateVO);
            requestBody = RequestBody.create(qc, JSON);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Was not able to serialize product order update.", e);
        }
        Request request = new Request.Builder().url(urlBuilder.build()).patch(requestBody).build();
        try (ResponseBody responseBody = executeRequest(request)) {
            return objectMapper.readValue(responseBody.bytes(), ProductOrderVO.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Was not able to read product order update response.", e);
        }
    }
}
