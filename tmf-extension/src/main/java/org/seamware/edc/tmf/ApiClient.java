package org.seamware.edc.tmf;

import okhttp3.*;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.web.spi.exception.BadGatewayException;

import java.io.IOException;

public abstract class ApiClient {


    protected static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");
    protected static final String OFFSET_PARAM = "offset";
    protected static final String LIMIT_PARAM = "limit";


    protected final Monitor monitor;
    protected final OkHttpClient okHttpClient;

    protected ApiClient(Monitor monitor, OkHttpClient okHttpClient) {
        this.monitor = monitor;
        this.okHttpClient = okHttpClient;
    }

    protected ResponseBody executeRequest(Request request) {
        try {
            Response response = okHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                return response.body();
            } else {
                monitor.warning(String.format("Was not able to get as successful response for %s. Was: %s", request.url(), response.code()));
                throw new BadGatewayException(String.format("Was not able to get as successful response for %s. Was: %s", request.url(), response.code()));
            }
        } catch (IOException e) {
            monitor.warning(String.format("Was not able to get response for %s", request.url()), e);
            throw new BadGatewayException(String.format("Was not able to get response for %s", request.url()));
        }
    }
}
