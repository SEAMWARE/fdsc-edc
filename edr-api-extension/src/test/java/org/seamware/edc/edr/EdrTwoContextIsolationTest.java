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

import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.seamware.edc.edr.transform.JsonObjectFromEndpointDataReferenceEntryTransformer.EDR_ENTRY_PARTICIPANT_CONTEXT_ID;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.eclipse.edc.api.auth.spi.ManagementApiScopes;
import org.eclipse.edc.api.auth.spi.ParticipantPrincipal;
import org.eclipse.edc.api.authorization.service.AuthorizationServiceImpl;
import org.eclipse.edc.connector.controlplane.edr.store.receiver.EndpointDataReferenceStoreReceiver;
import org.eclipse.edc.connector.controlplane.policy.spi.store.PolicyArchive;
import org.eclipse.edc.connector.controlplane.services.spi.contractagreement.ContractAgreementService;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessEvent;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessStarted;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessTerminated;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.edr.spi.store.EndpointDataReferenceStore;
import org.eclipse.edc.edr.spi.types.EndpointDataReferenceEntry;
import org.eclipse.edc.edr.store.EndpointDataReferenceStoreImpl;
import org.eclipse.edc.edr.store.defaults.InMemoryEndpointDataReferenceEntryIndex;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.query.CriterionOperatorRegistryImpl;
import org.eclipse.edc.spi.event.EventEnvelope;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.eclipse.edc.transform.TypeTransformerRegistryImpl;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.eclipse.edc.transform.spi.TypeTransformer;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.eclipse.edc.web.spi.exception.NotAuthorizedException;
import org.eclipse.edc.web.spi.exception.ObjectNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.seamware.edc.edr.transform.JsonObjectFromEndpointDataReferenceEntryTransformer;

/**
 * Ticket acceptance criterion in-process: CONSUMER transfer events of two participant contexts flow
 * through EDC's real EDR receiver and store, and the v5beta controller only hands each context its
 * own EDRs. Status codes are those EDC's exception mapper assigns to the thrown exceptions.
 */
class EdrTwoContextIsolationTest {

  private static final String CONTEXT_A = "dataspace-a";
  private static final String CONTEXT_B = "dataspace-b";
  private static final String TP_A = "tp-a";
  private static final String TP_B = "tp-b";
  private static final Set<String> KNOWN_CONTEXTS = Set.of(CONTEXT_A, CONTEXT_B);
  private static final String ENDPOINT = EDC_NAMESPACE + "endpoint";
  private static final String AUTHORIZATION = EDC_NAMESPACE + "authorization";
  private static final String ENDPOINT_TYPE = EDC_NAMESPACE + "endpointType";
  private static final String HTTP_ENDPOINT_TYPE = "https://w3id.org/idsa/v4.1/HTTP";
  private static final String PROVIDER_ID = "did:web:provider";
  private static final String DSP_PROTOCOL = "dataspace-protocol-http:2025-1";
  private static final boolean AUTO_REFRESH = true;
  private static final int OK = Status.OK.getStatusCode();
  private static final int FORBIDDEN = Status.FORBIDDEN.getStatusCode();
  private static final int NOT_FOUND = Status.NOT_FOUND.getStatusCode();

  private EndpointDataReferenceStoreReceiver receiver;
  private EdrApiV5Controller controller;

  @BeforeEach
  void setUp() {
    EndpointDataReferenceStore store =
        new EndpointDataReferenceStoreImpl(
            new InMemoryEndpointDataReferenceEntryIndex(CriterionOperatorRegistryImpl.ofDefaults()),
            new InMemoryEndpointDataReferenceCache(),
            new NoopTransactionContext());

    PolicyArchive policyArchive = mock();
    when(policyArchive.findPolicyForContract(anyString()))
        .thenReturn(Policy.Builder.newInstance().assigner(PROVIDER_ID).build());
    receiver =
        new EndpointDataReferenceStoreReceiver(
            store,
            policyArchive,
            mock(ContractAgreementService.class),
            new NoopTransactionContext(),
            mock(Monitor.class));

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

    var jsonFactory = Json.createBuilderFactory(Map.of());
    var transformerRegistry = new TypeTransformerRegistryImpl(mock(Monitor.class));
    transformerRegistry.register(
        new JsonObjectFromEndpointDataReferenceEntryTransformer(jsonFactory));
    transformerRegistry.register(new StringPropertiesDataAddressTransformer(jsonFactory));

    controller =
        new EdrApiV5Controller(
            store,
            transformerRegistry,
            mock(JsonObjectValidatorRegistry.class),
            authorizationService,
            new NoopEdrTokenRefresher(),
            mock(Monitor.class));

    publish(started(CONTEXT_A, TP_A));
    publish(started(CONTEXT_B, TP_B));
  }

  static Stream<Arguments> dataAddressAccess() {
    var read = EdrApiScopes.READ;
    var admin = ManagementApiScopes.ADMIN;
    return Stream.of(
        Arguments.of(CONTEXT_A, CONTEXT_A, read, TP_A, OK),
        Arguments.of(CONTEXT_B, CONTEXT_B, read, TP_B, OK),
        Arguments.of(CONTEXT_B, CONTEXT_A, read, TP_A, FORBIDDEN),
        Arguments.of(CONTEXT_A, CONTEXT_B, read, TP_B, FORBIDDEN),
        Arguments.of(CONTEXT_B, CONTEXT_B, read, TP_A, NOT_FOUND),
        Arguments.of(CONTEXT_A, CONTEXT_A, read, TP_B, NOT_FOUND),
        Arguments.of(CONTEXT_B, CONTEXT_A, admin, TP_A, OK));
  }

