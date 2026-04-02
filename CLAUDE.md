# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

FIWARE Data Space Connector EDC Extensions — a set of Eclipse Dataspace Components (EDC) extensions that integrate EDC with FIWARE ecosystem components. The project produces two deployable controlplane images:

- **controlplane-oid4vc** — EDC controlplane secured via OpenID4VP
- **controlplane-dcp** — EDC controlplane secured via DCP (Decentralized Claims Protocol)

## Tech Stack

- Language: Java 21
- Build: Maven (multi-module)
- Framework: Eclipse EDC 0.14.1 (Dataspace Components)
- Test: JUnit 5, Mockito
- Formatting: Spotless (Google Java Format) + license header enforcement
- Containers: Docker, Docker Compose

## Project Structure

```
fdsc-edc/
├── pom.xml                          # Parent POM (edc.version=0.14.1)
├── tmf-extension/                   # TMForum API storage backend for EDC
│   └── src/main/java/org/seamware/edc/
│       ├── TMFContractNegotiationExtension.java   # Main extension, provides stores + API clients
│       ├── TMFOfferResolverExtension.java         # Consumer offer resolution
│       ├── CatalogProtocolServiceExtension.java   # Catalog protocol service
│       ├── TMFConfig.java                         # Configuration (tmfExtension.* properties)
│       ├── store/                                 # EDC store implementations backed by TMForum
│       │   ├── TMFBackedContractNegotiationStore.java
│       │   ├── TMFBackedAssetIndex.java
│       │   ├── TMFBackedContractDefinitionStore.java
│       │   ├── TMFBackedPolicyDefinitionStore.java
│       │   ├── TMFEdcMapper.java                  # Central TMForum↔EDC entity mapper
│       │   └── TMForumBackedCatalogProtocolService.java
│       ├── tmf/                                   # TMForum API HTTP clients
│       │   ├── QuoteApiClient.java
│       │   ├── AgreementApiClient.java
│       │   └── ProductCatalogApiClient.java
│       └── domain/                                # Extended TMForum domain VOs
├── test-extension/                  # TCK test infrastructure
│   └── src/main/java/org/seamware/edc/
│       ├── TestConfig.java                        # Config: testExtension.* properties
│       ├── edc/
│       │   ├── DataAssembly.java                  # TCK test data factory (assets, agreements, state sequences)
│       │   ├── TckGuardExtension.java             # ServiceExtension: loads test data + registers guards
│       │   ├── ContractNegotiationGuard.java       # PendingGuard for negotiation state control
│       │   ├── TransferProcessGuard.java           # PendingGuard for transfer state control
│       │   ├── DelayedActionGuard.java             # Generic delayed-action guard base class
│       │   ├── StepRecorder.java                   # Sequential action recorder/player per entity ID
│       │   ├── ContractNegotiationTriggerSubscriber.java  # Event-driven negotiation transitions
│       │   └── TransferProcessTriggerSubscriber.java      # Event-driven transfer transitions
│       ├── tck/
│       │   ├── TckControllerExtension.java        # ServiceExtension: registers webhook controller
│       │   ├── TckWebhookController.java          # JAX-RS endpoints for TCK to trigger negotiations/transfers
│       │   ├── ContractNegotiationRequest.java    # DTO for negotiation webhook
│       │   └── TransferProcessRequest.java        # DTO for transfer webhook
│       └── services/
│           ├── TestIdentityExtension.java         # Mock identity/vault services for testing
│           ├── TestIdentityService.java
│           ├── InMemoryVault.java
│           └── NoopAudienceResolver.java
├── oid4vc-extension/                # OpenID4VP authentication
├── dcp-extension/                   # DCP authentication with TIR support
├── fdsc-transfer-extension/         # FDSC data transfer provisioning
├── controlplane-oid4vc/             # Shaded JAR + Docker image (OID4VC variant)
├── controlplane-dcp/                # Shaded JAR + Docker image (DCP variant)
├── config/tck/
│   ├── edc.properties               # EDC controlplane config for TCK mode
│   └── tck.properties               # DSP TCK runner configuration
├── scripts/
│   ├── init-tmforum.sh              # Populates TMForum with TCK test data
│   ├── run-tck.sh                   # One-command local TCK runner
│   └── verify-tck-config.sh         # Static config consistency checker
├── docker/
│   └── Dockerfile                   # EDC controlplane Docker image (eclipse-temurin:24-jre-alpine)
├── docker-compose.tck.yml           # TCK orchestration (postgis, scorpio, tmforum, edc, tck)
├── schemas/                         # JSON/JSON-LD schemas for contracts/policies
└── .github/workflows/test.yml       # CI: unit tests + TCK conformance
```

