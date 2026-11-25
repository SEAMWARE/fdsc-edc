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
package org.seamware.edc.edr;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.SecurityContext;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.edc.api.auth.spi.ManagementApiScopes;
import org.eclipse.edc.api.auth.spi.ParticipantPrincipal;
import org.eclipse.edc.api.auth.spi.RequiredScope;
import org.eclipse.edc.api.auth.spi.ScopeMatcher;
import org.eclipse.edc.api.authorization.service.AuthorizationServiceImpl;
import org.eclipse.edc.edr.spi.store.EndpointDataReferenceStore;
import org.eclipse.edc.edr.spi.types.EndpointDataReferenceEntry;
import org.eclipse.edc.edr.store.EndpointDataReferenceStoreImpl;
import org.eclipse.edc.edr.store.defaults.InMemoryEndpointDataReferenceEntryIndex;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.query.CriterionOperatorRegistryImpl;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.eclipse.edc.web.spi.exception.NotAuthorizedException;
import org.eclipse.edc.web.spi.exception.ObjectNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Runs the controller against EDC's real {@code AuthorizationServiceImpl} and in-memory EDR store
 * to pin the per-context access matrix and the scopes each endpoint demands.
 */
class EdrApiV5AuthorizationTest {

  private static final String CONTEXT_A = "dataspace-a";
  private static final String CONTEXT_B = "dataspace-b";
  private static final String TP_A = "tp-a";
  private static final String TP_B = "tp-b";
  private static final String UNKNOWN_TP = "tp-unknown";
  private static final String DATA_ADDRESS_TYPE = "HttpData";
  private static final String ID_KEY = "id";
  private static final String QUERY = "queryEdrsV5";
  private static final boolean AUTO_REFRESH = true;
  private static final String GET_DATA_ADDRESS = "getEdrDataAddressV5";
  private static final String DELETE = "removeEdrV5";
  private static final Set<String> KNOWN_CONTEXTS = Set.of(CONTEXT_A, CONTEXT_B);

  /** Expected result of a call; {@code exception} is null for success. */
  enum Outcome {
    OK(null),
    FORBIDDEN(NotAuthorizedException.class),
    NOT_FOUND(ObjectNotFoundException.class);

    final Class<? extends Exception> exception;

    Outcome(Class<? extends Exception> exception) {
      this.exception = exception;
    }
  }

  private EndpointDataReferenceStore store;
  private EdrApiV5Controller controller;

  @BeforeEach
  void setUp() {
    store =
        new EndpointDataReferenceStoreImpl(
            new InMemoryEndpointDataReferenceEntryIndex(CriterionOperatorRegistryImpl.ofDefaults()),
            new InMemoryEndpointDataReferenceCache(),
            new NoopTransactionContext());
    save(CONTEXT_A, TP_A);
    save(CONTEXT_B, TP_B);

    var authorizationService = new AuthorizationServiceImpl();
    authorizationService.addLookupFunction(
        EndpointDataReferenceEntry.class, new EdrOwnershipLookup(store));
    authorizationService.addLookupFunction(
        ParticipantContext.class,
        (owner, id) ->
            KNOWN_CONTEXTS.contains(id)
                ? ParticipantContext.Builder.newInstance()
                    .participantContextId(id)
                    .identity(id)
                    .build()
                : null);

    TypeTransformerRegistry transformerRegistry = mock();
    when(transformerRegistry.transform(any(), eq(JsonObject.class)))
        .thenAnswer(invocation -> Result.success(idOf(invocation.getArgument(0))));
    controller =
        new EdrApiV5Controller(
            store,
            transformerRegistry,
            mock(JsonObjectValidatorRegistry.class),
            authorizationService,
            new NoopEdrTokenRefresher(),
            mock(Monitor.class));
  }

  static Stream<Arguments> accessMatrix() {
    var read = EdrApiScopes.READ;
    var write = EdrApiScopes.WRITE;
    var admin = ManagementApiScopes.ADMIN;
    return Stream.of(
        Arguments.of(QUERY, CONTEXT_A, read, CONTEXT_A, null, Outcome.OK),
        Arguments.of(QUERY, CONTEXT_A, read, CONTEXT_B, null, Outcome.FORBIDDEN),
        Arguments.of(QUERY, CONTEXT_A, admin, CONTEXT_B, null, Outcome.OK),
        Arguments.of(GET_DATA_ADDRESS, CONTEXT_A, read, CONTEXT_A, TP_A, Outcome.OK),
        Arguments.of(GET_DATA_ADDRESS, CONTEXT_A, read, CONTEXT_B, TP_B, Outcome.FORBIDDEN),
        Arguments.of(GET_DATA_ADDRESS, CONTEXT_A, admin, CONTEXT_B, TP_B, Outcome.OK),
        Arguments.of(GET_DATA_ADDRESS, CONTEXT_A, read, CONTEXT_A, UNKNOWN_TP, Outcome.NOT_FOUND),
        Arguments.of(GET_DATA_ADDRESS, CONTEXT_A, read, CONTEXT_A, TP_B, Outcome.NOT_FOUND),
        Arguments.of(DELETE, CONTEXT_A, write, CONTEXT_A, TP_A, Outcome.OK),
        Arguments.of(DELETE, CONTEXT_A, write, CONTEXT_B, TP_B, Outcome.FORBIDDEN),
        Arguments.of(DELETE, CONTEXT_A, admin, CONTEXT_B, TP_B, Outcome.OK),
        Arguments.of(DELETE, CONTEXT_A, write, CONTEXT_A, UNKNOWN_TP, Outcome.NOT_FOUND),
        Arguments.of(DELETE, CONTEXT_A, write, CONTEXT_A, TP_B, Outcome.NOT_FOUND));
  }

