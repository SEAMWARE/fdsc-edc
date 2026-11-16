# Implementation Plan: Multi-Dataspace support for the FDSC-EDC

## Overview

Let one `controlplane-dcp` runtime take part in several dataspaces at once, using
EDC 0.18.1's **virtual participant-context mode** plus **dataspace profiles**. Each
dataspace becomes a participant context with its own DID, STS settings and
associated profile(s). The data still lives in the **single shared TMForum**, so
every TMForum-backed store must persist `participantContextId` and filter by it.
`controlplane-oid4vc` and `controlplane-tck` stay on classic mode, and the shared
modules must keep working in both modes. The deliverables are the code changes and
an operator guide, `docs/multi-dataspace.md`, with an example multi-dataspace
configuration.

### Scope decisions (from the ticket's analysis comment)

All points below are settled except the virtual-only DCP switch, which is
**pending maintainer confirmation**.

- Target EDC **0.18.1**, which `main` already uses, so there is no version bump.
- **Virtual mode applies only to `controlplane-dcp`.** `controlplane-oid4vc` and
  `controlplane-tck` keep `participant-context-connector-classic-core`.
  - This is a breaking change for every existing DCP deployment (management API
    v5 with OAuth2, DSP URLs gain `/{ctx}/{profile}`); Step 10 documents it and
    the PR is labelled `major`.
  - **Open: @wistefan has not yet confirmed this.** Step 8 must not start until
    he confirms it or asks for the alternative: a sibling
    `controlplane-dcp-virtual` module, leaving `controlplane-dcp` classic and the
    release non-breaking (no `major` label). Steps 1–7 work in both modes and do
    not depend on this answer.
- **One TMForum and one ODRL-PAP** serve all dataspaces.
- **DCP identity is configured per dataspace:** DID, STS, scope mappings and the
  EBSI TIR.

### Verified upstream facts (EDC tag `v0.18.1`, commit 3a22ff4 — do not re-research)

> The docs on the `main` branch of eclipse-edc.github.io describe features that
> 0.18.1 does **not** have: the `/v5beta/dataspaceprofiles` and `/v5beta/dcpscopes`
> APIs, `trustedIssuers` and `protocol.path` on a profile,
> `edc.iam.sts.type` and `edc.participants.config.validation.strict`. Do not plan
> against them.

- **Profile SPI** (`protocol-spi`, package `org.eclipse.edc.protocol.spi`):
  - `record DataspaceProfileContext(name, protocolVersion, webhook, idExtractionFunction, protocolNamespace, jsonLdContextsUrl)`
  - `DataspaceProfileContextRegistry`
  - `ParticipantProfileService.resolve(ctxId, profileId)` / `resolveAll(ctxId)`
  - `ProtocolWebhookResolver.getWebhook(ctxId, protocol)`

  A profile binds only the protocol version and binding, the JSON-LD
  namespace and contexts, and the webhook. It does **not** bind a DID, STS,
  trusted issuers or scopes.
- **Profile declaration** (`DataspaceProfileConfigurationExtension`, artifact
  `dsp-http-core-virtual`):
  - keys: `edc.dataspace.profiles.<key>.name`, `.protocol.version`,
    `.protocol.binding`, `.protocol.namespace` and `.jsonld.context.urls` (CSV);
  - `DspVirtualApiConfigurationV2025Extension` always registers the default
    profile `http-dsp-profile-2025-1`.
- **DSP path in virtual mode:**
  `<web.http.protocol.path>/{participantContextId}/{profileId}/{catalog|negotiations|transfers}/...`
  - It returns 404 unless the profile is associated with that context. The
    association comes from the per-context config entry `edc.dataspace.profiles`
    (CSV); `edc.dataspace.enable.profiles.all=true` bypasses it.
  - `web.http.protocol.virtual=true` is required. An explicit
    `edc.dsp.callback.address` must contain `%s`, which is replaced by the
    participant context id.
- **Virtual mode assembly:**
  - Use the `controlplane-virtual-base-bom` BOM. It contains `dsp-virtual`,
    `management-api-v5`, `management-api-authorization` and
    `management-api-oauth2-authentication`, and does **not** contain the classic
    core.
  - Optional add-ons: `controlplane-virtual-feature-dcp-bom`,
    `controlplane-virtual-feature-sql-bom` and `controlplane-virtual-feature-nats-bom`.
  - `participant-context-connector-core` (`ParticipantIdentityResolver`,
    `ParticipantProfileService`) is used by both modes.
  - **No `SingleParticipantContextSupplier` exists in virtual mode.**
