package org.seamware.edc.tmf;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.eclipse.edc.spi.monitor.Monitor;
import org.seamware.edc.domain.*;
import org.seamware.tmforum.agreement.model.AgreementVO;
import org.seamware.tmforum.usage.model.UsageVO;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.seamware.edc.store.TMFEdcMapper.USAGE_TYPE_DSP_TRANSFER;

/**
 * Client implementation to interact with the TMForum Usage API
 */
public class UsageApiClient extends ApiClient {

    private static final String USAGE_PATH = "usage";
    private static final String USAGE_TYPE_QUERY = "usageType";

    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public UsageApiClient(Monitor monitor, OkHttpClient okHttpClient, String baseUrl, ObjectMapper objectMapper) {
        super(monitor, okHttpClient);
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
    }

    /**
     * Remove a usage by its id
     *
     * @param usageId the TMForum usage id
     */
    public void deleteUsage(String usageId) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        urlBuilder.addPathSegment(USAGE_PATH);
        urlBuilder.addPathSegment(usageId);
        Request request = new Request.Builder().url(urlBuilder.build()).delete().build();
        executeRequest(request);
    }

    /**
     * Gets a usage by its id
     *
     * @param usageId the TMForum usage id
     */
    public ExtendableUsageVO getUsage(String usageId) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        urlBuilder.addPathSegment(USAGE_PATH);
        urlBuilder.addPathSegment(usageId);
        Request request = new Request.Builder().url(urlBuilder.build()).build();
        try (ResponseBody responseBody = executeRequest(request)) {
            return objectMapper.readValue(responseBody.bytes(), ExtendableUsageVO.class);
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Was not able to get usage %s.", usageId), e);
        }
    }

    /**
     * Returns a list of usage, supporting pagination
     */
    public List<ExtendableUsageVO> getUsages(int offset, int limit) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        urlBuilder.addPathSegment(USAGE_PATH);
        urlBuilder.addQueryParameter(USAGE_TYPE_QUERY, USAGE_TYPE_DSP_TRANSFER);
        urlBuilder.addQueryParameter(OFFSET_PARAM, String.valueOf(offset));
        urlBuilder.addQueryParameter(LIMIT_PARAM, String.valueOf(limit));
        Request request = new Request.Builder().url(urlBuilder.build()).build();
        try (ResponseBody responseBody = executeRequest(request)) {
            return objectMapper
                    .readValue(responseBody.bytes(), new TypeReference<List<ExtendableUsageVO>>() {
                    });
        } catch (IOException e) {
            throw new IllegalArgumentException("Was not able to get usages.", e);
        }
    }

    /**
     * Creates the given usage
     */
    public ExtendableUsageVO createUsage(ExtendableUsageCreateVO extendableUsageCreateVO) {

        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        urlBuilder.addPathSegment(USAGE_PATH);
        RequestBody requestBody = null;
        try {
            requestBody = RequestBody.create(objectMapper.writeValueAsString(extendableUsageCreateVO), JSON);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Was not able to serialize usage.", e);
        }
        Request request = new Request.Builder().url(urlBuilder.build()).post(requestBody).build();
        try (ResponseBody responseBody = executeRequest(request)) {
            return objectMapper.readValue(responseBody.bytes(), ExtendableUsageVO.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Was not able to read usage creation response.", e);
        }
    }


    /**
     * Updates the given usage
     */
    public ExtendableUsageVO updateUsage(String id, ExtendableUsageUpdateVO usageUpdateVO) {

        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        urlBuilder.addPathSegment(USAGE_PATH);
        urlBuilder.addPathSegment(id);
        RequestBody requestBody = null;
        try {
            requestBody = RequestBody.create(objectMapper.writeValueAsString(usageUpdateVO), JSON);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Was not able to serialize usage update.", e);
        }
        Request request = new Request.Builder().url(urlBuilder.build()).patch(requestBody).build();
        try (ResponseBody responseBody = executeRequest(request)) {
            return objectMapper.readValue(responseBody.bytes(), ExtendableUsageVO.class);
        } catch (IOException e) {
            monitor.severe("Was not able to read update creation.", e);
            throw new IllegalArgumentException("Was not able to read usage update response.", e);
        }
    }

    /**
     * Returns a usage by the id of its corresponding transfer
     */
    public Optional<ExtendableUsageVO> findByTransferId(String transferId) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        urlBuilder.addPathSegment(USAGE_PATH);
        urlBuilder.addQueryParameter("externalId", transferId);
        Request request = new Request.Builder().url(urlBuilder.build()).build();
        try (ResponseBody responseBody = executeRequest(request)) {
            List<ExtendableUsageVO> extendableUsageVOS = objectMapper.readValue(responseBody.bytes(), new TypeReference<List<ExtendableUsageVO>>() {
            });
            if (extendableUsageVOS.size() > 1) {
                throw new IllegalArgumentException(String.format("There cannot be more than one usage per correlation id. Found multiple for %s.", transferId));
            }
            if (extendableUsageVOS.isEmpty()) {
                return Optional.empty();
            }
            return Optional.ofNullable(extendableUsageVOS.getFirst());
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Was not able to get usage for transferId %s", transferId), e);
        }
    }
}