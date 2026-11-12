/*
 * Copyright 2025 Seamless Middleware Technologies S.L and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.seamware.edc.otel.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class TmfHubSubscriberTest {

  private static final String CATALOG_API =
      "http://tmforum:8632/tmf-api/productCatalogManagement/v4/";
  private static final String CALLBACK = "http://edc:8688/tmf-events";
  private static final String EVENT_TYPE = "ProductSpecificationCreateEvent";

  private OkHttpClient httpClient;
  private Call call;
  private ScheduledExecutorService executor;
  private TmfHubSubscriber subscriber;

  @BeforeEach
  void setUp() {
    httpClient = mock(OkHttpClient.class);
    call = mock(Call.class);
    executor = mock(ScheduledExecutorService.class);
    when(httpClient.newCall(any())).thenReturn(call);
    subscriber =
        new TmfHubSubscriber(
            httpClient,
            new ObjectMapper(),
            mock(Monitor.class),
            executor,
            CATALOG_API,
            CALLBACK,
            30);
  }

  @Test
  void subscriptionIsPostedToTheHub() throws Exception {
    when(call.execute()).thenReturn(response(201));

    subscriber.subscribe(EVENT_TYPE);

    var captor = ArgumentCaptor.forClass(Request.class);
    verify(httpClient).newCall(captor.capture());
    var request = captor.getValue();
    assertEquals(
        "http://tmforum:8632/tmf-api/productCatalogManagement/v4/hub", request.url().toString());
    var buffer = new Buffer();
    request.body().writeTo(buffer);
    var body = new ObjectMapper().readValue(buffer.readUtf8(), Map.class);
    assertEquals("eventType=" + EVENT_TYPE, body.get("query"));
    assertEquals(CALLBACK, body.get("callback"));
    verifyNoInteractions(executor);
  }

  @Test
  void existingSubscriptionIsAccepted() throws Exception {
    when(call.execute()).thenReturn(response(409));

    subscriber.subscribe(EVENT_TYPE);

    verifyNoInteractions(executor);
  }

  @Test
  void rejectedSubscriptionIsRetried() throws Exception {
    when(call.execute()).thenReturn(response(500));

    subscriber.subscribe(EVENT_TYPE);

    verify(executor).schedule(any(Runnable.class), eq(30L), eq(TimeUnit.SECONDS));
  }

  @Test
  void unreachableHubIsRetried() throws Exception {
    when(call.execute()).thenThrow(new IOException("connection refused"));

    subscriber.subscribe(EVENT_TYPE);

    verify(executor).schedule(any(Runnable.class), eq(30L), eq(TimeUnit.SECONDS));
  }

  @Test
  void startSubscribesToEveryType() {
    subscriber.start();

    verify(executor, times(TmfHubSubscriber.EVENT_TYPES.size())).execute(any(Runnable.class));
  }

  private static Response response(int code) {
    return new Response.Builder()
        .request(new Request.Builder().url(CATALOG_API).build())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("")
        .body(ResponseBody.create("", MediaType.get("application/json")))
        .build();
  }
}
