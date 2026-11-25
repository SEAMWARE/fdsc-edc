# Implementation Plan: controlplane-dcp 2.0.0: expose EDRs through the participant-context management API (v5beta)

## Overview
`controlplane-dcp` 2.x runs EDC 0.18.1 in virtual participant-context mode and serves only management API v5beta. EDC 0.18.1 has no v5 EDR resource: `EdrCacheApiV3Controller` is v3 only and is not in the virtual BOM. So a consumer cannot read the EDR of a STARTED transfer. This plan adds a new `edr-api-extension` module with a context-scoped `/v5beta/participants/{contextId}/edrs` resource that uses the v5beta OAuth2 scope and ownership checks, wires it into `controlplane-dcp`, and adds conditional token refresh, tests, docs and verify-script coverage. It ships as a `minor` release (2.1.0).

### Verified facts about EDC 0.18.1 (sources from Maven Central)
- `org.eclipse.edc.edr.spi.types.EndpointDataReferenceEntry` (`edr-store-spi`) extends `AbstractParticipantResource`, so it **has `participantContextId`**. Its other fields are `assetId`, `agreementId`, `transferProcessId`, `contractNegotiationId` and `providerId`. `id` is always the `transferProcessId`.
- `EndpointDataReferenceStore` (`org.eclipse.edc.edr.spi.store`) has these methods: `resolveByTransferProcess(tpId)` returning `StoreResult<DataAddress>`, `findById(tpId)`, `query(QuerySpec)`, `delete(tpId)` and `save(entry, dataAddress)`.
  - `edr-store-core` provides `EndpointDataReferenceStoreImpl`. Its defaults are `InMemoryEndpointDataReferenceEntryIndex`, which uses `ReflectionBasedQueryResolver`, and `VaultEndpointDataReferenceCache`, which stores DataAddresses under the vault key `edr--<tpId>`.
- `edr-store-receiver` (`EndpointDataReferenceStoreReceiver`) handles CONSUMER `TransferProcessStarted` events. It saves an entry with `participantContextId(event.getParticipantContextId())`, and deletes the entry on Terminated, Suspended or Completed. **Per-context partitioning is therefore already in the data. The new API only has to enforce it.**
- Pattern of a v5 controller, e.g. `AssetApiV5Controller`:
  - Class annotation `@Path("/v5beta/participants/{participantContextId}/assets")`.
  - Each method carries `@RequiredScope("management-api:<resource>:read|write")` and calls `authorizationService.authorize(securityContext, participantContextId, id, X.class)`.
  - Queries add `Criterion("participantContextId","=",ctx)` to the QuerySpec.
  - The QuerySpec body is annotated `@SchemaType(EDC_QUERY_SPEC_TYPE_TERM)`.
  - Registration: `webService.registerResource(ApiContext.MANAGEMENT, controller)` + `webService.registerDynamicResource(MANAGEMENT, Controller.class, new JerseyJsonLdInterceptor(jsonLd, typeManager, JSON_LD, ManagementApi.MANAGEMENT_SCOPE_V5, validatorRegistry, ManagementApiJsonSchema.V5.version()))`.
  - The transformer context is `transformerRegistry.forContext("management-api")`.
- `org.eclipse.edc.api.auth.spi.AuthorizationService` resolves ownership through per-class lookup functions:
  - A class with no registered lookup gets **401/403**.
  - A lookup that returns `null` gives 404.
  - The admin scope always passes.
  - Otherwise the call passes only if `sub` == owner == `resource.getParticipantContextId()`.
  - **The new extension must call `authorizationService.addLookupFunction(EndpointDataReferenceEntry.class, ...)`.**
- Scopes follow `prefix[:resource]:action`. `management-api:read` (no resource part means `*`) satisfies `management-api:edrs:read`, and admin ≥ write ≥ read (`ManagementApiScopes.READ/WRITE/ADMIN`).
- Upstream has **no** EDR refresh: no `auto_refresh` and no `EdrService`. Those come from Tractus-X. The FDSC provider data plane (APISIX + JWT from `FDSCDcpEndpointDataReferenceService`) issues no refresh token either. So refresh has to be written here, and it can only work against data planes that put refresh metadata into the DataAddress.
- Neither `edr-store-core` nor `edr-store-receiver` is in `controlplane-virtual-base-bom`. `controlplane-dcp/pom.xml` already declares both (and `edr-store-spi`) explicitly.
  - Upstream's v3 `JsonObjectFromEndpointDataReferenceEntryTransformer` lives in `edr-cache-api`, which is not on the classpath and does not output `participantContextId`. A new transformer is needed.

