# EDC 0.14.1 -> 0.18.1 upgrade: compatibility report

Status: **research complete, migration not started.** This document is the single
source of truth for the upgrade; `IMPLEMENTATION_PLAN.md` points here instead of
carrying its own copy of the findings. Everything below was verified directly
against Maven Central and by `javap` on the published 0.14.1 / 0.18.1 jars, or
against the repository itself (file:line references). Treat it as ground truth and
do not re-research it.

Seven upstream releases are crossed: 0.15.0, 0.15.1, 0.16.0, 0.16.1, 0.17.0,
0.18.0, 0.18.1 (there is no 0.17.1).

The three dominant breaks, in order of cost to this repository:

1. **Participant Context** — `ParticipantContext` is threaded through the protocol
   services and a `participantContextId` lands on every control-plane entity. The
   TMForum-backed stores filter in memory, so this one can fail *silently* (§4).
2. **DCP module rename** — `identity-trust-*` -> `decentralized-claims-*`, with the
   package root moving with it.
3. **Removal of DSP 0.8 and DSP 2024/1** — this repo still declares the 2024
   artifacts alongside the 2025 ones.

---

## 1. Release-by-release summary


Release index: <https://github.com/eclipse-edc/Connector/releases>. PR numbers link to `https://github.com/eclipse-edc/Connector/pull/NNNN` unless another repo is named.

### 0.15.0

Released 2025-12-02 — <https://github.com/eclipse-edc/Connector/releases/tag/v0.15.0>. By far the largest break in this range.

