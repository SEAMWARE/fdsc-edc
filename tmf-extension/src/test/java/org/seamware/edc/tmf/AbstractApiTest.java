package org.seamware.edc.tmf;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.api.BeforeEach;
import org.seamware.edc.SchemaBaseUriHolder;
import org.seamware.edc.store.UnknownPropertyMixin;

import java.net.URI;

import static org.mockito.Mockito.mock;

public abstract class AbstractApiTest {


    protected Monitor monitor;
    protected OkHttpClient okHttpClient;
    protected MockWebServer mockWebServer;
    protected ObjectMapper objectMapper;

    @BeforeEach
    public void setup() throws Exception {
        setupApiClient();
        setupConcreteClient(mockWebServer.url("").toString());
    }


    public abstract void setupConcreteClient(String baseUrl);

    protected void setupApiClient() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        SchemaBaseUriHolder.configure(URI.create("http://base.uri"));

        objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.addMixIn(Policy.Builder.class, UnknownPropertyMixin.class);
        objectMapper.registerModule(new JavaTimeModule());

        monitor = mock(Monitor.class);
        okHttpClient = new OkHttpClient();
    }

    protected void mockResponse(int statusCode) {
        mockWebServer.enqueue(
                new MockResponse()
                        .setResponseCode(statusCode)
        );
    }

    protected <T> void mockResponse(int statusCode, T response) throws Exception {
        mockWebServer.enqueue(
                new MockResponse()
                        .setResponseCode(statusCode)
                        .setBody(objectMapper.writeValueAsString(response))
        );
    }
}
