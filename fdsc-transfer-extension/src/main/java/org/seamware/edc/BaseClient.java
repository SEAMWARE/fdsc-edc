package org.seamware.edc;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.eclipse.edc.spi.monitor.Monitor;

import java.io.IOException;

/**
 * Base http client
 */
public abstract class BaseClient {

    protected static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    protected final Monitor monitor;
    protected final OkHttpClient okHttpClient;
    protected final String baseUrl;
    protected final ObjectMapper objectMapper;

    protected BaseClient(Monitor monitor, OkHttpClient okHttpClient, String baseUrl, ObjectMapper objectMapper) {
        this.monitor = monitor;
        this.okHttpClient = okHttpClient;
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
    }

    protected ResponseBody executeRequestWithResponse(Request request) {
        return executeRequest(request).body();
    }

    protected Response executeRequest(Request request) {
        try {
            Response response = okHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                return response;
            } else {
                monitor.warning(String.format("Was not able to get as successful response for %s. Was: %s - %s", request.url(), response.code(), response.body().string()));
                throw new IllegalArgumentException(String.format("Was not able to get as successful response for %s. Was: %s - %s", request.url(), response.code(), response.body().string()));
            }
        } catch (IOException e) {
            monitor.warning(String.format("Was not able to get response for %s", request.url()));
            throw new IllegalArgumentException(String.format("Was not able to get response for %s", request.url()), e);
        }
    }
}
