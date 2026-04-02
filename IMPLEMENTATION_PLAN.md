# Implementation Plan: [DSC] DSP Conformance Tests

## Overview

The DSP (Dataspace Protocol) conformance tests (Eclipse DSP TCK) are already scaffolded in the repository with Docker Compose orchestration, TMForum data initialization scripts, EDC test-extension with guards/webhooks/mock identity, TCK configuration, a local runner script, and a CI workflow. However, the tests do not execute successfully. This plan diagnoses and fixes all issues — from compilation errors and EDC API incompatibilities to Docker Compose configuration problems, init-script data mismatches, and runtime failures — until the TCK conformance tests pass end-to-end.

## Steps

### Step 1: Fix build, Docker Compose orchestration, and EDC startup ✅

**Goal:** Get the project to compile, pass unit tests, build Docker images, and have the EDC controlplane start successfully within the Docker Compose TCK stack.

**Issues found and fixed:**
1. **Spotless formatting violation** in `DataAssembly.java` — line wrapping issue.
2. **Wrong shaded JAR path** — `docker-compose.tck.yml`, `scripts/run-tck.sh`, and `.github/workflows/test.yml` all referenced `controlplane-oid4vc-0.0.1-SNAPSHOT.jar` (2KB non-shaded JAR) instead of the correct shaded JAR at `target/context/controlplane-oid4vc.jar` (38MB).
3. **Scorpio health check failure** — The Scorpio container (`scorpiobroker/all-in-one-runner:java-5.0.3`) does not have `curl` installed. Changed the health check to use bash `/dev/tcp/` instead.
4. **Docker bind mount issues** — Some Docker environments (overlayfs, CI) don't support single-file bind mounts. Created dedicated Dockerfiles (`docker/Dockerfile.tck`, `docker/Dockerfile.tck-runner`, `docker/Dockerfile.tmf-init`) that embed config files via COPY instead of relying on bind mounts.
5. **HashicorpVault extension crash** — The `vault-hashicorp` dependency (pulled in transitively via `fdsc-transfer-extension`) causes EDC startup to fail when `edc.vault.hashicorp.url` is not configured. Added an exclusion in `controlplane-oid4vc/pom.xml` since the test-extension provides `InMemoryVault`.
6. **OID4VP extension crash** — The OID4VP extension requires a `holder-id` config. Added `oid4vp.enabled=false` to `config/tck/edc.properties` since TCK mode uses mock identity services.
7. **FDSC Transfer extension crash** — `TransferConfig.fromConfig()` unconditionally builds `Apisix` config (which requires `address`), even when `fdscTransfer.enabled=false`. Fixed by only building sub-configs when the transfer extension is enabled.

**Files changed:**
- `test-extension/src/main/java/org/seamware/edc/edc/DataAssembly.java` (spotless fix)
- `docker-compose.tck.yml` (JAR path, Scorpio health check, embedded configs via Dockerfiles)
- `scripts/run-tck.sh` (JAR path)
- `.github/workflows/test.yml` (JAR path)
- `docker/Dockerfile.tck` (new — EDC image with embedded config)
- `docker/Dockerfile.tck-runner` (new — TCK runner with embedded config)
- `docker/Dockerfile.tmf-init` (new — tmf-init with embedded script)
- `controlplane-oid4vc/pom.xml` (exclude vault-hashicorp)
- `config/tck/edc.properties` (add oid4vp.enabled=false)
- `fdsc-transfer-extension/src/main/java/org/seamware/edc/TransferConfig.java` (conditional sub-config build)

### Step 2: Fix TMForum init script and EDC runtime errors

**Goal:** TMForum data initialization succeeds and EDC starts fully within the Docker Compose stack.

**Actions:**
- Debug why all TMForum API calls in `scripts/init-tmforum.sh` return empty errors (all ProductSpecifications, ProductOffering, and Agreements fail to create).
- Improve error reporting in the init script to capture HTTP response bodies.
- Fix any TMForum API compatibility issues (request format, endpoints, required fields).
- Verify EDC starts fully after the OID4VP and FDSC Transfer fixes from Step 1.
- Address any remaining SPI or dependency injection errors in the EDC startup.

**Acceptance criteria:** `tmf-init` successfully creates all test data, EDC passes health check.

### Step 3: Fix TCK configuration and test data alignment

**Goal:** Ensure TCK test IDs and configuration match what the EDC controlplane and TMForum data provide.

**Actions:**
- Verify consistency between `tck.properties`, `edc.properties`, `init-tmforum.sh`, and `DataAssembly.java`.
- Fix any ID mismatches between TMForum entities and what TCK expects.
- Verify the TMForum-to-EDC mapping produces correct catalog, negotiation, and transfer data.

**Acceptance criteria:** `scripts/verify-tck-config.sh` passes, TMForum data maps correctly to EDC entities.

### Step 4: Run TCK tests and fix runtime failures iteratively

**Goal:** All DSP TCK conformance test scenarios pass.

**Actions:**
- Run the full TCK suite and capture logs.
- Fix catalog, negotiation, and transfer test failures.
- Adjust guards, triggers, state transitions, and timing as needed.

**Acceptance criteria:** TCK runner exits with code 0.

### Step 5: Verify CI workflow and finalize

**Goal:** The GitHub Actions workflow for TCK conformance tests is correct and would pass in CI.

**Actions:**
- Review and fix `.github/workflows/test.yml`.
- Ensure `run-tck.sh` works for local development.
- Final `mvn spotless:check` and `mvn test` verification.

**Acceptance criteria:** CI workflow correct, all tests pass.
