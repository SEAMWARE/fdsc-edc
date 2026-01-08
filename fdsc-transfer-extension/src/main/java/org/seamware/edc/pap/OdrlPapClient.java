package org.seamware.edc.pap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.eclipse.edc.spi.monitor.Monitor;
import org.seamware.credentials.model.ServiceVO;
import org.seamware.edc.BaseClient;

import java.util.Optional;

public class OdrlPapClient extends BaseClient {

    private static final String SERVICE_PATH = "service";

    public OdrlPapClient(Monitor monitor, OkHttpClient okHttpClient, String baseUrl, ObjectMapper objectMapper) {
        super(monitor, okHttpClient, baseUrl, objectMapper);
    }

    public void createService(ServiceVO serviceVO) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        urlBuilder.addPathSegment(SERVICE_PATH);
        String serviceString = "";
        try {
            serviceString = objectMapper.writeValueAsString(serviceVO);
        } catch (JsonProcessingException e) {
            monitor.warning("Was not able to parse service.", e);
            throw new IllegalArgumentException("Was not able to parse service.", e);
        }

        Request request = new Request.Builder().url(urlBuilder.build()).post(RequestBody.create(serviceString, JSON)).build();
        Optional.ofNullable(executeRequest(request).body()).ifPresent(ResponseBody::close);
    }

    public void deleteService(String serviceId) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        urlBuilder.addPathSegment(SERVICE_PATH);
        urlBuilder.addPathSegment(serviceId);
        Optional.ofNullable(executeRequest(new Request.Builder().url(urlBuilder.build()).delete().build()).body()).ifPresent(ResponseBody::close);
    }
}