## Build & Test

```bash
# Full build (compile + test + package Docker images)
mvn clean package

# Run tests only
mvn clean test

# Run a single test class
mvn test -pl <module> -Dtest=<TestClassName>
# e.g.: mvn test -pl dcp-extension -Dtest=TirClientTest

# Check code formatting and license headers (CI runs this)
mvn clean spotless:check

# Auto-fix formatting
mvn spotless:apply

# Build controlplane shaded JAR only (for TCK)
mvn clean package -pl controlplane-oid4vc -am -DskipTests

# Run DSP TCK conformance tests locally
./scripts/run-tck.sh

# Or manually via Docker Compose
docker compose -f docker-compose.tck.yml up --build --abort-on-container-exit --exit-code-from tck

# Verify TCK config consistency
./scripts/verify-tck-config.sh
```

Java 21 is required. The project uses Maven (no Gradle — `.gradle` files under `target/` are generated artifacts).

## Key Conventions

- **Code formatting:** Google Java Format enforced by Spotless. License header from `license-header.txt` required on all Java files.
- **EDC extension pattern:** Extensions implement `ServiceExtension`, registered via SPI in `META-INF/services/org.eclipse.edc.spi.system.ServiceExtension`. Use `@Inject` for dependencies, `@Provides` to declare provided services.
- **Code generation:** `tmf-extension` generates TMForum model classes from OpenAPI specs at `generate-sources` phase. Generated code in `target/generated-sources/` — never edit manually. Package: `org.seamware.tmforum.<domain>.model` with `VO` suffix.
- **Annotation processing:** Lombok for boilerplate, MapStruct for object mapping.
- **HTTP clients:** OkHttp for TMForum API calls.
- **JSON:** Jackson with `jackson-datatype-jsr310`.

## Important Files

- `pom.xml` — Parent POM, defines `edc.version=0.14.1` and all shared dependency versions
- `config/tck/edc.properties` — EDC config for TCK mode (ports, TMForum URLs, test extension flags)
- `config/tck/tck.properties` — DSP TCK config (asset IDs, agreement IDs, offer IDs, endpoint URLs)
- `scripts/init-tmforum.sh` — Creates test data in TMForum (assets as ProductSpecifications, contracts as ProductOfferings, agreements)
- `docker-compose.tck.yml` — Full TCK test orchestration
- `test-extension/.../DataAssembly.java` — Central TCK test data: asset IDs, agreement IDs, state transition sequences, event triggers
- `test-extension/.../TckGuardExtension.java` — Initializes test data and registers pending guards
- `test-extension/.../TckWebhookController.java` — Webhook endpoints for TCK to trigger consumer negotiations/transfers
- `tmf-extension/.../TMFEdcMapper.java` — Maps TMForum entities to EDC entities
- `.github/workflows/test.yml` — CI workflow with unit tests + TCK conformance job

## DSP TCK Test Architecture

The DSP TCK tests verify DSP protocol conformance. The setup uses:
1. **TMForum** as storage backend (PostGIS → Scorpio → TMForum API)
2. **init-tmforum.sh** creates test data (17 assets, 1 contract definition, 32 agreements)
3. **test-extension** provides: mock identity services, TCK webhook controller, state transition guards
4. **Guards** control EDC state machine: `ContractNegotiationGuard` and `TransferProcessGuard` intercept transitions, `StepRecorder` plays pre-defined sequences, `TriggerSubscribers` react to events
5. **TCK runner** sends DSP protocol messages and verifies responses

Key test IDs: Assets=ACN0101-ACN0304,CAT0101-CAT0102; Agreements=ATP0101-ATP0306,ATPC0101-ATPC0306; Policy=P123; ContractDef=CD123; Participant=urn:connector:fdsc-edc

## Versioning and Release

Releases triggered on push to `main`. PR labels (`major`, `minor`, `patch`) control semver bumps. Images pushed to `quay.io/seamware/`.
