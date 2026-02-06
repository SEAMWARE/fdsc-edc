package org.seamware.edc.pap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.eclipse.edc.spi.monitor.Monitor;
import org.seamware.edc.BaseClient;
import org.seamware.pap.model.PolicyPathVO;
import org.seamware.pap.model.ServiceCreateVO;

import java.io.IOException;
import java.util.Optional;

public class OdrlPapClient extends BaseClient {

    private static final String SERVICE_PATH = "service";
    private static final String POLICY_PATH = "policy";


    public OdrlPapClient(Monitor monitor, OkHttpClient okHttpClient, String baseUrl, ObjectMapper objectMapper) {
        super(monitor, okHttpClient, baseUrl, objectMapper);
    }

    public PolicyPathVO createService(ServiceCreateVO serviceCreate) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        urlBuilder.addPathSegment(SERVICE_PATH);
        RequestBody requestBody = null;
        try {
            String br = objectMapper.writeValueAsString(serviceCreate);
            requestBody = RequestBody.create(br, JSON);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Was not able to serialize agreement.", e);
        }
        Request request = new Request.Builder().url(urlBuilder.build()).post(requestBody).build();
        try (ResponseBody responseBody = executeRequest(request).body()) {
            return objectMapper.readValue(responseBody.bytes(), PolicyPathVO.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Was not able to read agreement creation response.", e);
        }
    }

    public void createPolicy(String serviceId, Object policy) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        urlBuilder.addPathSegment(SERVICE_PATH);
        urlBuilder.addPathSegment(serviceId);
        urlBuilder.addPathSegment(POLICY_PATH);
        String policyString = "";
        try {
            policyString = objectMapper.writeValueAsString(policy);
            monitor.info("Policy " + policyString);
        } catch (JsonProcessingException e) {
            monitor.warning("Was not able to parse policy.", e);
            throw new IllegalArgumentException("Was not able to parse policy.", e);
        }
        HttpUrl url = urlBuilder.build();
        monitor.info("Create policy " + url.url());
        Request request = new Request.Builder().url(url).post(RequestBody.create(policyString, JSON)).build();
        Optional.ofNullable(executeRequest(request).body()).ifPresent(ResponseBody::close);
    }

    public void deleteService(String serviceId) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        urlBuilder.addPathSegment(SERVICE_PATH);
        urlBuilder.addPathSegment(serviceId);
        Optional.ofNullable(executeRequest(new Request.Builder().url(urlBuilder.build()).delete().build()).body()).ifPresent(ResponseBody::close);
    }
}
