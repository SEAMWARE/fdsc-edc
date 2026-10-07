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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.eclipse.edc.spi.monitor.Monitor;

/**
 * Registers this connector as listener of the TMForum product catalog events (TMF620 hub). TMForum
 * answers 409 when the same callback and query are already registered, so registering on every
 * start is idempotent and several replicas can share the callback.
 */
public class TmfHubSubscriber {

  static final List<String> EVENT_TYPES =
      List.of(
          "ProductSpecificationCreateEvent",
          "ProductSpecificationAttributeValueChangeEvent",
          "ProductSpecificationStateChangeEvent",
          "ProductOfferingCreateEvent",
          "ProductOfferingAttributeValueChangeEvent",
          "ProductOfferingStateChangeEvent");

  private static final MediaType JSON = MediaType.get("application/json");

  private final OkHttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final Monitor monitor;
  private final ScheduledExecutorService executor;
  private final String hubUrl;
  private final String callbackUrl;
  private final long retryIntervalSeconds;

  public TmfHubSubscriber(
      OkHttpClient httpClient,
      ObjectMapper objectMapper,
      Monitor monitor,
      ScheduledExecutorService executor,
      String productCatalogApi,
      String callbackUrl,
      long retryIntervalSeconds) {
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
    this.monitor = monitor;
    this.executor = executor;
    this.hubUrl = productCatalogApi.replaceAll("/+$", "") + "/hub";
    this.callbackUrl = callbackUrl;
    this.retryIntervalSeconds = retryIntervalSeconds;
  }

  public void start() {
    EVENT_TYPES.forEach(eventType -> executor.execute(() -> subscribe(eventType)));
  }

  public void stop() {
    executor.shutdownNow();
  }

  void subscribe(String eventType) {
    try {
      var body =
          objectMapper.writeValueAsString(
              Map.of("callback", callbackUrl, "query", "eventType=" + eventType));
      var request = new Request.Builder().url(hubUrl).post(RequestBody.create(body, JSON)).build();
      try (var response = httpClient.newCall(request).execute()) {
        switch (response.code()) {
          case 201 -> monitor.info("Otel Audit: subscribed to TMForum " + eventType);
          case 409 ->
              monitor.info("Otel Audit: TMForum subscription already exists for " + eventType);
          default -> retry(eventType, "TMForum answered " + response.code(), null);
        }
      }
    } catch (IOException | RuntimeException e) {
      retry(eventType, e.getMessage(), e);
    }
  }

  private void retry(String eventType, String reason, Throwable cause) {
    var message =
        String.format(
            "Otel Audit: could not subscribe to TMForum %s (%s), retrying in %ss",
            eventType, reason, retryIntervalSeconds);
    if (cause == null) {
      monitor.warning(message);
    } else {
      monitor.warning(message, cause);
    }
    if (!executor.isShutdown()) {
      executor.schedule(() -> subscribe(eventType), retryIntervalSeconds, TimeUnit.SECONDS);
    }
  }
}
