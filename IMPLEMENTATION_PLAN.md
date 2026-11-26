# Implementation Plan: [FDSC-EDC] DSP catalog request fails: Querying for contract definitions is currently not supported

## Overview
In `controlplane-dcp` 2.1.0 every DSP catalog request ends in a 500. The DSP catalog controller holds EDC's stock `CatalogProtocolServiceImpl`, which reaches `TMFBackedContractDefinitionStore.findAll`, and that method throws. The proxy then retries with the same token, so the consumer sees a 401. The fix makes the TMForum catalog service the one EDC injects, then implements `findAll` so the generic resolver path works too. A release note closes it out.

Root cause, confirmed in the EDC 0.18.1 sources:
- `ControlPlaneServicesExtension` provides the stock `CatalogProtocolService` through a plain `@Provider`.
- `DspVirtualCatalogApi2025Extension` (virtual) and `DspCatalogApi2025Extension` (classic) `@Inject` the service. Each copies the instance that exists at injection time into its controller during `initialize()`.
- `CatalogProtocolServiceExtension` is annotated `@Requires(CatalogProtocolService.class)` and calls `context.registerService(...)` inside `initialize()`. The dependency graph cannot see a `registerService` call, so nothing orders the DSP extensions after ours. The order falls back to ServiceLoader/classpath order. In the DCP image the controller is injected first and keeps the stock impl; the classic runtimes only work by luck.
- With `tmfExtension.catalog.enabled=false`, the stock path is the intended one. `ContractDefinitionResolverImpl.resolveFor` calls `ContractDefinitionStore.findAll(QuerySpec)` with criterion `participantContextId = <ctx>`, offset 0 and limit `Integer.MAX_VALUE`. It then calls `PolicyDefinitionStore.findById` for each access policy. `DatasetResolverImpl` uses `AssetIndex.queryAssets`, which is already implemented.

## Steps

### Step 1: Make the TMForum `CatalogProtocolService` the one injected into the DSP catalog controllers
Files: `tmf-extension/src/main/java/org/seamware/edc/CatalogProtocolServiceExtension.java`, plus a new test `tmf-extension/src/test/java/org/seamware/edc/CatalogProtocolServiceExtensionTest.java`.

- Replace `@Requires(CatalogProtocolService.class)` with `@Provides(CatalogProtocolService.class)`, and keep the conditional `context.registerService(...)` in `initialize()`.
  - Both annotations must not stay on the class: `@Requires` edges are not filtered for self-references, and keeping both throws `CyclicDependencyException` at boot.
  - The extension must start after `ControlPlaneServicesExtension`: EDC invokes an extension's `@Provider` methods right after its `initialize()`, so if the stock extension ran later its provider result would replace our registration. The only edge guaranteeing this is our `@Inject ProtocolTokenValidator`, which that extension also provides. Keep that injection (or add an explicit edge) and lock the ordering with the `DependencyGraph` test below.
  - Each DSP catalog extension now has edges to both providers. It is therefore injected after our replacement in the classic runtime (oid4vc, TCK) and the virtual runtime (DCP) alike.
- Fallback if `@Provides` has a problem when the catalog is disabled (nothing gets registered): use a non-default `@Provider public CatalogProtocolService catalogProtocolService()` together with `@Inject CatalogProtocolService` for the stock instance. It returns the TMForum service when `tmfExtension.enabled && tmfExtension.catalog.enabled`, and the injected stock instance otherwise. Do not use `@Provider(isDefault = true)`: a default is never used, because the stock provider always registers.
- Add a short comment explaining why `@Provides` is needed: `registerService` in `initialize()` is invisible to the dependency graph.
- Update the class Javadoc to one line if it is affected.
- Tests (JUnit 5 + Mockito):
  - Assert that the class carries `@Provides` including `CatalogProtocolService.class` and has no `@Requires` naming it. This is a regression guard for the ordering.
  - Add a `@ParameterizedTest` (`@CsvSource`) over `tmfExtension.enabled` × `tmfExtension.catalog.enabled`. Assert `initialize()` registers a `TMForumBackedCatalogProtocolService` only for `true,true`, and registers nothing otherwise. Mock `ServiceExtensionContext.getConfig()` with `ConfigFactory.fromMap`, and use named constants for the property keys (reuse `TMFConfig` constants if they are public).
  - Required: an ordering test with EDC's real `DependencyGraph`. Add `org.eclipse.edc:boot` (`${edc.version}`, test scope) to `tmf-extension/pom.xml`. Run the graph over three extensions given in deliberately wrong order:
    - an injecting stub that `@Inject`s `CatalogProtocolService` (stands in for the DSP catalog extensions);
    - `CatalogProtocolServiceExtension`;
    - a stock-provider stub with plain `@Provider` methods for `CatalogProtocolService` **and** `ProtocolTokenValidator` (stands in for `ControlPlaneServicesExtension`).
    Assert both orderings: stock-provider stub < `CatalogProtocolServiceExtension` < injecting stub. Also stub-provide every other type our extension `@Inject`s, so the graph resolves without a running runtime; a mocked `ServiceExtensionContext` suffices. This test turns the ticket's bug into a build failure if a later EDC upgrade or a change to our `@Inject`s breaks either ordering.
    - If the graph cannot be driven without booting a runtime, do not drop the test silently: say why in the PR description and fall back to `BaseRuntime`/`ExtensionLoader` test helpers from `org.eclipse.edc:junit` that sort extensions.