- **Creating contexts** (management API v5, which needs `management-api:admin`):
  - `POST /v5beta/participants` with `identity`, `properties` and `state`;
  - `PUT /v5beta/participants/{id}/config` with `entries` and `privateEntries`;
  - `PUT /v5beta/participants/{id}/profiles`.

  Values are read per context through `ParticipantContextConfig.getString(ctxId, key)`.
  The context and config stores are in memory unless the SQL BOM is added.
- **Management API v5:**
  - resources are scoped under `/v5beta/participants/{ctxId}/assets` (and the
    same for the other resources);
  - authentication is an OAuth2 JWT (`edc.iam.oauth2.jwks.url`,
    `edc.iam.oauth2.issuer`);
  - a token's `sub` must name an existing context. `management-api:admin`
    grants access to every context.
- **DCP:**
  - Per participant context: the DID (`DidConfigProvider`: `edc.participant.id`,
    then `edc.participant.did`, then `edc.iam.issuer.id`) and the STS settings
    `edc.iam.sts.oauth.token.url`, `.client.id` and `.client.secret.alias`.
    The secret is read with `vault.resolveSecret(ctxId, alias)`.
  - Per **profile, not per context:** upstream DCP scopes
    (`edc.iam.dcp.scopes.<k>.profile`). `DefaultScopeMappingFunction` matches
    the scope's profile against `message.getProtocol()`.
  - Global: `TrustedIssuerRegistry`, which has no context dimension.
- **Upstream code that filters on `participantContextId`:**
  - Filters: `ContractDefinitionResolverImpl`, `DatasetResolverImpl`,
    `TransferProcessProtocolServiceImpl.findAgreement` (`queryAgreements`),
    `EmbeddedDataPlaneSelectorService` and `DataPlaneSignalingFlowController`.
  - Compare `getParticipantContextId()` on the loaded entity: the negotiation and
    transfer protocol services.
- **Reference code:**
  - `system-tests/tck/dsp-tck-virtual-connector-under-test`: its
    `TckSetupExtension` seeds contexts in code.
  - `system-tests/management-api/management-api-test-runner/.../v5/CatalogApiV5MultiProfileEndToEndTest.java`:
    two custom profiles.
  - Decision records `2025-05-28-dataspace-profile-context` and
    `2026-05-11-multi-profile-virtual-connector`.

### Design decisions

1. **One dataspace is one participant context.** A dataspace's identity (DID,
   STS, TIR, scopes) lives on the context. The context is associated with one or
   more profiles. If an operator uses the same DID in two dataspaces, they may
   still configure two contexts, so that data and policy stay isolated.
2. **TMForum carries `participantContextId` as an extension property.** It sits
   next to `externalId` on ProductSpecification, ProductOffering, Quote and
   QuoteItem, Agreement and Usage.
   - It is declared **optional** in the JSON schemas under `schemas/`, so
     existing data still validates.
   - For a **non-default** context it is queried **server-side** with a
     `participantContextId=` query parameter, the same way
     `ProductCatalogApiClient.getProductSpecByExternalId` already queries
     `externalId`.
3. **Legacy data with no context id** belongs to the *default context*.
   - The new setting is `tmfExtension.defaultParticipantContextId`.
   - In classic mode it defaults to the `SingleParticipantContextSupplier`
     context.
   - In virtual mode it must be configured, or such entities stay invisible and
     a startup warning is logged.
   - **The default context is never pushed down as `participantContextId=<default>`**:
     that filter returns only tagged entities and drops the untagged legacy
     ones (all of the classic TCK data). For the default context the store
     queries "equals default OR absent" if Step 1 finds the TMForum API supports
     it, otherwise it fetches unfiltered. Either way it maps first (absent →
     default) and filters in memory afterwards.
