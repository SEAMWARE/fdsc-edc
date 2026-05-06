# Implementation Plan: Implement PolicyEngine to use ODRL-PAP

## Overview

Integrate the external ODRL-PAP (Policy Administration Point) service as a policy evaluation backend for the EDC PolicyEngine. Currently the `OdrlPapClient` only performs CRUD operations (create/delete services and policies) for transfer provisioning. This plan adds policy **evaluation** capabilities by calling the PAP's `POST /validate` endpoint and registering a pre-validator with EDC's `PolicyEngine`, so that ODRL policies are evaluated by the PAP before EDC's built-in constraint functions run.

### Architecture

The ODRL-PAP exposes a `POST /validate` endpoint that accepts an ODRL policy and a `TestRequest` (HTTP method, host, path, protocol, body, headers) and returns `{allow: boolean, explanation: string[]}`. The integration registers a `PolicyValidatorRule` as a pre-validator with the EDC `PolicyEngine` for configurable scopes (catalog, negotiation, transfer). When EDC evaluates a policy:

1. The pre-validator converts the EDC `Policy` to ODRL JSON-LD (via `TypeTransformerRegistry` + `JsonLd`)
2. It maps the EDC `PolicyContext` to a `TestRequestVO` (HTTP request representation)
3. It calls `OdrlPapClient.validate()` with both
4. If the PAP returns `allow=false`, evaluation fails immediately with explanations
5. If the PAP returns `allow=true`, EDC's built-in constraint functions still run (layered evaluation)

This approach is additive — existing evaluators like `DayOfWeekEvaluator` continue to work alongside PAP-based evaluation. The feature is gated behind `odrlPap.policy.enabled` (default `false`).

## Steps

### Step 1: Extend OdrlPapClient with Validation and Mappings API Methods

**Goal:** Add methods to `OdrlPapClient` to call the ODRL-PAP's `POST /validate` and `GET /mappings` endpoints, using the auto-generated model classes from the OpenAPI spec.

**Context:** The ODRL-PAP OpenAPI spec (referenced in `fdsc-transfer-extension/pom.xml` line 18) defines models including `ValidationRequestVO`, `TestRequestVO`, `ValidationResponseVO`, and `MappingsVO` that are generated at build time into the `org.seamware.pap.model` package. The `OdrlPapClient` (at `fdsc-transfer-extension/src/main/java/org/seamware/edc/pap/OdrlPapClient.java`) currently only has `createService()`, `createPolicy()`, and `deleteService()`.

**Changes:**

1. **Verify/fix OpenAPI spec URL** — The current spec URL (`https://raw.githubusercontent.com/wistefan/odrl-pap/refs/heads/policy-per-service/api/odrl.yaml`) may return 404 if the branch was merged. Update to the correct URL if needed (e.g. `https://raw.githubusercontent.com/wistefan/odrl-pap/main/api/odrl.yaml`). Verify the spec includes the `/validate` and `/mappings` endpoints and their model schemas (`ValidationRequest`, `TestRequest`, `ValidationResponse`, `Mappings`, `Mapping`).

2. **Add `validate()` method to `OdrlPapClient`:**
   - Signature: `public ValidationResponseVO validate(ValidationRequestVO request)`
   - POST to `/validate` with JSON body, deserialize response as `ValidationResponseVO`
   - Throw `HttpClientException` on non-2xx responses (following existing `createService`/`createPolicy` pattern)

3. **Add `getMappings()` method to `OdrlPapClient`:**
   - Signature: `public MappingsVO getMappings()`
   - GET `/mappings`, deserialize as `MappingsVO`

4. **Unit tests** (in `fdsc-transfer-extension/src/test/java/org/seamware/edc/pap/OdrlPapClientTest.java` or new class):
   - `validate()`: PAP returns allow=true, allow=false, and error (4xx/5xx) scenarios
   - `getMappings()`: success and error scenarios
   - Follow the existing MockWebServer-based test pattern in `OdrlPapClientTest`