  @ParameterizedTest(name = "{0}: sub={1} scope={2} path={3} tp={4} -> {5}")
  @MethodSource("accessMatrix")
  void access_isScopedToOwnContextUnlessAdmin(
      String endpoint,
      String subject,
      String scope,
      String pathContext,
      String transferProcessId,
      Outcome expected) {
    var securityContext = securityContext(subject, scope);
    Runnable call =
        switch (endpoint) {
          case QUERY -> () -> controller.queryEdrsV5(pathContext, null, securityContext);
          case GET_DATA_ADDRESS ->
              () ->
                  controller.getEdrDataAddressV5(
                      pathContext, transferProcessId, AUTO_REFRESH, securityContext);
          case DELETE ->
              () -> controller.removeEdrV5(pathContext, transferProcessId, securityContext);
          default -> throw new IllegalArgumentException(endpoint);
        };

    if (expected.exception == null) {
      assertDoesNotThrow(call::run);
    } else {
      assertThrows(expected.exception, call::run);
    }
  }

  @ParameterizedTest(name = "query by {0} sees only {1}")
  @MethodSource("queryVisibility")
  void query_returnsOnlyEntriesOfPathContext(String context, String expectedId) {
    var result = controller.queryEdrsV5(context, null, securityContext(context, EdrApiScopes.READ));

    assertEquals(
        List.of(expectedId),
        result.stream().map(json -> json.asJsonObject().getString(ID_KEY)).toList());
  }

  static Stream<Arguments> queryVisibility() {
    return Stream.of(Arguments.of(CONTEXT_A, TP_A), Arguments.of(CONTEXT_B, TP_B));
  }

  static Stream<Arguments> requiredScopes() {
    return Stream.of(
        Arguments.of(QUERY, EdrApiScopes.READ),
        Arguments.of(GET_DATA_ADDRESS, EdrApiScopes.READ),
        Arguments.of(DELETE, EdrApiScopes.WRITE));
  }

  @ParameterizedTest(name = "{0} requires {1}")
  @MethodSource("requiredScopes")
  void endpoint_declaresRequiredScope(String endpoint, String expectedScope) {
    assertEquals(expectedScope, requiredScope(endpoint));
  }

  static Stream<Arguments> grantedScopes() {
    return Stream.of(
        Arguments.of(QUERY, ManagementApiScopes.READ, true),
        Arguments.of(GET_DATA_ADDRESS, ManagementApiScopes.READ, true),
        Arguments.of(DELETE, ManagementApiScopes.READ, false),
        Arguments.of(DELETE, EdrApiScopes.READ, false),
        Arguments.of(DELETE, ManagementApiScopes.WRITE, true),
        Arguments.of(QUERY, EdrApiScopes.WRITE, true),
        Arguments.of(DELETE, ManagementApiScopes.ADMIN, true));
  }

  @ParameterizedTest(name = "{0} with granted {1}: allowed={2}")
  @MethodSource("grantedScopes")
  void requiredScope_isSatisfiedByManagementApiScopeHierarchy(
      String endpoint, String granted, boolean allowed) {
    assertEquals(allowed, new ScopeMatcher().isSatisfiedBy(requiredScope(endpoint), granted));
  }

  private static String requiredScope(String endpoint) {
    return Stream.of(EdrApiV5Controller.class.getMethods())
        .filter(method -> method.getName().equals(endpoint))
        .map(method -> method.getAnnotation(RequiredScope.class))
        .findFirst()
        .map(RequiredScope::value)
        .orElseThrow();
  }

  private static JsonObject idOf(Object source) {
    var builder = Json.createObjectBuilder();
    if (source instanceof EndpointDataReferenceEntry entry) {
      builder.add(ID_KEY, entry.getId());
    }
    return builder.build();
  }

  private static SecurityContext securityContext(String subject, String scope) {
    SecurityContext securityContext = mock();
    when(securityContext.getUserPrincipal()).thenReturn(new ParticipantPrincipal(subject, scope));
    return securityContext;
  }

  private void save(String contextId, String transferProcessId) {
    var entry =
        EndpointDataReferenceEntry.Builder.newInstance()
            .participantContextId(contextId)
            .transferProcessId(transferProcessId)
            .assetId("asset-" + transferProcessId)
            .agreementId("agreement-" + transferProcessId)
            .providerId("provider-" + contextId)
            .build();
    var dataAddress = DataAddress.Builder.newInstance().type(DATA_ADDRESS_TYPE).build();
    assertTrue(store.save(entry, dataAddress).succeeded());
  }
}
