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