4. **Asset, contract-definition and policy ids are globally unique, across
   all contexts.** `AssetIndex.findById(String)`, `AssetIndex.resolveForAsset(String)`,
   `ContractDefinitionStore.findById(String)` and `PolicyDefinitionStore.findById(String)`
   take no context, so a store cannot choose between two TMForum entities in
   different contexts that share an id. To offer the same data in two
   dataspaces, the operator creates two ProductSpecifications with distinct
   `externalId`s.
   - By-id lookups (`getProductSpecByExternalId`, `getProductOfferingByExternalId`,
     `getByPolicyId`) therefore stay context-less. A cross-context duplicate is
     a data error, raised by the existing "multiple … exist" check, which now
     names the id and the contexts involved (see Steps 2 and 3).
   - The loaded entity carries its mapped context (absent → default), so
     context-aware callers can still compare `getParticipantContextId()`.
   - Negotiation and agreement ids are UUIDs, so the rule costs nothing there.
5. **Your own identity is resolved per context.** It is never read from
   `TMFConfig.getParticipantId()` on a code path that has a context: use
   `ParticipantContext.getIdentity()` or `ParticipantIdentityResolver`.
   `TMFConfig.participantId` / `participantContextId` remain as the
   classic-mode default only.
6. **Contexts are seeded from static configuration at startup.** A new
   `participant-bootstrap-extension` reads them, because FDSC deploys through
   Helm and has no SQL store for EDC. The management API v5 stays available for
   runtime changes.

### Working agreement

- Use `mvn -pl <module> -am test`. Never use a bare `-pl`: sibling modules
  depend on each other.
- Every step must leave `mvn clean test` and `mvn spotless:check` green across
  the reactor, and must keep the classic DSP TCK (`controlplane-tck`, the
  `.github/workflows/test.yml` TCK job) green. Steps 2–7 change no assembly,
  so classic behaviour must stay byte-for-byte equivalent. Their unit tests must
  cover both an entity *with* and one *without* a `participantContextId`.
- New property keys are named `static final` constants in the module's config
  class (`TMFConfig`, `DcpConfig`, `TirConfig`, `TransferConfig`). No inline
  literals.
- Prefer `@ParameterizedTest` for the "context A / context B / no context" case
  tables instead of duplicating test methods.

## Steps

### Step 1: Multi-dataspace design record and open-item verification

Create `docs/multi-dataspace-design.md` and put the "Verified upstream facts" and
"Design decisions" sections above into it. Then replace those two sections in
this plan with a one-line pointer to the file, so that only one copy exists.
Resolve each of the following against the 0.18.1 source or a running stack, and
record the answer:

- **TMForum extension-property query:** does the FIWARE TMForum API used in
  `docker-compose.tck.yml` (a) accept an *optional* extra property in an
  `@schemaLocation` schema, and (b) filter list endpoints by
  `?participantContextId=`? `externalId` shows (b) works for a required
  property, but it is not proven for an optional one.
  - Verify this against the TCK compose stack and record the exact query form.
  - If (b) does not work, the fallback is fetch-all with in-memory filtering.
    Record the paging consequence for `TMForumBackedCatalogProtocolService.getCatalog`,
    which today pages specs by `QuerySpec` offset and limit.
  - Record the query form for the default context (decision 3): can the API
    express "`participantContextId` equals X OR is absent" in one request? If
    not, the default context fetches unfiltered and filters in memory; record
    the same paging consequence for that path.
- **Data plane registration:**
  `FDSCTransferControlExtension` saves exactly one `DataPlaneInstance`. Does
  `EmbeddedDataPlaneSelectorService` filter it out for a context that does not
  own it? If so, decide between one instance per context and a context-less
  shared instance.
- **Vault per context:** `vault.resolveSecret(ctxId, alias)` together with
  `vault-hashicorp`. Determine which path or prefix layout the STS client secret
  of each context needs.
- **TIR per dataspace:** `TrustedIssuerRegistry` is global. Pick a mechanism and
  record it:
  - a per-profile post-validator that checks the VC issuer against that
    profile's TIL; or
  - `EbsiTrustedIssuersRegistry` holding the union of all TILs, combined with a
    per-profile issuer check.
- **Management API auth for operators:** decide on the JWT issuer that FDSC uses
  for `management-api-oauth2-authentication`, e.g. the existing Keycloak in the
  FIWARE Data Space Connector chart.
- **DSP version endpoint in virtual mode:** is `/.well-known/dspace-version`
  still served at the protocol root, or per `{ctx}` / `{ctx}/{profile}`? Record
  the exact path(s); Step 9 asserts against them.
