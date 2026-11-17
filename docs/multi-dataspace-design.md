# Multi-dataspace design record

Design record for ticket #114 (multi-dataspace support in `controlplane-dcp`).
`IMPLEMENTATION_PLAN.md` holds the steps. This file holds the upstream facts
they rely on, the design decisions, and the Step 1 answers to the open items.

## Verified upstream facts (EDC tag `v0.18.1`, commit 3a22ff4 — do not re-research)

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
    same for the other resources). `AssetApiV5Controller` has `POST` (create),
    `POST /request` (query), `GET /{id}`, `PUT` and `DELETE /{id}`; there is
    **no collection `GET`**;
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
  - Upstream scopes of type `DEFAULT` are added identically to the catalog,
    negotiation and transfer requests (`DynamicDcpScopeExtension:60-62`
    registers one `DefaultScopeMappingFunction` on all three request policy
    contexts). They cannot express a scope for one request type only. Because
    `decentralized-claims-core` is already on the `dcp-extension` classpath,
    both this post-validator and ours (`DCPExtension:104-119`) run today. Both
    add to the same `RequestScope` set, so the result is the union.
  - Global: `TrustedIssuerRegistry`, which has no context dimension.
- **Upstream code that filters on `participantContextId`:**
  - Filters: `ContractDefinitionResolverImpl`, `DatasetResolverImpl`,
    `TransferProcessProtocolServiceImpl.findAgreement` (`queryAgreements`),
    `EmbeddedDataPlaneSelectorService` and `DataPlaneSignalingFlowController`.
  - Compare `getParticipantContextId()` on the loaded entity: the negotiation and
    transfer protocol services. `ContractNegotiationProtocolServiceImpl:247`
    rejects an offer whose **contract definition** belongs to another context.
  - **On the DSP protocol path an asset's `participantContextId` is never
    compared.** The context-less `AssetIndex.findById` callers only null-check
    the result
    (`ContractValidationServiceImpl:70`/`:157`, `TransferProcessProviderFactory:60`;
    `TransferProcessorsImpl:373` calls `resolveForAsset`). `countAssets` in
    `ContractValidationServiceImpl` uses only the definition's asset selector.
    Only `DatasetResolverImpl.query` filters assets by context, and it drops
    every dataset that has no offer from that context's contract definitions.
  - **The management API v5 path does compare it.** For every non-admin
    principal, `AuthorizationServiceImpl.authorize` checks
    `resource.getParticipantContextId()` against the owning context. The
    asset it checks is loaded by `AssetApiV5Extension.findAsset` with
    `assetService.search(participantContextId = <owner> AND id = <assetId>)`,
    i.e. through `AssetIndex.queryAssets`, not `findById`. Decision 4 holds
    only because `queryAssets` stamps the queried context (Step 3).
- **Reference code:**
  - `system-tests/tck/dsp-tck-virtual-connector-under-test`: its
    `TckSetupExtension` seeds contexts in code.
  - `system-tests/management-api/management-api-test-runner/.../v5/CatalogApiV5MultiProfileEndToEndTest.java`:
    two custom profiles.
  - Decision records `2025-05-28-dataspace-profile-context` and
    `2026-05-11-multi-profile-virtual-connector`.

## Design decisions

1. **One dataspace is one participant context.** A dataspace's identity (DID,
   STS, TIR, scopes) lives on the context. The context is associated with one or
   more profiles. If an operator uses the same DID in two dataspaces, they may
   still configure two contexts, so that data and policy stay isolated.
