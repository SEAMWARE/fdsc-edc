# Implementation Plan: FDSC-EDC upgrade to 0.18.1

## Overview

Upgrade `fdsc-edc` from Eclipse EDC `0.14.1` to `0.18.1` (six upstream releases:
0.15.0, 0.15.1, 0.16.0, 0.16.1, 0.17.0, 0.18.0, 0.18.1 — there is no 0.17.1).
The ticket scopes the *first* deliverable as a written compatibility overview; the
remaining steps carry out the migration module by module, ending with a green DSP
TCK conformance run and updated docs.

The dominant breaking change is upstream's **Participant Context** refactor
(0.15.0–0.17.0), which threads a `ParticipantContext` through the protocol services
and puts a `participantContextId` on every control-plane entity. Second is the
**DCP module rename** (`identity-trust-*` → `decentralized-claims-*`), third is the
**removal of DSP 0.8 and 2024/1**, which this repo still wires in.

### Working agreement for this plan

- All steps land on the work branch `ticket-72/work`, one PR per step.
- The six extension modules depend only on EDC artifacts and the parent POM —
  **not on each other** — so each can be migrated and verified in isolation with
  `mvn -pl <module> test`. Only `controlplane-oid4vc` / `controlplane-dcp`
  aggregate them.
- Consequently the **full reactor is expected to stay red from Step 2 until
  Step 7**. Each step's acceptance criterion is its *own* module going green;
  `mvn clean package` green across the reactor is the exit criterion of Step 7.
- Do not merge `ticket-72/work` into `main` before Step 9 passes.

### Verified findings (do not re-research)

These were confirmed directly against Maven Central and by `javap` on the published
0.14.1 / 0.18.1 jars. Treat as ground truth.

**Artifacts that no longer exist at 0.18.1** (6 of the 72 EDC artifacts this repo declares):

| Current (0.14.1) | Last published | Replacement at 0.18.1 |
|---|---|---|
| `identity-trust-core` | 0.14.1 | `decentralized-claims-core` |
| `identity-trust-spi` | 0.14.1 | `decentralized-claims-spi` |
| `identity-trust-transform` | 0.14.1 | `decentralized-claims-transform` |
| `dsp-2024` | 0.15.1 | *none* — DSP 2024/1 support deleted in 0.16.0 |
| `dsp-spi-2024` | 0.15.1 | *none* — same |
| `callback-http-dispatcher` | 0.17.0 | *none found* — **needs verification** (Step 1) |

The other 66 artifacts resolve unchanged at 0.18.1. Beware a false friend: `dcp-core`
and `dcp-spi` **do** exist at 0.18.1 but are **IdentityHub-side** artifacts
(`dcp-identityhub-core`, `dcp-issuer-*`); the Connector-side replacement for
`identity-trust-*` is `decentralized-claims-*`, package
`org.eclipse.edc.iam.decentralizedclaims.*`.

