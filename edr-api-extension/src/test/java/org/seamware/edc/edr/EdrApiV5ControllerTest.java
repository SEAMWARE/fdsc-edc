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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.SecurityContext;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.eclipse.edc.api.auth.spi.AuthorizationService;
import org.eclipse.edc.edr.spi.store.EndpointDataReferenceStore;
import org.eclipse.edc.edr.spi.types.EndpointDataReferenceEntry;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.eclipse.edc.validator.spi.ValidationResult;
import org.eclipse.edc.validator.spi.Violation;
import org.eclipse.edc.web.spi.exception.NotAuthorizedException;
import org.eclipse.edc.web.spi.exception.ObjectNotFoundException;
import org.eclipse.edc.web.spi.exception.ValidationFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class EdrApiV5ControllerTest {

  private static final String CONTEXT_ID = "dataspace-a";
  private static final String TP_ID = "tp-1";
  private static final boolean AUTO_REFRESH = true;
  private static final String ASSET_FILTER_PROPERTY = "assetId";
  private static final String ASSET_ID = "asset-1";
  private static final String DATA_ADDRESS_TYPE = "HttpData";
  private static final String FAILURE_MESSAGE = "denied";
  private static final Criterion CONTEXT_CRITERION =
      Criterion.criterion(
          EdrOwnershipLookup.PARTICIPANT_CONTEXT_ID_PROPERTY,
          EdrOwnershipLookup.EQUALS_OPERATOR,
          CONTEXT_ID);

  private final EndpointDataReferenceStore store = mock();
  private final TypeTransformerRegistry transformerRegistry = mock();
  private final JsonObjectValidatorRegistry validatorRegistry = mock();
  private final AuthorizationService authorizationService = mock();
  private final EdrTokenRefresher tokenRefresher = mock();
  private final SecurityContext securityContext = mock();
  private EdrApiV5Controller controller;

  @BeforeEach
  void setUp() {
    controller =
        new EdrApiV5Controller(
            store,
            transformerRegistry,
            validatorRegistry,
            authorizationService,
            tokenRefresher,
            mock(Monitor.class));
  }

  static Stream<JsonObject> allEntriesBodies() {
    return Stream.of(null, Json.createObjectBuilder().build());
  }

  @ParameterizedTest
  @MethodSource("allEntriesBodies")
  void query_withoutQuerySpec_returnsAllEntriesOfContext(JsonObject body) {
    authorizeContext(ServiceResult.success());
    var entry = entry();
    var entryJson = Json.createObjectBuilder().add("id", TP_ID).build();
    when(store.query(any())).thenReturn(StoreResult.success(List.of(entry)));
    when(transformerRegistry.transform(entry, JsonObject.class))
        .thenReturn(Result.success(entryJson));

    var result = controller.queryEdrsV5(CONTEXT_ID, body, securityContext);

    assertEquals(List.of(entryJson), result);
    assertEquals(List.of(CONTEXT_CRITERION), capturedQuery().getFilterExpression());
    verifyNoInteractions(validatorRegistry);
  }

  @Test
  void query_withQuerySpec_keepsUserFiltersAndAddsContextFilter() {
    authorizeContext(ServiceResult.success());
    var body = Json.createObjectBuilder().add("filterExpression", "ignored").build();
    var assetFilter =
        Criterion.criterion(ASSET_FILTER_PROPERTY, EdrOwnershipLookup.EQUALS_OPERATOR, ASSET_ID);
    when(validatorRegistry.validate(QuerySpec.EDC_QUERY_SPEC_TYPE, body))
        .thenReturn(ValidationResult.success());
    when(transformerRegistry.transform(body, QuerySpec.class))
        .thenReturn(Result.success(QuerySpec.Builder.newInstance().filter(assetFilter).build()));
    when(store.query(any())).thenReturn(StoreResult.success(List.of()));

    var result = controller.queryEdrsV5(CONTEXT_ID, body, securityContext);

    assertTrue(result.isEmpty());
    assertEquals(List.of(assetFilter, CONTEXT_CRITERION), capturedQuery().getFilterExpression());
  }

  @Test
  void query_invalidQuerySpec_throwsValidationFailure() {
    authorizeContext(ServiceResult.success());
    var body = Json.createObjectBuilder().add("limit", "not-a-number").build();
    when(validatorRegistry.validate(QuerySpec.EDC_QUERY_SPEC_TYPE, body))
        .thenReturn(ValidationResult.failure(Violation.violation(FAILURE_MESSAGE, "limit")));

    assertThrows(
        ValidationFailureException.class,
        () -> controller.queryEdrsV5(CONTEXT_ID, body, securityContext));
    verify(store, never()).query(any());
  }

  @Test
  void query_foreignContext_throwsNotAuthorized() {
    authorizeContext(ServiceResult.unauthorized(FAILURE_MESSAGE));

    assertThrows(
        NotAuthorizedException.class,
        () -> controller.queryEdrsV5(CONTEXT_ID, null, securityContext));
    verifyNoInteractions(store);
  }

  @ParameterizedTest(name = "auto_refresh={0}")
  @ValueSource(booleans = {true, false})
  void getDataAddress_servesRefresherResultOnlyWhenAutoRefresh(boolean autoRefresh) {
    authorizeEntry(ServiceResult.success());
    var entry = entry();
    var cached = DataAddress.Builder.newInstance().type(DATA_ADDRESS_TYPE).build();
    var refreshed = DataAddress.Builder.newInstance().type(DATA_ADDRESS_TYPE).build();
    var served = autoRefresh ? refreshed : cached;
    var servedJson = Json.createObjectBuilder().add("type", DATA_ADDRESS_TYPE).build();
    when(store.resolveByTransferProcess(TP_ID)).thenReturn(StoreResult.success(cached));
    when(store.findById(TP_ID)).thenReturn(entry);
    when(tokenRefresher.refreshIfNeeded(entry, cached)).thenReturn(refreshed);
    when(transformerRegistry.transform(served, JsonObject.class))
        .thenReturn(Result.success(servedJson));

    var result = controller.getEdrDataAddressV5(CONTEXT_ID, TP_ID, autoRefresh, securityContext);

    assertSame(servedJson, result);
    verify(tokenRefresher, times(autoRefresh ? 1 : 0)).refreshIfNeeded(any(), any());
  }

  @Test
  void getDataAddress_entryVanishedBeforeRefresh_throwsNotFound() {
    authorizeEntry(ServiceResult.success());
    when(store.resolveByTransferProcess(TP_ID))
        .thenReturn(
            StoreResult.success(DataAddress.Builder.newInstance().type(DATA_ADDRESS_TYPE).build()));
    when(store.findById(TP_ID)).thenReturn(null);

    assertThrows(
        ObjectNotFoundException.class,
        () -> controller.getEdrDataAddressV5(CONTEXT_ID, TP_ID, AUTO_REFRESH, securityContext));
    verifyNoInteractions(tokenRefresher);
  }

  @Test
  void getDataAddress_missingCachedAddress_throwsNotFound() {
    authorizeEntry(ServiceResult.success());
    when(store.resolveByTransferProcess(TP_ID)).thenReturn(StoreResult.notFound(FAILURE_MESSAGE));

    assertThrows(
        ObjectNotFoundException.class,
        () -> controller.getEdrDataAddressV5(CONTEXT_ID, TP_ID, AUTO_REFRESH, securityContext));
  }

  @Test
  void delete_removesEntry() {
    authorizeEntry(ServiceResult.success());
    when(store.delete(TP_ID)).thenReturn(StoreResult.success(entry()));

    controller.removeEdrV5(CONTEXT_ID, TP_ID, securityContext);

    verify(store).delete(TP_ID);
  }

  @Test
  void delete_storeNotFound_throwsNotFound() {
    authorizeEntry(ServiceResult.success());
    when(store.delete(TP_ID)).thenReturn(StoreResult.notFound(FAILURE_MESSAGE));

    assertThrows(
        ObjectNotFoundException.class,
        () -> controller.removeEdrV5(CONTEXT_ID, TP_ID, securityContext));
  }

  static Stream<Arguments> entryAuthorizationFailures() {
    Consumer<EdrApiV5Controller> getDataAddress =
        c -> c.getEdrDataAddressV5(CONTEXT_ID, TP_ID, AUTO_REFRESH, null);
    Consumer<EdrApiV5Controller> delete = c -> c.removeEdrV5(CONTEXT_ID, TP_ID, null);
    return Stream.of(
        Arguments.of(
            "dataaddress",
            getDataAddress,
            ServiceResult.notFound(FAILURE_MESSAGE),
            ObjectNotFoundException.class),
        Arguments.of(
            "dataaddress",
            getDataAddress,
            ServiceResult.unauthorized(FAILURE_MESSAGE),
            NotAuthorizedException.class),
        Arguments.of(
            "delete",
            delete,
            ServiceResult.notFound(FAILURE_MESSAGE),
            ObjectNotFoundException.class),
        Arguments.of(
            "delete",
            delete,
            ServiceResult.unauthorized(FAILURE_MESSAGE),
            NotAuthorizedException.class));
  }

  @ParameterizedTest(name = "{0}: {2} -> {3}")
  @MethodSource("entryAuthorizationFailures")
  void entryEndpoints_authorizationFailure_mapsToException(
      String endpoint,
      Consumer<EdrApiV5Controller> call,
      ServiceResult<Void> authorization,
      Class<? extends Exception> expected) {
    when(authorizationService.authorize(
            any(), eq(CONTEXT_ID), eq(TP_ID), eq(EndpointDataReferenceEntry.class)))
        .thenReturn(authorization);

    assertThrows(expected, () -> call.accept(controller));
    verifyNoInteractions(store);
  }

  private void authorizeContext(ServiceResult<Void> result) {
    when(authorizationService.authorize(
            securityContext, CONTEXT_ID, CONTEXT_ID, ParticipantContext.class))
        .thenReturn(result);
  }

  private void authorizeEntry(ServiceResult<Void> result) {
    when(authorizationService.authorize(
            eq(securityContext), eq(CONTEXT_ID), anyString(), eq(EndpointDataReferenceEntry.class)))
        .thenReturn(result);
  }

  private QuerySpec capturedQuery() {
    var captor = ArgumentCaptor.forClass(QuerySpec.class);
    verify(store).query(captor.capture());
    return captor.getValue();
  }

  private static EndpointDataReferenceEntry entry() {
    return EndpointDataReferenceEntry.Builder.newInstance()
        .participantContextId(CONTEXT_ID)
        .transferProcessId(TP_ID)
        .assetId(ASSET_ID)
        .agreementId("agreement-1")
        .providerId("provider-1")
        .build();
  }
}