2. **TMForum carries `participantContextId` as an extension property on the
   dataspace-specific entities only:**

   | TMForum entity | EDC entity | Tagged | Why |
   |---|---|---|---|
   | ProductSpecification | Asset | **no** | the product; shared across dataspaces (decision 4) |
   | ProductOffering (and its embedded policies) | ContractDefinition, PolicyDefinition | yes, **multi-valued** `participantContextIds` | an offer made in one, several or all dataspaces (decision 4) |
   | Quote, QuoteItem | ContractNegotiation | yes | a negotiation runs in one dataspace |
   | Agreement | ContractAgreement | yes | an agreement is made in one dataspace |
   | Usage | TransferProcess (`TMFEdcMapper.fromTransferProcess`) | yes | a transfer runs in one dataspace |
   | Product, ProductOrder, ProductSpecificationRef | — | no | reached only through a tagged Quote or Agreement (Step 2) |

   - Every tagged entity except the ProductOffering carries one
     `participantContextId` string. The ProductOffering carries
     `participantContextIds`, an array of context ids or the wildcard `*`
     (all contexts); see decision 4.
   - It is declared **optional** in the JSON schemas under `schemas/`, so
     existing data still validates.
   - For a **non-default** context it is queried **server-side** with a
     `participantContextId=` query parameter (for offerings: the array contains
     the context or `*`), the same way
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
     fetches unfiltered, because TMForum cannot express "equals default OR
     absent" (see "TMForum extension-property queries" below). It maps first
     (absent → default) and filters in memory afterwards.
4. **A product is shared; an offering names the dataspaces it is made in.**
   The ProductOffering's `participantContextIds` decides where it is offered:

   | `participantContextIds` | Offered in |
   |---|---|
   | absent or empty | the default context only (decision 3; legacy data) |
   | `["A"]` | dataspace A only |
   | `["A", "B"]` | dataspaces A and B |
   | `["*"]` | every context, including ones added later |

   Absent deliberately does **not** mean "all": an existing single-dataspace
   deployment that adds a second dataspace must not expose its existing offers
   there without an explicit tag. An offer that needs *different* policies per
   dataspace is modelled as one offering per dataspace, all referencing the same
   ProductSpecification. The wildcard is the named constant
   `ALL_PARTICIPANT_CONTEXTS = "*"`.
   - **Products (assets):** the ProductSpecification has no context. The asset
     index stamps the mapped `Asset` with the context the query asks for
     (`queryAssets`), or with the default context on the context-less
     `findById`. This is safe because the DSP path never compares an asset's
     context, and the management API v5 path loads the asset through
     `queryAssets` with the owner's context criterion (see the verified facts). Its `externalId` stays globally unique,
     as it is today.
   - **Offerings (contract definitions and policies):** an EDC
     `ContractDefinition` / `PolicyDefinition` has exactly one
     `participantContextId`, `ContractDefinitionStore.findById(String)` and
     `PolicyDefinitionStore.findById(String)` take no context, and upstream
     *does* compare the definition's context with the negotiation's
     (`ContractNegotiationProtocolServiceImpl:247`). A by-id lookup therefore
     has to learn the context from the id itself:
     - an offering that serves **exactly one** context (absent, or one id) keeps
       its plain `externalId` as contract definition id and its plain policy
       ids, stamped with that context. Classic and TCK data are therefore
       unchanged;
     - an offering that serves **several or all** contexts is exposed in
       context X under the *context-qualified* contract definition id
       `<offeringExternalId>@X`, and its policies under `<policyId>@X`, stamped
       with X. One shared helper `ContextQualifiedId` (in `store/`, separator
       constant `CONTEXT_QUALIFIER_SEPARATOR = "@"`) builds and parses these ids;
     - parsing tries the full id as a plain `externalId` first (one lookup, as
       today). Only if that finds nothing and the id contains the separator is
       it split at the **last** separator. The qualified form resolves only if
       the prefix names a multi-context offering whose `participantContextIds`
       contains X or `*`; otherwise the lookup returns nothing. Each
       (offering, context) pair thus has exactly one id, and an id qualified
       with X can only produce a definition stamped X, which line 247 rejects
       in any other context;
     - offering `externalId`s and policy ids stay globally unique. By-id
       lookups (`getProductOfferingByExternalId`, `getByPolicyId`) stay
       context-less. A duplicate is a data error, raised by the existing
       "multiple … exist" check, which now names the id and the offerings'
       contexts (see Steps 2 and 3).
   - The contract offer id is `ContractOfferId.create(<definitionId>, <specExternalId>)`,
     so the same product gets a distinct offer id per offering and, for a
     multi-context offering, per dataspace.
   - A negotiation, agreement and usage always belong to exactly one context:
     the one the negotiation ran in, even when the offering serves several.
     Their ids are UUIDs. An agreement's `assetId` may name a shared product;
     that is fine because the agreement itself is tagged and `findAgreement`
     filters by context.
