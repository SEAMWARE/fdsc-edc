# Implementation Plan: Multi-Dataspace support for the FDSC-EDC

## Overview

Let one `controlplane-dcp` runtime take part in several dataspaces at once, using
EDC 0.18.1's **virtual participant-context mode** plus **dataspace profiles**. Each
dataspace becomes a participant context with its own DID, STS settings and
associated profile(s). The data still lives in the **single shared TMForum**, so
the dataspace-specific TMForum entities (offerings, quotes, agreements, usage)
carry their context and the stores filter by it. Products
(ProductSpecifications) stay shared, and **one ProductOffering can be offered in
one dataspace, in several, or in all of them** (decision 4).
`controlplane-oid4vc` and `controlplane-tck` stay on classic mode, and the shared
modules must keep working in both modes. The deliverables are the code changes and
an operator guide, `docs/multi-dataspace.md`, with an example multi-dataspace
configuration.

### Scope decisions (from the ticket's analysis comment)

All points below are settled.

- Target EDC **0.18.1**, which `main` already uses, so there is no version bump.
- **Virtual mode applies only to `controlplane-dcp`.** `controlplane-oid4vc` and
  `controlplane-tck` keep `participant-context-connector-classic-core`.
  - This is a breaking change for every existing DCP deployment (management API
    v5 with OAuth2, DSP URLs gain `/{ctx}/{profile}`); Step 10 documents it and
    the PR is labelled `major`.
  - **Confirmed by the maintainer** (PR #7 review: "Please switch the
    controlplane to virtual mode"). Step 8 switches the existing
    `controlplane-dcp` module in place; no sibling `controlplane-dcp-virtual`
    module is added.
- **One TMForum and one ODRL-PAP** serve all dataspaces.
- **DCP identity is configured per dataspace:** DID, STS, scope mappings and the
  EBSI TIR.

### Upstream facts and design decisions

Moved to [`docs/multi-dataspace-design.md`](docs/multi-dataspace-design.md), which also records the Step 1 open-item resolutions. "Decision N" in the steps below refers to its "Design decisions" section, and "the verified facts" to its "Verified upstream facts" section.

### Working agreement

- Use `mvn -pl <module> -am test`. Never use a bare `-pl`: sibling modules
  depend on each other.
- Every step must leave `mvn clean test` and `mvn spotless:check` green across
  the reactor, and must keep the classic DSP TCK (`controlplane-tck`, the
  `.github/workflows/test.yml` TCK job) green. Steps 2–7 change no assembly,
  so classic behaviour must stay byte-for-byte equivalent. Their unit tests must
  cover both an entity *with* and one *without* a `participantContextId`.
- New property keys are named `static final` constants in the module's config
  class (`TMFConfig`, `DcpConfig`, `TirConfig`, `TransferConfig`). No inline
  literals.
- Prefer `@ParameterizedTest` for the "context A / context B / no context" case
  tables instead of duplicating test methods.

## Steps

### Step 1: Multi-dataspace design record and open-item verification

Create `docs/multi-dataspace-design.md` and put the "Verified upstream facts" and
"Design decisions" sections above into it. Then replace those two sections in
this plan with a one-line pointer to the file, so that only one copy exists.
Resolve each of the following against the 0.18.1 source or a running stack, and
record the answer:

- **TMForum extension-property query:** does the FIWARE TMForum API used in
  `docker-compose.tck.yml` (a) accept an *optional* extra property in an
  `@schemaLocation` schema, and (b) filter list endpoints by
  `?participantContextId=`? `externalId` shows (b) works for a required
  property, but it is not proven for an optional one.
  - Verify this against the TCK compose stack and record the exact query form.
  - If (b) does not work, the fallback is fetch-all with in-memory filtering.
    Record the paging consequence for `TMForumBackedCatalogProtocolService.getCatalog`,
    which today pages specs by `QuerySpec` offset and limit and fetches up to
    `MAX_OFFERINGS` offerings. Only the offerings are filtered by context; the
    specs are shared (decision 4).
  - Record the query form for the default context (decision 3): can the API
    express "`participantContextId` equals X OR is absent" in one request? If
    not, the default context fetches unfiltered and filters in memory; record
    the same paging consequence for that path.
  - Record the query form for the multi-valued offering tag (decision 4): can
    the API filter "`participantContextIds` contains X OR contains `*`" (plus
    "OR absent" for the default context) in one request? If not, offerings
    are fetched unfiltered and filtered in memory; record the paging
    consequence.
  - Confirm in the 0.18.1 source which DSP-path callers of
    `ContractDefinitionStore.findById` / `PolicyDefinitionStore.findById`
    exist, and that they take the id from the offer id / the definition's
    policy ids, so the context-qualified ids of decision 4 reach them
    unchanged.
- **Data plane registration:**
  `FDSCTransferControlExtension` saves exactly one `DataPlaneInstance`. Does
  `EmbeddedDataPlaneSelectorService` filter it out for a context that does not
  own it? If so, decide between one instance per context and a context-less
  shared instance.
- **Vault per context:** `vault.resolveSecret(ctxId, alias)` together with
  `vault-hashicorp`. Determine which path or prefix layout the STS client secret
  of each context needs.
- **TIR per dataspace:** `TrustedIssuerRegistry` is global. Pick a mechanism and
  record it:
  - a per-profile post-validator that checks the VC issuer against that
    profile's TIL; or
  - `EbsiTrustedIssuersRegistry` holding the union of all TILs, combined with a
    per-profile issuer check.
- **Management API auth for operators:** decide on the JWT issuer that FDSC uses
  for `management-api-oauth2-authentication`, e.g. the existing Keycloak in the
  FIWARE Data Space Connector chart.
- **DSP version endpoint in virtual mode:** is `/.well-known/dspace-version`
  still served at the protocol root, or per `{ctx}` / `{ctx}/{profile}`? Record
  the exact path(s); Step 9 asserts against them.
- **DSP request without a token in virtual mode:** do the `dsp-virtual`
  controllers resolve the `{ctx}/{profile}` association *before* they check the
  DCP token? Record the exact status Step 9 expects for
  `POST <protocol>/{A}/{profileA}/catalog/request` without a token (associated
  profile, e.g. 401) and for `<protocol>/{A}/{profileB}/catalog/request`
  (unassociated profile, 404). If the token is checked first, both return the
  same status; record that, and Step 9 then asserts the association through
  `GET /v5beta/participants/{A}/profiles` (or the config entry) instead.
- **`test-extension` in `controlplane-dcp`:** it is compile-scoped at
  `controlplane-dcp/pom.xml:54`. Decide whether to drop it from the DCP assembly;
  the `controlplane-tck` assembly owns TCK use.

Acceptance:
- the design record exists and every open item above is answered or explicitly
  deferred with a reason;
- this plan holds a single pointer instead of the moved sections;
- no code changes.

### Step 2: TMForum data layer — `participantContextId` extension property

This step adds the data layer only. No caller uses it yet, so behaviour does not
change.

- `schemas/`: add
  - an optional `participantContextIds` (array of string; an id or `*`) for the
    ProductOffering. `external-id.json` is shared with the ProductSpecification,
    Product and ref, so add it to `external-id.json` (they *may* carry it, but
    nothing reads it there) or give the offering its own schema, whichever
    Step 1 found the API accepts;
  - an optional `participantContextId` (string) to `quote-item.json`, `contract-negotiation.json`, `agreement.json`,
    `usage.json` and `contract-definition.json`,

  or add one shared schema, whichever Step 1 found the TMForum API accepts. Do
  not make it `required`. Existing offerings keep their `@schemaLocation`.
- `ExtendableUsageCreateVO.getAtSchemaLocation` resolves
  `CONTRACT_NEGOTIATION_SCHEMA`; point it at `USAGE_SCHEMA` like the other
  Usage VOs (see the per-VO schema table in the design record).
- `tmf-extension/src/main/java/org/seamware/edc/domain/`: add a
  `participantContextId` field with a getter and a fluent setter to these VOs
  (and only these):
  - next to `externalId`: `ExtendableQuoteItemVO`,
    `ExtendableAgreementVO` / `ExtendableAgreementCreateVO` /
    `ExtendableAgreementUpdateVO`, and `ExtendableUsageVO` /
    `ExtendableUsageCreateVO` / `ExtendableUsageUpdateVO`;
  - next to `contractNegotiation` (the Quote VOs have no `externalId`):
    `ExtendableQuoteVO` / `ExtendableQuoteCreateVO` / `ExtendableQuoteUpdateVO`;
  - `ExtendableProductOffering` gets `List<String> participantContextIds`
    instead, next to `externalId`.

  Include the new field in `equals` and `hashCode`. Add the constant
  `ALL_PARTICIPANT_CONTEXTS` (decision 4) to `TMFConfig`.
- **Not tagged:** `ExtendableProductSpecification`. It is the shared product
  (decision 4); the asset index stamps the context on read.
- **Not tagged either:** `ExtendableProduct` / `ExtendableProductCreate` and the
  ProductOrder that `TMFBackedContractNegotiationStore` creates (around `:798`
  and `:955`–`:989`), and `ExtendableProductSpecificationRef`. Nothing lists
  or looks these up by `externalId`: a Product is read by its TMForum id
  (`ProductInventoryApiClient.getProductById`), a ProductOrder by its Quote id
  (`ProductOrderApiClient.findByQuoteId`), and the Product's `externalId`
  already embeds the agreement id (a UUID). They are only reached through a
  Quote or Agreement, which carries the context. The ref is an embedded
  reference, not a stored entity.
- `tmf/ProductCatalogApiClient`, `tmf/QuoteApiClient` and `tmf/AgreementApiClient`
  (there is no Usage client; Usage VOs are only mapped in `TMFEdcMapper`): add
  context-aware overloads of the **list** lookups of tagged entities only (e.g.
  `getProductOfferings(int offset, int limit, String participantContextId)`).
  `getProductSpecifications` gets no overload, because specs are untagged.
  - A `null` context means no filter, which is today's behaviour.
  - The offering overload returns the offerings whose `participantContextIds`
    contains the context or `*`, in the query form Step 1 recorded.
  - The by-`externalId` lookups get no context parameter (decision 4). The
    offering lookup's "multiple … exist" check stays global; its error message
    adds the `participantContextId` of each match, so a cross-context duplicate
    is identifiable from the log. The spec lookup is unchanged.
  - Add two shared constants instead of per-client literals:
    `PARTICIPANT_CONTEXT_ID_QUERY_PARAM` (`participantContextId`, Quote and
    Agreement) and `PARTICIPANT_CONTEXT_IDS_QUERY_PARAM`
    (`participantContextIds`, ProductOffering). Using the singular one on
    offerings matches nothing (see decision 2).
- Tests:
  - Jackson round-trip of each VO, with and without the field;
  - MockWebServer/WireMock tests asserting the query parameter, following the
    pattern of the existing client tests.

Acceptance: `mvn -pl tmf-extension -am test` is green, and existing tests pass
unchanged.

### Step 3: Context-aware mapper, asset/contract-definition/policy stores and catalog service

- `TMFConfig`:
  - add `tmfExtension.defaultParticipantContextId` (decision 3) and resolve
    the default context in this order, first non-blank wins:
    1. `tmfExtension.defaultParticipantContextId`;
    2. the context of the `SingleParticipantContextSupplier`, when one is
       registered (classic mode; this is what `TMFConfig.fromConfig` fills
       `participantContextId` from today);
    3. no supplier registered (virtual mode, or a test): **nothing else**. Do
       not fall back to `edc.participant.context.id` / `edc.participant.id`:
       `fromConfig` reads the latter with an `ANONYMOUS_PARTICIPANT` default, so
       it always yields a value, and in virtual mode those keys are per-context
       entries, not a runtime identity. The default stays unset, legacy
       entities stay unstamped and invisible, and the decision 3 startup
       warning is logged.

    The `participantId` fallback in `fromConfig` (the classic-mode identity,
    decision 5) is unchanged; only the default *context* stops using it.
  - log a warning when the explicit key (1) and the supplier context (2) are
    both set and differ. The explicit key still wins, so a classic deployment
    with both set behaves predictably;
  - keep `getParticipantContextId()` as a deprecated accessor that returns the
    resolved default, so existing callers compile unchanged until they move to
    the new accessor.
- `store/TMFEdcMapper`:
  - stop stamping the constructor-injected `participantContextId`;
  - stamp each entity of a tagged TMForum type with that entity's
    `participantContextId`, or with the default context when it is absent;
  - for a ProductOffering, `fromProductOffer` takes the requesting context.
    It applies the decision 4 table: a single-context offering keeps its plain
    id and its own context; a multi-context or `*` offering gets the
    `ContextQualifiedId` definition and policy ids and the requesting context.
    It returns empty when the offering does not serve that context;
  - stamp an `Asset` (from an untagged ProductSpecification) with a context the
    caller passes in: the queried context, or the default context
    (decision 4);
  - in the EDC→TMForum direction, write `entity.getParticipantContextId()`.

  This applies to every builder that sets it today (`:321`, `:512`, `:772`,
  `:935`). Rename the constructor parameter to `defaultParticipantContextId`.
- `store/TMFBackedAssetIndex`, `TMFBackedContractDefinitionStore` are
  **read-only** like the policy store (`create`/`save`, `update`/`updateAsset`
  and `deleteById` throw `UnsupportedOperationException`; the contract
  definition store's `findAll` too). There is no write path to add.
  - `TMFBackedAssetIndex` (products are shared, decision 4):
    - `findById` / `resolveForAsset` are unchanged apart from stamping the
      default context. Upstream only null-checks their result.
    - `queryAssets` / `countAssets`: when the `QuerySpec` holds a
      `participantContextId` criterion
      (`ParticipantResource.filterByParticipantContextId`), stamp every mapped
      asset with that context before the in-memory `CriterionOperatorRegistry`
      predicate runs, so the criterion matches. Nothing is pushed down to
      TMForum. Every product is therefore listed in every context; a product
      only becomes *offered* in a dataspace through that dataspace's offering
      (the catalog and the negotiation check both go through the offering).
      Without a context criterion, stamp the default context, as today.
    - This includes a `QuerySpec` that combines an `id` criterion with the
      context criterion. Management API v5 loads `GET /assets/{id}` exactly
      this way and then authorizes non-admin tokens against the asset's
      context (see "Verified upstream facts" in the design record). Do not route such id queries to
      `findById`: its default stamp would turn every non-default-context
      `GET /assets/{id}` into a 404 for non-admin tokens.
  - `TMFBackedContractDefinitionStore`:
    - `findById` stays context-less (decision 4): it resolves a plain or
      context-qualified id through `ContextQualifiedId` and maps it with the
      context from the qualifier (or the offering's single context). Its
      `catch (RuntimeException)`
      keeps returning `null` as the interface requires, but logs a
      cross-context duplicate with `monitor.severe` instead of `warning`, since
      it hides the offering in every dataspace until the data is fixed.
    - `findAll` keeps throwing. The catalog is built by
      `TMForumBackedCatalogProtocolService` (below), which filters the
      offerings by context itself.
- `store/TMFBackedPolicyDefinitionStore` is **read-only**: `create`, `update`,
  `delete` and `findAll` throw `UnsupportedOperationException`, and `findById`
  reads the policy out of its owning ProductOffering via
  `ProductCatalogApiClient.getByPolicyId`. There is no TMForum policy entity and
  no write path to add.
  - The policy's context follows decision 4: the owning ProductOffering's
    single context (absent → default), or, for a multi-context offering, the
    qualifier of the requested `<policyId>@X`. Today `getByPolicyId` returns a bare
    `Optional<Policy>`, which loses the offering, and the store builds the
    `PolicyDefinition` inline. Change this:
    - add `record OfferingPolicy(Policy policy, @Nullable List<String> participantContextIds)`
      in `tmf/`; `getByPolicyId` (called with the unqualified policy id)
      returns `Optional<OfferingPolicy>`, carrying the owning offering's raw
      (possibly absent) contexts;
    - add `TMFEdcMapper.toPolicyDefinition(OfferingPolicy, @Nullable String qualifier)`,
      which builds the `PolicyDefinition` (id via `getIdFromPolicy`, qualified
      when the offering is multi-context) and applies the decision 4 table, so
      context stamping lives only in the mapper;
    - the store injects `TMFEdcMapper` and calls it. Drop the store's own
      `participantContextId` field.
  - Policy ids are globally unique (decision 4): one policy id belongs to one
    offering, which may serve several contexts. Today `getByPolicyId` stops
    at the first page with a hit; change it to keep paging through all
    offerings, and throw `BadGatewayException` naming the policy id, the
    offerings and their contexts when more than one offering matches.
  - Do not add a schema or a write path for policies.
- `store/TMForumBackedCatalogProtocolService.getCatalog` / `getDataset`:
  - fetch the offerings that serve `participantContext.getParticipantContextId()`
    only: tagged with it or with `*`, plus the untagged ones for the default
    context (decisions 3 and 4); specs stay unfiltered;
  - `TMFEdcMapper.datasetFromProductSpecification` takes the requesting context
    and builds each offer id from the definition id `fromProductOffer` would
    return in that context (plain or `ContextQualifiedId`);
  - no other change is needed: `TMFEdcMapper.datasetFromProductSpecification`
    already drops a spec that no given offering references, so a product
    appears in a dataspace's catalog only with that dataspace's offers, and
    `getDataset` returns not-found for a product that has no offering in the
    requesting context.
- `TMFOfferResolverExtension` / `store/TMForumConsumerOfferResolver`: resolve
  offers within the requesting context. Every caller that turns an offer id's
  `definitionPart()` into an offering (this resolver, and
  `TMFBackedContractNegotiationStore.getTmfOfferId`, Step 4) goes through
  `ContextQualifiedId`, so a qualified definition id finds its offering.
- Tests, parameterized over (context A, context B, legacy-no-context):
  - each mapper output has a non-null `participantContextId`;
  - the catalog service fetches only the requesting context's offerings, and
    the asset index returns every product stamped with the queried context
    (the contract-definition store's `findAll` keeps throwing, see above);
  - a default-context query returns the untagged legacy entities (and does not
    send `participantContextId=<default>` as a plain server-side filter);
  - `queryAssets` with `participantContextId = B AND id = <sharedSpec>` (the
    management API v5 lookup) returns that one asset stamped with B;
  - `findById` on the policy store stamps the owning offering's context, and
    fails for a policy id used by offerings in two contexts;
  - `findById` on the contract-definition store returns the offering with its
    mapped context, and returns `null` (logged severe) for an offering
    `externalId` used in two contexts;
  - **one product offered in A and B** (one spec, two offerings): A's catalog
    and B's catalog both list the dataset, each with only its own offer id and
    policy; `getDataset` in each context returns its own offer;
  - **one offering in several dataspaces**, parameterized over
    `participantContextIds` (`["A","B"]`, `["*"]`) × requesting context
    (A, B, C): the offering appears exactly where the decision 4 table says,
    with offer id `<externalId>@<ctx>`; the contract-definition and policy
    stores resolve that qualified id to a definition stamped with `<ctx>`;
    an id qualified with a context the offering does not serve, or a
    qualified id for a single-context offering, resolves to `null`;
  - a single-context and an untagged offering keep their plain ids (the
    classic offer ids are unchanged);
  - an offering `externalId` that itself contains `@` still resolves as a plain
    id;
  - a product offered only in A is missing from B's catalog, and `getDataset`
    in B returns not-found.
- `TMFConfig` default-context resolution, parameterized over which of
  (explicit key, supplier context, `edc.participant.*`) are set: the expected
  winner, and a warning only when the explicit key and the supplier differ.
  Include the row "no supplier, no explicit key, `edc.participant.id` set"
  → default unset and the decision 3 warning logged.

Acceptance:
- `mvn -pl tmf-extension -am test` is green;
- the classic TCK run is still green (the default context equals the TCK's
  `urn:connector:fdsc-edc`).

### Step 4: Context-aware negotiation and agreement store

- `store/TMFBackedContractNegotiationStore`:
  - persist the negotiation's `participantContextId` on the Quote (and its items)
    and on the Agreement. Products and ProductOrders stay untagged
    (see Step 2). Usage is not written today (no Usage client, and the
    mapper's Usage `toCreate`/`toUpdate` have no callers), so there is nothing
    to change for it here; whatever adds a Usage write path later must stamp
    the context (the Step 2 VO field is ready for it). A negotiation on a multi-context offering carries the
    context it ran in, never the offering's set (decision 4);
  - `getTmfOfferId` resolves the offer's `definitionPart()` through
    `ContextQualifiedId` (Step 3), so the Quote item references the right
    ProductOffering for a qualified id;
  - `queryNegotiations`, `queryAgreements` and `nextNotLeased` honour context
    criteria. `queryAgreements` must match `(agreementId, participantContextId)`,
    which upstream `TransferProcessProtocolServiceImpl.findAgreement` relies on;
  - `findById` and `findContractAgreement` take only an id, so they get no
    filtered overload. The loaded entity must carry its correct mapped context
    (absent → default); the upstream protocol services compare
    `getParticipantContextId()` themselves.
- Your own identity per context:
  - replace the `participantId` constructor field (used for related parties at
    `:1305`/`:1310` and in `TMFEdcMapper` at `:557` to derive the negotiation
    role) with a lookup through `ParticipantIdentityResolver` for the
    negotiation's context;
  - fall back to `TMFConfig.getParticipantId()` only when no resolver is
    registered, i.e. a classic runtime without the connector core.
- `TMFContractNegotiationExtension`: inject `ParticipantIdentityResolver`
  (optional) and pass it through. The `SingleParticipantContextSupplier` stays
  optional, as it is today.
- `TMFBackedLeaseHolder` / `AutomaticUnlockingLockManager`: confirm that lease
  keys are entity ids that are unique across contexts. Ids are UUIDs, but
  record this in a test.
- `test-extension/.../edc/DataAssembly.java` and
  `scripts/init-tmforum.sh`: only touch these if TCK data must carry the
  context explicitly. Prefer relying on the default context, so TCK data stays
  unchanged.
- Tests:
  - two negotiations with the same counter-party in two contexts are stored with
    distinct related parties (their own DID per context) and are not visible
    across contexts;
  - the legacy Quote with no context maps to the default context.

Acceptance:
- `mvn -pl tmf-extension -am test` and `mvn clean test` are green;
- the classic TCK run is green.

### Step 5: Per-context identity in `fdsc-transfer-extension`; verify `policy-extension`

- **Where the transfer's context comes from.** The EDR service and the
  provisioner run on the data-plane side and never see a `TransferProcess`:
  `FDSCDcpEndpointDataReferenceService.createEndpointDataReference(DataFlow)`
  gets the `DataFlow` that `FDSCProvisioner.endpointDataReference` loads via
  `dataPlaneStore.findById(flowId)`, and `FDSCDcpProvisioner.provision` gets a
  `ProvisionResource`. In 0.18.1 neither carries a context:
  `DataFlow.getParticipantId()` is the **counter-party** and must never be used
  as our context or `iss`; `ProvisionResource` has only `getFlowId()` and
  properties; `DataFlowStartMessage` has no context either. Therefore:
  - add one `TransferContextResolver` in `fdsc-transfer-extension`
    (`resolve(String flowId)`), the **single source** for both the EDR `iss`
    and the route `discovery` suffix, so the two cannot diverge;
  - it loads the transfer with `TransferProcessStore.findById(flowId)` (the
    store is already injected in `FDSCTransferControlExtension:134`). In the
    embedded data plane the flow id is the transfer process id
    (`DataPlaneSignalingFlowController` sends `processId = transferProcess.getId()`;
    `toDcpServiceRoute` already names its flow-id argument `transferProcessId`).
    Step 5 verifies this in the 0.18.1 source before relying on it. If it does
    not hold, resolve instead from `dataFlow.getAgreementId()` (also the
    `AGREEMENT_ID_PROPERTY` on the `ProvisionResource`) through the agreement
    store, whose agreements carry their context since Step 4;
  - it returns the transfer's `participantContextId`, or the resolved default
    context (Step 3) when that is null. When no transfer is found it returns a
    failure, and provisioning fails with a clear message rather than signing
    with a guessed issuer.
- Remove the single `tmfConfig.getParticipantId()` from
  `FDSCTransferControlExtension` (`:192`, `:201`):
  - `FDSCDcpEndpointDataReferenceService` resolves the context with
    `TransferContextResolver.resolve(dataFlow.getId())` and the issuer from it
    through `ParticipantIdentityResolver`;
  - `dcp/OidConfigController` (decided here, not in Step 1): add
    `GET /.well-known/openid-configuration/{participantContextId}`, which
    returns that context's identity (via `ParticipantIdentityResolver`) as
    `issuer` and 404 for an unknown context. The existing
    `/.well-known/openid-configuration` keeps answering for the default
    context. Its `issuer` is the identity of the resolved default context
    (Step 3) through `ParticipantIdentityResolver`, no longer
    `tmfConfig.getParticipantId()`. In classic mode that is the
    `SingleParticipantContextSupplier` identity, the same value as today, so
    classic deployments are unchanged. In virtual mode with no default context
    configured it returns 404 instead of `ANONYMOUS_PARTICIPANT`. Both paths
    return the same `jwks_uri`;
  - `transfer/TransferMapper.toDcpServiceRoute` and its caller
    `FDSCDcpProvisioner` (`:137`): the APISIX `openid-connect` plugin checks
    the EDR's `iss` against the discovery document's `issuer`, so the route
    must point at the transfer's own context. Pass the context from
    `TransferContextResolver.resolve(provisionResource.getFlowId())` (the same
    resolver the EDR service signs with) into `toDcpServiceRoute`, and build
    `discovery` as
    `oidConfig().host() + oidConfig().openIdPath() + "/" + participantContextId`.
    For the default context, or when the id is null, keep today's
    `host() + openIdPath()`, so classic routes are byte-for-byte unchanged;
  - `dcp/JwksController`: unchanged, one signing key for all contexts. The
    JWKS is not identity-bound; only the `issuer` claim differs per context.
- `DataPlaneInstance` registration: apply the Step 1 decision, either one
  instance per configured context or a context-less shared instance.
- `transfer/FDSCDcpProvisioner` / `FDSCOID4VPProvisioner`: keep the
  context-less `getProductSpecByExternalId`. The product is shared (decision 4),
  so there is no context to check on the spec. The dataspace binding is the
  agreement, which upstream `findAgreement` already filters by context.
- `policy-extension`: no per-dataspace split, because the ODRL-PAP is shared.
  - Verify that `PolicyContextInputMapper` / `OdrlPapPolicyExtension` work
    unchanged with entities from several contexts.
  - Add `participantContextId` to the PAP/OPA input as an **optional** field,
    so that PAP policies *can* tell dataspaces apart. Add it only if the
    existing input shape has an obvious place for it. If it does not, record
    the decision in the PR and add nothing.
- Tests: the context always comes from a `DataFlow` / `ProvisionResource`
  plus a mocked `TransferProcessStore`, never from a pre-set context:
  - `TransferContextResolver`, parameterized over (transfer in B, transfer with
    null context, no transfer found) → B, the default context, failure; a
    `DataFlow` whose `getParticipantId()` is the counter-party DID never
    leaks into the result;
  - for one flow id, the EDR `iss` and the route `discovery` suffix come from
    the same resolved context;
  - an EDR issuer is created for a transfer in each of two contexts; the OID
    config is returned for each context and for the default path, and an
    unknown context gets 404;
  - parameterized over (context B, default context, null),
    `toDcpServiceRoute` sets `discovery` ending in `/{B}` for B and the
    unchanged default path otherwise, and the issuer of B's OID config equals
    the `iss` of B's EDR.

Acceptance:
- `mvn -pl policy-extension -am test` is green (it pulls in
  `fdsc-transfer-extension` and `tmf-extension`);
- `mvn clean test` is green.

### Step 6: Per-dataspace DCP scopes and TIR in `dcp-extension`

- **Two scope mechanisms, split by what each can express** (see the DCP
  entries under "Verified upstream facts" in the design record):
  - a scope that a profile needs on **every** request (catalog, negotiation and
    transfer alike) uses the upstream keys
    `edc.iam.dcp.scopes.<k>.{id,type=DEFAULT,value,profile}`. Nothing is added
    for it in our code;
  - a scope that differs **per request type** (what `DCPExtension` registers
    today: one scope for catalog, one for negotiation, one for transfer) cannot
    be expressed upstream, because an upstream `DEFAULT` scope is added to all
    three. Only this case uses our keys.
- `DcpConfig`:
  - keep the global `dcp.scopes.{catalog,negotiation,transfer}` as the
    profile-less default;
  - add per-profile overrides `dcp.profiles.<profileName>.scopes.{catalog,negotiation,transfer}`.
- `policy/DefaultScopeMappingFunction`: choose the scope from the message's
  protocol (`message.getProtocol()` is the profile name), mirroring upstream's
  profile matching. Profiles without an override fall back to the global
  scopes.
- Both post-validators keep running on the same request policy contexts, as
  they do today, and both add to one `RequestScope` set. The two mechanisms are
  therefore additive and never replace each other. The operator guide (Step 10)
  says which to use when, and that a scope must not be configured in both.
- `TirConfig` / `TirExtension` / `tir/EbsiTrustedIssuersRegistry`: add
  per-profile TIL addresses (`ebsiTir.profiles.<profileName>.tilAddress`)
  using the mechanism Step 1 picked. The global `ebsiTir.tilAddress` stays the
  default.
- Do **not** re-implement what upstream already does per context: the DID and
  the STS keys `edc.iam.sts.oauth.*` are participant-context config entries
  (Step 7 seeds them). Check `DCPExtension` for any global DID or STS
  assumption and remove it.
- Tests:
  - parameterized (profile with override, profile without override, unknown
    profile) → expected scopes;
  - with an upstream `edc.iam.dcp.scopes.*` scope and a `dcp.profiles.*` scope
    both configured for one profile, the request scope is their union;
  - a TIR check for an issuer trusted in profile A but not in B.

Acceptance:
- `mvn -pl dcp-extension -am test` is green;
- with no `dcp.profiles.*` / `ebsiTir.profiles.*` set, behaviour is identical to
  today.

### Step 7: `participant-bootstrap-extension` — seed participant contexts from config

Add a new module `participant-bootstrap-extension` (register it in the parent
`pom.xml` `<modules>`, following the existing extension POM layout). It holds a
`ServiceExtension`, modelled on upstream `TckSetupExtension`, that seeds the
contexts in `prepare()`:

- It reads `fdsc.participants.<key>.*`:
  - `.id`, which becomes the participant context id;
  - `.identity` (the DID), the **single source** of the context's DID;
  - `.profiles` (CSV of dataspace profile names);
  - `.config.<edcKey>` (plain entries such as `edc.iam.sts.oauth.token.url`);
  - `.privateConfig.<edcKey>`, for sensitive entries.
- For each participant it upserts:
  - the context, through `ParticipantContextService`;
  - its configuration, through the `ParticipantContextConfig` store. The
    profiles become the `edc.dataspace.profiles` entry, and `.identity`
    becomes the `edc.participant.did` entry that `DidConfigProvider` reads.
    Startup fails when `.config.` sets `edc.participant.id`,
    `edc.participant.did` or `edc.iam.issuer.id` to a value other than
    `.identity`; an equal value is accepted.
- It upserts: a context or config entry that already exists (e.g. created
  earlier through the management API v5, or persisted by a later SQL store) is
  updated, not duplicated, and does not fail startup.
- It fails startup with a clear message when a referenced profile is not
  registered in `DataspaceProfileContextRegistry`.
- It fails startup when a context id (`.id`) contains `@`, the
  `ContextQualifiedId` separator (see "DSP-path callers" in the design
  record). `:` is allowed, so DID- and URN-shaped ids work.
- It is a no-op when no `fdsc.participants.*` keys are set, so it is harmless
  on the classic classpath. It is added only to `controlplane-dcp`.

The config class holds named constants for every key and has a one-line Javadoc.

Tests (Mockito on the EDC SPIs):
- two participants are seeded with their config and profiles;
- re-running against already-seeded stores updates and does not duplicate them;
- an unknown profile fails startup;
- a `.config.` DID entry that differs from `.identity` fails startup;
- parameterized over context ids: `ctx-a` and `did:web:a.example` are seeded,
  `a@b` fails startup;
- with no keys nothing is seeded.

Acceptance: `mvn -pl participant-bootstrap-extension -am test` is green.

### Step 8: Switch `controlplane-dcp` to virtual mode with an example profile configuration

- `controlplane-dcp/pom.xml`:
  - import `controlplane-virtual-base-bom` (plus `controlplane-virtual-feature-dcp-bom`)
    instead of `controlplane-base-bom`;
  - drop `participant-context-connector-classic-core`, `dsp`/`dsp-2025` classic
    wiring and `management-api` v3/v4, replacing them with what the virtual
    BOMs bring (`dsp-virtual`, `management-api-v5`,
    `management-api-oauth2-authentication`);
  - add `participant-bootstrap-extension`;
  - remove `test-extension` (Step 1 decided so) and `dsp-version-http-api`
    (Step 1: virtual mode serves the version endpoint per context; the root
    one would advertise every profile to every caller).
- Resolve the full assembly with `mvn -pl controlplane-dcp -am dependency:tree`.
  Make sure exactly one `DataFlowController` is present and no duplicate
  providers (see `docs/migration/EDC-0.18.1-UPGRADE.md` §8.3).
- Add the example configuration `config/multi-dataspace/edc.properties` with:
  - two profiles via `edc.dataspace.profiles.*`, plus the default
    `http-dsp-profile-2025-1`;
  - two participants via `fdsc.participants.*`, each with its own DID, STS
    client and profile list;
  - `edc.dsp.callback.address` containing `%s` (this makes
    `web.http.protocol.virtual` unnecessary; do not set it, see the design
    record);
  - management OAuth2 settings;
  - per-profile `dcp.profiles.*` / `ebsiTir.profiles.*`;
  - `tmfExtension.defaultParticipantContextId`.

  Add a one-line comment per block.
- Confirm that `controlplane-oid4vc/pom.xml` and `controlplane-tck/pom.xml` are
  untouched.

Acceptance:
- `mvn clean package` is green, so the `controlplane-dcp` image builds;
- the image starts with `config/multi-dataspace/edc.properties` against a
  TMForum and logs both contexts seeded, both profiles registered and no
  unresolved-service or participant-context warnings;
- the classic TCK job is still green.

### Step 9: Compose-level two-dataspace verification

- Add `docker-compose.multi-dataspace.yml`, which reuses the postgis, scorpio
  and tmforum services of `docker-compose.tck.yml`. It runs the
  `controlplane-dcp` image with `config/multi-dataspace/edc.properties`, plus a
  static JWKS server for the management API JWT and a static server for
  `schemas/`. The seed's `@schemaLocation`s and the controlplane's
  `tmfExtension.schemaBaseUri` both point at that schema server (see the
  "published schema URL" pitfall in the design record).
- `scripts/init-tmforum-multi-dataspace.sh` seeds (decision 4):
  - one **shared** ProductSpecification with one ProductOffering tagged `["A"]`
    and one tagged `["B"]`, each with a different policy;
  - one ProductSpecification offered only in A;
  - one ProductSpecification with a single ProductOffering tagged `["*"]`
    (offered in every dataspace);
  - one legacy ProductOffering without a context (default context).

  Offering `externalId`s are distinct; the duplicate case is covered by the
  Step 3 unit tests.
- `scripts/verify-multi-dataspace.sh` generates an admin JWT with
  `openssl`/`jq` and asserts the following, exiting non-zero on any failure:
  - `GET /v5beta/participants` lists both contexts;
  - `POST /v5beta/participants/{A}/assets/request` and
    `.../{B}/assets/request` (empty QuerySpec) both list the shared product
    (products are listed in every context; offer isolation is asserted in the
    Step 3 catalog tests). There is no collection `GET` on assets in 0.18.1;
  - `GET /v5beta/participants/{B}/assets/{sharedProductId}` with a
    **non-admin** token whose `sub` is B returns 200. This exercises the
    `AuthorizationServiceImpl` context check on a shared product;
  - `POST <protocol>/{A}/{profileA}/catalog/request` without a DCP token
    returns the status Step 1 recorded for an associated profile (not 404),
    while `<protocol>/{A}/{profileB}/catalog/request` returns 404, because
    that profile is not associated with A. If Step 1 found the token is
    checked first, use the fallback it recorded instead;
  - the DSP version endpoint answers at the path(s) Step 1 recorded for virtual
    mode;
  - the TMForum seed itself: listing offerings with the Step 1 query form for
    A and for B both return the `*` offering, and only A's returns the A-only
    one.

  A full DSP catalog exchange needs a real DCP wallet and is out of scope here.
  The unit tests of Steps 3–6 cover catalog isolation.
- `.github/workflows/test.yml`: add a `multi-dataspace` job that runs the
  script. Keep the existing TCK job unchanged.

Acceptance:
- the script passes locally and in CI;
- the classic TCK job is still green.

### Step 10: Operator guide and documentation

- Add `docs/multi-dataspace.md`, the deliverable guide. It covers:
  - concepts, in the order dataspace → participant context → profile;
  - the DSP URL layout `/protocol/{ctx}/{profile}`;
  - which settings are per context (DID, STS) and which are per profile (DCP
    scopes, TIR);
  - `fdsc.participants.*` reference;
  - `edc.dataspace.profiles.*` reference;
  - TMForum schemas: `schemas/` must be published or self-hosted where
    TMForum can reach it, with `tmfExtension.schemaBaseUri` pointing there
    (the default is an external GitHub URL that lacks the new properties);
  - management API v5 and OAuth2;
  - which TMForum entities carry `participantContextId` (the decision 2
    table), and the default-context rule for legacy data;
  - how to offer a product in one, several or all dataspaces: one
    ProductSpecification, with either one offering whose
    `participantContextIds` lists the dataspaces (or `*`), or one offering per
    dataspace when the policies differ; untagged offerings stay in the default
    context only; offering and policy ids are globally unique, and
    multi-dataspace offerings appear under `<id>@<ctx>` (decision 4);
  - DCP scopes: upstream `edc.iam.dcp.scopes.*` for scopes on every request of
    a profile, `dcp.profiles.*.scopes.*` only for per-request-type scopes, and
    never the same scope in both (Step 6);
  - a walkthrough of `config/multi-dataspace/edc.properties`;
  - migrating an existing single-dataspace `controlplane-dcp` deployment.
- `README.md`, `controlplane-dcp/README.md`: link the guide and state that the
  DCP image now runs in virtual mode, which is a breaking change: the
  management API moves to v5 with OAuth2, and DSP URLs change.
- `AGENTS.md`:
  - add `participant-bootstrap-extension` to the structure;
  - record the virtual-vs-classic assembly split and the TMForum
    `participantContextId` convention.
- `releases/notes/RELEASE_NOTES_MULTI_DATASPACE.md`: list the operator-facing
  breaking changes. Label the final PR `major`, because the DCP image's DSP and
  management API URLs change.
- In `docs/multi-dataspace-design.md`, mark every open item resolved.

Acceptance:
- `mvn clean package` and `mvn spotless:check` are green;
- every config key named in the guide exists as a constant in code or is an
  upstream key listed in the design record.
