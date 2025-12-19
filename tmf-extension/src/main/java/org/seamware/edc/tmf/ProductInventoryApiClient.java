package org.seamware.edc.tmf;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.eclipse.edc.spi.monitor.Monitor;
import org.seamware.edc.domain.ExtendableProduct;
import org.seamware.edc.domain.ExtendableProductCreate;
import org.seamware.edc.domain.ExtendableProductOffering;
import org.seamware.tmforum.productcatalog.model.ProductSpecificationVO;
import org.seamware.tmforum.productinventory.model.ProductCreateVO;
import org.seamware.tmforum.productinventory.model.ProductVO;
import org.seamware.tmforum.productorder.model.ProductOrderUpdateVO;
import org.seamware.tmforum.productorder.model.ProductOrderVO;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Client implementation to interact with the TMForum Product Inventory API
 */
public class ProductInventoryApiClient extends ApiClient {

    private static final String PRODUCT_PATH = "product";

    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public ProductInventoryApiClient(Monitor monitor, OkHttpClient okHttpClient, String baseUrl, ObjectMapper objectMapper) {
        super(monitor, okHttpClient);
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates the given product
     */
    public ExtendableProduct createProduct(ExtendableProductCreate productCreateVO) {

        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        urlBuilder.addPathSegment(PRODUCT_PATH);
        RequestBody requestBody = null;
        try {
            requestBody = RequestBody.create(objectMapper.writeValueAsString(productCreateVO), JSON);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Was not able to serialize product.", e);
        }
        Request request = new Request.Builder().url(urlBuilder.build()).post(requestBody).build();
        try (ResponseBody responseBody = executeRequest(request)) {
            return objectMapper.readValue(responseBody.bytes(), ExtendableProduct.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Was not able to read product creation response.", e);
        }
    }

    /**
     * Returns the product by its id
     */
    public Optional<ExtendableProduct> getProductById(String id) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        urlBuilder.addPathSegment(PRODUCT_PATH);
        urlBuilder.addPathSegment(id);
        Request request = new Request.Builder().url(urlBuilder.build()).build();
        try (ResponseBody responseBody = executeRequest(request)) {
            return Optional.ofNullable(objectMapper
                    .readValue(responseBody.bytes(), ExtendableProduct.class));
        } catch (IOException e) {
            monitor.warning(String.format("Was not able to get product %s.", id), e);
            return Optional.empty();
        }
    }

    /**
     * Returns the product by its externalId(the data-set id)
     */
    public Optional<ExtendableProduct> getProductByExternalId(String externalId) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        urlBuilder.addPathSegment(PRODUCT_PATH);
        urlBuilder.addQueryParameter("externalId", externalId);
        Request request = new Request.Builder().url(urlBuilder.build()).build();
        try (ResponseBody responseBody = executeRequest(request)) {
            List<ExtendableProduct> extendableProducts = objectMapper.readValue(responseBody.bytes(), new TypeReference<List<ExtendableProduct>>() {
            });
            if (extendableProducts.size() > 1) {
                throw new IllegalArgumentException(String.format("Multiple products for id %s exist. External Ids need to be unique.", externalId));
            }
            if (extendableProducts.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(extendableProducts.getFirst());
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Was not able to get products for external id %s", externalId), e);
        }
    }
}