5. **Your own identity is resolved per context.** It is never read from
   `TMFConfig.getParticipantId()` on a code path that has a context: use
   `ParticipantContext.getIdentity()` or `ParticipantIdentityResolver`.
   `TMFConfig.participantId` / `participantContextId` remain as the
   classic-mode default only.
6. **Contexts are seeded from static configuration at startup.** A new
   `participant-bootstrap-extension` reads them, because FDSC deploys through
   Helm and has no SQL store for EDC. The management API v5 stays available for
   runtime changes.

## Open-item resolutions (Step 1)

All citations are to the EDC Connector tag `v0.18.1` (commit 3a22ff4), unless a
file in this repository is named. Each item ends with a **Decision** line
that later steps apply.

### TMForum extension-property queries

Measured against the TCK stack images (`tmforum-all-in-one:1.5.4`, Scorpio
`java-4.1.10`, postgis), with a local schema server. "Works" means the
request returned exactly the expected entities.

| Question | Result |
|---|---|
| Optional string property `participantContextId` on ProductOffering, Quote and Agreement | **works**: creates with and without it both return 201. The schema is enforced: a number gives 400 `integer found, string expected` |
| Optional array property `participantContextIds` on ProductOffering | **works**: `["A","B"]` gives 201; `"A"` or `[1]` gives 400 |
| `GET …?participantContextId=A` (offering, quote, agreement) | **works**: only A-tagged entities are returned and untagged ones are excluded. `.eq=A` is equivalent |
| OR across values | **works**: `participantContextId=A,B` (or `participantContextId=A;participantContextId=B`) |
| `participantContextId=A&participantContextId=B` | means AND: 0 results |
| "equals A **OR absent**" in one request | **not expressible**. The operators are `.eq/.gt/.gte/.lt/.lte/.regex`; there is no `ne` or exists. `participantContextId=` gives 400; `!=`, `%7C`, `%22%22` give 500; `q=` gives 400; regex with `.*` or anchors matches nothing |
| `GET …/productOffering?participantContextIds=A` | **works**: array-contains, so `["A","B"]` matches |
| `GET …/productOffering?participantContextIds=A,*` | **works**: contains A **or** the literal `"*"`. `*` is matched literally (no wildcard) and needs no encoding |
| `;` combining *different* extension properties (`participantContextId=A;externalId=x`) | **500**: do not use |
| Paging with a filter (`&offset=N&limit=M`) | **works**: 15 matches paged as 5/5/5/0 with no duplicates, also for `A,*`, the array filter, and Quote |
| Page size | the default is **10**, so an unpaged filtered query silently truncates. `limit=1000` works; `limit=1001` gives **500** |

Further behaviour that later steps must respect:
- **Empty array:** `participantContextIds: []` is stored but reads back as
  `[{}]`.
  - Writers must omit the property instead of writing `[]`.
  - The mapper (Step 3) must treat a missing, empty or non-string-only array as
    absent.
  - `""` reads back as `""` and is also treated as absent.
- **Schema fetching:** TMForum fetches the `@schemaLocation` URL on every create
  or update that carries extension properties, and returns 400 if it is
  unreachable or 404. Reads succeed without it. Validation runs only when the
  body has properties outside the base model, and an extension property
  without `@schemaLocation` gives 400.
  - An entity has exactly one `@schemaLocation`, so Step 2 adds the optional
    property to the schema each tagged entity already references
    (`external-id.json` for offerings, `agreement.json`, `quote-item.json`,
    `usage.json`, …) instead of a separate schema.
  - **Pitfall:** `init-tmforum.sh` points at
    `raw.githubusercontent.com/wistefan/edc-dsc/refs/heads/init/schemas/…`.
    Schema changes in this repository are not visible at that URL until they
    are published there. Step 9's seed must reference schemas that contain the
    new properties: serve `schemas/` from a static container in the compose
    file and point `@schemaLocation` at it.

**Decision: query forms**

| Store / context | Query | Filtering |
|---|---|---|
| Quote, Agreement, Usage in non-default context X | `?participantContextId=X` | server-side, so paging stays server-side |
| Offerings in non-default context X | `?participantContextIds=X,*` | server-side, so paging stays server-side |
| Any entity in the **default** context | unfiltered | in memory: map (absent → default), then filter |
| By-id lookups (`externalId`, policy id, quote id) | unchanged, context-less | the mapped entity carries its context |