**Acceptance Criteria:**
- `mvn clean test -pl fdsc-transfer-extension` passes with new tests
- `mvn spotless:check -pl fdsc-transfer-extension` passes
- Generated model classes are used (no hand-written DTOs for PAP API models)

**Files affected:**
- Possibly modified: `fdsc-transfer-extension/pom.xml` (OpenAPI spec URL fix)
- Modified: `fdsc-transfer-extension/src/main/java/org/seamware/edc/pap/OdrlPapClient.java`
- Modified or new test: `fdsc-transfer-extension/src/test/java/org/seamware/edc/pap/OdrlPapClientTest.java`

---

### Step 2: Create PolicyContextRequestMapper and OdrlPapPolicyValidator

**Goal:** Create the core policy evaluation classes: a mapper that converts EDC `PolicyContext` into the PAP's `TestRequestVO` format, and a validator that orchestrates PAP-based policy evaluation.

**Context:** The PAP's `/validate` endpoint expects a `TestRequest` with HTTP-level attributes (`method`, `host`, `path`, `protocol`, `body`, `headers`). EDC's `PolicyContext` subtypes (`RequestCatalogPolicyContext`, `RequestContractNegotiationPolicyContext`, `RequestTransferProcessPolicyContext`) contain participant agent information and scope details. The mapper bridges these two representations. The validator converts the EDC Policy to ODRL JSON-LD (using the same `TypeTransformerRegistry` + `JsonLd.expand()` approach used in `FDSCOID4VPProvisioner.provision()` at lines 171-183), combines it with the mapped request, and calls the PAP.

**Changes:**

1. **Create `PolicyContextRequestMapper`** at `fdsc-transfer-extension/src/main/java/org/seamware/edc/pap/policy/PolicyContextRequestMapper.java`:
   - `toTestRequest(PolicyContext context)` method returning `TestRequestVO`
   - Handle `RequestCatalogPolicyContext` → GET request with catalog path
   - Handle `RequestContractNegotiationPolicyContext` → POST request with negotiation path
   - Handle `RequestTransferProcessPolicyContext` → POST request with transfer path
   - Extract `ParticipantAgent` identity from context into request headers
   - Named constants for HTTP methods, paths, header names (no magic strings)
   - Full JavaDoc

2. **Create `OdrlPapPolicyValidator`** at `fdsc-transfer-extension/src/main/java/org/seamware/edc/pap/policy/OdrlPapPolicyValidator.java`:
   - Implements `BiFunction<Policy, PolicyContext, Boolean>` (matches `PolicyValidatorRule` contract)
   - Constructor: `OdrlPapClient`, `TypeTransformerRegistry`, `JsonLd`, `PolicyContextRequestMapper`, `Monitor`, `boolean denyOnError`
   - `apply(Policy policy, PolicyContext context)`:
     1. Convert EDC `Policy` → `JsonObject` via `TypeTransformerRegistry`
     2. Expand JSON-LD via `JsonLd.expand()`
     3. Convert to `Map<String, Object>` for PAP
     4. Build `TestRequestVO` via `PolicyContextRequestMapper`
     5. Create `ValidationRequestVO` with policy and test request
     6. Call `OdrlPapClient.validate()`
     7. Return `true` if `allow=true`; report problems and return `false` if `allow=false`
     8. On exception: log warning, return `!denyOnError`
   - `name()` returns `"OdrlPapPolicyValidator"`
   - Full JavaDoc

3. **Unit tests:**
   - `PolicyContextRequestMapperTest.java` — parameterized tests for each context type
   - `OdrlPapPolicyValidatorTest.java` — allow/deny, PAP errors with denyOnError true/false, policy conversion failures. Mock all dependencies.

**Acceptance Criteria:**
- All unit tests pass; spotless passes
- No magic constants
- Complete JavaDoc on both classes

**Files affected:**
- New: `fdsc-transfer-extension/src/main/java/org/seamware/edc/pap/policy/PolicyContextRequestMapper.java`
- New: `fdsc-transfer-extension/src/main/java/org/seamware/edc/pap/policy/OdrlPapPolicyValidator.java`
- New: `fdsc-transfer-extension/src/test/java/org/seamware/edc/pap/policy/PolicyContextRequestMapperTest.java`
- New: `fdsc-transfer-extension/src/test/java/org/seamware/edc/pap/policy/OdrlPapPolicyValidatorTest.java`