## Steps

### Step 1: Add `edr-api-extension` module with ownership lookup and partition tests
Create the new Maven module `edr-api-extension/`. Its package is `org.seamware.edc.edr`.
- `pom.xml` (parent `fdsc-edc`). Dependencies, scope `provided` where the existing extensions do the same:
  - `edr-store-spi`, `connector-participant-context-spi`, `web-spi`, `json-ld-spi`, `transform-spi`, `validator-spi`, plus the module that holds `org.eclipse.edc.api.auth.spi` (`AuthorizationService`, `RequiredScope`, `ManagementApiScopes`). Confirm the artifactId from the 0.18.1 sources; `auth-spi` or `management-api-authorization-spi` are likely.
  - Test dependencies: JUnit 5, `junit-jupiter-params`, Mockito, and `edr-store-core` for the real in-memory index.
- Add `<module>edr-api-extension</module>` to the root `pom.xml` before the `controlplane-*` modules.
- Add a dependency on `edr-api-extension` in `controlplane-dcp/pom.xml`, next to `participant-bootstrap-extension`. Do **not** add it to `controlplane-oid4vc` or `controlplane-tck`, which are classic mode.
- `EdrManagementApiV5Extension implements ServiceExtension`:
  - Register it in `META-INF/services/org.eclipse.edc.spi.system.ServiceExtension` with `@Extension(NAME)`.
  - In this step it only injects `EndpointDataReferenceStore` and `AuthorizationService`.
  - It registers `authorizationService.addLookupFunction(EndpointDataReferenceEntry.class, this::findEntry)`. `findEntry(ownerId, tpId)` queries the store with `filter(participantContextId = ownerId)` and `filter(id = tpId)`, and returns the entry or `null`. This mirrors `AssetApiV5Extension#findAsset`.
- Tests:
  - `EdrOwnershipLookupTest`, parameterized with `@MethodSource`. It saves entries for `dataspace-a`/`dataspace-b` into a real `InMemoryEndpointDataReferenceEntryIndex` and a real `EndpointDataReferenceStoreImpl` (in-memory cache stub).
  - It asserts that the lookup returns the entry for its own context and `null` for a foreign or unknown id.
  - It asserts that `query(QuerySpec)` with a `participantContextId` criterion returns only that context's entries. This settles the UNVERIFIED question of whether `ReflectionBasedQueryResolver` sees the inherited `participantContextId` field.
  - If the resolver does not see it, add a small filtering decorator of `EndpointDataReferenceStore` in this module and record the finding in the PR.
- Acceptance: `mvn -pl edr-api-extension -am test` and `mvn spotless:check` pass, and `mvn -pl controlplane-dcp -am package -DskipTests` builds the shaded JAR with the new extension.

### Step 2: JSON-LD transformers for EDR entries
In `edr-api-extension`:
- Add `JsonObjectFromEndpointDataReferenceEntryTransformer` (`EndpointDataReferenceEntry` → `JsonObject`).
  - `@type` is `EDC_NAMESPACE + "EndpointDataReferenceEntry"`.
  - It outputs `@id`, `assetId`, `agreementId`, `transferProcessId`, `contractNegotiationId`, `providerId`, `participantContextId` and `createdAt`, all in `EDC_NAMESPACE`.
  - Declare the property names as constants, or reuse `EndpointDataReferenceEntry.*_PROPERTY` constants if the SPI defines them.
  - The output must match the v3 shape, plus `participantContextId`.
- DataAddress output uses the `JsonObjectFromDataAddressTransformer` that `management-api-configuration` already registers in the `management-api` context. Verify this in a test. If it is not registered there, register `org.eclipse.edc.transform.transformer.edc.from.JsonObjectFromDataAddressTransformer` in the extension.
- Register the transformer in `EdrManagementApiV5Extension.initialize` with `transformerRegistry.forContext("management-api").register(...)`.
- Tests: `JsonObjectFromEndpointDataReferenceEntryTransformerTest` covers a full entry and an entry with a null `contractNegotiationId`. Use `@ParameterizedTest` where it fits.

