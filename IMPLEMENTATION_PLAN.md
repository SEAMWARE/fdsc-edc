# Implementation Plan: FDSC-EDC upgrade to 0.18.1

## Overview

Upgrade `fdsc-edc` from Eclipse EDC `0.14.1` to `0.18.1` (seven upstream releases:
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
- **The extension modules are not all independent.** Four of the six
  (`tmf-extension`, `test-extension`, `oid4vc-extension`, `dcp-extension`) depend
  only on EDC artifacts and the parent POM, but two have compile-scoped sibling
  dependencies:

  ```
  policy-extension  depends on  fdsc-transfer-extension  depends on  tmf-extension
      (policy-extension/pom.xml:18)        (fdsc-transfer-extension/pom.xml:25)
  ```

  So the build order is `tmf-extension` → `fdsc-transfer-extension` →
  `policy-extension`.

  Two consequences, both binding on the step order below:
  - **Always use `mvn -pl <module> -am test`**, never a bare `-pl`. A bare `-pl`
    resolves the sibling from `~/.m2` at `0.0.1-SNAPSHOT`, where it is either
    absent or a stale 0.14.1 build — so the step would verify against the wrong
    code or fail for an unrelated reason. Alternatively end each step with
    `mvn -pl <module> install -DskipTests`, but `-am` is the safer default
    because it cannot leave a stale artifact behind.
  - **Step 5 is hard-blocked by Step 3**, and *within* Step 5
    `fdsc-transfer-extension` must be migrated before `policy-extension`. The
    step order below already satisfies this — do not reorder the steps on the
    assumption that the modules are independent.

  Only `controlplane-oid4vc` / `controlplane-dcp` aggregate all six.
- Consequently the **full reactor is expected to stay red from Step 2 until
  Step 6**. Each step's acceptance criterion is its *own* module going green.
  The two reactor-wide gates are split so that neither step is asked to pass a
  gate depending on work scoped to the next one:
  - **Step 6** — `mvn clean test` green across the reactor (every module
    compiles, all unit tests pass). This is where the build comes back.
  - **Step 7** — `mvn clean package` green across the reactor. `package` also
    builds the two controlplane Docker images, which is Step 7's own deliverable.
- `.github/workflows/test.yml` triggers on every push, so **CI will be red on the
  PRs for Steps 2–5 by design**. Say so in each of those PR descriptions; a red
  check there is the planned state, not breakage. CI is expected green from
  Step 6 onwards.
- Do not merge `ticket-72/work` into `main` before Step 9 passes.
- **Known-wrong line in `AGENTS.md`, read this instead.** The doc-comment bullet in
  `AGENTS.md` says `@param`/`@return` blocks are "used only in config classes".
  That is false for this codebase: 19 of the 138 `src/main/java` files use them —
  all 7 files of `policy-extension`, 5 in `tmf-extension`, 4 in
  `fdsc-transfer-extension`, 2 in `test-extension` and 1 in `oid4vc-extension`.
  Read it as "concentrated in config classes and the policy extension". The
  surrounding density guidance is sound (69 of 138 files carry a `/**` block, so
  "roughly half" holds) and still applies — match the density of the file you are
  editing. The line is corrected in `AGENTS.md` itself in Step 9, which owns that
  file; it is flagged here so no agent running Steps 2–8 acts on the wrong
  version.

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
| `callback-http-dispatcher` | 0.17.0 | *none* — **behaviour change, see below** (Step 1) |

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

`ParticipantContext` lives in the **new** artifact `connector-participant-context-spi`
(404 at 0.14.1) as `org.eclipse.edc.participantcontext.spi.types.ParticipantContext`.

**Second false friend — pick the right participant artifact.** Three similarly named
artifacts resolve at 0.18.1 and only the first is the one this plan means:

| Artifact | Package root | What it is |
|---|---|---|
| `connector-participant-context-spi` | `org.eclipse.edc.participantcontext.spi` | **Correct.** Connector-side `ParticipantContext`, `ParticipantContextService`, `ParticipantContextStore`. New at 0.18.1. |
| `participant-context-spi` | `org.eclipse.edc.identityhub.spi.participantcontext` | IdentityHub-side. Its `ParticipantContext` was **renamed** `IdentityHubParticipantContext` at 0.18.1 and its store/state types moved to the connector artifact. |
| `participant-spi` | `org.eclipse.edc.participant.spi` | Unrelated to participant *context* — `ParticipantAgent`, `ParticipantAgentPolicyContext`, `ParticipantIdMapper`. **This repo already declares it**; do not mistake it for either of the above. |

`ContractDefinitionStore`, `ContractNegotiationStore`, `AssetIndex` and
`PolicyDefinitionStore` declare **no** changed methods of their own — but
`ContractNegotiationStore` inherits the two `StateEntityStore` breaks above.
`PendingGuard<E>` and `EventSubscriber` are **unchanged**, so the existing guards
and trigger subscribers survive.

**Other confirmed facts:**

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
    gain, and Step 7 should not treat it as new.

  `participant-context-connector-classic-core` is the **single-participant
  compatibility mode** and is the intended migration path for this connector.
- **The BOM diff is informational only — BOM membership wires nothing here.**
  `controlplane-base-bom` is imported under `<dependencyManagement>` with
  `<scope>import</scope>` (`controlplane-oid4vc/pom.xml:13-21`,
  `controlplane-dcp/pom.xml` likewise), and every dependency in both controlplane
  POMs carries an explicit `${edc.version}` — so the BOM manages no version and
  contributes no artifact to the runtime. Consequence for Steps 2 and 7: each
  artifact this plan wants added (`participant-context-connector-classic-core`,
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
  shape of runtime-only regression as `callback-http-dispatcher`, and Step 2 owes
  it an explicit decision. Note both controlplane POMs already declare
  `data-plane-signaling-client` (`oid4vc:200`, `dcp:190`), which is the
  *client* module and not a substitute for either of the two above.
- **`callback-http-dispatcher` is a functional regression risk, not a coordinate
  removal.** The artifact is discontinued outright (last published 0.17.0; 404 at
  0.18.1) and no replacement exists under any name — the only remaining
  `*-http-dispatcher` artifacts at 0.18.1 are DSP *protocol* message dispatchers
  (`dsp-catalog-`, `dsp-negotiation-`, `dsp-transfer-process-http-dispatcher`),
  not callback transports. Meanwhile `callback-event-dispatcher` and
  `callback-static-endpoint` both **still exist at 0.18.1** and are both declared
  by this repo (`controlplane-oid4vc/pom.xml:205,215`;
  `controlplane-dcp/pom.xml:195,205`). Removing only the HTTP dispatcher therefore
  leaves the event dispatcher and static endpoints wired with **no HTTP
  transport**. This matters concretely here:
  `tmf-extension/.../domain/TMFContractNegotiation.java:24` persists a
  `List<TMFCallbackAddress>`, i.e. the connector stores consumer-supplied callback
  addresses it is expected to dispatch to. No `dependency:resolve` or compile gate
  will catch this — it needs a runtime check (see Step 1 open questions and the
  Step 7 acceptance criterion).
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
incompatible, and what adjacent components must move in lockstep. The only code-
adjacent task is capturing the TCK baseline (last bullet), which must happen while
the build is still at 0.14.1 and green.

**Move and extend, do not rewrite.** The "Verified findings" section above is the
seed of this document, not a parallel copy of it. Cut that section out of
`IMPLEMENTATION_PLAN.md`, paste it into `docs/migration/EDC-0.18.1-UPGRADE.md`,
and leave behind a one-line pointer to the new file. Two copies of the same
artifact table will drift; there must be exactly one.

Create `docs/migration/EDC-0.18.1-UPGRADE.md` containing:

- **Release-by-release summary** of 0.15.0 → 0.18.1 with the breaking change for
  each, linked to the upstream release notes and PR numbers.
- **Artifact coordinate table** — the six removed artifacts and their replacements,
  moved over from the "Verified findings" section above, plus the full list of the
  72 declared EDC artifacts with a resolve-status column, and the two false-friend
  warnings (`dcp-*`, `*participant-context-spi`) recorded there.
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
  `2025-1` in `config/tck/tck.properties` and already declares `dsp-2025` /
  `dsp-spi-2025` next to the 2024 artifacts, so confirm that dropping `dsp-2024` /
  `dsp-spi-2024` is behaviour-neutral for the TCK, and state the consequence for
  any external counterparty still on 2024/1. Record explicitly that the 2024/1
  leftOperand IRI in `config/tck/scope-mappings.json:8` is ODRL policy vocabulary,
  **not** protocol wiring, and must survive the removal (see Step 7).
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
- **Open questions** — at minimum:
  - **Are EDC `CallbackAddress` notifications still delivered at 0.18.1 without
    `callback-http-dispatcher`, and if so by which artifact?** The coordinate is
    gone with no same-named replacement (see "Verified findings"), while
    `callback-event-dispatcher` and `callback-static-endpoint` remain wired.
    Answer it concretely: read `callback-event-dispatcher`'s 0.18.1 extension to
    see what `CallbackRegistry`/transport it now resolves, and name the artifact
    that must be added in its place — or record that HTTP callback dispatch is
    genuinely gone and what that costs the TMForum callback addresses stored in
    `TMFContractNegotiation`. Do not close this one as "resolve succeeds".
  - Whether to adopt upstream's `tck-extension` in place of the hand-rolled
    `test-extension`.
- **TCK baseline capture** (do this *first*, while the tree is still at 0.14.1 and
  green): run `./scripts/run-tck.sh` with `TCK_FILTER` unset and commit the
  resulting per-test pass/fail list as `docs/migration/tck-baseline-0.14.1.md`.
  The repo records no TCK baseline anywhere today, so without this file Step 8 has
  no way to tell a genuine regression from a test that never passed — and by then
  the 0.14.1 build no longer exists on the branch. Note in the file whether the
  run was fully green or already had failures.

Acceptance: the document resolves every "needs verification" item above or states
explicitly why it remains open; `docs/migration/tck-baseline-0.14.1.md` is
committed; the "Verified findings" section has been moved out of this plan rather
than copied; and Steps 2–9 can be executed from it without further upstream
research.

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
- **Decide `transfer-data-plane-signaling` explicitly** in both controlplane POMs
  (`controlplane-oid4vc/pom.xml:185`, `controlplane-dcp/pom.xml:175`). It still
  resolves at 0.18.1, so it will not surface on its own — see "Other confirmed
  facts" above. Two admissible outcomes, and the PR description must state which
  and why:
  - **Drop it and declare `data-plane-signaling` in its place** (plus
    `data-plane-signaling-oauth2` only if the OAuth2 data-plane path is actually
    used — this repo does not currently declare it), following upstream's own
    controlplane assembly; or
  - **keep it**, with a recorded reason naming what in
    `fdsc-transfer-extension` still needs it.

  Because the BOM is import-scoped here, the replacement must be declared with an
  explicit `${edc.version}` like every other dependency in those POMs. Whichever
  way this goes, it is a runtime-behaviour change that no compile gate covers, so
  it folds into Step 7's data-flow verification.
- Delete the stray `org/eclipse/edc/iam/identitytrust/spi/DcpConstants.java` at the
  repo root; if anything actually references it, move it into a module source root
  under an `org.seamware` package instead.

Acceptance: `mvn -DskipTests dependency:resolve` emits **no
`Could not resolve ... org.eclipse.edc:*`** line. Note that the command as a whole
is expected to fail: `controlplane-oid4vc` and `controlplane-dcp` depend on the
five extension modules at `0.0.1-SNAPSHOT`, which this step deliberately leaves
non-compiling and therefore uninstalled — a red exit code caused by
`org.seamware.edc:*-extension` is the planned state. Only EDC coordinates are
being proved here. Capture the full `mvn -DskipTests compile` error output and
attach it to the PR description as the breakage inventory driving Steps 3–6.

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

Acceptance: `mvn -pl tmf-extension -am test` green; `mvn -pl tmf-extension spotless:check`
clean. (`tmf-extension` has no sibling dependency, so `-am` only pulls in the parent
POM here — but use it anyway for consistency with Steps 4 and 5.)

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

Acceptance: `mvn -pl dcp-extension -am test` and `mvn -pl oid4vc-extension -am test` green.
Neither module depends on a sibling, so these two can be done in either order.

### Step 5: Migrate `fdsc-transfer-extension` and `policy-extension`

- `fdsc-transfer-extension`: the control-plane `provisioner` API was cleaned up in
  0.16.0 and `DataAddress` now lives in the vault rather than the store (0.16.0) —
  rework `FDSCProvisioner`, `FDSCDcpProvisioner`, `FDSCOID4VPProvisioner`,
  `FDSCProvisionedResource` and the two `*EndpointDataReferenceService`
  implementations accordingly. The Data Plane Framework is deprecated in
  favour of data-plane-signaling, which is the BOM default at 0.18.0 — confirm
  `FDSCEndpointGenerator` and `FDSCDataAddress` still fit the signaling flow.
- **Data-flow API renames** (`javap`-confirmed, see the API break inventory):
  `DataFlowResponse` (artifact `transfer-spi`) kept `dataPlaneId` but renamed
  `boolean provisioning` / `isProvisioning()` → `boolean async` / `isAsync()` and
  gained `toBuilder()`; `DataFlowController.provision(TransferProcess, Policy)` was
  renamed `prepare(...)` and the interface gained four more abstract methods —
  `resume(TransferProcess)`, `started(TransferProcess)`,
  `completed(TransferProcess)` and a `transferTypesFor(String)` overload. The four
  do not share a return type — `resume` returns `StatusResult<DataFlowResponse>`,
  not `StatusResult<Void>`; see the table in Step 6.
  `DataFlowResponseMessage` (artifact `core-spi`) is **byte-identical** between
  0.14.1 and 0.18.1 — it never had a `dataPlaneId`, so nothing was removed there.
  Audit `fdsc-transfer-extension` for `DataFlowController` implementations and
  `DataFlowResponse` builder calls; the one in `test-extension` is Step 6's.
- `policy-extension`: check `PolicyContextInputMapper`, `ScopeMappingRule` and
  `OdrlPapPolicyExtension` against the new `ParticipantAgentPolicyContext`
  (introduced 0.15.0) and the request-policy-context SPI. Claims are now stored on
  `ContractAgreement` / `TransferProcess` (0.17.0), which may simplify how the PAP
  input is assembled.

Acceptance: `mvn -pl fdsc-transfer-extension -am test` and `mvn -pl policy-extension -am test`
green. The `-am` is load-bearing in this step: `fdsc-transfer-extension` depends on the
`tmf-extension` migrated in Step 3 and `policy-extension` depends on
`fdsc-transfer-extension`, so a bare `-pl` resolves a stale or missing
`0.0.1-SNAPSHOT` sibling. Migrate `fdsc-transfer-extension` first.

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
- `edc/NoopDataFlowController.java`: **will not compile at 0.18.1** — three
  confirmed breaks. Rename the `provision(TransferProcess, Policy)` override
  (line 58) to `prepare(...)`; change `.provisioning(false)` to `.async(false)` in
  both `DataFlowResponse` builders (lines 64 and 75); and implement the four
  methods `DataFlowController` added — `resume(TransferProcess)`,
  `started(TransferProcess)`, `completed(TransferProcess)` and the
  `transferTypesFor(String)` overload. **The four do not share a return type**, so
  a blanket `StatusResult.success(...)` will not compile:

  | New method | Return type | No-op implementation |
  |---|---|---|
  | `resume(TransferProcess)` | `StatusResult<DataFlowResponse>` | needs a `DataFlowResponse` payload — build one from the same `noopDataAddress()` helper the existing `provision`/`start` bodies use; `StatusResult.success()` with no argument does **not** satisfy it |
  | `started(TransferProcess)` | `StatusResult<Void>` | `StatusResult.success()` |
  | `completed(TransferProcess)` | `StatusResult<Void>` | `StatusResult.success()` |
  | `transferTypesFor(String)` | `Set<String>` | mirror the existing `transferTypesFor(Asset)` body |

  Make these choices deliberately rather than by autocompletion: the TCK drives
  transfer state through these callbacks. Registration at
  `TckGuardExtension.java:149` is unaffected.
- `edc/ProviderRequestedStateFixListener.java`: verify against the
  data-plane-signaling defaults.

Acceptance: `mvn -pl test-extension -am test` green, **and** `mvn clean test` green
across the whole reactor — this is the step where the build comes back. The
image-producing `mvn clean package` gate belongs to Step 7, which owns the
controlplane assembly those images depend on.

### Step 7: Update runtime configuration and controlplane assembly

- `config/tck/edc.properties`: apply the Step 1 config migration table — add
  `edc.participant.context.id` (stable value matching `edc.participant.id`) and drop
  any 2024/1 protocol *wiring* — of which there is in fact none in that file, so
  expect this to be a no-op. **Do not confuse protocol wiring with the ODRL
  vocabulary.** `config/tck/scope-mappings.json:8` matches the leftOperand IRI
  `https://w3id.org/dspace/2024/1/membershipType`, which maps to the
  `contract.negotiation` and `catalog` scopes. That is a policy vocabulary
  identifier consumed by `policy-extension` via
  `odrlPap.policy.scopeMappingsPath` (`config/tck/edc.properties:140`) — it has
  nothing to do with the DSP 2024/1 *protocol binding*, and deleting it on a
  string match for "2024" would silently break scope mapping for negotiation and
  catalog. **Leave that IRI untouched.** Dropping `dsp-2024` / `dsp-spi-2024` is
  behaviour-neutral precisely because `dsp-2025` and `dsp-spi-2025` are already
  declared alongside them (`controlplane-oid4vc/pom.xml:80`,
  `controlplane-dcp/pom.xml:69`, `tmf-extension/pom.xml:153`,
  `test-extension/pom.xml:128`) and `config/tck/tck.properties` already targets
  `2025-1`. Note that neither `edc.iam.issuer.id` nor
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
- **Exercise a callback delivery end to end**, acting on the Step 1 answer about
  `callback-http-dispatcher`. Run a negotiation against the compose stack with a
  consumer-supplied HTTP `CallbackAddress` and assert the connector actually POSTs
  the state-change notification to it. This is the only gate in the plan that can
  catch the dropped HTTP callback transport; neither `dependency:resolve` nor
  compilation will.
- **Exercise a data-plane transfer end to end**, acting on the Step 2 decision
  about `transfer-data-plane-signaling` vs `data-plane-signaling`. Like the
  callback case, swapping (or keeping) that module is invisible to
  `dependency:resolve` and to the compiler — the failure mode is a data flow that
  never starts at runtime. Drive one transfer through the compose stack and
  confirm the data plane is selected and the flow reaches `STARTED`.

Acceptance: `mvn clean package` green across the whole reactor — both controlplane
images build; a container starts against the TCK compose stack and logs no
missing-config or unresolved-service errors; and both runtime gates above — one
callback delivered, one transfer started — are observed, not assumed.

### Step 8: Re-pin and green the DSP TCK conformance run

- Bump `DSP_TCK_VERSION` from `1.0.0-RC6` in `docker-compose.tck.yml` (line 237),
  `.github/workflows/test.yml` (line 8) and the comment block at
  `docker-compose.tck.yml:19`. EDC 0.18.1 pins `dsp-tck:1.0.1`; the TCK runtime image
  has a released `1.0.2`. Prefer the version EDC itself pins unless 1.0.2 passes
  cleanly — record the choice.
- `scripts/init-tmforum.sh`: if `participantContextId` became part of the TMForum
  entity shape in Step 3, the seeded test data must carry it.
- Run `./scripts/run-tck.sh` and diff the result against
  `docs/migration/tck-baseline-0.14.1.md`, captured in Step 1. Any test that passes
  in the baseline and fails now is a regression to fix, not to filter out.

Acceptance: the TCK job passes locally and in CI with `TCK_FILTER` unset (its
compose default, `docker-compose.tck.yml:244`), `docker/tck-filter/src/.../DisplayNameFilter.java`
unmodified, and no test that passes in `docs/migration/tck-baseline-0.14.1.md`
failing. Note `docker/tck-filter` is a JUnit extension source tree, not a list of
skipped tests — filtering is driven only by the `tck.filter` system property fed
from `TCK_FILTER`.

### Step 9: Update documentation and release notes

- `AGENTS.md`: update the EDC version, the module/artifact names changed in Step 2,
  the DSP protocol note, and the TCK architecture section if Step 6 adopted
  upstream's `tck-extension`. Also correct the doc-comment convention: the
  parenthetical "`@param`/`@return` blocks are rare (used only in config classes)"
  is wrong — 19 of 138 `src/main/java` files use `@param`, concentrated in the
  whole of `policy-extension` plus `FDSCDcpProvisioner`, `FDSCOID4VPProvisioner`,
  `OdrlPapClient`, `TMFEdcMapper`, `TMForumBackedCatalogProtocolService` and
  `DataAssembly`. Reword to "concentrated in config classes and the policy
  extension" or drop the parenthetical. (The "roughly half" density claim is
  accurate — 69 of 138 files carry a `/**` block — and stays; an earlier revision of
  this plan said 59, which was a miscount.) This is deferred to
  Step 9 rather than fixed on the plan branch because `AGENTS.md` is appended to
  every agent's system prompt: the rename landed in commit `a655f1b` and is
  therefore already in the cached prefix, so a further edit now invalidates that
  cache for every chained session downstream rather than riding along for free.
  The interim correction is recorded in the working agreement at the top of this
  plan, which every step agent reads, so no one executing Steps 2–8 acts on the
  wrong version in the meantime.
- `README.md` and `TRANSACTION_README.md`: update version references and any
  renamed config keys.
- Add `releases/notes/RELEASE_NOTES_EDC_0_18_1.md` covering the operator-facing
  changes: required config renames, the IdentityHub 0.18.1 lockstep requirement,
  and the loss of DSP 2024/1 support for counterparties.
- Label the final PR `minor` or `major` per the repo's semver-label convention —
  the dropped DSP 2024/1 support and the config renames argue for `major`.

Acceptance: `mvn clean package` and `mvn spotless:check` green, TCK green, and
`docs/migration/EDC-0.18.1-UPGRADE.md` updated to mark each item as resolved.