  @ParameterizedTest(name = "sub={0} path={1} scope={2} tp={3} -> {4}")
  @MethodSource("dataAddressAccess")
  void getDataAddress_onlyOwnContextUnlessAdmin(
      String caller, String pathContext, String scope, String transferProcessId, int expected) {
    var securityContext = securityContext(caller, scope);

    assertEquals(
        expected,
        status(
            () ->
                controller.getEdrDataAddressV5(
                    pathContext, transferProcessId, AUTO_REFRESH, securityContext)));
  }

  @ParameterizedTest(name = "{0} reads its own EDR {1}")
  @MethodSource("owners")
  void getDataAddress_returnsEndpointAndAuthorizationFromStartedEvent(
      String context, String transferProcessId) {
    var dataAddress =
        controller.getEdrDataAddressV5(
            context, transferProcessId, AUTO_REFRESH, securityContext(context, EdrApiScopes.READ));

    assertEquals(endpointOf(transferProcessId), dataAddress.getString(ENDPOINT));
    assertEquals(tokenOf(transferProcessId), dataAddress.getString(AUTHORIZATION));
    assertEquals(HTTP_ENDPOINT_TYPE, dataAddress.getString(ENDPOINT_TYPE));
  }

  @ParameterizedTest(name = "{0} lists only {1}")
  @MethodSource("owners")
  void query_listsOnlyCallersOwnEntries(String context, String transferProcessId) {
    JsonArray result =
        controller.queryEdrsV5(context, null, securityContext(context, EdrApiScopes.READ));

    assertEquals(
        List.of(transferProcessId),
        result.stream().map(json -> json.asJsonObject().getString(ID)).toList());
    assertEquals(context, result.getJsonObject(0).getString(EDR_ENTRY_PARTICIPANT_CONTEXT_ID));
  }

  @Test
  void terminatedTransfer_removesOnlyItsEntry() {
    var securityContextA = securityContext(CONTEXT_A, EdrApiScopes.READ);
    var securityContextB = securityContext(CONTEXT_B, EdrApiScopes.READ);

    publish(event(TransferProcessTerminated.Builder.newInstance(), CONTEXT_A, TP_A).build());

    assertEquals(
        NOT_FOUND,
        status(
            () -> controller.getEdrDataAddressV5(CONTEXT_A, TP_A, AUTO_REFRESH, securityContextA)));
    assertEquals(0, controller.queryEdrsV5(CONTEXT_A, null, securityContextA).size());
    assertEquals(
        OK,
        status(
            () -> controller.getEdrDataAddressV5(CONTEXT_B, TP_B, AUTO_REFRESH, securityContextB)));
  }

  static Stream<Arguments> owners() {
    return Stream.of(Arguments.of(CONTEXT_A, TP_A), Arguments.of(CONTEXT_B, TP_B));
  }

  /** Mirrors EDC's {@code EdcApiExceptionMapper} for the exceptions the controller throws. */
  private static int status(Supplier<?> call) {
    try {
      assertNotNull(call.get());
      return OK;
    } catch (NotAuthorizedException e) {
      return FORBIDDEN;
    } catch (ObjectNotFoundException e) {
      return NOT_FOUND;
    }
  }

  private void publish(TransferProcessEvent event) {
    receiver.on(
        EventEnvelope.Builder.newInstance().at(Clock.systemUTC().millis()).payload(event).build());
  }

  private static TransferProcessStarted started(String context, String transferProcessId) {
    var dataAddress =
        DataAddress.Builder.newInstance()
            .type(HTTP_ENDPOINT_TYPE)
            .property(ENDPOINT, endpointOf(transferProcessId))
            .property(AUTHORIZATION, tokenOf(transferProcessId))
            .property(ENDPOINT_TYPE, HTTP_ENDPOINT_TYPE)
            .build();
    return event(TransferProcessStarted.Builder.newInstance(), context, transferProcessId)
        .dataAddress(dataAddress)
        .build();
  }

  private static <T extends TransferProcessEvent, B extends TransferProcessEvent.Builder<T, B>>
      B event(B builder, String context, String transferProcessId) {
    return builder
        .transferProcessId(transferProcessId)
        .participantContextId(context)
        .type(TransferProcess.Type.CONSUMER.name())
        .assetId("asset-" + transferProcessId)
        .contractId("agreement-" + transferProcessId)
        .protocol(DSP_PROTOCOL);
  }

  private static String endpointOf(String transferProcessId) {
    return "https://provider.example/" + transferProcessId;
  }

  private static String tokenOf(String transferProcessId) {
    return "token-" + transferProcessId;
  }

  private static SecurityContext securityContext(String subject, String scope) {
    SecurityContext securityContext = mock();
    when(securityContext.getUserPrincipal()).thenReturn(new ParticipantPrincipal(subject, scope));
    return securityContext;
  }

  /**
   * Stand-in for EDC's DataAddress transformer, which needs a JSON-P-enabled ObjectMapper absent
   * from this module's test classpath; string properties are all an EDR carries.
   */
  private record StringPropertiesDataAddressTransformer(JsonBuilderFactory jsonFactory)
      implements TypeTransformer<DataAddress, JsonObject> {

    @Override
    public Class<DataAddress> getInputType() {
      return DataAddress.class;
    }

    @Override
    public Class<JsonObject> getOutputType() {
      return JsonObject.class;
    }

    @Override
    public JsonObject transform(DataAddress dataAddress, TransformerContext context) {
      var builder = jsonFactory.createObjectBuilder();
      dataAddress
          .getProperties()
          .forEach(
              (key, value) -> {
                if (value instanceof String string) {
                  builder.add(key, string);
                }
              });
      return builder.build();
    }
  }
}