**Paging consequence (default context and in-memory paths):**
- the server offset no longer corresponds to the filtered result, so the store
  must page through **all** entities with `limit=1000` (the server maximum;
  a named constant) until a short page;
- it then filters, and applies the `QuerySpec` offset and limit in memory;
- non-default contexts keep server-side `offset`/`limit`.

**Catalog (`TMForumBackedCatalogProtocolService.getCatalog`):**
- specs stay shared and keep their server-side `QuerySpec` paging;
- offerings, fetched today with `getProductOfferings(0, MAX_OFFERINGS = 100)`,
  use `participantContextIds=X,*` for a non-default context;
- for the default context they are fetched unfiltered and filtered in memory.
  The existing 100-offering cap then applies **before** the context filter, so
  Step 3 must page offerings to exhaustion on that path instead of relying on
  one `MAX_OFFERINGS` call;
- an offering tagged with the default context explicitly
  (`participantContextIds` contains the default, or `*`) is handled by the
  same in-memory filter.

### DSP-path callers of `ContractDefinitionStore.findById` / `PolicyDefinitionStore.findById`

- On the provider side of a negotiation, `ContractNegotiationProtocolServiceImpl:92,242-245`
  calls `ConsumerOfferResolverImpl.resolveOffer`:
  - `contractDefinitionStore.findById(parsedOfferId.definitionPart())` (`ConsumerOfferResolverImpl:47-48`);
  - `policyDefinitionStore.findById(...)` with the definition's
    `getAccessPolicyId()` (`:53`) and `getContractPolicyId()` (`:58`);
  - the result is checked against the request's context at
    `ContractNegotiationProtocolServiceImpl:247`.
- The catalog never calls `ContractDefinitionStore.findById`. Instead:
  - `ContractDefinitionResolverImpl:54-60` runs `findAll(queryByParticipantContextId(ctx))`
    and then `policyStore.findById(accessPolicyId)`;
  - `DatasetResolverImpl:143-144` runs `policyDefinitionStore.findById(contractPolicyId)`.
- `ContractDefinitionServiceImpl:46` and `PolicyDefinitionServiceImpl:72` are
  used by the management API only.
- **Ids reach the store unchanged.** `ContractOfferId:61` splits only on `:`, and
  `:70-76` Base64-decodes each part. The offer ids EDC builds itself are
  Base64-encoded (`DatasetResolverImpl:147`, `ContractOfferId:108-114`), so
  `CD123@ctxA` round-trips exactly. For an unencoded offer id, `@` is not a
  Base64 character, so decoding fails and the raw part is used as-is (`:74-75`).
  The policy ids are passed verbatim.

**Decision:** decision 4's context-qualified ids (`<id>@<ctx>`) need no
upstream change. Constraint for Step 3: a context id and an offering
`externalId` must not contain `:`, because it would break `ContractOfferId`
parsing. The bootstrap (Step 7) rejects such context ids.

### Data plane registration

- `EmbeddedDataPlaneSelectorService.selectFor` (`:99`) queries
  `participantContextId = transferProcess.getParticipantContextId()` with strict
  equality (`EqualOperatorPredicate:30-32`). A null context matches only null,
  and there is no wildcard or shared instance.
  `DataPlaneSignalingFlowController:84,119,282` uses `selectFor`.
- `LegacyDataPlaneSignalingFlowController:97,159` uses
  `select(strategy, predicate)` and does **no** context filtering
  (`EmbeddedDataPlaneSelectorService:69-80`).
- `controlplane-dcp` deliberately runs the legacy controller
  (`transfer-data-plane-signaling` + `data-plane-signaling-client`; see
  `controlplane-dcp/pom.xml` and `docs/migration/EDC-0.18.1-UPGRADE.md` §8.2).

**Decision:** keep the **single, context-less** `DataPlaneInstance` that
`FDSCTransferControlExtension` saves today. Under the legacy controller it is
selectable in every context. Step 8 must keep the legacy controller in the
virtual assembly and exclude `data-plane-signaling` if a virtual BOM brings it
in, so that exactly one `DataFlowController` is present. If a later change
moves to `DataPlaneSignalingFlowController`, the instance must be saved once
per context, stamped with that context id. That is out of scope here.

