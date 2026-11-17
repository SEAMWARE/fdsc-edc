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
package org.seamware.edc.dcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.eclipse.edc.participantcontext.spi.identity.ParticipantIdentityResolver;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.web.spi.exception.ObjectNotFoundException;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.seamware.edc.TransferConfig;
import org.seamware.edc.apisix.OpenidConnectPlugin;
import org.seamware.edc.store.OwnParticipantIdentity;
import org.seamware.edc.transfer.TransferMapper;

/**
 * Fetches the route's discovery URL from the controller on the Jetty/Jersey stack EDC serves it
 * with, so the context segment is proven to pass Jetty's URI compliance and JAX-RS decoding.
 */
class OidConfigControllerHttpTest {

  private static final String CONTEXT_PATH = "/api";
  private static final String LOCALHOST = "http://localhost:";
  private static final String DEFAULT_CONTEXT = "default-context";
  private static final String DEFAULT_IDENTITY = "did:web:default.example";
  private static final String OPENID_CONNECT_PLUGIN = "openid-connect";
  private static final int EPHEMERAL_PORT = 0;
  private static final int HTTP_OK = 200;
  private static final int HTTP_BAD_REQUEST = 400;
  private static final int HTTP_NOT_FOUND = 404;

  private static final Map<String, String> IDENTITIES =
      Map.of(
          "context-a",
          "did:web:a.example",
          "did:web:a.example%3A8443",
          "did:web:a.example%3A8443",
          "ctx/with space",
          "did:web:space.example",
          DEFAULT_CONTEXT,
          DEFAULT_IDENTITY);

  private static Server server;
  private static TransferConfig transferConfig;
  private static String oidHost;

  private final HttpClient httpClient = HttpClient.newHttpClient();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeAll
  static void startServer() throws Exception {
    server = new Server();
    // EDC's JettyService uses a default HttpConfiguration, i.e. UriCompliance.DEFAULT.
    ServerConnector connector =
        new ServerConnector(server, new HttpConnectionFactory(new HttpConfiguration()));
    connector.setPort(EPHEMERAL_PORT);
    server.addConnector(connector);
    ServletContextHandler handler = new ServletContextHandler(CONTEXT_PATH);
    server.setHandler(handler);
    server.start();

    oidHost = LOCALHOST + connector.getLocalPort() + CONTEXT_PATH;
    transferConfig =
        TransferConfig.Builder.newInstance()
            .apisix(new TransferConfig.Apisix("http://apisix-admin:9180", "token", null))
            .dcp(
                new TransferConfig.Dcp.Builder()
                    .enabled(true)
                    .oidConfigBuilder(new TransferConfig.OidConfig.Builder().host(oidHost))
                    .build())
            .build();
    ResourceConfig resourceConfig =
        new ResourceConfig()
            .register(JacksonFeature.class)
            .register(new NotFoundMapper())
            .register(
                new OidConfigController(
                    mock(Monitor.class),
                    transferConfig,
                    new OwnParticipantIdentity(
                        OidConfigControllerHttpTest::identityResolver,
                        DEFAULT_IDENTITY,
                        DEFAULT_CONTEXT),
                    DEFAULT_CONTEXT));
    handler.addServlet(new ServletHolder(new ServletContainer(resourceConfig)), "/*");
  }

  @AfterAll
  static void stopServer() throws Exception {
    server.stop();
  }

  private static ParticipantIdentityResolver identityResolver() {
    ParticipantIdentityResolver resolver = mock(ParticipantIdentityResolver.class);
    when(resolver.getParticipantId(any(), any()))
        .thenAnswer(invocation -> IDENTITIES.get(invocation.<String>getArgument(0)));
    return resolver;
  }

  @ParameterizedTest(name = "context {0}")
  @ValueSource(
      strings = {"context-a", "did:web:a.example%3A8443", "ctx/with space", DEFAULT_CONTEXT})
  void routeDiscoveryUrl_servesIssuerOfTransferContext(String contextId) throws Exception {
    String discovery = discoveryUrl(contextId);

    HttpResponse<String> response = get(discovery);

    assertEquals(HTTP_OK, response.statusCode(), "Discovery: " + discovery);
    assertEquals(
        IDENTITIES.get(contextId),
        objectMapper.readValue(response.body(), Map.class).get(OidConfigController.ISSUER_KEY));
  }

  @Test
  void unknownContext_isNotFound() throws Exception {
    assertEquals(HTTP_NOT_FOUND, get(discoveryUrl("unknown-context")).statusCode());
  }

  /** Guards the test itself: the percent-encoded form of the previous round must be rejected. */
  @Test
  void percentEncodedAmbiguousSegment_isRejectedByJetty() throws Exception {
    assertEquals(
        HTTP_BAD_REQUEST,
        get(oidHost + "/.well-known/openid-configuration/did%3Aweb%3Aa.example%253A8443")
            .statusCode());
  }

  private String discoveryUrl(String contextId) {
    String discovery =
        ((OpenidConnectPlugin)
                new TransferMapper(transferConfig, DEFAULT_CONTEXT)
                    .toDcpServiceRoute("transfer-1", "upstream:8080", contextId)
                    .getPlugins()
                    .get(OPENID_CONNECT_PLUGIN))
            .getDiscovery();
    assertTrue(discovery.startsWith(oidHost), discovery);
    return discovery;
  }

  private HttpResponse<String> get(String url) throws Exception {
    return httpClient.send(
        HttpRequest.newBuilder(URI.create(url)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  /**
   * Stands in for EDC's exception mapper, which answers {@link ObjectNotFoundException} with 404.
   */
  private static class NotFoundMapper implements ExceptionMapper<ObjectNotFoundException> {
    @Override
    public Response toResponse(ObjectNotFoundException exception) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
  }
}