- Acceptance: `mvn test -pl tmf-extension` and `mvn clean spotless:check` pass. `mvn clean package -pl controlplane-dcp,controlplane-oid4vc,controlplane-tck -am -DskipTests` builds; a boot cycle would only show at runtime, so make sure the annotations are consistent. If Docker allows, start `docker-compose.multi-dataspace.yml` and check that the edc log shows the "already registered and has now been replaced" line for `CatalogProtocolService` before the DSP catalog API is registered, with no `CyclicDependencyException`.

### Step 2: Implement `TMFBackedContractDefinitionStore.findAll(QuerySpec)`
Files: `tmf-extension/src/main/java/org/seamware/edc/store/TMFBackedContractDefinitionStore.java`, `tmf-extension/src/main/java/org/seamware/edc/TMFContractNegotiationExtension.java` (constructor wiring around line 379), `tmf-extension/src/test/java/org/seamware/edc/store/TMFBackedContractDefinitionStoreTest.java`.

- Add `ProductCatalogApiClient` and `CriterionOperatorRegistry` constructor arguments. Wire them in `TMFContractNegotiationExtension.contractDefinitionStore(...)` using `productCatalogApi(tmfConfig)` and the already-injected `criterionOperatorRegistry`. Update the existing test `setup()` to match.
- Follow the pattern of `TMFBackedAssetIndex.queryAssets`:
  - Context: `ContextCriteria.requestedContext(criteria)`, falling back to `tmfEdcMapper.getDefaultParticipantContextId()`.
  - Fetch: page through `productCatalogApi.getProductOfferings(offset, PAGE_SIZE, ContextCriteria.serverSideContext(criteria, default).orElse(null))`. Declare `PAGE_SIZE = 100` as a named constant and stop when a page is shorter than `PAGE_SIZE`. Unfiltered fetch for the default context keeps untagged legacy offerings (ADR-3).
  - Map: `tmfEdcMapper.fromProductOffer(offering, context)`. An empty result means the offering does not serve that context or has no usable term, so skip it.
  - Filter: AND together `criterionOperatorRegistry.toPredicate(criterion)` over all criteria. The `participantContextId` criterion matches because the mapper stamps the context.
  - Sort, page, return: if `querySpec.getSortField()` is set, sort with a comparator over the field's `Comparable` value (`id`, `createdAt`, and so on, honouring `getSortOrder()`). Unknown fields compare equal. Then apply offset/limit as `stream.skip(querySpec.getOffset()).limit(querySpec.getLimit())`. Do **not** copy the `Math.min(offset + limit, size)` bound from `TMFBackedAssetIndex.queryAssets`: `ContractDefinitionResolverImpl` sends `limit = Integer.MAX_VALUE` and v5beta queries can send large limits, so with any `offset > 0` the sum overflows and `subList` throws (another 500). The asset index carries the same latent bug; fixing it there is out of scope, but a one-line `skip`/`limit` change is acceptable if touched.
- Error handling: a single offering that fails to map is skipped and logged at debug. TMForum API failures (`BadGatewayException`) propagate, as they do in `queryAssets`; do not swallow them into an empty catalog.
- Out of scope, but mention it in the class Javadoc only if you touch that line: `TMFBackedPolicyDefinitionStore.findAll` still throws. Neither catalog resolver calls it; resolvers only use `findById`.
- Tests:
  - A `@ParameterizedTest` with a `@MethodSource` case table covering: no criteria (default context); an explicit default-context criterion (unfiltered fetch); a non-default context (server-side filter argument passed); offerings the mapper rejects (skipped); offset/limit windows including offset beyond size and `offset > 0` with `limit = Integer.MAX_VALUE` (no overflow, returns the tail); paging across more than one `PAGE_SIZE` page (verify the second `getProductOfferings` call); and sort by `id` ascending and descending.
  - Use real `ContractDefinition`s and a real criterion registry (`CriterionOperatorRegistryImpl.ofDefaults()` if it is on the test classpath; otherwise mock `toPredicate` as `TMFBackedAssetIndexTest` does).
  - Add a test that a `BadGatewayException` from the client propagates.
  - Keep all constants named.
- Acceptance: `mvn test -pl tmf-extension` and `mvn clean spotless:check` pass. `findAll` no longer throws `UnsupportedOperationException`. A query of the shape `ContractDefinitionResolverImpl` sends (`participantContextId = ctx`, limit `Integer.MAX_VALUE`) returns the definitions of exactly that context's offerings.

### Step 3: Release note and docs
Files: new `releases/notes/RELEASE_NOTES_CATALOG_FIX.md`, `releases/NOTES.md` (add a top row, e.g. `2.1.1: DSP catalog requests in virtual participant contexts`), `docs/multi-dataspace.md` (short troubleshooting entry).

- The release note follows the style of `RELEASE_NOTES_EDR_API_V5.md`: Overview, Fix, Affected images, Upgrade.
  - Explain the 500 → proxy retry → DCP `JWT id ... was already used` → consumer 502/401 chain.
  - Explain that the TMForum catalog service is now reliably used when `tmfExtension.catalog.enabled=true`, and that `ContractDefinitionStore.findAll` now works for the stock catalog path and v5beta contract-definition queries.
  - Bug fix, PR label `patch`, no configuration changes.
- Troubleshooting entry in `docs/multi-dataspace.md`: a consumer-side `401 Unauthorized` on catalog requests together with a provider-side "JWT id ... was already used" usually hides an earlier provider 500. Check the provider log first.
- Acceptance: links in `releases/NOTES.md` resolve. Run `mvn clean test` and `mvn clean spotless:check` once more across the whole build. The downstream check is the `@dsp` scenarios in the data-space-connector chart (`dsp_dcp_flow`, `dsp_advanced_policy`, `dsp_multi_profile`); they cannot be run in this repo, so note them in the PR description.
