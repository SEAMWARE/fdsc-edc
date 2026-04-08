# FIWARE Data Space Connector EDC Extensions
> Eclipse Dataspace Components extensions for FIWARE ecosystem integration

A set of [Eclipse Dataspace Components (EDC)](https://github.com/eclipse-edc/Connector) extensions that bridge EDC with the [FIWARE Data Space Connector](https://github.com/FIWARE/data-space-connector) ecosystem. The extensions use [TMForum APIs](https://github.com/FIWARE/tmforum-api) as the storage backend for contract negotiations, transfer processes, and catalogs, and provision data transfers through the FIWARE stack — including APISIX gateway routing, credentials management, and ODRL policy administration.

Two deployable controlplane images are produced, each offering a different authentication mechanism:

| Image | Auth Mechanism | Registry |
|---|---|---|
| `fdsc-edc-controlplane-oid4vc` | [OpenID for Verifiable Presentations (OID4VP)](https://openid.net/specs/openid-4-verifiable-presentations-1_0.html) | [quay.io](https://quay.io/repository/seamware/fdsc-edc-controlplane-oid4vc) |
| `fdsc-edc-controlplane-dcp` | [Decentralized Claims Protocol (DCP)](https://projects.eclipse.org/projects/technology.dataspacetck) with [EBSI Trusted Issuers Registry](https://hub.ebsi.eu/) | [quay.io](https://quay.io/repository/seamware/fdsc-edc-controlplane-dcp) |

## Getting Started

### Prerequisites

- **Java 21** (JDK, not JRE — required for compilation)
- **Maven 3.8+**
- **Docker** with [Buildx](https://docs.docker.com/build/buildx/) (for multi-platform image builds)

### Building

```bash
# Full build: compile, test, and package Docker images
mvn clean package

# Build without Docker images (faster local iteration)
mvn clean package -Pdebug

# Run tests only
mvn clean test

# Run a single test class
mvn test -pl <module> -Dtest=<TestClassName>
# e.g.: mvn test -pl dcp-extension -Dtest=TirClientTest
```

The Docker build produces multi-platform images (`linux/amd64`, `linux/arm64`) based on `eclipse-temurin:24_36-jre-alpine`.

## Architecture

### Module Overview

```
fdsc-edc/
├── tmf-extension/            TMForum API storage backend
├── oid4vc-extension/         OpenID4VP authentication
├── dcp-extension/            DCP authentication + EBSI TIR
├── fdsc-transfer-extension/  Data transfer provisioning
├── test-extension/           Test utilities & TCK support
├── controlplane-oid4vc/      Assembled controlplane (OID4VP)
└── controlplane-dcp/         Assembled controlplane (DCP)
```

### Modules

#### tmf-extension

Uses [TMForum APIs](https://github.com/FIWARE/tmforum-api) as the persistence layer for EDC contract lifecycle data. Backed by auto-generated Java model classes from TMForum OpenAPI specifications (Product Catalog, Quote, Agreement, Party, Usage, Product Order, Product Inventory).

Key components:
- **`TMFBackedContractNegotiationStore`** — Persists contract negotiations across TMForum Quote, Agreement, ProductOrder, and ProductInventory APIs
- **`TMFBackedContractDefinitionStore`** / **`TMFBackedPolicyDefinitionStore`** / **`TMFBackedAssetIndex`** — EDC store implementations backed by TMForum
- **`TMFTransactionContext`** — Saga-based compensation for multi-step REST writes (see [TRANSACTION_README.md](TRANSACTION_README.md))
- **`TMForumConsumerOfferResolver`** — Resolves consumer offers from TMForum catalogs

#### oid4vc-extension

Authenticates connector-to-connector interactions via [OpenID for Verifiable Presentations (OID4VP)](https://openid.net/specs/openid-4-verifiable-presentations-1_0.html).

- Implements EDC's `IdentityService` with OID4VP-based token verification
- Extracts organization identity from JWT verifiable credentials
- Supports configurable trust anchors, proxy settings, and X.509 SAN-based client resolution

#### dcp-extension

Authenticates via the [Decentralized Claims Protocol (DCP)](https://projects.eclipse.org/projects/technology.dataspacetck) with support for the [EBSI Trusted Issuers Registry](https://hub.ebsi.eu/).

- Credential scope extraction and mapping for catalog, negotiation, transfer, and version requests
- Integration with EBSI Trusted Issuers Registry (TIR) for issuer validation
- Custom policy evaluators (e.g., `DayOfWeekEvaluator` for time-based access control)
- JWS 2020 signature suite support

#### fdsc-transfer-extension

Provisions data transfers through the FIWARE Data Space Connector infrastructure. Supports HTTP-Pull transfers via both OID4VC and DCP modes.

- **APISIX gateway** — Creates and manages API routes for data access
- **Credentials Config Service** — Generates authentication configuration for credential issuance
- **ODRL PAP** — Administers access policies for provisioned transfers
- Endpoint Data Reference (EDR) service registration

#### test-extension

Provides test utilities for conformance testing in a FIWARE Dataspace setup.

- Mock `IdentityService` for integration testing without real credential infrastructure
- TCK (Technology Conformance Kit) webhook controller
- Contract negotiation and transfer process guards with step recording

#### controlplane-oid4vc / controlplane-dcp

Assembly modules that combine the above extensions into deployable shaded JARs and Docker images. Each bundles the full EDC controlplane stack (DSP protocol, management API, EDR store, HashiCorp Vault integration) with the appropriate authentication extension.

### Extension Registration

All extensions are registered via Java SPI (`META-INF/services/org.eclipse.edc.spi.system.ServiceExtension`). The controlplane shade plugins merge service registrations from all bundled modules.

### Code Generation

The `tmf-extension`, `fdsc-transfer-extension`, `dcp-extension`, and `test-extension` modules generate Java model classes from remote OpenAPI specifications at build time via the `openapi-generator-maven-plugin`. Generated code is placed in `target/generated-sources/` and must not be edited manually.

## DSP Conformance Testing

This project integrates the [Eclipse DSP Technology Compatibility Kit (TCK)](https://github.com/eclipse-dataspacetck/dsp-tck) to verify conformance with the [Dataspace Protocol (DSP)](https://docs.internationaldataspaces.org/ids-knowledgebase/dataspace-protocol). TCK tests run automatically in CI on every push and can also be run locally.

### Prerequisites

In addition to the [standard build prerequisites](#prerequisites), you need:

- **Docker** and **Docker Compose** v2 (included with Docker Desktop, or install the `docker-compose-plugin`)

### Running Locally

The simplest way to run TCK conformance tests is with the provided script:

```bash
# Full build + test run
./scripts/run-tck.sh

# Skip the Maven build if the shaded JAR already exists
./scripts/run-tck.sh --skip-build

# Keep containers running after tests (useful for debugging)
./scripts/run-tck.sh --keep-containers
```

You can also override the TCK image version or pass extra Maven arguments:

```bash
DSP_TCK_VERSION=1.0.0-RC6 ./scripts/run-tck.sh
MVN_ARGS="-T 2C" ./scripts/run-tck.sh
```

### Running Manually with Docker Compose

If you prefer more control, you can run the steps individually:

```bash
# 1. Build the controlplane shaded JAR
mvn clean package -pl controlplane-oid4vc -am -DskipTests

# 2. Start the EDC controlplane and TCK runner
docker compose -f docker-compose.tck.yml up --build --abort-on-container-exit --exit-code-from tck

# 3. Tear down when done
docker compose -f docker-compose.tck.yml down --volumes --remove-orphans
```

### Test Suites

The TCK validates the following DSP protocol areas:

| Suite | Description | Tests |
|---|---|---|
| **CAT** | Catalog protocol — dataset query and listing | 2 |
| **CN** | Contract negotiation (provider side) — offer, accept, verify, finalize flows | 15 |
| **CN_C** | Contract negotiation (consumer side) — consumer-initiated negotiation flows | 11 |
| **TP** | Transfer process (provider side) — start, suspend, resume, complete, terminate flows | 16 |
| **TP_C** | Transfer process (consumer side) — consumer-initiated transfer flows | 16 |

### Interpreting Results

- **Exit code 0** — All TCK conformance tests passed.
- **Non-zero exit code** — One or more tests failed. Check the TCK container logs for details.

When running in CI, container logs are automatically uploaded as build artifacts on failure (14-day retention).

To view logs from a local run:

```bash
# While containers are still running (use --keep-containers)
docker compose -f docker-compose.tck.yml logs tck
docker compose -f docker-compose.tck.yml logs edc
```

### Configuration

TCK configuration files are in `config/tck/`:

| File | Purpose |
|---|---|
| `tck.properties` | TCK runtime settings — connector endpoints, callback addresses, and test scenario IDs (dataset IDs, offer IDs, agreement IDs) matching `DataAssembly.java` |
| `edc.properties` | EDC controlplane settings for TCK mode — enables test extensions, configures API ports, and disables production features (FDSC transfer, DCP) |

The Docker Compose file (`docker-compose.tck.yml`) orchestrates PostGIS, Scorpio (NGSI-LD context broker), TMForum API (backed by Scorpio), the EDC controlplane, and the TCK runner. Configuration files are mounted into the containers. If you need to customize connector endpoints or test IDs, edit the properties files before running.

### CI Integration

The GitHub Actions workflow (`.github/workflows/test.yml`) includes a `tck-conformance` job that:

1. Waits for unit tests and spotless checks to pass.
2. Builds the controlplane shaded JAR with Maven dependency caching.
3. Builds the Docker image with layer caching.
4. Runs the TCK via Docker Compose.
5. Uploads EDC and TCK container logs as artifacts if tests fail.

## Developing

```bash
git clone https://github.com/SEAMWARE/fdsc-edc.git
cd fdsc-edc
mvn clean package
```

### Code Formatting

[Spotless](https://github.com/diffplug/spotless) enforces **Google Java Format** and a copyright license header on all Java files. CI will reject non-conforming code.

```bash
# Check formatting
mvn clean spotless:check

# Auto-fix formatting
mvn spotless:apply
```

### Key Libraries

| Library | Purpose |
|---|---|
| [EDC](https://github.com/eclipse-edc/Connector) v0.14.1 | Dataspace connector framework |
| [Lombok](https://projectlombok.org/) | Boilerplate reduction |
| [MapStruct](https://mapstruct.org/) | Type-safe bean mapping |
| [OkHttp](https://square.github.io/okhttp/) | HTTP client |
| [Jackson](https://github.com/FasterXML/jackson) | JSON serialization |
| [Nimbus JOSE+JWT](https://connect2id.com/products/nimbus-jose-jwt) | JWT/JWS/JWE processing |
| [JUnit 5](https://junit.org/junit5/) + [Mockito](https://site.mockito.org/) | Testing |

## Deploying / Publishing

Releases are triggered automatically on push to `main`. PR labels control [semantic versioning](https://semver.org/) bumps:

| Label | Effect |
|---|---|
| `major` | Bump major version (breaking changes) |
| `minor` | Bump minor version (new features) |
| `patch` | Bump patch version (bug fixes) |

Images are pushed to `quay.io/seamware/`:
- `quay.io/seamware/fdsc-edc-controlplane-oid4vc:<version>`
- `quay.io/seamware/fdsc-edc-controlplane-dcp:<version>`

## Features

- **TMForum-native persistence** — Contract negotiations, definitions, policies, and assets stored via standard TMForum REST APIs
- **Saga-based transaction compensation** — Multi-step REST writes are automatically rolled back on failure (see [TRANSACTION_README.md](TRANSACTION_README.md))
- **Dual authentication modes** — Choose between OID4VP or DCP depending on your dataspace's identity framework
- **FIWARE transfer provisioning** — Automated APISIX route creation, credentials configuration, and ODRL policy management
- **EBSI Trusted Issuers Registry** — Validate credential issuers against the European Blockchain Services Infrastructure (DCP mode)
- **Multi-platform Docker images** — Built for both `linux/amd64` and `linux/arm64`

## Contributing

If you'd like to contribute, please fork the repository and use a feature branch. Pull requests are warmly welcome.

Before submitting:
1. Ensure all tests pass: `mvn clean test`
2. Ensure formatting is correct: `mvn clean spotless:check`
3. All public methods and classes must be documented (Javadoc)
4. Avoid magic constants — use named constants with descriptive names

## Links

- Repository: https://github.com/SEAMWARE/fdsc-edc
- Issue tracker: https://github.com/SEAMWARE/fdsc-edc/issues
- Related projects:
  - [Eclipse Dataspace Components (EDC)](https://github.com/eclipse-edc/Connector)
  - [FIWARE Data Space Connector](https://github.com/FIWARE/data-space-connector)
  - [FIWARE TMForum API](https://github.com/FIWARE/tmforum-api)

## Licensing

Copyright 2025 Seamless Middleware Technologies S.L and/or its affiliates.

Licensed under the [Apache License, Version 2.0](LICENSE).