---

### Step 3: Create OdrlPapPolicyExtension ServiceExtension with Configuration

**Goal:** Create a `ServiceExtension` that wires the ODRL-PAP policy validator into the EDC `PolicyEngine`, gated by configuration.

**Context:** EDC extensions implement `ServiceExtension`, use `@Inject` for dependencies, and register via `META-INF/services/org.eclipse.edc.spi.system.ServiceExtension`. The existing file at `fdsc-transfer-extension/src/main/resources/META-INF/services/org.eclipse.edc.spi.system.ServiceExtension` already lists `org.seamware.edc.FDSCTransferControlExtension`. The DCP extension at `dcp-extension/src/main/java/org/seamware/edc/DCPExtension.java` (lines 95-146) shows the reference pattern for `policyEngine.registerPostValidator()` and `policyEngine.registerFunction()`.

**Changes:**

1. **Create `OdrlPapConfig`** at `fdsc-transfer-extension/src/main/java/org/seamware/edc/pap/policy/OdrlPapConfig.java`:
   - Configuration loaded from EDC `Config` via static `fromConfig(Config config)` factory
   - Properties (all with named setting constants):
     - `odrlPap.policy.enabled` (boolean, default `false`)
     - `odrlPap.host` (string, required when enabled)
     - `odrlPap.policy.denyOnError` (boolean, default `true`)
     - `odrlPap.policy.scopes.catalog` (boolean, default `true`)
     - `odrlPap.policy.scopes.negotiation` (boolean, default `true`)
     - `odrlPap.policy.scopes.transfer` (boolean, default `true`)

2. **Create `OdrlPapPolicyExtension`** at `fdsc-transfer-extension/src/main/java/org/seamware/edc/pap/policy/OdrlPapPolicyExtension.java`:
   - `@Inject PolicyEngine policyEngine`
   - `@Inject Monitor monitor`
   - `@Inject OkHttpClient okHttpClient`
   - `@Inject ObjectMapper objectMapper`
   - `@Inject TypeTransformerRegistry typeTransformerRegistry`
   - `@Inject JsonLd jsonLd`
   - `name()` → `"ODRL PAP Policy Extension"`
   - `initialize(ServiceExtensionContext)`:
     1. Load `OdrlPapConfig`; return early if disabled
     2. Create `OdrlPapClient(monitor, okHttpClient, config.host(), objectMapper)`
     3. Create `PolicyContextRequestMapper()`
     4. Create `OdrlPapPolicyValidator(client, typeTransformerRegistry, jsonLd, mapper, monitor, config.denyOnError())`
     5. Register pre-validators per enabled scopes:
        - `policyEngine.registerPreValidator(RequestCatalogPolicyContext.class, validator::apply)`
        - `policyEngine.registerPreValidator(RequestContractNegotiationPolicyContext.class, validator::apply)`
        - `policyEngine.registerPreValidator(RequestTransferProcessPolicyContext.class, validator::apply)`
     6. Log registered scopes at info level

3. **Update SPI file** at `fdsc-transfer-extension/src/main/resources/META-INF/services/org.eclipse.edc.spi.system.ServiceExtension`:
   - Add `org.seamware.edc.pap.policy.OdrlPapPolicyExtension`

4. **Add policy context dependencies** to `fdsc-transfer-extension/pom.xml` if not already present:
   - `org.eclipse.edc:policy-engine-spi`
   - `org.eclipse.edc:policy-context-request-spi`

5. **Unit tests** at `fdsc-transfer-extension/src/test/java/org/seamware/edc/pap/policy/OdrlPapPolicyExtensionTest.java`:
   - Disabled mode: no validators registered
   - Enabled with all scopes: three pre-validators registered
   - Enabled with subset of scopes: only matching validators registered
   - Missing host when enabled: initialization fails gracefully

