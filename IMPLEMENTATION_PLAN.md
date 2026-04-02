# Implementation Plan: [DSC] DSP Conformance Tests

## Overview

The DSP (Dataspace Protocol) conformance tests (Eclipse DSP TCK) are already scaffolded in the repository with Docker Compose orchestration, TMForum data initialization scripts, EDC test-extension with guards/webhooks/mock identity, TCK configuration, a local runner script, and a CI workflow. However, the tests do not execute successfully. This plan diagnoses and fixes all issues — from compilation errors and EDC API incompatibilities to Docker Compose configuration problems, init-script data mismatches, and runtime failures — until the TCK conformance tests pass end-to-end.

## Steps

### Step 1: Build the project and identify compilation errors

**Goal:** Get the project to compile cleanly, especially the `test-extension` and `controlplane-oid4vc` modules.

**Actions:**
- Run `mvn clean compile -pl controlplane-oid4vc -am` to identify all compilation errors.
- Focus on EDC 0.14.1 API compatibility issues in the `test-extension` module, which is the most likely source of problems. Key files to check and fix:
  - `test-extension/src/main/java/org/seamware/edc/tck/TckWebhookController.java` — `ContractRequest.Builder`, `ContractOffer.Builder`, and `TransferRequest.Builder` method signatures may not match EDC 0.14.1. Check `callbackAddresses()`, `contractOffer()`, `id()`, `transferType()`, `protocol()`, `contractId()`.
  - `test-extension/src/main/java/org/seamware/edc/edc/DataAssembly.java` — `ContractDefinition.Builder` methods (`accessPolicyId`, `contractPolicyId`), `ContractAgreement.Builder` methods (`contractSigningDate`, `providerId`, `consumerId`), `Asset.Builder`, `DataAddress.Builder` may have changed in 0.14.1.
  - `test-extension/src/main/java/org/seamware/edc/edc/TckGuardExtension.java` — line 145 uses `cn.getContractOffers().get(0).getAssetId()` which may not exist in EDC 0.14.1's `ContractNegotiation` class. The API for retrieving the asset ID from a negotiation may have changed.
  - `test-extension/src/main/java/org/seamware/edc/edc/DelayedActionGuard.java` — verify `PendingGuard<T>` interface location and method signature (`org.eclipse.edc.spi.entity.PendingGuard`).
  - `test-extension/src/main/java/org/seamware/edc/edc/ContractNegotiationGuard.java` — verify `ContractNegotiationPendingGuard` interface exists and is compatible.
  - `test-extension/src/main/java/org/seamware/edc/edc/TransferProcessGuard.java` — verify `TransferProcessPendingGuard` interface exists and is compatible.
  - `test-extension/src/main/java/org/seamware/edc/services/TestIdentityExtension.java` — verify `DefaultParticipantIdExtractionFunction`, `AudienceResolver`, `IdentityService`, `Vault` interface locations.
  - `test-extension/src/main/java/org/seamware/edc/edc/ContractNegotiationTriggerSubscriber.java` and `TransferProcessTriggerSubscriber.java` — verify `EventSubscriber`, `EventEnvelope`, `findByIdAndLease()` API.
- Also check `tmf-extension` for any compilation issues since it is a dependency of the controlplane.
- Fix all compilation errors by adapting to the EDC 0.14.1 API. Consult EDC 0.14.1 source/javadoc for correct builder methods, interface names, and package locations.

**Acceptance criteria:** `mvn clean compile -pl controlplane-oid4vc -am` completes without errors.

**Files likely affected:**
- `test-extension/src/main/java/org/seamware/edc/tck/TckWebhookController.java`
- `test-extension/src/main/java/org/seamware/edc/edc/DataAssembly.java`
- `test-extension/src/main/java/org/seamware/edc/edc/TckGuardExtension.java`
- `test-extension/src/main/java/org/seamware/edc/edc/DelayedActionGuard.java`
- `test-extension/src/main/java/org/seamware/edc/edc/ContractNegotiationGuard.java`
- `test-extension/src/main/java/org/seamware/edc/edc/TransferProcessGuard.java`
- `test-extension/src/main/java/org/seamware/edc/services/TestIdentityExtension.java`
- `test-extension/src/main/java/org/seamware/edc/edc/ContractNegotiationTriggerSubscriber.java`
- `test-extension/src/main/java/org/seamware/edc/edc/TransferProcessTriggerSubscriber.java`
- `test-extension/pom.xml` (if dependency updates are needed)
- Any `tmf-extension` files with compilation issues