### Vault per context

- The default `Vault.resolveSecret(partition, key)` ignores the partition
  (`spi/common/boot-spi/.../Vault.java:61-63`).
- `HashicorpVault:72-77,95-108`:
  - a null partition uses the global config;
  - otherwise it reads the participant-context config entry
    `edc.vault.hashicorp.config`, a JSON `HashicorpVaultSettings` record
    (`VaultConstants:29`, `HashicorpVaultSettings:46-52`);
  - if that entry is absent, it falls back to the global config, because
    `edc.vault.hashicorp.allowfallback` (legacy `allow-fallback`) defaults to
    `true` (`HashicorpVaultConfig:71-81`).
- The context id is **never** added to the path. The URL is
  `<url>/<api.secret.path>/data/<folder?>/<key>` (`HashicorpVaultClient:140-161`).

**Decision:**
- contexts share the global Vault config (fallback on, no per-context
  `edc.vault.hashicorp.config`);
- every context uses a distinct STS secret alias. The convention, which Step 7
  documents and Step 10 shows, is `edc.iam.sts.oauth.client.secret.alias=<ctxId>-sts-client-secret`;
- the alias is configured explicitly per context, not derived from the
  context id, so the operator controls the KV key layout;
- an operator who needs hard isolation can set the context's private config
  entry `edc.vault.hashicorp.config` with its own `folder`. Step 10 mentions
  this; no code is needed.

### TIR per dataspace

- `TrustedIssuerRegistry` in 0.18.1 has only `register(Issuer, type)` and
  `getSupportedTypes(Issuer)` (`TrustedIssuerRegistry:34,42`), with no
  context or profile dimension. `HasValidIssuer:45-48`
  (`VerifiableCredentialValidationServiceImpl:78`) checks against it globally.
- **The plan's first option is not feasible.** A post-validator on the
  `Request*PolicyContext`s runs **before** token verification
  (`ProtocolTokenValidatorImpl:63-64`), where our scope mapping also runs, so it
  cannot see the presented VCs. The later policy contexts carry only the
  `ParticipantAgent`, with no profile.
- `DcpIdentityService.verifyJwtToken(participantContextId, …)` (`:125-161`)
  knows the context but not the profile, and its `getAdditionalValidations()`
  is hard-coded empty (`:201-202`).
- `ProtocolTokenValidatorImpl:77-84` calls the **per-profile**
  `dataspaceProfileContextRegistry.getIdExtractionFunction(protocol)` on the
  verified `ClaimToken`. A `null` id results in 401. The `ClaimToken`'s `vc`
  claim carries the credentials (`DcpDefaultServicesExtension:45`).

