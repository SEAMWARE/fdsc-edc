# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FIWARE Data Space Connector EDC Extensions — a set of Eclipse Dataspace Components (EDC) extensions that integrate EDC with FIWARE ecosystem components. The project produces two deployable controlplane images:

- **controlplane-oid4vc** — EDC controlplane secured via OpenID4VP
- **controlplane-dcp** — EDC controlplane secured via DCP (Decentralized Claims Protocol)

## Build Commands

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
```

Java 21 is required. The project uses Maven (no Gradle — the `.gradle` files under `target/` are generated artifacts).

## Code Formatting

Spotless enforces **Google Java Format** and a **license header** (`license-header.txt`) on all Java files. CI will fail if formatting or headers are missing. Run `mvn spotless:apply` before committing.

## Architecture

### Module Structure

| Module | Purpose |
|---|---|
| `tmf-extension` | Uses TMForum APIs as storage backend for EDC catalog/contract/agreement data. Generates Java model classes from TMForum OpenAPI specs at build time via `openapi-generator-maven-plugin`. |
| `fdsc-transfer-extension` | Provisions data transfers at the FIWARE Data Space Connector. |
| `oid4vc-extension` | Identity/authentication via OpenID4VP (verifiable presentations). |
| `dcp-extension` | Identity/authentication via DCP with EBSI Trusted Issuers Registry support. |
| `test-extension` | Test utilities: mock identity service, TCK webhook controller, contract negotiation/transfer process guards. |
| `controlplane-oid4vc` | Assembles a shaded JAR + Docker image combining tmf + fdsc-transfer + oid4vc extensions with EDC controlplane. |
| `controlplane-dcp` | Assembles a shaded JAR + Docker image combining tmf + fdsc-transfer + dcp extensions with EDC controlplane. |

### EDC Extension Pattern

Extensions are registered via Java SPI in `META-INF/services/org.eclipse.edc.spi.system.ServiceExtension`. Each extension module implements one or more `ServiceExtension` classes. Key extensions:

- `TMFOfferResolverExtension`, `TMFContractNegotiationExtension`, `CatalogProtocolServiceExtension` (tmf)
- `FDSCTransferControlExtension` (fdsc-transfer)
- `OID4VPExtension` (oid4vc)
- `DCPExtension`, `TirExtension` (dcp)

### Code Generation

The `tmf-extension` module generates TMForum model classes from remote OpenAPI specs during `generate-sources`. Generated code lives in `target/generated-sources/` and should never be edited manually. The package namespace is `org.seamware.tmforum.<domain>.model` with a `VO` suffix on class names.

### Key Libraries

- **Lombok** and **MapStruct** for annotation processing
- **OkHttp** for HTTP clients
- **JUnit 5** + **Mockito** for testing
- **Jackson** for JSON serialization (with `jackson-datatype-jsr310`)

### Schema Files

`schemas/` contains JSON/JSON-LD schema files used for EDC contract definitions, policies, agreements, and ODRL context.

## Versioning and Release

Releases are triggered on push to `main`. PR labels (`major`, `minor`, `patch`) control semver bumps. Images are pushed to `quay.io/seamware/`.