### Step 2: Fix unit tests

**Goal:** All existing unit tests pass after the compilation fixes from Step 1.

**Actions:**
- Run `mvn clean test` to identify test failures.
- Fix any broken tests due to API changes made in Step 1.
- Ensure tests in `tmf-extension`, `test-extension`, `dcp-extension`, `oid4vc-extension`, and `fdsc-transfer-extension` all pass.
- Run `mvn spotless:check` and fix any formatting issues with `mvn spotless:apply`.

**Acceptance criteria:** `mvn clean test` and `mvn spotless:check` both pass.

**Files likely affected:**
- Test files in any module that had API changes in Step 1.
- Any source files needing formatting fixes.

### Step 3: Build the controlplane shaded JAR and Docker image

**Goal:** Successfully build the controlplane-oid4vc shaded JAR and Docker image used by the TCK Docker Compose.

**Actions:**
- Run `mvn clean package -pl controlplane-oid4vc -am -DskipTests` to build the shaded JAR.
- Verify the JAR exists at `controlplane-oid4vc/target/controlplane-oid4vc-0.0.1-SNAPSHOT.jar`.
- Build the Docker image: `docker build --build-arg JAR=controlplane-oid4vc/target/controlplane-oid4vc-0.0.1-SNAPSHOT.jar -f docker/Dockerfile -t fdsc-edc-controlplane-oid4vc:latest .`
- Verify the image starts and the EDC health endpoint responds.
- If the shaded JAR has service conflicts (duplicate SPI entries, missing dependencies), fix the `controlplane-oid4vc/pom.xml` shade plugin configuration.

**Acceptance criteria:** Docker image builds and EDC starts without SPI/dependency errors.

**Files likely affected:**
- `controlplane-oid4vc/pom.xml` (shade plugin config if needed)
- `docker/Dockerfile` (if base image or startup issues)

### Step 4: Fix Docker Compose TCK orchestration and service startup

**Goal:** All services in `docker-compose.tck.yml` start successfully and health checks pass.

**Actions:**
- Run `docker compose -f docker-compose.tck.yml up --build` and observe startup.
- Verify each service starts and passes health checks:
  - **postgis** — PostgreSQL with PostGIS, health: `pg_isready`
  - **scorpio** — NGSI-LD context broker on port 9090, health: `/q/health`
  - **tmforum** — TMForum API on port 8632, health: `/health`
  - **tmf-init** — One-shot curl container running `scripts/init-tmforum.sh`
  - **edc** — EDC controlplane on ports 8080/8181/8282/8687, health: `/api/check/health`
  - **tck** — DSP TCK runner
- Fix any issues with:
  - Service dependency ordering (e.g., tmf-init depends on tmforum being healthy, edc depends on tmf-init completing successfully).
  - EDC configuration in `config/tck/edc.properties` — verify all TMForum API URLs resolve correctly via Docker networking (hostname `tmforum`, port `8632`).
  - Health check timing — EDC or TMForum may need longer start periods or more retries.
  - The `tmf-init` script (`scripts/init-tmforum.sh`) — verify it creates all required test data entities (17 ProductSpecifications, 1 ProductOffering, 16 provider agreements, 16 consumer agreements) without errors. Check for HTTP errors, missing fields, or TMForum API incompatibilities.
- Verify the TCK container can reach the EDC DSP endpoint at `http://edc:8282/protocol` and the webhook endpoint at `http://edc:8687/tck`.

**Acceptance criteria:** All Docker Compose services start, health checks pass, and tmf-init completes successfully.

**Files likely affected:**
- `docker-compose.tck.yml` (health check tuning, environment variables)
- `config/tck/edc.properties` (EDC configuration)
- `scripts/init-tmforum.sh` (init script fixes)

### Step 5: Fix TCK configuration and test data alignment

**Goal:** Ensure TCK test IDs and configuration match what the EDC controlplane and TMForum data provide.