**Acceptance Criteria:**
- Extension compiles and is in SPI file
- `mvn clean test -pl fdsc-transfer-extension` passes
- Configuration documented with JavaDoc and named constants

**Files affected:**
- New: `fdsc-transfer-extension/src/main/java/org/seamware/edc/pap/policy/OdrlPapConfig.java`
- New: `fdsc-transfer-extension/src/main/java/org/seamware/edc/pap/policy/OdrlPapPolicyExtension.java`
- Modified: `fdsc-transfer-extension/src/main/resources/META-INF/services/org.eclipse.edc.spi.system.ServiceExtension`
- Possibly modified: `fdsc-transfer-extension/pom.xml`
- New: `fdsc-transfer-extension/src/test/java/org/seamware/edc/pap/policy/OdrlPapPolicyExtensionTest.java`

---

### Step 4: Integration Wiring, DCP Extension Cleanup, and Example Configuration

**Goal:** Update the DCP extension's TODO, remove unused code, add example configuration, and verify cross-module integration.

**Changes:**

1. **Update DCP extension TODO** at `dcp-extension/src/main/java/org/seamware/edc/DCPExtension.java` line 130-131:
   - Replace `// TODO: support odrl-pap based evaluation in the future.` with:
     ```java
     // ODRL-PAP based policy evaluation is handled by OdrlPapPolicyExtension
     // (policy-extension). Enable via odrlPap.policy.enabled=true.
     ```

2. **Remove unused `policyEngine` field** from `FDSCTransferControlExtension` at `fdsc-transfer-extension/src/main/java/org/seamware/edc/FDSCTransferControlExtension.java`:
   - Remove line 127: `private PolicyEngine policyEngine;`
   - Remove line 60: `import org.eclipse.edc.policy.engine.spi.PolicyEngine;` (if no longer used)

3. **Add example configuration** to `config/tck/edc.properties`:
   ```properties
   # ODRL-PAP Policy Evaluation (disabled for TCK mode)
   # odrlPap.policy.enabled=false
   # odrlPap.host=http://odrl-pap:8080
   # odrlPap.policy.denyOnError=true
   # odrlPap.policy.scopes.catalog=true
   # odrlPap.policy.scopes.negotiation=true
   # odrlPap.policy.scopes.transfer=true
   ```

**Acceptance Criteria:**
- No unused imports or fields remain
- DCP extension TODO is resolved
- Example config documents the new feature
- `mvn clean test` passes across all modules

**Files affected:**
- Modified: `dcp-extension/src/main/java/org/seamware/edc/DCPExtension.java`
- Modified: `fdsc-transfer-extension/src/main/java/org/seamware/edc/FDSCTransferControlExtension.java`
- Modified: `config/tck/edc.properties`

---

### Step 5: Build Verification, Formatting, and Final Tests ✓

**Goal:** Ensure the complete build passes, formatting is correct, and all tests succeed across the entire project.

**Verification steps:**

1. Run `mvn spotless:apply` then `mvn spotless:check` to verify formatting
2. Run `mvn clean test` to verify all unit tests pass (all modules)
3. Run `mvn clean package -DskipTests` to verify build and packaging succeeds
4. Verify no regressions in existing functionality
5. Fix any issues found during verification

**Acceptance Criteria:**
- `mvn clean spotless:check` passes
- `mvn clean test` passes (all modules)
- `mvn clean package -DskipTests` succeeds
- No regressions in existing tests or functionality

**Verification Results:**
All acceptance criteria met:
- `mvn spotless:apply` required no changes; `mvn spotless:check` passes
- `mvn clean test` — BUILD SUCCESS across all 9 modules (fdsc-edc, tmf-extension, oid4vc-extension, fdsc-transfer-extension, test-extension, dcp-extension, policy-extension, controlplane-oid4vc, controlplane-dcp)
- `mvn clean package -DskipTests -Pdebug` — BUILD SUCCESS, all JARs including shaded controlplane JARs built (Docker image build requires buildx which is a CI/CD concern, not a code issue)
- No regressions: all existing tests pass alongside the new policy-extension module tests