### Step 3: v5beta EDR controller (query, dataaddress, delete) wired with OAuth2 scopes
In `edr-api-extension`, add `EdrApiV5Controller` with `@Path("/v5beta/participants/{participantContextId}/edrs")`, `@Consumes`/`@Produces(APPLICATION_JSON)`. Scope constants go in a holder such as `EdrApiScopes.READ = "management-api:edrs:read"` and `WRITE = "management-api:edrs:write"`.
- `POST /request` uses `@RequiredScope(READ)` and a `@SchemaType(EDC_QUERY_SPEC_TYPE_TERM) JsonObject` body; an absent or empty body means all entries.
  1. Call `authorize(sc, ctx, ctx, ParticipantContext.class)`.
  2. Validate against `EDC_QUERY_SPEC_TYPE` and transform to `QuerySpec`.
  3. Add `Criterion("participantContextId","=",ctx)`, then call `store.query(...)`.
  4. Map each entry with the Step 2 transformer and return a `JsonArray`.
- `GET /{transferProcessId}/dataaddress` uses `@RequiredScope(READ)`.
  1. Call `authorize(sc, ctx, tpId, EndpointDataReferenceEntry.class)`. This maps 403 for a foreign context and 404 for an unknown entry through `exceptionMapper(EndpointDataReferenceEntry.class, tpId)`, as upstream does.
  2. Call `store.resolveByTransferProcess(tpId)` and transform the `DataAddress` to a `JsonObject`. Keep the v3 shape: `endpoint`, `authorization`, `endpointType`, plus any other properties.
- `DELETE /{transferProcessId}` uses `@RequiredScope(WRITE)`, calls `authorize(...)` as above, then `store.delete(tpId)` and returns 204.
- Map `StoreResult` failures to `ObjectNotFoundException`/`InvalidRequestException` the way other v5 controllers do (`org.eclipse.edc.web.spi.exception.*`).
- In `EdrManagementApiV5Extension`, inject `WebService`, `TypeTransformerRegistry`, `JsonObjectValidatorRegistry`, `JsonLd` and `TypeManager`. Register the controller with `webService.registerResource(ApiContext.MANAGEMENT, ...)` and the `JerseyJsonLdInterceptor` with `MANAGEMENT_SCOPE_V5` / `ManagementApiJsonSchema.V5.version()`, following the `AssetApiV5Extension` pattern. This may add `api-lib`/`management-api-lib` dependencies.
- Tests:
  - `EdrApiV5ControllerTest` (Mockito) covers success and the 404/403 paths for each endpoint.
  - A parameterized authorization matrix (`@MethodSource`) uses the real `AuthorizationServiceImpl` from `management-api-authorization` (test scope) with a `SecurityContext` whose `ParticipantPrincipal` has a given `sub` and scope. Cases:
    - own context with read: 200
    - foreign context without admin: 403
    - foreign context with admin: 200
    - unknown tpId: 404
  - Assert the `@RequiredScope` values on each method by reflection: read for query/GET, write for DELETE.
- Acceptance: unit tests and spotless pass. The image builds. In the multi-dataspace compose stack, `POST .../v5beta/participants/dataspace-a/edrs/request` with an admin token returns `200 []`.

### Step 4: Refresh expired EDR tokens when the data plane supports it
Upstream EDC 0.18.1 has no refresh, and the FDSC/APISIX provider data plane issues no refresh tokens. So refresh is opt-in per DataAddress and must not affect the FDSC path. In `edr-api-extension`:
- Add an `EdrTokenRefresher` interface, with `HttpEdrTokenRefresher` as the default implementation, registered as `@Provider(isDefault = true)` so a deployment can replace it.
  - It refreshes only when the DataAddress carries `refreshEndpoint` **and** `refreshToken` (EDC namespace property names, as constants) **and** the access token is expired.
  - Expiry comes from `expiresIn` (seconds) relative to the entry's `createdAt`, or from the JWT `exp` claim when the token parses as a JWT. Apply a configurable leeway (`edc.edr.refresh.leeway.seconds`, default constant, read in a small config record).
  - The refresh is an RFC 6749 §6 `refresh_token` grant: POST a form to `refreshEndpoint`, using the runtime's `EdcHttpClient`. On success, build a DataAddress with the new `access_token` → `authorization`, `refresh_token` and `expires_in`, and persist it with `store.save(entry, newAddress)`.
  - On failure, return the stored DataAddress unchanged and log a warning through `Monitor`.