**Actions:**
- Run the verification script `scripts/verify-tck-config.sh` to detect mismatches between `config/tck/tck.properties`, `config/tck/edc.properties`, `docker-compose.tck.yml`, and `test-extension/src/main/java/org/seamware/edc/edc/DataAssembly.java`.
- Verify consistency of:
  - **Asset IDs:** `DataAssembly.ASSET_IDS` must match `init-tmforum.sh` `ASSET_IDS` and `tck.properties` `*_DATASETID` values.
  - **Agreement IDs:** `DataAssembly.AGREEMENT_IDS` must match `init-tmforum.sh` `PROVIDER_AGREEMENT_IDS` + `CONSUMER_AGREEMENT_IDS` and `tck.properties` `*_AGREEMENTID` values.
  - **Contract definition ID:** `DataAssembly.CONTRACT_DEFINITION_ID` ("CD123") must match the `init-tmforum.sh` `CONTRACT_DEF_ID` and the offer ID prefix in `tck.properties` (format `CD123:<assetId>`).
  - **Policy:** The ODRL policy in `init-tmforum.sh` must match what `DataAssembly` and `TMFEdcMapper` expect.
  - **Participant IDs:** `edc.participant.id` in `edc.properties` must match `dataspacetck.dsp.connector.agent.id` in `tck.properties` and the `PARTICIPANT_ID` in `init-tmforum.sh`.
  - **Ports and paths:** DSP protocol (8282/protocol), TCK webhook (8687/tck), management (8181/management).
  - **DSP callback address:** `edc.dsp.callback.address` must match `dataspacetck.dsp.connector.http.url`.
  - **TCK callback address:** `DataAssembly.TCK_CALLBACK_ADDRESS` ("http://tck:8083") must match `dataspacetck.callback.address` in `tck.properties`.
- Fix any mismatches found, keeping all IDs, ports, and URLs consistent.
- Pay special attention to the TMForum-to-EDC mapping: when TMF is enabled, assets come from ProductSpecifications (matched by `externalId`/`name`), contract definitions from ProductOfferings, and agreements from TMForum Agreements. The `TMFEdcMapper` must correctly map these to what the TCK expects.

**Acceptance criteria:** `scripts/verify-tck-config.sh` passes. All IDs are consistent across configuration files, init scripts, and Java code.

**Files likely affected:**
- `config/tck/tck.properties`
- `config/tck/edc.properties`
- `scripts/init-tmforum.sh`
- `test-extension/src/main/java/org/seamware/edc/edc/DataAssembly.java`

### Step 6: Fix TMForum-to-EDC mapping for TCK scenarios

**Goal:** Ensure the TMForum storage backend correctly serves catalog, negotiation, and transfer data that the TCK expects.

**Actions:**
- When `tmfExtension.enabled=true` (which it is in TCK mode), assets, contract definitions, policies, and agreements are served from TMForum via `TMFBackedAssetIndex`, `TMFBackedContractDefinitionStore`, `TMFBackedPolicyDefinitionStore`, and `TMFBackedContractNegotiationStore`. Verify these stores return data in the format the TCK expects.
- Key mapping issues to check in `tmf-extension/src/main/java/org/seamware/edc/store/TMFEdcMapper.java`:
  - ProductSpecification → Asset: Does the mapper produce an Asset with the correct ID (matching the `externalId` from init)?
  - ProductOffering → ContractDefinition/Dataset: Does the mapper produce valid contract definitions with the correct ID?
  - Agreement → ContractAgreement: Does the mapper produce agreements with correct provider/consumer IDs, asset IDs, and policies?
  - ODRL policy deserialization: Does the expanded JSON-LD policy format in `init-tmforum.sh` get correctly parsed back to an EDC Policy object?
- Check `TMForumBackedCatalogProtocolService` — it must serve catalog/datasets in valid DSP protocol format.
- Check `TMFBackedContractNegotiationStore` — it must handle negotiation state transitions that the TCK expects (create, find, lease, save with state changes).
- Verify the init script creates data with the right structure for the mappers to consume.

**Acceptance criteria:** EDC serves correct catalog, negotiation, and transfer data when backed by TMForum test data.

**Files likely affected:**
- `tmf-extension/src/main/java/org/seamware/edc/store/TMFEdcMapper.java`
- `tmf-extension/src/main/java/org/seamware/edc/store/TMForumBackedCatalogProtocolService.java`
- `tmf-extension/src/main/java/org/seamware/edc/store/TMFBackedContractNegotiationStore.java`
- `tmf-extension/src/main/java/org/seamware/edc/store/TMFBackedAssetIndex.java`
- `tmf-extension/src/main/java/org/seamware/edc/store/TMFBackedContractDefinitionStore.java`
- `tmf-extension/src/main/java/org/seamware/edc/store/TMFBackedPolicyDefinitionStore.java`
- `scripts/init-tmforum.sh`