- **`test-extension` in `controlplane-dcp`:** it is compile-scoped at
  `controlplane-dcp/pom.xml:54`. Decide whether to drop it from the DCP assembly;
  the `controlplane-tck` assembly owns TCK use.

Acceptance:
- the design record exists and every open item above is answered or explicitly
  deferred with a reason;
- this plan holds a single pointer instead of the moved sections;
- no code changes.

### Step 2: TMForum data layer — `participantContextId` extension property

This step adds the data layer only. No caller uses it yet, so behaviour does not
change.

- `schemas/`: add an optional `participantContextId` (string) to
  - `external-id.json`,
  - `quote-item.json`, `contract-negotiation.json`, `agreement.json`,
    `usage.json` and `contract-definition.json`,

  or add one shared schema, whichever Step 1 found the TMForum API accepts. Do
  not make it `required`.
- `tmf-extension/src/main/java/org/seamware/edc/domain/`: add a
  `participantContextId` field with a getter and a fluent setter to these VOs
  (and only these):
  - next to `externalId`: `ExtendableProductSpecification`,
    `ExtendableProductOffering`, `ExtendableQuoteItemVO`,
    `ExtendableAgreementVO` / `ExtendableAgreementCreateVO` /
    `ExtendableAgreementUpdateVO`, and `ExtendableUsageVO` /
    `ExtendableUsageCreateVO` / `ExtendableUsageUpdateVO`;
  - next to `contractNegotiation` (the Quote VOs have no `externalId`):
    `ExtendableQuoteVO` / `ExtendableQuoteCreateVO` / `ExtendableQuoteUpdateVO`.

  Include the new field in `equals` and `hashCode`.
- **Not tagged:** `ExtendableProduct` / `ExtendableProductCreate` and the
  ProductOrder that `TMFBackedContractNegotiationStore` creates (around `:798`
  and `:955`–`:989`), and `ExtendableProductSpecificationRef`. Nothing lists
  or looks these up by `externalId`: a Product is read by its TMForum id
  (`ProductInventoryApiClient.getProductById`), a ProductOrder by its Quote id
  (`ProductOrderApiClient.findByQuoteId`), and the Product's `externalId`
  already embeds the agreement id (a UUID). They are only reached through a
  Quote or Agreement, which carries the context. The ref is an embedded
  reference, not a stored entity.
- `tmf/ProductCatalogApiClient`, `tmf/QuoteApiClient` and `tmf/AgreementApiClient`
  (there is no Usage client; Usage VOs are only mapped in `TMFEdcMapper`): add
  context-aware overloads of the **list** lookups only (e.g.
  `getProductSpecifications(int offset, int limit, String participantContextId)`).
  - A `null` context means no filter, which is today's behaviour.
  - The by-`externalId` lookups get no context parameter (decision 4). Their
    "multiple … exist" checks stay global; the error message adds the
    `participantContextId` of each match, so a cross-context duplicate is
    identifiable from the log.
  - Add one shared constant `PARTICIPANT_CONTEXT_ID_QUERY_PARAM` instead of
    per-client literals.
- Tests:
  - Jackson round-trip of each VO, with and without the field;
  - MockWebServer/WireMock tests asserting the query parameter, following the
    pattern of the existing client tests.

Acceptance: `mvn -pl tmf-extension -am test` is green, and existing tests pass
unchanged.

### Step 3: Context-aware mapper, asset/contract-definition/policy stores and catalog service

- `TMFConfig`:
  - add `tmfExtension.defaultParticipantContextId` (decision 3) and resolve
    the default context in this order, first non-blank wins:
    1. `tmfExtension.defaultParticipantContextId`;
    2. the context of the `SingleParticipantContextSupplier`, when one is
       registered (classic mode; this is what `TMFConfig.fromConfig` fills
       `participantContextId` from today);
    3. no supplier registered (virtual mode, or a test): **nothing else**. Do
       not fall back to `edc.participant.context.id` / `edc.participant.id`:
       `fromConfig` reads the latter with an `ANONYMOUS_PARTICIPANT` default, so
       it always yields a value, and in virtual mode those keys are per-context
       entries, not a runtime identity. The default stays unset, legacy
       entities stay unstamped and invisible, and the decision 3 startup
       warning is logged.

    The `participantId` fallback in `fromConfig` (the classic-mode identity,
    decision 5) is unchanged; only the default *context* stops using it.
  - log a warning when the explicit key (1) and the supplier context (2) are
    both set and differ. The explicit key still wins, so a classic deployment
    with both set behaves predictably;
  - keep `getParticipantContextId()` as a deprecated accessor that returns the
    resolved default, so existing callers compile unchanged until they move to
    the new accessor.