- `GET .../dataaddress` gets `@QueryParam("auto_refresh") @DefaultValue("true") boolean autoRefresh`. When the flag is true, it calls the refresher before returning.
- Tests: `HttpEdrTokenRefresherTest`, parameterized over these cases:
  - no refresh metadata: unchanged, no HTTP call
  - not expired: unchanged
  - expired with a 200 response: new token, `save` called
  - expired with a 4xx/5xx response: unchanged
  - The HTTP endpoint is a mock (OkHttp `MockWebServer` or a Mockito'd `EdcHttpClient`).
  - Add a controller test that `auto_refresh=false` skips the refresher.

### Step 5: In-process two-context EDR isolation test
In `edr-api-extension/src/test`, add `EdrTwoContextIsolationTest`, which reproduces the ticket's acceptance criterion without a wallet or APISIX.
- Wire real objects:
  - the real `EndpointDataReferenceStoreReceiver` (from `edr-store-receiver`, test scope) feeding a real `EndpointDataReferenceStoreImpl` (in-memory index plus an in-memory map cache);
  - the real `AuthorizationServiceImpl` with the Step 1 lookup;
  - the `EdrApiV5Controller`.
- Publish CONSUMER `TransferProcessStarted` events for `dataspace-a` and `dataspace-b`. Each event's DataAddress has `endpoint`, `authorization` and `endpointType`; the receiver's `ContractAgreementService`/`PolicyArchive` collaborators are mocked.
- Assert:
  - The context-A principal gets A's `endpoint` and `authorization` from `GET .../edrs/<tpA>/dataaddress`.
  - The context-B principal without admin gets 403 for the same call.
  - `POST .../edrs/request` lists only the caller's own entries.
  - After a `TransferProcessTerminated` event, the entry is gone (404).
- Use `@ParameterizedTest` across the (caller, target context, scope) → status table.
- Acceptance: `mvn -pl edr-api-extension test` is green.

### Step 6: Docs, verify script, release notes
- `docs/multi-dataspace.md`, "Managing it: the management API":
  - Add the three `edrs` paths to the path listing.
  - Add a short subsection covering:
    - the required scopes (`management-api:read` for query/GET, `:write` for DELETE, `:admin` for foreign contexts);
    - partitioning: entries carry the transfer's `participantContextId` and are only visible to that context;
    - the `dataaddress` response shape (`endpoint`, `authorization`, `endpointType`);
    - `auto_refresh` semantics: only for data planes that return `refreshEndpoint`/`refreshToken`, and a no-op for FDSC/APISIX EDRs;
    - a curl example.
  - Remove "EDR retrieval" from any out-of-scope wording.
- `docs/multi-dataspace-design.md`: add ADR-14, "EDRs exposed per participant context through v5beta". It records that there is no upstream v5 resource, the reuse of the receiver-stamped `participantContextId`, the `AuthorizationService` lookup, and refresh as opt-in.
- `scripts/verify-multi-dataspace.sh`: add `verify_edrs()`. Update the header comment so EDR retrieval is in scope for auth and isolation, while the end-to-end STARTED path is covered by `EdrTwoContextIsolationTest` and the FIWARE Data Space Connector ITs (`dsp_dcp_flow`, `dsp_advanced_policy` Scenario C). Use the existing `expect_status`/`token` helpers and named HTTP constants. Checks:
  - admin `POST .../{A,B}/edrs/request {}`: 200, a JSON array;
  - non-admin B `POST .../B/edrs/request`: 200;
  - non-admin B `POST .../A/edrs/request`: 403;
  - non-admin B `GET .../A/edrs/<unknown>/dataaddress`: 403 or 404, accepting both;
  - non-admin B `GET .../B/edrs/<unknown>/dataaddress`: 404;
  - non-admin B with only read scope `DELETE .../B/edrs/<unknown>`: 403;
  - no token: 401.
- Run the compose stack (`docker compose -f docker-compose.multi-dataspace.yml up -d --build`, then `run --rm verify`). Record the result in the PR.
- `releases/notes/RELEASE_NOTES_EDR_API_V5.md` (new) and a row in `releases/NOTES.md`. Describe the new endpoints, that the change is non-breaking, and that the PR label is `minor` so it releases as **2.1.0** to `quay.io/seamware/fdsc-edc-controlplane-dcp`.
- `AGENTS.md`: add `edr-api-extension/` to the project structure, with its key classes.
- `README.md`: one line under the DCP controlplane module list, if a module list exists there.

