package org.seamware.edc.apisix;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.eclipse.edc.spi.monitor.Monitor;
import org.seamware.edc.BaseClient;

import java.io.IOException;
import java.util.Optional;

public class ApisixAdminClient extends BaseClient {

    private static final String ROUTES_PATH = "apisix/admin/routes";
    private static final String ADMIN_TOKEN_HEADER = "X-API-KEY";

    private final String adminToken;

    public ApisixAdminClient(Monitor monitor, OkHttpClient okHttpClient, String baseUrl, ObjectMapper objectMapper, String adminToken) {
        super(monitor, okHttpClient, baseUrl, objectMapper);
        this.adminToken = adminToken;
    }

    public Route getRoute(String processId) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        urlBuilder.addPathSegment(ROUTES_PATH);
        urlBuilder.addPathSegment(processId);
        Request request = new Request.Builder().url(urlBuilder.build()).header(ADMIN_TOKEN_HEADER, adminToken).build();
        try (ResponseBody responseBody = executeRequestWithResponse(request)) {
            return objectMapper.readValue(responseBody.bytes(), Route.class);
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Was not able to get route for process %s.", processId), e);
        }
    }

    public Route addRoute(Route route) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        urlBuilder.addPathSegment(ROUTES_PATH);
        String routeString = "";
        try {
            routeString = objectMapper.writeValueAsString(route);
        } catch (JsonProcessingException e) {
            monitor.warning("Was not able to parse route.", e);
            throw new IllegalArgumentException("Was not able to parse route.", e);
        }

        Request request = new Request.Builder().url(urlBuilder.build()).header(ADMIN_TOKEN_HEADER, adminToken).put(RequestBody.create(routeString, JSON)).build();
        try (ResponseBody responseBody = executeRequestWithResponse(request)) {
            return objectMapper.readValue(responseBody.bytes(), Route.class);
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Was not able to create route: %s.", routeString), e);
        }
    }

    public void deleteRoute(String routeId) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        urlBuilder.addPathSegment(ROUTES_PATH);
        urlBuilder.addPathSegment(routeId);
        Optional.ofNullable(executeRequest(new Request.Builder().url(urlBuilder.build()).header(ADMIN_TOKEN_HEADER, adminToken).delete().build()).body()).ifPresent(ResponseBody::close);
    }


}