**SPI signature changes confirmed by `javap`:**

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
```

`ParticipantContext` lives in the **new** artifact `connector-participant-context-spi`
as `org.eclipse.edc.participantcontext.spi.types.ParticipantContext`.

`ContractDefinitionStore`, `ContractNegotiationStore`, `AssetIndex` and
`PolicyDefinitionStore` declare **no** changed methods of their own — but
`ContractNegotiationStore` inherits the two `StateEntityStore` breaks above.
`PendingGuard<E>` and `EventSubscriber` are **unchanged**, so the existing guards
and trigger subscribers survive.

**Other confirmed facts:**

- Bytecode target is still Java 17 at 0.18.1; the Java 21 toolchain is fine.
- `controlplane-base-bom` at 0.18.1 no longer carries `callback-http-dispatcher`;
  it gained `participant-context-connector-core`,
  `participant-context-connector-classic-core`, `control-plane-contract-manager`,
  `control-plane-transfer-manager`, `data-plane-signaling`, `auth-configuration`.
  `participant-context-connector-classic-core` is the **single-participant
  compatibility mode** and is the intended migration path for this connector.
- Upstream now publishes `dsp-tck-connector-under-test` and `tck-extension` at
  0.18.1 — overlapping with this repo's hand-rolled `test-extension`.
- The DSP TCK runtime image has a released **1.0.2**; this repo pins `1.0.0-RC6`.
  EDC 0.18.1 itself pins `dsp-tck:1.0.1` in its `libs.versions.toml`.
- IdentityHub releases are version-aligned 1:1 with the Connector and **0.18.1
  exists**. No IdentityHub reference exists inside this repo — it is deployed
  alongside fdsc-edc, so its alignment is an external compatibility finding.
- Config key `edc.iam.issuer.id` was renamed to `edc.participant.did` (0.17.0,
  old key deprecated but still honoured); `edc.dsp.well-known-path.enabled` was
  **removed** in 0.17.0; a new `edc.participant.context.id` must be stable across
  restarts.
- Stray tracked file `org/eclipse/edc/iam/identitytrust/spi/DcpConstants.java` sits
  at the repo root outside every module source root — dead code in a package that
  no longer exists upstream.

## Steps

### Step 1: Migration overview and compatibility report

The ticket's explicit first deliverable: a written overview of what changes, what is
incompatible, and what adjacent components must move in lockstep. No code changes.

Create `docs/migration/EDC-0.18.1-UPGRADE.md` containing:

- **Release-by-release summary** of 0.15.0 → 0.18.1 with the breaking change for
  each, linked to the upstream release notes and PR numbers.
- **Artifact coordinate table** — the six removed artifacts and their replacements,
  reproduced from the "Verified findings" section above, plus the full list of the
  72 declared EDC artifacts with a resolve-status column.
- **API break inventory** — the `javap`-confirmed signature changes above, each
  mapped to the concrete files in this repo that implement them:
  `TMFBackedContractNegotiationStore`, `TMForumBackedCatalogProtocolService`,
  `OID4VPIdentityService`, `TestIdentityService`, `NoopAudienceResolver`,
  `CounterPartyAddressAudienceResolver`.
- **Participant Context impact analysis** — the central open design question: the
  TMForum-backed stores have no SQL schema, so decide and record *where*
  `participantContextId` lives in the TMForum entity model, and whether to adopt
  `participant-context-connector-classic-core` (single-participant compatibility)
  or the full multi-context core. Recommend `classic`.
- **DSP protocol impact** — DSP 0.8 and 2024/1 are gone; the repo already targets
  `2025-1` in `config/tck/tck.properties`, so confirm that dropping `dsp-2024` /
  `dsp-spi-2024` is behaviour-neutral for the TCK, and state the consequence for
  any external counterparty still on 2024/1.
- **Config migration table** — `edc.iam.issuer.id` → `edc.participant.did`,
  removal of `edc.dsp.well-known-path.enabled`, addition of
  `edc.participant.context.id`, and the DCP scope config move to map-based
  `...prefix.mapping`. Cross-check every key in `config/tck/edc.properties`
  and the Helm/deployment values that feed the controlplane images.
- **External component alignment** — IdentityHub must move to 0.18.1 together with
  the connector (STS moved out of the Connector into IdentityHub in 0.15.0;
  IdentityHub 0.18.0 replaced the `CredentialFormat` enum with profile strings,
  bumped Admin APIs to v1beta and swapped roles for scopes). Flag the TMForum,
  Scorpio and ODRL-PAP images as unaffected.
- **Open questions** — at minimum: the `callback-http-dispatcher` replacement
  (verify against the 0.18.0 release notes and the BOM; if callbacks are now
  dispatched without it, record that), and whether to adopt upstream's
  `tck-extension` in place of the hand-rolled `test-extension`.

Acceptance: the document resolves every "needs verification" item above or states
explicitly why it remains open, and Steps 2–9 can be executed from it without
further upstream research.

### Step 2: Bump `edc.version` and migrate dependency coordinates

Mechanical dependency work only — make the build *resolve* against 0.18.1.
Expect compilation to fail afterwards; that is this step's output, not a defect.

- `pom.xml`: `edc.version` `0.14.1` → `0.18.1`.
- `dcp-extension/pom.xml`: `identity-trust-core` → `decentralized-claims-core`,
  `identity-trust-spi` → `decentralized-claims-spi`,
  `identity-trust-transform` → `decentralized-claims-transform`. Also remove the
  duplicate `catalog-spi` declaration (declared twice, lines 62 and 67).
- `controlplane-oid4vc/pom.xml` and `controlplane-dcp/pom.xml`: drop `dsp-2024` and
  `callback-http-dispatcher`; apply the `callback-http-dispatcher` resolution from
  Step 1.
- `tmf-extension/pom.xml` and `test-extension/pom.xml`: drop `dsp-spi-2024`.
- Add `connector-participant-context-spi` to `tmf-extension` (needed for
  `ParticipantContext` in Step 3) and add
  `participant-context-connector-classic-core` to both controlplane modules, per
  the Step 1 decision.
- Delete the stray `org/eclipse/edc/iam/identitytrust/spi/DcpConstants.java` at the
  repo root; if anything actually references it, move it into a module source root
  under an `org.seamware` package instead.

Acceptance: `mvn -DskipTests dependency:resolve` succeeds for every module (no
`Could not resolve` / 404). Capture the full `mvn -DskipTests compile` error output
and attach it to the PR description as the breakage inventory driving Steps 3–6.

### Step 3: Migrate `tmf-extension` to the Participant Context store SPIs

The largest module (56 main + 28 test files) and the one the Participant Context
refactor hits hardest.

- `store/TMFBackedContractNegotiationStore.java`: change `save(T)` to return
  `StoreResult<Void>` and implement the new `breakLease(T)`. Implement
  `breakLease` consistently with the existing lease handling in
  `AutomaticUnlockingLockManager` — do not stub it to a no-op, protocol services
  now call it explicitly.
- `store/TMForumBackedCatalogProtocolService.java`: adopt the new
  `getCatalog(ParticipantContext, ...)` and
  `getDataset(ParticipantContext, DatasetRequestMessage, TokenRepresentation)`
  signatures. Note `getDataset` changed shape, not just arity — the dataset id now
  arrives inside `DatasetRequestMessage`.
- `store/TMFBackedAssetIndex.java`, `TMFBackedContractDefinitionStore.java`,
  `TMFBackedPolicyDefinitionStore.java`: their declared methods are unchanged, but
  entities now carry `participantContextId`. Implement the persistence and
  `QuerySpec`/`Criterion` filtering decided in Step 1, in `TMFEdcMapper`.
- `TMFEdcMapper.java`: map `participantContextId` in both directions. Introduce it
  as a named constant alongside the existing TMForum field-name constants — no
  inline literals.
- Update the 28 test files; prefer `@ParameterizedTest` where a new participant-context
  dimension multiplies existing cases rather than copying test methods.

Acceptance: `mvn -pl tmf-extension test` green; `mvn -pl tmf-extension spotless:check` clean.

### Step 4: Migrate the identity stack (`dcp-extension`, `oid4vc-extension`)

- `dcp-extension`: rewrite imports from `org.eclipse.edc.iam.identitytrust.*` to
  `org.eclipse.edc.iam.decentralizedclaims.*`. `IdentityAndTrustService` →
  `DcpIdentityService`, `IdentityAndTrustExtension` → `DcpCoreExtension`,
  `IdentityAndTrustTransformExtension` → `DcpTransformExtension`. Affected files
  include `DCPExtension.java`, `TirExtension.java`,
  `FixedJwtToVerifiableCredentialTransformer.java` (its superclass
  `JwtToVerifiableCredentialTransformer` moved package),
  `tir/EbsiTrustedIssuersRegistry.java` (`TrustedIssuerRegistry`) and
  `policy/CredentialScopeExtractorRegistry.java` (`ScopeExtractorRegistry`).
- Migrate DCP scope configuration to the map-based `...prefix.mapping` form; the
  dashed `prefix-mapping` variant is deprecated.
- `oid4vc-extension/identity/OID4VPIdentityService.java`: adopt the new
  `IdentityService` signatures carrying `participantContextId`, and thread that id
  through to the OID4VP client rather than ignoring it.
- `oid4vc-extension/identity/CounterPartyAddressAudienceResolver.java`: change
  `resolve(RemoteMessage)` to `resolve(ProtocolRemoteMessage)`.
- `oid4vc-extension/identity/OID4VPParticipantIdExtractionFunction.java`: verify
  against upstream's `DefaultDcpParticipantIdExtractionFunction`, since 0.18.0 moved
  principal identity into the `sub` claim and dropped the `participant_context_id`
  and `role` claims.
- Note `edc.iam.issuer.id` → `edc.participant.did`: this repo never reads that key,
  so there is nothing to rename here, but `DCPExtension` should be checked for any
  hard-coded issuer/DID resolution that upstream now derives from the new key.

Acceptance: `mvn -pl dcp-extension test` and `mvn -pl oid4vc-extension test` green.

### Step 5: Migrate `fdsc-transfer-extension` and `policy-extension`

- `fdsc-transfer-extension`: the control-plane `provisioner` API was cleaned up in
  0.16.0 and `DataAddress` now lives in the vault rather than the store (0.16.0) —
  rework `FDSCProvisioner`, `FDSCDcpProvisioner`, `FDSCOID4VPProvisioner`,
  `FDSCProvisionedResource` and the two `*EndpointDataReferenceService`
  implementations accordingly. `dataplaneId` was removed from
  `DataFlowResponseMessage` (0.17.0). The Data Plane Framework is deprecated in
  favour of data-plane-signaling, which is the BOM default at 0.18.0 — confirm
  `FDSCEndpointGenerator` and `FDSCDataAddress` still fit the signaling flow.
- `policy-extension`: check `PolicyContextInputMapper`, `ScopeMappingRule` and
  `OdrlPapPolicyExtension` against the new `ParticipantAgentPolicyContext`
  (introduced 0.15.0) and the request-policy-context SPI. Claims are now stored on
  `ContractAgreement` / `TransferProcess` (0.17.0), which may simplify how the PAP
  input is assembled.

Acceptance: `mvn -pl fdsc-transfer-extension test` and `mvn -pl policy-extension test`
green.

### Step 6: Migrate `test-extension` and decide on upstream's `tck-extension`

- Act on the Step 1 decision about upstream's `tck-extension` / 
  `dsp-tck-connector-under-test` (both published at 0.18.1). If adopted, delete the
  overlapping hand-rolled pieces rather than running both; if not, record in the PR
  why the local harness is retained.
- `services/TestIdentityService.java`: new `IdentityService` signatures.
- `services/NoopAudienceResolver.java`: `resolve(ProtocolRemoteMessage)`.
- `services/TestIdentityExtension.java`: 0.17.0 added a runtime-provided default
  `TokenValidationService`; the local override may now clash — register it as a
  default or remove it.
- `edc/DataAssembly.java`: entities need `participantContextId` set consistently
  with Step 3 and with `edc.participant.context.id`.
- `edc/ContractNegotiationGuard.java`, `edc/TransferProcessGuard.java`,
  `edc/DelayedActionGuard.java` and both trigger subscribers: `PendingGuard` and
  `EventSubscriber` are unchanged, so these should need no signature work — but
  protocol services now call `breakLease` explicitly, so re-check the interaction
  with `StepRecorder`. Handle the new `TransferProcessPrepared` event.
- `edc/NoopDataFlowController.java` and `edc/ProviderRequestedStateFixListener.java`:
  verify against the data-plane-signaling defaults.

Acceptance: `mvn -pl test-extension test` green, **and** `mvn clean package` green
across the whole reactor — this is the step where the build comes back.

### Step 7: Update runtime configuration and controlplane assembly

- `config/tck/edc.properties`: apply the Step 1 config migration table — add
  `edc.participant.context.id` (stable value matching `edc.participant.id`) and drop
  any 2024/1 protocol wiring. Note that neither `edc.iam.issuer.id` nor
  `edc.dsp.well-known-path.enabled` is set anywhere in this repo, so those two
  upstream changes are no-ops here — but both *are* likely set in the surrounding
  FIWARE Data Space Connector deployment (Helm values), so they belong in the
  operator-facing notes of Step 9 rather than in a code change.
- Verify the DSP protocol path still resolves to `/protocol/2025-1` and that
  `GET /.well-known/dspace-version` answers, now that the version toggle is gone.
- `docker/Dockerfile`, `docker/Dockerfile.tck`: review the
  `eclipse-temurin:24_36-jre-alpine` base against 0.18.1 (bytecode target is 17,
  runtime support extends to Java 25 — no change forced, but confirm).
- Re-run `./scripts/verify-tck-config.sh` and extend it if the new keys are not
  covered.

Acceptance: both controlplane images build; a container starts against the TCK
compose stack and logs no missing-config or unresolved-service errors.

### Step 8: Re-pin and green the DSP TCK conformance run

- Bump `DSP_TCK_VERSION` from `1.0.0-RC6` in `docker-compose.tck.yml` (line 237),
  `.github/workflows/test.yml` (line 8) and the comment block at
  `docker-compose.tck.yml:19`. EDC 0.18.1 pins `dsp-tck:1.0.1`; the TCK runtime image
  has a released `1.0.2`. Prefer the version EDC itself pins unless 1.0.2 passes
  cleanly — record the choice.
- `scripts/init-tmforum.sh`: if `participantContextId` became part of the TMForum
  entity shape in Step 3, the seeded test data must carry it.
- Run `./scripts/run-tck.sh` and drive the suite to the same pass set as on 0.14.1.
  Any test that passed before and fails now is a regression to fix, not to filter out.

Acceptance: the TCK job passes locally and in CI with no new entries in
`docker/tck-filter`.

### Step 9: Update documentation and release notes

- `AGENTS.md`: update the EDC version, the module/artifact names changed in Step 2,
  the DSP protocol note, and the TCK architecture section if Step 6 adopted
  upstream's `tck-extension`.
- `README.md` and `TRANSACTION_README.md`: update version references and any
  renamed config keys.
- Add `releases/notes/RELEASE_NOTES_EDC_0_18_1.md` covering the operator-facing
  changes: required config renames, the IdentityHub 0.18.1 lockstep requirement,
  and the loss of DSP 2024/1 support for counterparties.
- Label the final PR `minor` or `major` per the repo's semver-label convention —
  the dropped DSP 2024/1 support and the config renames argue for `major`.

Acceptance: `mvn clean package` and `mvn spotless:check` green, TCK green, and
`docs/migration/EDC-0.18.1-UPGRADE.md` updated to mark each item as resolved.
