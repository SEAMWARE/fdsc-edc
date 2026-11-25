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

import static jakarta.json.stream.JsonCollectors.toJsonArray;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.eclipse.edc.spi.query.QuerySpec.EDC_QUERY_SPEC_TYPE;
import static org.eclipse.edc.spi.query.QuerySpec.EDC_QUERY_SPEC_TYPE_TERM;
import static org.eclipse.edc.web.spi.exception.ServiceResultHandler.exceptionMapper;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.edc.api.auth.spi.AuthorizationService;
import org.eclipse.edc.api.auth.spi.RequiredScope;
import org.eclipse.edc.edr.spi.store.EndpointDataReferenceStore;
import org.eclipse.edc.edr.spi.types.EndpointDataReferenceEntry;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.eclipse.edc.web.spi.exception.InvalidRequestException;
import org.eclipse.edc.web.spi.exception.ValidationFailureException;
import org.eclipse.edc.web.spi.validation.SchemaType;

/**
 * Participant-context scoped EDR resource of management API v5beta. Every call is authorized
 * against the path's context, so a caller only sees EDRs of contexts its token owns (or all with
 * the admin scope).
 */
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Path(EdrApiV5Controller.BASE_PATH)
public class EdrApiV5Controller {

  static final String BASE_PATH = "/v5beta/participants/{participantContextId}/edrs";
  static final String PARTICIPANT_CONTEXT_ID_PARAM = "participantContextId";
  static final String TRANSFER_PROCESS_ID_PARAM = "transferProcessId";

  private final EndpointDataReferenceStore store;
  private final TypeTransformerRegistry transformerRegistry;
  private final JsonObjectValidatorRegistry validatorRegistry;
  private final AuthorizationService authorizationService;
  private final Monitor monitor;

  /** Expects the {@code management-api} transformer context. */
  public EdrApiV5Controller(
      EndpointDataReferenceStore store,
      TypeTransformerRegistry managementApiTransformerRegistry,
      JsonObjectValidatorRegistry validatorRegistry,
      AuthorizationService authorizationService,
      Monitor monitor) {
    this.store = store;
    this.transformerRegistry = managementApiTransformerRegistry;
    this.validatorRegistry = validatorRegistry;
    this.authorizationService = authorizationService;
    this.monitor = monitor;
  }

  /** Lists the context's EDR entries; an absent or empty body returns all of them. */
  @POST
  @Path("/request")
  @RequiredScope(EdrApiScopes.READ)
  public JsonArray queryEdrsV5(
      @PathParam(PARTICIPANT_CONTEXT_ID_PARAM) String participantContextId,
      @SchemaType(EDC_QUERY_SPEC_TYPE_TERM) JsonObject querySpecJson,
      @Context SecurityContext securityContext) {
    authorizationService
        .authorize(
            securityContext, participantContextId, participantContextId, ParticipantContext.class)
        .orElseThrow(exceptionMapper(ParticipantContext.class, participantContextId));

    var query =
        toQuerySpec(querySpecJson).toBuilder()
            .filter(
                Criterion.criterion(
                    EdrOwnershipLookup.PARTICIPANT_CONTEXT_ID_PROPERTY,
                    EdrOwnershipLookup.EQUALS_OPERATOR,
                    participantContextId))
            .build();

    return ServiceResult.from(store.query(query))
        .orElseThrow(exceptionMapper(QuerySpec.class, null))
        .stream()
        .map(entry -> transformerRegistry.transform(entry, JsonObject.class))
        .peek(result -> result.onFailure(failure -> monitor.warning(failure.getFailureDetail())))
        .filter(Result::succeeded)
        .map(Result::getContent)
        .collect(toJsonArray());
  }

  /** Returns the cached EDR DataAddress (endpoint, authorization, ...) of a transfer process. */
  @GET
  @Path("/{transferProcessId}/dataaddress")
  @RequiredScope(EdrApiScopes.READ)
  public JsonObject getEdrDataAddressV5(
      @PathParam(PARTICIPANT_CONTEXT_ID_PARAM) String participantContextId,
      @PathParam(TRANSFER_PROCESS_ID_PARAM) String transferProcessId,
      @Context SecurityContext securityContext) {
    authorizeEntry(securityContext, participantContextId, transferProcessId);

    var dataAddress =
        ServiceResult.from(store.resolveByTransferProcess(transferProcessId))
            .orElseThrow(exceptionMapper(EndpointDataReferenceEntry.class, transferProcessId));
    return transformerRegistry
        .transform(dataAddress, JsonObject.class)
        .orElseThrow(failure -> new EdcException(failure.getFailureDetail()));
  }

  /** Removes the EDR entry and its cached DataAddress. */
  @DELETE
  @Path("/{transferProcessId}")
  @RequiredScope(EdrApiScopes.WRITE)
  public void removeEdrV5(
      @PathParam(PARTICIPANT_CONTEXT_ID_PARAM) String participantContextId,
      @PathParam(TRANSFER_PROCESS_ID_PARAM) String transferProcessId,
      @Context SecurityContext securityContext) {
    authorizeEntry(securityContext, participantContextId, transferProcessId);

    ServiceResult.from(store.delete(transferProcessId))
        .orElseThrow(exceptionMapper(EndpointDataReferenceEntry.class, transferProcessId));
  }

  private void authorizeEntry(
      SecurityContext securityContext, String participantContextId, String transferProcessId) {
    authorizationService
        .authorize(
            securityContext,
            participantContextId,
            transferProcessId,
            EndpointDataReferenceEntry.class)
        .orElseThrow(exceptionMapper(EndpointDataReferenceEntry.class, transferProcessId));
  }

  private QuerySpec toQuerySpec(JsonObject querySpecJson) {
    if (querySpecJson == null || querySpecJson.isEmpty()) {
      return QuerySpec.Builder.newInstance().build();
    }
    validatorRegistry
        .validate(EDC_QUERY_SPEC_TYPE, querySpecJson)
        .orElseThrow(ValidationFailureException::new);
    return transformerRegistry
        .transform(querySpecJson, QuerySpec.class)
        .orElseThrow(InvalidRequestException::new);
  }
}