- **Participant Context introduced.** New entity `ParticipantContext`; `participantContextId` added to `Asset`, `PolicyDefinition`, `ContractDefinition`, `ContractNegotiation`, `ContractAgreement`, `TransferProcess`, `DataPlaneInstance`, `EndpointDataReferenceEntry`. Design: `docs/developer/decision-records/2025-08-26-participant-context/README.md` (PR #5193). Entity PRs: #5208 (entity), #5217 (asset), #5222 (policy definitions), #5225 (contracts), #5236 (transfers).
- **Protocol service layer signatures changed.** `CatalogProtocolService`, `ContractNegotiationProtocolService` and `TransferProcessProtocolService` now take `ParticipantContext` as first argument; `getDataset`/`findById` now take a message object instead of loose `String` args (PR #5251, see also #5240 DSP layer, #5253 fallible participant-context supplier). Verified by diffing `spi/control-plane/control-plane-spi/.../CatalogProtocolService.java` between tags v0.14.1 and v0.15.0.
- **`IdentityService` signature changed** — `obtainClientCredentials(String participantContextId, TokenParameters)` and `verifyJwtToken(String participantContextId, TokenRepresentation, VerificationContext)` (PR #5259; DCP side #5298). Verified in `spi/common/core-spi/.../iam/IdentityService.java` at v0.15.0.
- **`AudienceResolver.resolve()` now takes `ProtocolRemoteMessage`** instead of `RemoteMessage` (RemoteMessage hierarchy cleanup, PR #5350). Verified in `spi/common/core-spi/.../iam/AudienceResolver.java` at v0.15.0.
- **New artifact `connector-participant-context-spi`** — `spi/common/participant-context-spi` was renamed to avoid a name clash with the IdentityHub module of the same name (PR #5229). Companion modules added: `spi/common/participant-context-single-spi`, `spi/common/participant-context-config-spi`, `core/common/participant-context-core`, `core/common/participant-context-single-core`, `core/common/participant-context-config-core`.
- **New config key `edc.participant.context.id`** (optional; falls back to `edc.participant.id`) for single-participant ("classic") runtimes (PR #5280; DR `docs/developer/decision-records/2025-10-15-participant-context-config`, PR #5279). The DR explicitly recommends a stable, dedicated value because it is written into every participant-context-aware row.
- **DCP module/package/class rename** `identity-trust-*` → `decentralized-claims-*`, package `org.eclipse.edc.iam.identitytrust.*` → `org.eclipse.edc.iam.decentralizedclaims.*`, classes `IdentityAndTrustService` → `DcpIdentityService`, `IdentityAndTrustExtension` → `DcpCoreExtension`, `IdentityAndTrustTransformExtension` → `DcpTransformExtension` (PR #5324, closes #4275). SPI module `spi/common/identity-trust-spi` → `spi/common/decentralized-claims-spi`.
- **`StateEntityStore.save()` return type changed** from `void` to `StoreResult<Void>` (PR #5207). Same PR rewrote the lease mechanism: `edc_lease.lease_id` FK dropped, column renamed to `resource_id`, new `resource_kind` column, composite PK `(resource_id, resource_kind)`, lease acquisition is now a single atomic upsert. **DDL migration required.**
- **`DataFlowController.provision()` renamed to `prepare()`** (PR #5325, part of #5323); control-plane provision stuff deprecated in the same PR. Signaling API start endpoint split into `prepare` + `start` (PR #5262).
- **`ContractAgreement` schema change** — new `agreementId` field (`agr_agreement_id` column) distinct from `id`; agreement lookup on transfer request is by `(agreementId, participantContextId)` (PR #5275, DR #5273). **DDL migration required.**
- Management API moved to **v4beta** (PR #5336; v4alpha controllers #5157, #5176, #5182); **protocol-version management API removed** (PR #5184).
- `MutableEntity` removed (PR #5227); `CallbackAddresses` extracted as an interface (PR #5241); custom runtime extension classes removed (PR #5308); `TransferServiceSelectionStrategy` removed (PR #5297); deprecations from 0.6.x–0.13.0 cleaned up (PRs #5296, #5338, #5340).
- Vault interfaces merged and made multi-tenant/partitioned (PRs #5372, #5352, #5384, #5376); transfer-destination secrets are offloaded to the vault before storage (PR #5381). New `private_entries` column (PR #5383). **DDL migration required.**
- Control-plane/data-plane transformers relocated into `control-plane-transform` (PRs #5341, #5343, #5345).
- `monitor-jdk-logger` deprecated (PR #5339); version endpoint moved under the default API context (PR #5263).
- OAuth2/JWT-based authn/authz upstreamed for the management API (PR #5373).

### 0.15.1

Released 2025-12-17 — <https://github.com/eclipse-edc/Connector/releases/tag/v0.15.1>.

- No changes listed in the release notes (empty generated changelog, compare `v0.15.0...v0.15.1`). No breaking changes.

### 0.16.0

Released 2026-02-19 — <https://github.com/eclipse-edc/Connector/releases/tag/v0.16.0>.

- **DSP 0.8 and DSP 2024/1 removed.** The `data-protocols/dsp/dsp-08/*` and `data-protocols/dsp/dsp-2024/*` module trees (including `dsp-spi-08` and `dsp-spi-2024`) are deleted; only `dsp-2025` / `dsp-spi-2025` remain (PR #5452, "Delete deprecated DSP 0.8 and 2024 version modules"). All E2E tests were switched to DSP 2025/1 in PR #5427. Verified by diffing the v0.15.0 and v0.16.0 git trees.
- **Control-plane provisioning/deprovisioning removed** (deprecated in 0.14.x). PR #5444 is flagged `BREAKING CHANGE`: internal APIs changed; the columns `resource_manifest`, `provisioned_resource_set` and `deprovisioned_resources` become unused and may be dropped **after** upgrading to 0.16.0.
- **`TransferProcess` `DataAddress` is stored in the Vault.** PR #5489 is flagged `BREAKING CHANGE`: a new `data_address_alias TEXT` column must be added to the transfer-process SQL table. **DDL migration required.**
- **`DataFlowResponse.provisioning` renamed to `async`** (builder method `provisioning(boolean)` → `async(boolean)`), PR #5471 ("feat(DPS): async prepare"). Verified by diffing `DataFlowResponse.java` between v0.15.0 and v0.16.0.
- **`DataFlowController` gains `started()` and `completed()`** — `StatusResult<Void> started(TransferProcess)` / `StatusResult<Void> completed(TransferProcess)` (PR #5437; related restructuring in #5435 "inline DataFlowManager").
- `DataFlowManager` inlined / removed as a separate abstraction (PR #5435, DR #5434); `DataPlaneSelectorManager` dismissed (PR #5500, DR #5497).
- `Asset.dataAddress` is now optional (PR #5405); address validation semantics changed accordingly.
- `ParticipantAgent` id made non-null (PR #5415).
- Launchers removed from the repo (PR #5447); 0.14.0 generic deprecations cleaned up (PR #5438).
- CEL expressions adopted for policy evaluation (PR #5476, DR #5473) — new `cel-store-sql` schema.
- **Dynamic DCP scopes introduced** — config-driven `edc.iam.dcp.scopes.<id>.{id,type,value,prefix-mapping,profile}` replaces hand-written `ScopeExtractor`/`PolicyValidatorRule` extensions (PR #5485, DR #5478).
- Revocation-registry registration moved from IdentityHub into the Connector (PR #5502).
- `dataDestination` removed from the management transfer v4 API (PR #5510).
- Data Plane Framework deprecated in favour of data-plane-signaling (PR #5514).
- `TransferProcess.dataAddress` is cleaned up on final states (PR #5495).

### 0.16.1

Released 2026-08-11 (i.e. *after* 0.18.0) — <https://github.com/eclipse-edc/Connector/releases/tag/v0.16.1>.

- Single change: `TypeTransformerRegistry` backed by a `Map` instead of an `ArrayList` (PR #5942). Backport of the same change released as 0.18.1. Behavioural note below under 0.18.1.
- The generated changelog compares `v0.18.0...v0.16.1`, which is an artefact of the release tooling, not an indication that 0.16.1 contains 0.18.0 content.

### 0.17.0

Released 2026-04-20 — <https://github.com/eclipse-edc/Connector/releases/tag/v0.17.0>.

- **`edc.dsp.well-known-path.enabled` removed.** `DspRequestBasePathProvider` became a default provider; equivalent behaviour now requires supplying a custom `DspRequestBasePathProvider` (PR #5527, closes #5525).
- **`edc.iam.issuer.id` → `edc.participant.did`.** Old key deprecated but still honoured; annotated `@Deprecated(since = "0.17.0")` on `DcpCoreExtension.DEPRECATED_ISSUER_ID_KEY` (PR #5559).
- **`StateEntityStore.breakLease(T entity)` added** — returns `StoreResult<Void>`; lets state machines release a lease without updating the entity (PR #5533). Custom `StateEntityStore` implementations must implement it.
- **Participant-context module split/rename** — `participant-context-single-core` → `participant-context-connector-classic-core` (single-participant "classic" compatibility shim: auto-creates a `ParticipantContext` from `edc.participant.id` / `edc.participant.context.id`, provides `SingleParticipantContextSupplier` and a config-backed `ParticipantContextConfigStore`), plus a new `participant-context-connector-core` usable in both classic and virtual mode providing default `ParticipantIdentityResolver` and `ProtocolWebhookResolver` (PR #5598, closes #5591).
- **DCP scope alias changed** `org.eclipse.edc.vc.type` → `org.eclipse.dspace.dcp.vc.type` for spec compliance (PR #5593). Policy constraints/left operands using the old alias stop matching.
- **Counter-party claims stored on `ContractAgreement`** (consumer side). PR #5626 is flagged `BREAKING CHANGE`: new `claims JSON` column on `edc_contract_agreement`. Claims on `TransferProcess` added in the same release (PRs #5631, #5632, #5633). **DDL migration required.**
- `monitor-jdk-logger` module removed (PR #5539).
- **Management API versioning**: V4 stabilised, V3 deprecated, V5 promoted to beta (PR #5668); v5alpha controllers ported in #5588, #5594, #5603; authn/authz ported to the management API in #5566.
- ~~`DataFlowResponseMessage` renamed to `DataFlowStatusMessage` and `dataplaneId` removed from it (PR #5652).~~
  **Corrected — it is not a rename of the `core-spi` type.** `core-spi` 0.18.1 still ships
  `org/eclipse/edc/spi/types/domain/transfer/DataFlowResponseMessage.class`, byte-identical to
  0.14.1. `DataFlowStatusMessage` exists only in the *new* `data-plane-signaling-core` module
  (`org.eclipse.edc.signaling.domain.DataFlowStatusMessage`), as one of that module's own
  signaling message types. Two different classes in two different artifacts — see §3.2.
- `jwt-signer-spi` merged into `jwt-spi` (PR #5657).
- DPS signaling endpoints moved to a dedicated `signaling` API context (PR #5589); signaling API group renamed (#5625); DPS registration adapted to the latest spec (#5575, #5578); data-plane authorization profile became a single object (#5630); `DataPlaneMetadata#properties` became a `@json` type (#5642).
- Connection-pool configuration switched to the new `@Configuration` format (PR #5667); `@Configuration` supports `Map` (#5649).
- EDR cache deprecated (PR #5641, DR #5644).
- `ParticipantAgentContextMapper` no longer maps the `vc` claim (PR #5548).
- Policy monitor switched from a state machine to a watchdog (PR #5597); state-machine processors now return `CompletableFuture` (PR #5555).
- Federated Catalog source moved into the Connector repo (PRs #5531, #5538, #5541, #5542, #5543) and its API exposed under the management context (#5549).
- 0.14.0 and 0.15.0 deprecations cleaned up (PRs #5534, #5639).

### 0.18.0

Released 2026-06-30 — <https://github.com/eclipse-edc/Connector/releases/tag/v0.18.0>.

- **`callback-http-dispatcher` module discontinued.** `extensions/control-plane/callback/callback-http-dispatcher` exists at v0.17.0 and is gone at v0.18.0 (verified by git-tree diff). HTTP delivery of `CallbackAddress` notifications is now done by a dedicated `CallbackClient` (`spi/control-plane/control-plane-spi/.../services/spi/callback/CallbackClient.java`), registered as a default service and overridable (PRs #5808, #5815). `callback-event-dispatcher` and `callback-static-endpoint` remain.
- **`RemoteMessageDispatcherRegistry` and `RemoteMessageDispatcher` removed from `core-spi`**, `RemoteMessageDispatcherRegistryImpl` removed from `runtime-core`; DSP services now use `ProtocolRemoteMessageDispatcher` (moved to `control-plane-spi`) with the single `DspHttpRemoteMessageDispatcherImpl` implementation (PR #5814, DR `docs/developer/decision-records/2026-06-10-remove-dispatcher-registry`, PR #5811). Any extension injecting `RemoteMessageDispatcherRegistry` will fail to resolve.
- **`DataFlowController.resume()` added** — `StatusResult<DataFlowResponse> resume(TransferProcess)` (PR #5709, "feat(dps): implement suspend/resume cases"). Verified by diffing `DataFlowController.java` v0.17.0 → v0.18.1.
- **data-plane-signaling is the default in the BOMs.** PR #5694 is flagged `BREAKING CHANGE`: runtimes that still need the deprecated EDC Data Plane signaling API must swap the signaling modules back manually.
- **Identity is carried in the `sub` claim** instead of `participant_context_id`; all remnants of the `role` claim dropped (PR #5813).
- **API authorization moved from roles to scopes.** New `Scope` + `ScopeMatcher` model (#5804); `management-api:admin` becomes the cross-tenant elevation signal (#5805); v5 controllers use fine-grained scopes such as `management-api:assets:read` while coarse `management-api:read` / `management-api:*:read` still satisfy them, and the role-based filter is dropped (#5812); admin elevation scope made configurable (#5816). DR: `docs/developer/decision-records/2026-06-06-scope-based-api-authorization` (PR #5798).
- **DCP scope config moved to an injected config map**, with env-friendly `edc.iam.dcp.scopes.<id>.prefix.mapping` replacing the now-deprecated `prefix-mapping` (PR #5795). Both keys are present in `DynamicDcpScopeConfigurationExtension` at v0.18.1, the hyphenated one marked "(Legacy Config)".
- **`dct:conformsTo` adopted as a known `Asset` property** (PR #5780). Flagged as a potential break for anyone who already stored a `conformsTo` asset property: it was previously expanded into `https://w3id.org/edc/v0.0.1/ns/` and is now namespaced on Dublin Core, changing how it appears in the catalog and management API.
- **HashiCorp Vault token-exchange auth**: `HashicorpJwtTokenProvider` and `HashicorpVaultCredentials` replaced by `HashicorpTokenExchangeProvider` (RFC 8693) plus `HashicorpVaultTokenProviderFactoryImpl` (PR #5821).
- **Encryption disabled by default** unless explicitly configured; the `strict` setting became unnecessary (PR #5781).
- **`@SettingContext` superseded by `@Configuration(context = ...)`** — the old annotation value is now only a fallback (PR #5791).
- Counter-party claims also stored on the provider-side agreement (PR #5727) — `claims` column on `edc_contract_agreement` is written on both sides now.
- `TransferProcessProtocolService` calls now fail when the store update fails, and missing `breakLease` calls were added in both protocol services (PRs #5793, #5729, #5788).
- Default state-machine implementations moved out of `controlplane-core` (PR #5691); catalog-crawler core modules merged and `ExtensionManager` refactored into `CatalogCrawlerManager` (PRs #5762, #5730).
- Address validation removed from `AssetService` (PR #5720) and control-plane address parse/validation removed (PR #5830).
- Dataspace profile configuration + profile field + profile-based validation added for policy definitions and the v5 management API (PRs #5747, #5752, #5756, #5765, #5785); new discovery endpoint (#5768, #5789).
- NOT in 0.18.x: the five-module SPI consolidation (PR #5842, DR #5831) lands only in the 1.0.0-RC line — `spi/common/core-spi` etc. still exist at v0.18.1.

### 0.18.1

Released 2026-09-25 — <https://github.com/eclipse-edc/Connector/releases/tag/v0.18.1>.

- Single change: `TypeTransformerRegistry` switched from an `ArrayList` to `Map<inputType, Map<outputType, transformer>>` (PR #6019, backport of the main-branch fix for issue #5921). Not labelled breaking, but behaviour changes: transformer selection is now deterministic and most-specific-type based instead of `findAny()`, only one transformer per input/output pair can exist, and registering a duplicate logs an override warning. Extensions that relied on registering several transformers for the same type pair are affected.

---

## 2. Artifact coordinates

### 2.1 Artifacts that no longer exist at 0.18.1

**Artifacts that no longer exist at 0.18.1** (6 of the 72 EDC artifacts this repo declares):

| Current (0.14.1) | Last published | Replacement at 0.18.1 |
|---|---|---|
| `identity-trust-core` | 0.14.1 | `decentralized-claims-core` |
| `identity-trust-spi` | 0.14.1 | `decentralized-claims-spi` |
| `identity-trust-transform` | 0.14.1 | `decentralized-claims-transform` |
| `dsp-2024` | 0.15.1 | *none* — DSP 2024/1 support deleted in 0.16.0 |
| `dsp-spi-2024` | 0.15.1 | *none* — same |
| `callback-http-dispatcher` | 0.17.0 | *none* under that name — the HTTP transport moved into `callback-event-dispatcher` (§8.1) |

The other 66 artifacts resolve unchanged at 0.18.1. Beware a false friend: `dcp-core`
and `dcp-spi` **do** exist at 0.18.1 but are **IdentityHub-side** artifacts
(`dcp-identityhub-core`, `dcp-issuer-*`); the Connector-side replacement for
`identity-trust-*` is `decentralized-claims-*`, package
`org.eclipse.edc.iam.decentralizedclaims.*`.

Independently re-verified artifact by artifact: 145 `<dependency>` declarations
across the 9 POMs resolve to **72 distinct `org.eclipse.edc` artifacts**, of which
**66 resolve at 0.18.1** and exactly the 6 above return 404. Where each of the 6 is
declared:

| artifactId | declared in | last published |
|---|---|---|
| `callback-http-dispatcher` | `controlplane-oid4vc/pom.xml:210`, `controlplane-dcp/pom.xml:200` | 0.17.0 |
| `dsp-2024` | `controlplane-oid4vc`, `controlplane-dcp` | 0.15.1 |
| `dsp-spi-2024` | `tmf-extension`, `test-extension` | 0.15.1 |
| `identity-trust-core` | `dcp-extension` | 0.14.1 |
| `identity-trust-spi` | `dcp-extension` | 0.14.1 |
| `identity-trust-transform` | `dcp-extension` | 0.14.1 |

The `identity-trust-*` trio stopped *at* the version currently in use, so
`dcp-extension` carries the largest coordinate rework. `dsp-2024` / `dsp-spi-2024`
are a straight drop rather than a replacement hunt, because `dsp-2025` /
`dsp-spi-2025` are already declared next to them and both resolve at 0.18.1.

### 2.2 First false friend — `dcp-*` is IdentityHub, not the Connector

`dcp-core` and `dcp-spi` **do** exist at 0.18.1 but are **IdentityHub-side**
artifacts (siblings of `dcp-identityhub-core`, `dcp-issuer-*`). The Connector-side
replacement for `identity-trust-*` is `decentralized-claims-*`, package
`org.eclipse.edc.iam.decentralizedclaims.*`.

### 2.3 Second false friend — pick the right participant artifact

Three similarly named artifacts resolve at 0.18.1 and only the first is the one
this document means:

| Artifact | Package root | What it is |
|---|---|---|
| `connector-participant-context-spi` | `org.eclipse.edc.participantcontext.spi` | **Correct.** Connector-side `ParticipantContext`, `ParticipantContextService`, `ParticipantContextStore`. New at 0.18.1 (404 at 0.14.1). |
| `participant-context-spi` | `org.eclipse.edc.identityhub.spi.participantcontext` | IdentityHub-side. Its `ParticipantContext` was **renamed** `IdentityHubParticipantContext` at 0.18.1 and its store/state types moved to the connector artifact. |
| `participant-spi` | `org.eclipse.edc.participant.spi` | Unrelated to participant *context* — `ParticipantAgent`, `ParticipantAgentPolicyContext`, `ParticipantIdMapper`. **This repo already declares it**; do not mistake it for either of the above. |

### 2.4 Full resolve-status table

All 72 artifacts share groupId `org.eclipse.edc` (`—` in the groupId column means
exactly that). `cp-oid4vc` / `cp-dcp` abbreviate the two controlplane modules.

| artifactId | groupId | declared in | 0.18.1 | note |
|---|---|---|---|---|
| `api-core`  | — | cp-oid4vc, cp-dcp | resolves |  |
| `api-observability`  | — | cp-oid4vc, cp-dcp | resolves |  |
| `boot`  | — | cp-oid4vc, cp-dcp | resolves |  |
| `boot-spi`  | — | tmf-extension, test-extension, oid4vc-extension, fdsc-transfer-extension | resolves |  |
| `callback-event-dispatcher`  | — | cp-oid4vc, cp-dcp | resolves |  |
| `callback-http-dispatcher`  | — | cp-oid4vc, cp-dcp | **404** | last published 0.17.0 |
| `callback-static-endpoint`  | — | cp-oid4vc, cp-dcp | resolves |  |
| `catalog-spi`  | — | dcp-extension, policy-extension | resolves |  |
| `configuration-filesystem`  | — | cp-oid4vc, cp-dcp | resolves |  |
| `connector-core`  | — | cp-oid4vc, cp-dcp | resolves |  |
| `contract-spi`  | — | policy-extension | resolves |  |
| `control-api-configuration`  | — | cp-oid4vc, cp-dcp | resolves |  |
| `control-plane-api`  | — | cp-oid4vc, cp-dcp | resolves |  |
| `control-plane-api-client`  | — | cp-oid4vc, cp-dcp | resolves |  |
| `control-plane-core`  | — | cp-oid4vc, cp-dcp | resolves |  |
| `control-plane-spi`  | — | tmf-extension, test-extension, fdsc-transfer-extension | resolves |  |
| `control-plane-transform`  | — | tmf-extension | resolves |  |
| `controlplane-base-bom`  | — | cp-oid4vc, cp-dcp | resolves |  |
| `core-spi`  | — | tmf-extension, test-extension, oid4vc-extension, fdsc-transfer-extension | resolves |  |
| `data-plane-core`  | — | dcp-extension, fdsc-transfer-extension | resolves |  |
| `data-plane-selector-api`  | — | cp-oid4vc, cp-dcp | resolves |  |
| `data-plane-selector-control-api`  | — | cp-oid4vc, cp-dcp | resolves |  |
| `data-plane-selector-core`  | — | cp-oid4vc, cp-dcp | resolves |  |
| `data-plane-selector-spi`  | — | tmf-extension, fdsc-transfer-extension | resolves |  |
| `data-plane-signaling-client`  | — | cp-oid4vc, cp-dcp | resolves |  |
| `data-plane-spi`  | — | tmf-extension, test-extension, fdsc-transfer-extension | resolves |  |
| `did-spi`  | — | dcp-extension | resolves |  |
| `dsp`  | — | cp-oid4vc, cp-dcp | resolves |  |
| `dsp-2024`  | — | cp-oid4vc, cp-dcp | **404** | last published 0.15.1 |
| `dsp-2025`  | — | cp-oid4vc, cp-dcp | resolves |  |
| `dsp-catalog-lib`  | — | cp-oid4vc, cp-dcp | resolves |  |
| `dsp-http-spi`  | — | tmf-extension, test-extension, fdsc-transfer-extension | resolves |  |
| `dsp-spi-2024`  | — | tmf-extension, test-extension | **404** | last published 0.15.1 |
| `dsp-spi-2025`  | — | tmf-extension, test-extension | resolves |  |
| `dsp-version-http-api`  | — | cp-oid4vc, cp-dcp | resolves |  |
| `edr-store-core`  | — | cp-oid4vc, cp-dcp | resolves |  |
| `edr-store-receiver`  | — | cp-oid4vc, cp-dcp | resolves |  |
| `edr-store-spi`  | — | cp-oid4vc, cp-dcp | resolves |  |
| `http`  | — | cp-oid4vc, cp-dcp | resolves |  |
| `http-spi`  | — | tmf-extension, test-extension, fdsc-transfer-extension | resolves |  |
| `identity-did-core`  | — | dcp-extension | resolves |  |
| `identity-did-spi`  | — | dcp-extension | resolves |  |
| `identity-did-web`  | — | dcp-extension | resolves |  |
| `identity-trust-core`  | — | dcp-extension | **404** | last published 0.14.1 |
| `identity-trust-spi`  | — | dcp-extension | **404** | last published 0.14.1 |
| `identity-trust-transform`  | — | dcp-extension | **404** | last published 0.14.1 |
| `json-ld`  | — | tmf-extension | resolves |  |
| `json-ld-spi`  | — | policy-extension | resolves |  |
| `jws2020-lib`  | — | dcp-extension | resolves |  |
| `management-api`  | — | cp-oid4vc, cp-dcp | resolves |  |
| `management-api-configuration`  | — | cp-oid4vc, cp-dcp | resolves |  |
| `oauth2-client`  | — | dcp-extension | resolves |  |
| `participant-spi`  | — | policy-extension | resolves |  |
| `policy-engine-spi`  | — | policy-extension | resolves |  |
| `policy-monitor-core`  | — | cp-oid4vc, cp-dcp | resolves |  |
| `protocol-spi`  | — | tmf-extension, test-extension, oid4vc-extension, fdsc-transfer-extension | resolves |  |
| `query-lib`  | — | tmf-extension, fdsc-transfer-extension | resolves |  |
| `request-policy-context-spi`  | — | policy-extension | resolves |  |
| `runtime-core`  | — | cp-oid4vc, cp-dcp | resolves |  |
| `sts-core`  | — | dcp-extension | resolves |  |
| `token-core`  | — | dcp-extension | resolves |  |
| `token-spi`  | — | tmf-extension, test-extension, oid4vc-extension, fdsc-transfer-extension | resolves |  |
| `transaction-spi`  | — | tmf-extension, test-extension, fdsc-transfer-extension | resolves |  |
| `transfer-data-plane-signaling`  | — | cp-oid4vc, cp-dcp | resolves |  |
| `transform-lib`  | — | tmf-extension, dcp-extension, cp-oid4vc, cp-dcp | resolves |  |
| `transform-spi`  | — | tmf-extension, test-extension, dcp-extension, fdsc-transfer-extension | resolves |  |
| `util-lib`  | — | tmf-extension, fdsc-transfer-extension | resolves |  |
| `validator-data-address-http-data`  | — | cp-oid4vc, cp-dcp | resolves |  |
| `vault-hashicorp`  | — | dcp-extension, fdsc-transfer-extension, cp-dcp | resolves |  |
| `verifiable-credentials-spi`  | — | policy-extension | resolves |  |
| `version-api`  | — | cp-oid4vc, cp-dcp | resolves |  |
| `web-spi`  | — | tmf-extension, test-extension, dcp-extension | resolves |  |

---

## 3. API break inventory

### 3.1 Signature changes confirmed by `javap`

```java
// StateEntityStore<T>  — save() return type changed AND breakLease() added (0.17.0)
0.14.1:  void                     save(T);
0.18.1:  StoreResult<Void>        save(T);
0.18.1:  StoreResult<Void>        breakLease(T);   // new abstract method

// CatalogProtocolService — both methods changed
0.14.1:  getCatalog(CatalogRequestMessage, TokenRepresentation)
0.18.1:  getCatalog(ParticipantContext, CatalogRequestMessage, TokenRepresentation)
0.14.1:  getDataset(String, TokenRepresentation, String)
0.18.1:  getDataset(ParticipantContext, DatasetRequestMessage, TokenRepresentation)

// IdentityService — participantContextId added to both methods
0.18.1:  obtainClientCredentials(String participantContextId, TokenParameters)
0.18.1:  verifyJwtToken(String participantContextId, TokenRepresentation, VerificationContext)

// AudienceResolver — parameter type narrowed
0.14.1:  Result<String> resolve(RemoteMessage)
0.18.1:  Result<String> resolve(ProtocolRemoteMessage)

// DataFlowResponse (artifact transfer-spi) — dataPlaneId KEPT, provisioning renamed
0.14.1:  boolean provisioning;  isProvisioning();  Builder.provisioning(boolean)
0.18.1:  boolean async;         isAsync();         Builder.async(boolean)
0.18.1:  Builder toBuilder();            // new
// dataAddress and dataPlaneId are present at BOTH versions — neither was removed.

// DataFlowController (artifact transfer-spi) — provision() renamed, four methods added
// Generic type arguments below are read from the Signature attribute (javap erases
// them in its default output) — they matter, see resume().
0.14.1:  boolean                      canHandle(TransferProcess)
         StatusResult<DataFlowResponse> provision(TransferProcess, Policy)
         StatusResult<DataFlowResponse> start(TransferProcess, Policy)
         StatusResult<Void>           suspend(TransferProcess)
         StatusResult<Void>           terminate(TransferProcess)
         Set<String>                  transferTypesFor(Asset)
0.18.1:  boolean                      canHandle(TransferProcess)
         StatusResult<DataFlowResponse> prepare(TransferProcess, Policy)   // was provision
         StatusResult<DataFlowResponse> start(TransferProcess, Policy)
         StatusResult<Void>           suspend(TransferProcess)
         StatusResult<Void>           terminate(TransferProcess)
         Set<String>                  transferTypesFor(Asset)
         StatusResult<DataFlowResponse> resume(TransferProcess)     // new — NOT Void
         StatusResult<Void>           started(TransferProcess)      // new
         StatusResult<Void>           completed(TransferProcess)    // new
         Set<String>                  transferTypesFor(String)      // new overload

// DataFlowResponseMessage (artifact core-spi) — UNCHANGED, byte-identical
// Declares only dataAddress + provisioning at both versions. It never had a
// dataPlaneId; do not confuse it with DataFlowResponse above.
```

`ContractDefinitionStore`, `ContractNegotiationStore`, `AssetIndex` and
`PolicyDefinitionStore` declare **no** changed methods of their own — but
`ContractNegotiationStore` inherits the two `StateEntityStore` breaks above.
`PendingGuard<E>` and `EventSubscriber` are **unchanged**, so the existing guards
and trigger subscribers survive.

### 3.2 Which files in this repo break

Every implementation site, located by grep over `src/main/java` (test sources
follow their production class and are not listed separately):

| Upstream break | File(s) in this repo | Current signature |
|---|---|---|
| `StateEntityStore.save` returns `StoreResult<Void>`; new abstract `breakLease` | `tmf-extension/.../store/TMFBackedContractNegotiationStore.java:374` | `public void save(ContractNegotiation)` — the **only** `StateEntityStore` implementation in the repo |
| `CatalogProtocolService.getCatalog` / `getDataset` take a `ParticipantContext`, and `getDataset` takes a `DatasetRequestMessage` instead of `(String, TokenRepresentation, String)` | `tmf-extension/.../store/TMForumBackedCatalogProtocolService.java:88,127` | `getCatalog(CatalogRequestMessage, TokenRepresentation)`, `getDataset(String, TokenRepresentation, String)` |
| `IdentityService` gains a leading `String participantContextId` on both methods | `oid4vc-extension/.../identity/OID4VPIdentityService.java:78,125`; `test-extension/.../services/TestIdentityService.java:60,70` | `obtainClientCredentials(TokenParameters)`, `verifyJwtToken(TokenRepresentation, VerificationContext)` |
| `AudienceResolver.resolve` narrows `RemoteMessage` -> `ProtocolRemoteMessage` | `oid4vc-extension/.../identity/CounterPartyAddressAudienceResolver.java:50`; `test-extension/.../services/NoopAudienceResolver.java:53` | `Result<String> resolve(RemoteMessage)` |
| `DataFlowController.provision` -> `prepare`, plus new `resume` / `started` / `completed` / `transferTypesFor(String)` | `test-extension/.../edc/NoopDataFlowController.java:35` | implements the 0.14.1 six-method interface; `provision` at :58 |
| `DataFlowResponse.Builder.provisioning` -> `async` | `test-extension/.../edc/NoopDataFlowController.java:64,75` | `.provisioning(false)` |
| entities gain `participantContextId` (§4) | `tmf-extension/.../store/TMFEdcMapper.java` and all four TMF-backed stores | builders never set it |
| `DataFlowManager` removed; `DataFlowController` is a single injected service (§8.3) | `test-extension/.../edc/TckGuardExtension.java:50,88,151` | `@Inject DataFlowManager` + `register(noopPriority, controller)` |

Two non-breaks worth stating, because both look like breaks:

- **`fdsc-transfer-extension` has no `DataFlowController`.** Its
  `FDSCTransferControlExtension.java:197,234` only *calls*
  `DataPlaneInstanceStore.save(...)`, and a caller that ignores the new
  `StoreResult<Void>` return value still compiles. The repo's single
  `DataFlowController` implementation lives in `test-extension`, so the
  `prepare`/`resume`/`async` work belongs to the `test-extension` step, not the
  transfer step.
- **`DataFlowResponseMessage` (`core-spi`) is byte-identical** between 0.14.1 and
  0.18.1 and still declares `provisioning`. Only the `provisioning` field of
  `DataFlowResponse` (`transfer-spi`) was renamed. Do not "fix" the former.

---

## 4. Participant Context impact analysis

This is the central design question of the upgrade, and the only break that can
pass compilation and still be wrong at runtime.

### 4.1 What upstream actually added

`connector-participant-context-spi` 0.18.1 (`javap`):

```java
// org.eclipse.edc.participantcontext.spi.types.ParticipantResource
public interface ParticipantResource {
  static QuerySpec.Builder queryByParticipantContextId(String);
  static Criterion        filterByParticipantContextId(String);
  String getParticipantContextId();
}

// org.eclipse.edc.participantcontext.spi.types.AbstractParticipantResource
public abstract class AbstractParticipantResource extends Entity implements ParticipantResource {
  protected String participantContextId;
  public    String getParticipantContextId();
  // Builder<T, B> extends Entity.Builder<T, B>
  public    B      participantContextId(String);   // on every entity builder
}
```

Which control-plane entities carry it at 0.18.1 (none of them did at 0.14.1):

| Entity | 0.14.1 | 0.18.1 |
|---|---|---|
| `ContractNegotiation` | `extends StatefulEntity` | `extends StatefulEntity implements ParticipantResource`, own `participantContextId` field |
| `TransferProcess` | `extends StatefulEntity` | same shape as `ContractNegotiation` |
| `Asset` | `extends Entity` | `extends AbstractParticipantResource` |
| `ContractDefinition` | `extends Entity` | `extends AbstractParticipantResource` |

### 4.2 Why this is a silent failure here, not a compile error

`TMFBackedAssetIndex.queryAssets` (`tmf-extension/.../store/TMFBackedAssetIndex.java:131-154`)
does **not** translate the `QuerySpec` into a TMForum query. It pages the full
ProductSpecification list out of TMForum, maps each one with `TMFEdcMapper`, and
then applies the criteria **in memory** through
`CriterionOperatorRegistry.toPredicate`, which reflects over the mapped `Asset`.

So the moment EDC core starts appending `ParticipantResource.filterByParticipantContextId(ctx)`
to its queries — which is the entire point of the refactor — the predicate reads
`Asset.participantContextId`, finds `null` on every mapped asset, and the catalog
comes back **empty**. No exception, no failed build, no failing unit test that
mocks the registry. The same reasoning applies to the contract-definition and
policy-definition stores, which filter the same way.

**Therefore the hard requirement is:** `TMFEdcMapper` must set
`participantContextId` on every entity it builds (`Asset`, `ContractDefinition`,
`ContractNegotiation`, `PolicyDefinition` where applicable), and the value must be
the same one the runtime is configured with. This belongs in the `tmf-extension`
step and must be covered by a test that asserts a non-null `participantContextId`
on mapper output.

### 4.3 Where `participantContextId` should live in the TMForum model

The TMForum-backed stores have no SQL schema to migrate, and TMForum entities have
no natural slot for a connector-internal context id. **Decision: do not persist it.**
This connector serves exactly one participant; the id is a runtime constant, so it
is *derived from configuration at mapping time*, not stored per entity and not
round-tripped through TMForum. Persisting it would add a field to every TMForum
payload that only this connector understands, and would let stored data drift out
of sync with `edc.participant.context.id`.

Consequence: the TMF stores can ignore a `participantContextId` criterion entirely
(it is trivially true for every entity they can ever return) **provided** the
mapper stamps the configured value, so that the generic in-memory predicate also
passes. Stamping is the cheaper and less surprising of the two, so stamp.

### 4.4 `classic` vs full multi-context core — recommendation: `classic`

`participant-context-connector-classic-core` 0.18.1 contains exactly three classes
— `ClassicParticipantContextServicesExtension`,
`ClassicParticipantContextDefaultServicesExtension` and
`SingleParticipantContextConfigStore` — i.e. it is the single-participant
compatibility mode: at `prepare()` it seeds one `ParticipantContext` into the
`ParticipantContextStore` from configuration and nothing else.

Its two settings, read straight off the `@Setting` annotations in the bytecode:

| Setting key | Default | Required | Description (verbatim from the annotation) |
|---|---|---|---|
| `edc.participant.id` | `anonymous` | — | "Configures the participant id this runtime is operating on behalf of" |
| `edc.participant.context.id` | — | **no** (`required = false`) | "Configures the participant context id for the single participant runtime" |

Note `required = false`: `edc.participant.context.id` is *optional* in classic
mode. It should still be set explicitly and kept stable across restarts, because
it is the value that ends up on persisted entities — but a missing key will not
fail startup, which is precisely how a wrong-but-silent configuration gets shipped.

**Recommendation: adopt `participant-context-connector-classic-core`.** The full
`participant-context-connector-core` adds a profile service, a webhook resolver and
an identity resolver for *multi*-participant runtimes; this connector has one
participant identity (`urn:connector:fdsc-edc`) backed by one TMForum tenant, so
the multi-context machinery buys nothing and would force a real decision about
per-context storage in TMForum.

---

## 5. DSP protocol impact

**Dropping `dsp-2024` / `dsp-spi-2024` is behaviour-neutral for this connector.**
Three independent checks:

- `config/tck/tck.properties:37` already points the TCK at
  `http://edc:8282/protocol/2025-1`, the versioned endpoint registered by the
  `dsp-2025` module, and `:40` sets the unversioned base URL. Nothing in the TCK
  configuration exercises a 2024/1 endpoint.
- Both controlplane POMs already declare `dsp-2025` and `dsp-spi-2025` next to the
  2024 artifacts, and both 2025 artifacts resolve at 0.18.1 (§2.4).
- DSP 0.8 and 2024/1 were deleted upstream in 0.16.0 (PR #5452), so staying on the
  2024 modules is not an option in any case.

**Consequence for counterparties.** After the upgrade this connector speaks DSP
2025/1 only. Any external counterparty still on DSP 2024/1 (or 0.8) can no longer
negotiate with it — that is an external coordination item, not a code change, and
it should be announced with the release.

**Do not delete the 2024/1 IRI in `config/tck/scope-mappings.json:8`.** That file
maps ODRL *left operands* to credential scopes:

```json
{ "match": { "http://www.w3.org/ns/odrl/2/leftOperand": "https://w3id.org/dspace/2024/1/membershipType" },
  "scopes": ["contract.negotiation", "catalog"] }
```

`https://w3id.org/dspace/2024/1/membershipType` is **policy vocabulary**, not
protocol wiring: it is the stable IRI of the DSpace membership left operand and it
is unaffected by the removal of the 2024/1 *protocol* modules. Removing it would
silently stop the membership constraint from mapping to the
`contract.negotiation` / `catalog` scopes. Ring-fenced here so the `dsp-2024`
removal does not take it along.

Related upstream change to watch in the same area: 0.17.0 changed the DCP scope
alias `org.eclipse.edc.vc.type` -> `org.eclipse.dspace.dcp.vc.type` (PR #5593).
Policy constraints using the old alias stop matching. This repo does not set the
alias anywhere, but any policy authored externally against it does.

---

## 6. Configuration migration

### 6.1 What this repo actually sets

The repository contains exactly **one** EDC properties file,
`config/tck/edc.properties`, plus env vars in `docker-compose.tck.yml`. **There is
no Helm chart, `values.yaml` or k8s manifest in this repository, and
`docker/Dockerfile` bakes in no configuration** — production configuration for both
controlplane images lives outside this repo and must be migrated by whoever owns
the deployment.

Distinct EDC-core keys found: 5 `edc.*` (`edc.participant.id`, `edc.hostname`,
`edc.log.level`, `edc.dsp.callback.address`, `edc.fs.config`) plus 8 `web.http.*`
keys, and 2 env vars (`EDC_FS_CONFIG`, `EDC_LOG_LEVEL`). Everything else in that
file is this repo's own namespaces (`tmfExtension.*`, `testExtension.*`,
`fdscTransfer.*`, `dcp.*`, `oid4vp.*`, `odrlPap.*`), which upstream does not touch.

### 6.2 Migration table

| Key | Status at 0.18.1 | Present in this repo? | Action |
|---|---|---|---|
| `edc.participant.id` | kept; default `anonymous` in classic mode | **yes** — `config/tck/edc.properties:19` = `urn:connector:fdsc-edc`, asserted by `scripts/verify-tck-config.sh:165`, must stay equal to `dataspacetck.dsp.connector.agent.id` (`config/tck/tck.properties:33`) | leave as is |
| `edc.participant.context.id` | **new** (0.15.0, PR #5280), `required = false` | **no** | add, pinned to a stable value; see §4.4 |
| `edc.iam.issuer.id` | renamed to `edc.participant.did` (0.17.0, PR #5559); old key deprecated but still honoured | **no** (prose only) | nothing to rename here; relevant only to external DCP deployments |
| `edc.dsp.well-known-path.enabled` | **removed** (0.17.0, PR #5527) | **no** | nothing to remove here; external configs must drop it |
| `edc.iam.dcp.scopes.<id>.prefix-mapping` | deprecated in favour of `...prefix.mapping` (0.18.0, PR #5795) | **no** — no `edc.iam.*` key is set or read anywhere in this repo | no action; see note below |
| `edc.hostname`, `edc.log.level`, `edc.dsp.callback.address`, `edc.fs.config`, `web.http.*` | unchanged | yes | leave as is |

**Note on DCP scopes.** `dcp-extension` has its own `dcp.scopes.*` namespace
(`dcp-extension/.../DcpConfig.java:51`, four fixed leaves read at `:72-79`), set
only in test fixtures under `dcp-extension/src/test/resources/valid/dcp/`. It is
*this repo's* configuration and is unrelated to upstream's `edc.iam.dcp.scopes.*`
(introduced 0.16.0, PR #5485). Do not conflate the two. Whether the hand-rolled
scope wiring should be replaced by upstream's now-dynamic DCP scope configuration
is a design question for the DCP step, not a config rename.

---

## 7. External component alignment

### 7.1 Eclipse IdentityHub — must move in lockstep

No IdentityHub coordinate appears anywhere in this repository — it is deployed
alongside fdsc-edc — so this is an external coordination item, not a code change
here. It is a hard one: the Connector and IdentityHub share the DCP wire contract,
and 0.18.1 exists on both sides, so they must be upgraded together.

- **IdentityHub is version-aligned with the Connector**: <https://github.com/eclipse-edc/IdentityHub/releases> carries the same tags on the same dates — v0.15.0 (2025-12-02), v0.15.1 (2025-12-18), v0.16.0 (2026-02-19), v0.17.0 (2026-04-20), v0.18.0 (2026-06-30), v0.18.1 (2026-09-28). Exception: IdentityHub has **no 0.16.1** (the Connector's 0.16.1 is a standalone backport).
- **STS did not move in 0.15.0.** The move of the STS modules from the Connector into IdentityHub was Connector PR #4839 (merged 2025-02-25, first released in **0.12.0**), paired with IdentityHub PR #623. At Connector v0.14.1 only the remote STS client remained (`extensions/common/iam/identity-trust/identity-trust-sts`), renamed to `decentralized-claims-sts` by the 0.15.0 DCP rename.
- **IdentityHub 0.15.0** breaking changes (<https://github.com/eclipse-edc/IdentityHub/releases/tag/v0.15.0>): stores refactored onto the new lease model (IdentityHub PR #801), improved `VerifiableCredentialResource` handling (#834), improved `KeyPair` handling (#836), **`participantId` renamed to `participantContextId`** (#845), ownership check added to the authorization service (#847), holder attestation (#861). Also notable: `identity-api` module renamed to `identity-api-configuration` (#820), customizable API-key alias (#860), anonymous credential requests (#837).
- **IdentityHub 0.18.0** breaking changes (<https://github.com/eclipse-edc/IdentityHub/releases/tag/v0.18.0>): profile strings replace the `CredentialFormat` enum for format matching (IdentityHub PR #993), Admin/Identity APIs bumped to **v1beta** (#998), Identity and Issuer Admin APIs migrated **from roles to scopes** (#1010, mirroring Connector #5805/#5812), participant-scoped vault auth switched to token exchange (#1014, mirroring Connector #5821).

### 7.2 Unaffected components

TMForum API, Scorpio, PostGIS and the ODRL-PAP / OPA pair are not EDC artifacts and
carry no version coupling to the Connector. They stay where they are. The only
indirect coupling is the ODRL vocabulary in `config/tck/scope-mappings.json` (§5).

### 7.3 DSP TCK runtime

`docker-compose.tck.yml` pins the TCK image at `1.0.0-RC6`. Released versions now go
up to **1.0.2**, and EDC 0.18.1 itself pins `dsp-tck:1.0.1` in its
`libs.versions.toml`. Re-pinning is part of the TCK step, and it must be done
*after* the baseline in §10 is captured, so that a TCK-version change and a
connector-version change never move at the same time.

---

## 8. Controlplane assembly

### 8.1 Callback dispatch — the `callback-http-dispatcher` question, answered

`callback-http-dispatcher` is discontinued (last published 0.17.0, 404 at 0.18.1)
and no artifact of that name replaces it. **HTTP callback delivery is not gone,
though — it moved inside `callback-event-dispatcher` at 0.18.0.** Evidence, from
listing and `javap`-ing the published jars:

| | 0.14.1 | 0.18.1 |
|---|---|---|
| `control-plane-spi` callback SPI | `CallbackRegistry`, `CallbackProtocolResolver`, `CallbackProtocolResolverRegistry`, `CallbackEventRemoteMessage` | `CallbackRegistry`, **`CallbackClient`** (the other three are gone) |
| `callback-event-dispatcher` | `CallbackEventDispatcher`, `CallbackEventDispatcherExtension`, `CallbackProtocolResolverRegistryImpl` | `CallbackEventDispatcher`, `CallbackEventDispatcherExtension`, **`CallbackEventDispatcherDefaultExtension`**, **`CallbackHttpClient`** |
| `callback-http-dispatcher` | `CallbackEventDispatcherHttpExtension`, `GenericHttpRemoteDispatcher(Impl)`, `GenericHttpDispatcherDelegate`, `CallbackEventRemoteMessage` | artifact does not exist |

At 0.18.1 `CallbackHttpClient implements CallbackClient` and does the POST itself
(`EdcHttpClient` + `ObjectMapper` + `Vault`, with a private
`resolveAuthCode(String, String)` that reads the callback's auth code out of the
vault); `CallbackEventDispatcherDefaultExtension` provides it as the default
`CallbackClient`. `CallbackEventDispatcher` now holds a `CallbackClient` instead of
routing through the `RemoteMessageDispatcherRegistry`, which was itself removed
upstream (PR #5814). `CallbackHttpClient` first appears in the 0.18.0 jar and is
absent from 0.16.1 and 0.17.0 — exactly matching the removal of the separate
module (PRs #5808 / #5815).

**Conclusion: delete the `callback-http-dispatcher` dependency and add nothing.**
`callback-event-dispatcher` (already declared at `controlplane-oid4vc/pom.xml:205`
and `controlplane-dcp/pom.xml:195`) carries the HTTP transport from 0.18.0 onward,
so the consumer-supplied callback addresses this connector stores as
`List<TMFCallbackAddress>` (`tmf-extension/.../domain/TMFContractNegotiation.java:24`)
keep being delivered. Because nothing about this is caught by `dependency:resolve`
or by compilation, the transfer/negotiation step must still prove it at runtime: a
negotiation with a `CallbackAddress` set, and an actual HTTP hit observed on the
far side.

### 8.2 Other assembly facts

- Bytecode target is still Java 17 at 0.18.1; the Java 21 toolchain is fine.
- `controlplane-base-bom` is a flat `<dependencies>` POM (31 deps at 0.14.1, 39 at
  0.18.1). Full diff, confirmed by fetching both POMs:
  - **removed:** `callback-http-dispatcher`, `transfer-data-plane-signaling`
  - **added:** `participant-context-connector-core`,
    `participant-context-connector-classic-core`, `control-plane-contract-manager`,
    `control-plane-transfer-manager`, `data-plane-signaling`,
    `data-plane-signaling-oauth2`, `catalog-crawler-core`, `console-monitor`,
    `federated-catalog-api`, `oauth2-client`
  - `auth-configuration` was **already** a BOM dependency at 0.14.1 — it is not a
    gain, and the assembly step must not treat it as new.

  `participant-context-connector-classic-core` is the **single-participant
  compatibility mode** and is the intended migration path for this connector (§4.4).
  Upstream renamed it from `participant-context-single-core` in 0.17.0 (PR #5598).
- **The BOM diff is informational only — BOM membership wires nothing here.**
  `controlplane-base-bom` is imported under `<dependencyManagement>` with
  `<scope>import</scope>` (`controlplane-oid4vc/pom.xml:13-21`,
  `controlplane-dcp/pom.xml` likewise), and every dependency in both controlplane
  POMs carries an explicit `${edc.version}` — so the BOM manages no version and
  contributes no artifact to the runtime. Consequence: each artifact to be added (`participant-context-connector-classic-core`,
  `data-plane-signaling`) **must be declared explicitly**; it does not arrive by
  the BOM gaining it. Equally, an artifact the BOM *dropped* is still wired here
  as long as the POM declares it — which is exactly the
  `transfer-data-plane-signaling` trap below.
- **`transfer-data-plane-signaling` is a silent carry-over, not a removal.** The
  0.18.1 BOM dropped it in favour of `data-plane-signaling`, but both controlplane
  POMs declare it explicitly (`controlplane-oid4vc/pom.xml:185`,
  `controlplane-dcp/pom.xml:175`) and **it still resolves at 0.18.1** (confirmed —
  `data-plane-signaling`, `data-plane-signaling-client`,
  `transfer-data-plane-signaling` and `data-plane-signaling-oauth2` all return 200
  at 0.18.1). So nothing in the pipeline catches it: `dependency:resolve` passes,
  compilation passes, and the connector keeps loading a module upstream
  deliberately removed from the default controlplane assembly. This is the same
  shape of runtime-only regression as `callback-http-dispatcher` (§8.1), and the
  dependency-coordinate step owes it an explicit keep-or-replace decision. Note both controlplane POMs already declare
  `data-plane-signaling-client` (`oid4vc:200`, `dcp:190`), which is the
  *client* module and not a substitute for either of the two above.
- Upstream now publishes `dsp-tck-connector-under-test` and `tck-extension` at
  0.18.1 — overlapping with this repo's hand-rolled `test-extension`.
- Stray tracked file `org/eclipse/edc/iam/identitytrust/spi/DcpConstants.java` sits
  at the repo root outside every module source root — dead code in a package that
  no longer exists upstream.


### 8.3 `DataFlowManager` is gone — `DataFlowController` is now a single injected service

Not previously recorded, found while mapping §3.2 and confirmed by `javap`:

- `org.eclipse.edc.connector.controlplane.transfer.spi.flow.DataFlowManager` exists in
  `transfer-spi` 0.14.1 and **is absent from `transfer-spi` 0.18.1** (removed in 0.16.0,
  PR #5435, "inline DataFlowManager").
- At 0.18.1 `TransferCoreExtension` and `TransferProcessorsImpl` take **one**
  `DataFlowController` by injection. There is no registry and no priority any more.
- `test-extension/.../edc/TckGuardExtension.java:50,88,151` imports `DataFlowManager`,
  `@Inject`s it and calls `dataFlowManager.register(noopPriority, new NoopDataFlowController(monitor))`.
  **This will not compile at 0.18.1.** `NoopDataFlowController` has to be contributed as a
  `@Provider`-ed `DataFlowController` instead, and the `noopPriority` constant becomes
  meaningless.
- Because exactly one `DataFlowController` can exist, the noop controller now *conflicts*
  with whatever else provides one. Both candidates do:
  `transfer-data-plane-signaling` 0.18.1 (`TransferDataPlaneSignalingExtension#dataFlowController()`,
  now holding only a `LegacyDataPlaneSignalingFlowController`) and
  `data-plane-signaling-core` (`DataPlaneSignalingFlowControllerExtension#dataFlowController()`,
  reached through the `data-plane-signaling` aggregator POM). A runtime that wires the noop
  controller **and** a signaling module will fail on a duplicate provider, so the TCK
  assembly has to pick one. **Owner: Step 6**, informed by Step 2's keep-or-replace call on
  `transfer-data-plane-signaling` (§8.2) — no compile or `dependency:resolve` gate catches
  a duplicate provider, it surfaces as a runtime startup failure.

This also sharpens §8.2's open item: at 0.18.1 `transfer-data-plane-signaling` is explicitly
the *legacy* flow controller and `data-plane-signaling(-core)` is the current one — which is
why the BOM swapped them. The artifact still resolving is not evidence that keeping it is
correct.

---

## 9. Open questions

### 9.1 Answered here

| Question | Answer | Where |
|---|---|---|
| Are `CallbackAddress` notifications still delivered without `callback-http-dispatcher`? | Yes. The HTTP transport moved into `callback-event-dispatcher` at 0.18.0 as `CallbackHttpClient implements CallbackClient`. Delete the dependency, add nothing, prove it at runtime. | §8.1 |
| `classic` or full multi-context participant core? | `classic` (`participant-context-connector-classic-core`). | §4.4 |
| Where does `participantContextId` live in the TMForum entity model? | Nowhere — it is not persisted. It is stamped onto mapped entities from configuration. | §4.3 |
| Is dropping `dsp-2024` / `dsp-spi-2024` behaviour-neutral? | Yes for this connector; not for 2024/1 counterparties. | §5 |
| Does `DataFlowResponseMessage` need renaming? | No. It is byte-identical at 0.18.1; only `DataFlowResponse` changed. | §3.2 |

### 9.2 Still open — decide during the migration

- **Should upstream's `tck-extension` / `dsp-tck-connector-under-test` replace the
  hand-rolled `test-extension`?** Both are published at 0.18.1 and overlap with what
  `test-extension` does (mock identity services, state-transition guards, a webhook
  controller). Not decided here: the overlap is partial — this repo's guards drive
  TMForum-backed stores and its webhook controller speaks a shape the local
  `tck.properties` expects — so swapping is a behaviour change to the conformance
  run itself, and it must not be made in the same step that moves the connector
  version. Recommendation: migrate `test-extension` as-is first, re-green the TCK,
  and only then evaluate the swap against the baseline in §10.
- **`transfer-data-plane-signaling` vs `data-plane-signaling`** — both resolve at
  0.18.1, the BOM dropped the former, this repo declares it explicitly. Needs an
  explicit keep-or-replace decision in Step 2 plus a runtime transfer check (§8.2), and
  Step 6 then has to make the TCK assembly provide exactly one `DataFlowController`
  (§8.3).
- **The `dcp.scopes.*` wiring** in `dcp-extension` predates upstream's dynamic DCP
  scope configuration (0.16.0, PR #5485) and its 0.18.0 `prefix.mapping` form.
  Keeping the hand-rolled version is valid; adopting upstream's is a separate
  change (§6.2).
- **DDL migrations do not apply to this connector, but do apply to its neighbours.**
  Upstream flagged several `BREAKING CHANGE` schema deltas in this range (new lease
  model and `resource_id`/`resource_kind` columns, `agr_agreement_id`,
  `data_address_alias`, `claims`, `private_entries`). This repo stores through
  TMForum and ships no SQL stores, so none of it is actionable here — but any
  co-deployed EDC runtime that *does* use the SQL stores needs those migrations.

### 9.3 Not confirmed upstream

- UNCONFIRMED: no upstream `MIGRATION.md` / upgrade guide exists for this range. Searched the Connector repo tree at v0.18.1 and `eclipse-edc/docs` (only release-process decision records, `developer/releases.md`). The closest thing to migration guidance is the migration section of `docs/developer/decision-records/2025-08-26-participant-context/README.md` (added by PR #5379), which covers only the participant-context DDL/ID question and recommends Flyway placeholders for the participant-context-id backfill.
- ~~UNCONFIRMED: `callback-http-dispatcher` last published in 0.17.0.~~ **Now confirmed** by the
  Maven Central audit in §2 (404 at 0.18.1, `maven-metadata.xml` highest version 0.17.0) and by
  the jar evidence in §8.1.
- UNCONFIRMED: exact DDL delta per release was not reconstructed from migration scripts; column changes above are taken from PR bodies flagged `BREAKING CHANGE` and from diffing the `*-schema.sql` files between tags v0.14.1 and v0.18.1.



---

## 10. TCK baseline at 0.14.1

Captured in Step 1, before the build moved: [`tck-baseline-0.14.1.md`](tck-baseline-0.14.1.md).
**65 tests, 65 passed, 0 failed** — fully green across `CAT`, `CN`, `CN_C`, `MET`,
`TP` and `TP_C`, on DSP TCK `1.0.0-RC6` against DSP `2025-1`.

Because the baseline is clean, the re-run gate after the upgrade is absolute: any
failure at 0.18.1 is a regression introduced by this migration. There is no
pre-existing failure to discount.

Two things the baseline file records that the migration must carry forward:

- **It is only green with a fix to `DelayedActionGuard`.** On the unmodified tree the
  suite hung indefinitely (twice, at different tests) because the guard dropped a
  queued action whenever the state machine still held the entity lease, stranding the
  entity as `pending`. The guard now retries the lease. This is harness code the
  `test-extension` migration step will touch — do not revert the retry while porting
  it to the 0.18.1 `StateEntityStore` signatures, where `save` returns
  `StoreResult<Void>` and `breakLease` becomes abstract (§3).
- **The TCK image must not move in the same step as the connector version** (§7.3).
  The baseline is pinned to `1.0.0-RC6`; re-pinning to `1.0.1` / `1.0.2` is a separate,
  later change so that a TCK-version difference can never be mistaken for a
  connector regression.