### Step 7: Run TCK tests and fix runtime failures iteratively

**Goal:** All DSP TCK conformance test scenarios pass.

**Actions:**
- Run the full TCK suite: `docker compose -f docker-compose.tck.yml up --build --abort-on-container-exit --exit-code-from tck`
- Capture and analyze TCK logs and EDC logs for failures.
- Common runtime issues to investigate and fix:
  - **Catalog tests (CAT_01_*):** TCK queries the catalog and expects datasets with IDs `CAT0101`, `CAT0102`. Check that the catalog endpoint returns valid DSP catalog responses.
  - **Provider negotiation tests (CN_01_* through CN_03_*):** TCK sends negotiation requests and expects specific state transitions. The `StepRecorder` and `ContractNegotiationGuard` must correctly advance negotiations through states (REQUESTED → OFFERED → AGREED → FINALIZED, etc.). Check that the guard's filter logic matches what EDC 0.14.1 expects.
  - **Consumer negotiation tests (CN_C_*):** TCK triggers consumer negotiations via the webhook at `http://edc:8687/tck/negotiations/requests`. The `TckWebhookController` must build correct `ContractRequest` objects and the trigger subscribers must fire at the right events.
  - **Provider transfer tests (TP_01_* through TP_03_*):** Similar to negotiations but for transfer processes. The `TransferProcessGuard` and `StepRecorder` must handle state transitions (INITIATED → STARTED → COMPLETED/SUSPENDED/TERMINATED).
  - **Consumer transfer tests (TP_C_*):** TCK triggers consumer transfers via webhook at `http://edc:8687/tck/transfers/requests`.
- Fix issues found in each iteration — this may require:
  - Adjusting guard filter states (which states are "automatic" vs. "guarded").
  - Fixing trigger predicates (event type matching, asset/agreement ID extraction).
  - Fixing state transition sequences in `DataAssembly` recorder methods.
  - Adjusting timing (guard delay, trigger sleep, health check timeouts).
  - Fixing DSP protocol version string if the TCK expects a specific version.

**Acceptance criteria:** TCK runner exits with code 0 (all conformance tests pass).

**Files likely affected:**
- `test-extension/src/main/java/org/seamware/edc/edc/DataAssembly.java`
- `test-extension/src/main/java/org/seamware/edc/edc/ContractNegotiationGuard.java`
- `test-extension/src/main/java/org/seamware/edc/edc/TransferProcessGuard.java`
- `test-extension/src/main/java/org/seamware/edc/edc/DelayedActionGuard.java`
- `test-extension/src/main/java/org/seamware/edc/edc/TckGuardExtension.java`
- `test-extension/src/main/java/org/seamware/edc/tck/TckWebhookController.java`
- `config/tck/tck.properties`
- `config/tck/edc.properties`

### Step 8: Verify CI workflow and finalize

**Goal:** The GitHub Actions workflow for TCK conformance tests is correct and would pass in CI.

**Actions:**
- Review `.github/workflows/test.yml` `tck-conformance` job:
  - Verify the build step (`mvn package -pl controlplane-oid4vc -am -DskipTests -q`) works.
  - Verify the Docker buildx image build step tags the image as `fdsc-edc-controlplane-oid4vc:latest` which matches `docker-compose.tck.yml`.
  - Verify the `docker compose -f docker-compose.tck.yml up` command matches what was tested locally.
  - Verify the `DSP_TCK_VERSION` env var (`1.0.0-RC6`) is compatible with the configuration.
- If the TCK version needed to be changed during debugging, update it in the workflow.
- Ensure the `run-tck.sh` script also works correctly for local developer use.
- Run `mvn spotless:check` one final time to ensure all code formatting is correct.
- Run `mvn test` one final time to ensure all unit tests still pass.

**Acceptance criteria:** CI workflow is correct, `mvn test` passes, `mvn spotless:check` passes, and the full TCK suite passes via Docker Compose.

**Files likely affected:**
- `.github/workflows/test.yml` (if TCK version or build steps need updating)
- `scripts/run-tck.sh` (if any issues found)