**Decision:** a two-layer check (the plan's second option):
1. `EbsiTrustedIssuersRegistry` is backed by the **union** of the global
   `ebsiTir.tilAddress` and all `ebsiTir.profiles.<profile>.tilAddress`, so
   `HasValidIssuer` passes for an issuer trusted in any dataspace.
2. For each profile with its own TIL, Step 6 wraps that profile's id-extraction
   function:
   - it returns `null` (and therefore 401) unless every VC issuer in the `vc`
     claim is trusted by **that** profile's TIL;
   - otherwise it delegates to the original function.

   Step 6 must confirm how `DataspaceProfileContextRegistry` allows replacing a
   profile registered from configuration (`DataspaceProfileContextRegistry:38,45`).
   If it cannot be replaced in place, re-register the profile under the same
   name, built from the same `edc.dataspace.profiles.*` values.

### Management API auth for operators

- The keys of `management-api-oauth2-authentication` are `edc.iam.oauth2.issuer`,
  `edc.iam.oauth2.jwks.url` and `edc.iam.oauth2.jwks.cache.validity` (default
  5 minutes) (`ManagementApiOauth2AuthenticationExtension:85-90`). There is
  **no audience check**.
- The token rules are `iss` equality, `nbf`, `exp` and `iat` (`:76-80`). The
  issuer check uses `Objects.equals` (`IssuerEqualsValidationRule:31`), so if
  the issuer is unset, only tokens *without* `iss` pass. **Always set it.**
- The claims read are `sub` and `scope` (space-separated;
  `ServicePrincipalAuthenticationFilter:70-71`).
  - With `management-api:admin`, `sub` need not be a context (`:120-121`).
  - Otherwise `sub` must name an existing context, or the request gets 401
    (`:123-128`).
  - Endpoints require `management-api:read` / `management-api:write`
    (`ScopeBasedAccessFeature:26`).
  - Ownership requires `sub` to equal the resource's context, unless the caller
    is admin (`AuthorizationServiceImpl:70-76`).

**Decision:**
- use the Keycloak that the FIWARE Data Space Connector chart already deploys.
  A dedicated realm or client issues the management tokens:
  - `edc.iam.oauth2.issuer=<keycloak>/realms/<realm>`;
  - `edc.iam.oauth2.jwks.url=<keycloak>/realms/<realm>/protocol/openid-connect/certs`;
  - client scopes `management-api:admin`, `management-api:read` and
    `management-api:write`, which Keycloak emits in the `scope` claim;
- operators use an admin client;
- a per-dataspace client needs `sub` equal to the context id, i.e. a
  hardcoded-claim mapper on `sub`. **Deferred to Step 10**, because it is chart
  configuration and is not exercised by any test in this repository;
- Step 9 uses no Keycloak: a static JWKS plus tokens signed with `openssl`,
  with `iss` set to the configured issuer.

### DSP version endpoint in virtual mode

- Virtual mode serves it at `<protocol>/{participantContextId}/.well-known/dspace-version`
  (`DspVirtualMetadataApiController:41`). It returns 404 for an unknown
  context and lists only that context's associated profiles (`:57-67`).
- The root `/.well-known/dspace-version` (`DspVersionApiController:33`) exists
  only with the non-virtual `dsp-version` module. The virtual BOM brings only
  `dsp-virtual` (`controlplane-virtual-base-bom/build.gradle.kts:37`).
- `web.http.protocol.virtual` changes no routes. It only appends `/%s` to the
  default callback address (`DspApiBaseConfigurationExtension:91-103`).

**Decision (Step 9 asserts):**
- `GET <protocol>/{A}/.well-known/dspace-version` returns 200 and lists A's
  profiles only;
- `GET <protocol>/{unknown}/.well-known/dspace-version` returns 404;
- there is no per-`{ctx}/{profile}` version path, and the root path is absent
  in the virtual assembly.

### DSP request without a token in virtual mode

- The profile association is resolved **before** the token is checked.
  `DspVirtualCatalogApiController20251:91` resolves the profile before it calls
  the handler (`:111`). Inside the handler, the token is checked before the
  context lookup (`DspRequestHandlerImpl:106-115`).

**Decision (Step 9 asserts, with `edc.dataspace.enable.profiles.all` unset/false):**

| Request (no `Authorization` header) | Status | Source |
|---|---|---|
| `POST <protocol>/{A}/{profileA}/catalog/request`, profile associated with A | **401** | `DspRequestHandlerImpl:107-108,251-252` |
| `POST <protocol>/{A}/{profileB}/catalog/request`, profile not associated | **404** | `DspVirtualCatalogApiController20251:139-141`, `UnexpectedExceptionMapper:46-47` |
| `POST <protocol>/{unknown}/{profileA}/catalog/request` | **404** | `ParticipantProfileServiceImpl:85-91,99-103` |

With `edc.dataspace.enable.profiles.all=true`
(`ConnectorParticipantContextExtension:39`), the association check is skipped
(`ParticipantProfileServiceImpl:81-82`) and all three return 401. The example
configuration (Step 8) therefore must **not** set it. The fallback assertion
via `/v5beta/participants/{A}/profiles` is not needed.

### `test-extension` in `controlplane-dcp`

- `TckControllerExtension` and `TckWebhookController` `@Inject` a
  `SingleParticipantContextSupplier`, which does not exist in virtual mode. A
  virtual runtime that contains them fails to start with an unresolved
  dependency, even with `testExtension.*` disabled, because injection is
  resolved before `initialize`.
- `TestIdentityExtension` `@Provides` `IdentityService` and `Vault`. These
  compete with DCP and `vault-hashicorp` on a production assembly.
- The `controlplane-tck` assembly (classic mode) owns TCK use.

**Decision:** Step 8 removes `test-extension` from `controlplane-dcp/pom.xml`.
`controlplane-tck` keeps it unchanged.