- `store/TMFEdcMapper`:
  - stop stamping the constructor-injected `participantContextId`;
  - stamp each entity with the TMForum entity's `participantContextId`, or with
    the default context when it is absent;
  - in the EDC→TMForum direction, write `entity.getParticipantContextId()`.

  This applies to every builder that sets it today (`:321`, `:512`, `:772`,
  `:935`). Rename the constructor parameter to `defaultParticipantContextId`.
- `store/TMFBackedAssetIndex`, `TMFBackedContractDefinitionStore` are
  **read-only** like the policy store (`create`/`save`, `update`/`updateAsset`
  and `deleteById` throw `UnsupportedOperationException`; the contract
  definition store's `findAll` too). There is no write path to add.
  - `findById` (and `resolveForAsset`, which delegates to it) stays context-less
    (decision 4). Its `catch (RuntimeException)` keeps returning `null` as the
    interface requires, but logs a cross-context duplicate with
    `monitor.severe` instead of `warning`, since it hides the asset in every
    dataspace until the data is fixed.
  - `TMFBackedAssetIndex.queryAssets`: when the `QuerySpec` holds a `participantContextId` criterion
    (`ParticipantResource.filterByParticipantContextId`) for a non-default
    context, push it down to the Step 2 client overloads. For the default
    context follow decision 3 (no plain push-down). Always keep the in-memory
    `CriterionOperatorRegistry` predicate, applied after mapping, as the final
    filter.
- `store/TMFBackedPolicyDefinitionStore` is **read-only**: `create`, `update`,
  `delete` and `findAll` throw `UnsupportedOperationException`, and `findById`
  reads the policy out of its owning ProductOffering via
  `ProductCatalogApiClient.getByPolicyId`. There is no TMForum policy entity and
  no write path to add.
  - The policy's context is the owning ProductOffering's `participantContextId`
    (absent → default context). Today `getByPolicyId` returns a bare
    `Optional<Policy>`, which loses the offering, and the store builds the
    `PolicyDefinition` inline. Change this:
    - add `record OfferingPolicy(Policy policy, @Nullable String participantContextId)`
      in `tmf/`; `getByPolicyId` returns `Optional<OfferingPolicy>`, carrying the
      owning offering's raw (possibly absent) context;
    - add `TMFEdcMapper.toPolicyDefinition(OfferingPolicy)`, which builds the
      `PolicyDefinition` (id via `getIdFromPolicy`) and applies the
      absent → default rule, so default stamping lives only in the mapper;
    - the store injects `TMFEdcMapper` and calls it. Drop the store's own
      `participantContextId` field.
  - Policy ids are globally unique (decision 4). `getByPolicyId` keeps paging
    after the first hit and throws `BadGatewayException` naming the policy id
    and the contexts when matches come from offerings in more than one
    context. Matches within one context keep today's "policies need to be
    unique" error.
  - Do not add a schema or a write path for policies.
- `store/TMForumBackedCatalogProtocolService.getCatalog` / `getDataset`:
  - fetch specs and offerings for `participantContext.getParticipantContextId()`
    only;
  - `getDataset` returns not-found for a dataset owned by another context.
- `TMFOfferResolverExtension` / `store/TMForumConsumerOfferResolver`: resolve
  offers within the requesting context.
- Tests, parameterized over (context A, context B, legacy-no-context):
  - each mapper output has a non-null `participantContextId`;
  - each store returns only the matching context's entities for a filtered
    `QuerySpec`;
  - a default-context query returns the untagged legacy entities (and does not
    send `participantContextId=<default>` as a plain server-side filter);
  - `findById` on the policy store stamps the owning offering's context, and
    fails for a policy id used by offerings in two contexts;
  - `findById` on the asset index and contract-definition store returns the
    entity with its mapped context, and returns `null` (logged severe) for an
    `externalId` used in two contexts;
  - the catalog for context A never contains B's datasets.
- `TMFConfig` default-context resolution, parameterized over which of
  (explicit key, supplier context, `edc.participant.*`) are set: the expected
  winner, and a warning only when the explicit key and the supplier differ.
  Include the row "no supplier, no explicit key, `edc.participant.id` set"
  → default unset and the decision 3 warning logged.

Acceptance:
- `mvn -pl tmf-extension -am test` is green;
- the classic TCK run is still green (the default context equals the TCK's
  `urn:connector:fdsc-edc`).

### Step 4: Context-aware negotiation and agreement store

- `store/TMFBackedContractNegotiationStore`:
  - persist the negotiation's `participantContextId` on the Quote (and its items)
    and on the Agreement and Usage. Products and ProductOrders stay untagged
    (see Step 2);
  - `queryNegotiations`, `queryAgreements` and `nextNotLeased` honour context
    criteria. `queryAgreements` must match `(agreementId, participantContextId)`,
    which upstream `TransferProcessProtocolServiceImpl.findAgreement` relies on;
  - `findById` and `findContractAgreement` take only an id, so they get no
    filtered overload. The loaded entity must carry its correct mapped context
    (absent → default); the upstream protocol services compare
    `getParticipantContextId()` themselves.
- Your own identity per context:
  - replace the `participantId` constructor field (used for related parties at
    `:1305`/`:1310` and in `TMFEdcMapper` at `:557` to derive the negotiation
    role) with a lookup through `ParticipantIdentityResolver` for the
    negotiation's context;
  - fall back to `TMFConfig.getParticipantId()` only when no resolver is
    registered, i.e. a classic runtime without the connector core.
- `TMFContractNegotiationExtension`: inject `ParticipantIdentityResolver`
  (optional) and pass it through. The `SingleParticipantContextSupplier` stays
  optional, as it is today.
- `TMFBackedLeaseHolder` / `AutomaticUnlockingLockManager`: confirm that lease
  keys are entity ids that are unique across contexts. Ids are UUIDs, but
  record this in a test.
- `test-extension/.../edc/DataAssembly.java` and
  `scripts/init-tmforum.sh`: only touch these if TCK data must carry the
  context explicitly. Prefer relying on the default context, so TCK data stays
  unchanged.
- Tests:
  - two negotiations with the same counter-party in two contexts are stored with
    distinct related parties (their own DID per context) and are not visible
    across contexts;
  - the legacy Quote with no context maps to the default context.

Acceptance:
- `mvn -pl tmf-extension -am test` and `mvn clean test` are green;
- the classic TCK run is green.

### Step 5: Per-context identity in `fdsc-transfer-extension`; verify `policy-extension`

- Remove the single `tmfConfig.getParticipantId()` from
  `FDSCTransferControlExtension` (`:192`, `:201`):
  - `FDSCDcpEndpointDataReferenceService` resolves the issuer from the transfer
    process's `participantContextId` through `ParticipantIdentityResolver`;
  - `dcp/OidConfigController` serves the OpenID configuration per context,
    either with a context path segment or with a query by context id, as Step 1
    decided. Keep the current path answering for the default context, so
    classic deployments are unchanged;
  - `dcp/JwksController`: keep a single signing key unless Step 1 decided
    otherwise. The JWKS is not identity-bound.
- `DataPlaneInstance` registration: apply the Step 1 decision, either one
  instance per configured context or a context-less shared instance.
- `transfer/FDSCDcpProvisioner` / `FDSCOID4VPProvisioner`: keep the
  context-less `getProductSpecByExternalId` (asset ids are globally unique,
  decision 4); assert that the found spec's mapped context equals the transfer
  process's `participantContextId` and fail the provisioning otherwise.
- `policy-extension`: no per-dataspace split, because the ODRL-PAP is shared.
  - Verify that `PolicyContextInputMapper` / `OdrlPapPolicyExtension` work
    unchanged with entities from several contexts.
  - Add `participantContextId` to the PAP/OPA input as an **optional** field,
    so that PAP policies *can* tell dataspaces apart. Add it only if the
    existing input shape has an obvious place for it. If it does not, record
    the decision in the PR and add nothing.
- Tests: an EDR issuer is created for a transfer in each of two contexts; the
  OID config is returned for each context and for the default path.

Acceptance:
- `mvn -pl policy-extension -am test` is green (it pulls in
  `fdsc-transfer-extension` and `tmf-extension`);
- `mvn clean test` is green.

### Step 6: Per-dataspace DCP scopes and TIR in `dcp-extension`

- `DcpConfig`:
  - keep the global `dcp.scopes.{catalog,negotiation,transfer}` as the default
    for every profile;
  - add per-profile overrides `dcp.profiles.<profileName>.scopes.{catalog,negotiation,transfer}`.
- `policy/DefaultScopeMappingFunction`: choose the scope from the message's
  protocol (`message.getProtocol()` is the profile name), mirroring upstream's
  profile matching. Profiles without an override fall back to the global
  scopes.
- `TirConfig` / `TirExtension` / `tir/EbsiTrustedIssuersRegistry`: add
  per-profile TIL addresses (`ebsiTir.profiles.<profileName>.tilAddress`)
  using the mechanism Step 1 picked. The global `ebsiTir.tilAddress` stays the
  default.
- Do **not** re-implement what upstream already does per context: the DID and
  the STS keys `edc.iam.sts.oauth.*` are participant-context config entries
  (Step 7 seeds them). Check `DCPExtension` for any global DID or STS
  assumption and remove it.
- Tests:
  - parameterized (profile with override, profile without override, unknown
    profile) → expected scopes;
  - a TIR check for an issuer trusted in profile A but not in B.

Acceptance:
- `mvn -pl dcp-extension -am test` is green;
- with no `dcp.profiles.*` / `ebsiTir.profiles.*` set, behaviour is identical to
  today.

### Step 7: `participant-bootstrap-extension` — seed participant contexts from config

Add a new module `participant-bootstrap-extension` (register it in the parent
`pom.xml` `<modules>`, following the existing extension POM layout). It holds a
`ServiceExtension`, modelled on upstream `TckSetupExtension`, that seeds the
contexts in `prepare()`:

- It reads `fdsc.participants.<key>.*`:
  - `.id`, which becomes the participant context id;
  - `.identity` (the DID), the **single source** of the context's DID;
  - `.profiles` (CSV of dataspace profile names);
  - `.config.<edcKey>` (plain entries such as `edc.iam.sts.oauth.token.url`);
  - `.privateConfig.<edcKey>`, for sensitive entries.
- For each participant it upserts:
  - the context, through `ParticipantContextService`;
  - its configuration, through the `ParticipantContextConfig` store. The
    profiles become the `edc.dataspace.profiles` entry, and `.identity`
    becomes the `edc.participant.did` entry that `DidConfigProvider` reads.
    Startup fails when `.config.` sets `edc.participant.id`,
    `edc.participant.did` or `edc.iam.issuer.id` to a value other than
    `.identity`; an equal value is accepted.
- It upserts: a context or config entry that already exists (e.g. created
  earlier through the management API v5, or persisted by a later SQL store) is
  updated, not duplicated, and does not fail startup.
- It fails startup with a clear message when a referenced profile is not
  registered in `DataspaceProfileContextRegistry`.
- It is a no-op when no `fdsc.participants.*` keys are set, so it is harmless
  on the classic classpath. It is added only to `controlplane-dcp`.

The config class holds named constants for every key and has a one-line Javadoc.

Tests (Mockito on the EDC SPIs):
- two participants are seeded with their config and profiles;
- re-running against already-seeded stores updates and does not duplicate them;
- an unknown profile fails startup;
- a `.config.` DID entry that differs from `.identity` fails startup;
- with no keys nothing is seeded.

Acceptance: `mvn -pl participant-bootstrap-extension -am test` is green.

### Step 8: Switch `controlplane-dcp` to virtual mode with an example profile configuration

- `controlplane-dcp/pom.xml`:
  - import `controlplane-virtual-base-bom` (plus `controlplane-virtual-feature-dcp-bom`)
    instead of `controlplane-base-bom`;
  - drop `participant-context-connector-classic-core`, `dsp`/`dsp-2025` classic
    wiring and `management-api` v3/v4, replacing them with what the virtual
    BOMs bring (`dsp-virtual`, `management-api-v5`,
    `management-api-oauth2-authentication`);
  - add `participant-bootstrap-extension`;
  - remove `test-extension` if Step 1 decided so.
- Resolve the full assembly with `mvn -pl controlplane-dcp -am dependency:tree`.
  Make sure exactly one `DataFlowController` is present and no duplicate
  providers (see `docs/migration/EDC-0.18.1-UPGRADE.md` §8.3).
- Add the example configuration `config/multi-dataspace/edc.properties` with:
  - two profiles via `edc.dataspace.profiles.*`, plus the default
    `http-dsp-profile-2025-1`;
  - two participants via `fdsc.participants.*`, each with its own DID, STS
    client and profile list;
  - `web.http.protocol.virtual=true`;
  - `edc.dsp.callback.address` containing `%s`;
  - management OAuth2 settings;
  - per-profile `dcp.profiles.*` / `ebsiTir.profiles.*`;
  - `tmfExtension.defaultParticipantContextId`.

  Add a one-line comment per block.
- Confirm that `controlplane-oid4vc/pom.xml` and `controlplane-tck/pom.xml` are
  untouched.

Acceptance:
- `mvn clean package` is green, so the `controlplane-dcp` image builds;
- the image starts with `config/multi-dataspace/edc.properties` against a
  TMForum and logs both contexts seeded, both profiles registered and no
  unresolved-service or participant-context warnings;
- the classic TCK job is still green.

### Step 9: Compose-level two-dataspace verification

- Add `docker-compose.multi-dataspace.yml`, which reuses the postgis, scorpio
  and tmforum services of `docker-compose.tck.yml`. It runs the
  `controlplane-dcp` image with `config/multi-dataspace/edc.properties`, plus a
  static JWKS server for the management API JWT.
- `scripts/init-tmforum-multi-dataspace.sh` seeds:
  - one ProductSpecification and ProductOffering per context;
  - one legacy spec without a context;
  - distinct `externalId`s in each context (decision 4: ids are globally
    unique; the cross-context duplicate case is covered by the Step 3 unit
    tests).
- `scripts/verify-multi-dataspace.sh` generates an admin JWT with
  `openssl`/`jq` and asserts the following, exiting non-zero on any failure:
  - `GET /v5beta/participants` lists both contexts;
  - `GET /v5beta/participants/{A}/assets` returns only A's assets, and the same
    holds for B; the legacy asset appears only under the default context;
  - `GET <protocol>/{A}/{profileA}/...` resolves, while
    `<protocol>/{A}/{profileB}/...` returns 404, because that profile is not
    associated with A;
  - the DSP version endpoint answers at the path(s) Step 1 recorded for virtual
    mode.

  A full DSP catalog exchange needs a real DCP wallet and is out of scope here.
  The unit tests of Steps 3–6 cover catalog isolation.
- `.github/workflows/test.yml`: add a `multi-dataspace` job that runs the
  script. Keep the existing TCK job unchanged.

Acceptance:
- the script passes locally and in CI;
- the classic TCK job is still green.

### Step 10: Operator guide and documentation

- Add `docs/multi-dataspace.md`, the deliverable guide. It covers:
  - concepts, in the order dataspace → participant context → profile;
  - the DSP URL layout `/protocol/{ctx}/{profile}`;
  - which settings are per context (DID, STS) and which are per profile (DCP
    scopes, TIR);
  - `fdsc.participants.*` reference;
  - `edc.dataspace.profiles.*` reference;
  - management API v5 and OAuth2;
  - how to tag TMForum entities with `participantContextId`, and the
    default-context rule for legacy data;
  - globally unique asset, contract-definition and policy ids (decision 4),
    and how to offer the same data in two dataspaces with distinct ids;
  - a walkthrough of `config/multi-dataspace/edc.properties`;
  - migrating an existing single-dataspace `controlplane-dcp` deployment.
- `README.md`, `controlplane-dcp/README.md`: link the guide and state that the
  DCP image now runs in virtual mode, which is a breaking change: the
  management API moves to v5 with OAuth2, and DSP URLs change.
- `AGENTS.md`:
  - add `participant-bootstrap-extension` to the structure;
  - record the virtual-vs-classic assembly split and the TMForum
    `participantContextId` convention.
- `releases/notes/RELEASE_NOTES_MULTI_DATASPACE.md`: list the operator-facing
  breaking changes. Label the final PR `major`, because the DCP image's DSP and
  management API URLs change.
- In `docs/multi-dataspace-design.md`, mark every open item resolved.

Acceptance:
- `mvn clean package` and `mvn spotless:check` are green;
- every config key named in the guide exists as a constant in code or is an
  upstream key listed in the design record.
