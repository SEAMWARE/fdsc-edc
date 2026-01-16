package org.seamware.edc.tmf;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.eclipse.edc.spi.monitor.Monitor;
import org.seamware.edc.domain.ExtendableQuoteUpdateVO;
import org.seamware.edc.domain.ExtendableQuoteVO;
import org.seamware.tmforum.quote.model.QuoteCreateVO;

import java.io.IOException;
import java.util.List;

/**
 * Client implementation to interact with the TMForum Usage API
 */
public class QuoteApiClient extends ApiClient {

    private static final String QUOTE_PATH = "quote";

    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public QuoteApiClient(Monitor monitor, OkHttpClient okHttpClient, String baseUrl, ObjectMapper objectMapper) {
        super(monitor, okHttpClient);
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper.copy().setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }


    /**
     * Returns a list of quotes, supporting pagination
     */
    public List<ExtendableQuoteVO> getQuotes(int offset, int limit) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        urlBuilder.addPathSegment(QUOTE_PATH);
        urlBuilder.addQueryParameter(OFFSET_PARAM, String.valueOf(offset));
        urlBuilder.addQueryParameter(LIMIT_PARAM, String.valueOf(limit));
        Request request = new Request.Builder().url(urlBuilder.build()).build();
        try (ResponseBody responseBody = executeRequest(request)) {
            return objectMapper.readValue(responseBody.bytes(), new TypeReference<List<ExtendableQuoteVO>>() {
            });
        } catch (Exception e) {
            monitor.warning("Was not able to get quotes.", e);
            return List.of();
        }
    }

    /**
     * Updates the given quote
     */
    public ExtendableQuoteVO updateQuote(String id, ExtendableQuoteUpdateVO quoteUpdateVO) {

        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        urlBuilder.addPathSegment(QUOTE_PATH);
        urlBuilder.addPathSegment(id);
        RequestBody requestBody = null;
        try {
            String qc = objectMapper.writeValueAsString(quoteUpdateVO);
            requestBody = RequestBody.create(qc, JSON);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Was not able to serialize quote update.", e);
        }
        Request request = new Request.Builder().url(urlBuilder.build()).patch(requestBody).build();
        try (ResponseBody responseBody = executeRequest(request)) {
            return objectMapper.readValue(responseBody.bytes(), ExtendableQuoteVO.class);
        } catch (IOException e) {
            monitor.severe("Was not able to read quote creation.", e);
            throw new IllegalArgumentException("Was not able to read quote creation response.", e);
        }
    }

    /**
     * Creates the given quote
     */
    public ExtendableQuoteVO createQuote(QuoteCreateVO quoteCreateVO) {

        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        urlBuilder.addPathSegment(QUOTE_PATH);
        RequestBody requestBody = null;
        try {
            requestBody = RequestBody.create(objectMapper.writeValueAsString(quoteCreateVO), JSON);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Was not able to serialize quote.", e);
        }
        Request request = new Request.Builder().url(urlBuilder.build()).post(requestBody).build();
        try (ResponseBody responseBody = executeRequest(request)) {
            return objectMapper.readValue(responseBody.bytes(), ExtendableQuoteVO.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Was not able to read quote creation response.", e);
        }
    }

    /**
     * Returns all quotes corresponding to the given negotiationId
     */
    public List<ExtendableQuoteVO> findByNegotiationId(String negotiationId) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        urlBuilder.addPathSegment(QUOTE_PATH);
        urlBuilder.addQueryParameter("externalId", negotiationId);
        Request request = new Request.Builder().url(urlBuilder.build()).build();
        try (ResponseBody responseBody = executeRequest(request)) {
            return objectMapper.readValue(responseBody.bytes(), new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Was not able to get quotes for negotiation %s", negotiationId), e);
        }
    }

}
