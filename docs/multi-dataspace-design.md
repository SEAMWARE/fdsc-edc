# Multi-dataspace design records

Why `controlplane-dcp` takes part in several dataspaces the way it does,
recorded as architecture decision records (ADRs). Each record states the
problem, the decision and what follows from it. The operator-facing guide is
[`multi-dataspace.md`](multi-dataspace.md).

The records are numbered, stable and referenced from the code as `ADR-<n>`.
Where an upstream or TMForum behaviour drove a decision, the behaviour is named
here; the exact call site is commented in the code that relies on it, not in
this document.

| ADR | Decision |
|---|---|
| [ADR-1](#adr-1--one-dataspace-is-one-participant-context) | One dataspace is one participant context |
| [ADR-2](#adr-2--only-dataspace-specific-tmforum-entities-carry-a-context-tag) | Only dataspace-specific TMForum entities carry a context tag |
| [ADR-3](#adr-3--untagged-tmforum-data-belongs-to-a-default-context) | Untagged TMForum data belongs to a default context |
| [ADR-4](#adr-4--a-product-is-shared-an-offering-names-the-dataspaces-it-is-made-in) | A product is shared; an offering names the dataspaces it is made in |
| [ADR-5](#adr-5--our-own-identity-is-resolved-per-context) | Our own identity is resolved per context |
| [ADR-6](#adr-6--contexts-are-seeded-from-static-configuration-at-startup) | Contexts are seeded from static configuration at startup |
| [ADR-7](#adr-7--one-shared-context-less-data-plane-instance) | One shared, context-less data plane instance |
| [ADR-8](#adr-8--all-contexts-share-one-vault-separated-by-secret-alias) | All contexts share one Vault, separated by secret alias |
| [ADR-9](#adr-9--per-dataspace-trusted-issuers-as-a-two-layer-check) | Per-dataspace trusted issuers as a two-layer check |
| [ADR-10](#adr-10--management-api-access-through-oauth2-tokens) | Management API access through OAuth2 tokens |
| [ADR-11](#adr-11--virtual-dsp-assembly-explicit-callback-address-no-root-version-endpoint) | Virtual DSP assembly: explicit callback address, no root version endpoint |
| [ADR-12](#adr-12--test-extension-stays-out-of-controlplane-dcp) | `test-extension` stays out of `controlplane-dcp` |
| [ADR-13](#adr-13--edr-tokens-are-bound-to-a-transfer-first-and-a-dataspace-second) | EDR tokens are bound to a transfer first and a dataspace second |
| [ADR-14](#adr-14--edrs-exposed-per-participant-context-through-v5beta) | EDRs exposed per participant context through v5beta |

Appendices: [measured TMForum behaviour](#appendix-a--measured-tmforum-behaviour),
[where the configuration keys are read](#appendix-b--where-the-configuration-keys-are-read).

---

## ADR-1 — One dataspace is one participant context

**Status:** accepted.

**Context.** A dataspace is a trust framework: it decides which issuers are
trusted, which credential proves membership, and which DSP dialect is spoken.
EDC 0.18.1 offers two dimensions that could carry this:

- a **participant context**, which holds an identity (DID) and its own
  configuration entries, such as the STS client;
- a **dataspace profile**, which binds only the protocol version and binding,
  the JSON-LD namespace and contexts, and the webhook. A profile carries no
  DID, no STS, no trusted issuers and no scopes.

**Decision.** One dataspace is one participant context, associated with the
profile(s) it speaks. The dataspace's identity (DID), STS client and Vault
alias live on the context. DCP scopes and the trusted issuers list are keyed by
**profile**, because that is the only dimension available where those checks
run: upstream picks scopes from the request's profile, and the issuer check
runs where only the profile is known. An operator who uses the same DID in two
dataspaces still configures two contexts.

**Consequences.**

- Negotiations, agreements and transfers belong to exactly one context, so data
  and policy stay isolated per dataspace.
- A dataspace that needs its own scopes or its own trusted issuers needs its
  own profile. The example configuration gives each dataspace one.
- DSP URLs carry both dimensions:
  `<web.http.protocol.path>/<participant context id>/<profile name>/...`.

---

## ADR-2 — Only dataspace-specific TMForum entities carry a context tag

**Status:** accepted.

**Context.** TMForum is the storage backend, and one TMForum instance is shared
by all dataspaces. Every EDC entity carries a `participantContextId`, so the
TMForum entities behind them must be able to carry it too. TMForum stores such
extension properties only against a JSON schema referenced by
`@schemaLocation`, and validates each write against it
(see [Appendix A](#appendix-a--measured-tmforum-behaviour)).

**Decision.** Tag the entities that belong to one dataspace, and nothing else:

| TMForum entity | EDC entity | Tagged | Why |
|---|---|---|---|
| ProductSpecification | Asset | **no** | the product itself; shared across dataspaces (ADR-4) |
| ProductOffering (and its embedded policies) | ContractDefinition, PolicyDefinition | yes, **multi-valued** `participantContextIds` | an offer made in one, several or all dataspaces (ADR-4) |
| Quote, QuoteItem | ContractNegotiation | yes, `participantContextId` | a negotiation runs in one dataspace |
| Agreement | ContractAgreement | yes, `participantContextId` | an agreement is made in one dataspace |
| Usage | TransferProcess | yes, `participantContextId` | a transfer runs in one dataspace |
| Product, ProductOrder, ProductSpecificationRef | — | no | reached only through a tagged Quote or Agreement |

- The property is **optional** in the schemas under `schemas/`, so existing
  data still validates.
- For a non-default context it is filtered **server-side**, so paging stays
  server-side: `participantContextId=X` for Quote, Agreement and Usage, and
  `participantContextIds=X,*` (array contains `X` or the wildcard) for the
  ProductOffering.

**Consequences.**

- An entity has exactly one `@schemaLocation`, so the property is added to the
  schema each tagged value object already references, rather than to a schema
  of its own. `ExtendableUsageCreateVO` referenced the contract-negotiation
  schema instead of the usage schema, which silently disabled validation of
  every Usage create; it now points at `usage.json`.
- The schemas must be published somewhere TMForum can reach, and
  `tmfExtension.schemaBaseUri` must point there. Against an older published
  schema the writes still succeed, but the new properties are not type-checked;
  against an unreachable URL every extended write fails with 400.
- Writers must omit an empty `participantContextIds` rather than write `[]`,
  and readers treat a missing, empty or non-string-only array as absent.

---

## ADR-3 — Untagged TMForum data belongs to a default context

**Status:** accepted.

**Context.** Every entity written by an existing single-dataspace deployment is
untagged, and the TCK data set is untagged as well. TMForum's query language
cannot express "equals X **or** absent": there is no `ne` and no exists
operator, and an empty value is rejected
(see [Appendix A](#appendix-a--measured-tmforum-behaviour)). A filter on the
default context would therefore return only the tagged entities and silently
drop all legacy data.

**Decision.** Untagged entities belong to the **default context**, configured as
`tmfExtension.defaultParticipantContextId`. In classic mode it defaults to the
single participant context; in virtual mode it must be configured, otherwise
such entities are invisible and a warning is logged at startup. The default
context is **never** pushed down as a filter: the stores fetch unfiltered, map
absent to the default, and filter in memory afterwards.

**Consequences.**

- An existing deployment keeps all of its data by naming its context in one
  setting. There is no data migration.
- On the in-memory path the server offset no longer matches the filtered
  result, so the stores page through all entities with the server maximum
  (`limit=1000`) until a short page, then apply the `QuerySpec` offset and limit
  in memory. Non-default contexts keep server-side offset and limit.
- The catalog fetched offerings with a fixed cap. On the default-context path
  that cap would apply **before** the context filter, so offerings are paged to
  exhaustion there instead.
- An offering tagged with the default context explicitly, or with the wildcard,
  is handled by the same in-memory filter.

---

## ADR-4 — A product is shared; an offering names the dataspaces it is made in

**Status:** accepted.

**Context.** The same product is usually offered in several dataspaces, often
under different policies. Modelling it once per dataspace would duplicate every
ProductSpecification. On the EDC side, however, a `ContractDefinition` and a
`PolicyDefinition` have exactly one `participantContextId`, their stores'
`findById` takes no context, and the negotiation protocol service compares the
definition's context with the negotiation's — so a by-id lookup has to learn the
context from the id itself. Assets are not affected: the DSP path never compares
an asset's context, and the management API loads assets through a query that
carries the owner's context criterion.

**Decision.** The product (ProductSpecification) is shared and carries no
context. The ProductOffering's `participantContextIds` decides where it is
offered:

| `participantContextIds` | Offered in |
|---|---|
| absent or empty | the default context only (ADR-3; legacy data) |
| `["A"]` | dataspace A only |
| `["A", "B"]` | dataspaces A and B |
| `["*"]` | every context, including ones added later |

Absent deliberately does **not** mean "all": a deployment that adds a second
dataspace must not expose its existing offers there without an explicit tag. An
offer that needs *different* policies per dataspace is modelled as one offering
per dataspace, all referencing the same ProductSpecification. The wildcard is
the named constant `ALL_PARTICIPANT_CONTEXTS`.

Ids follow from the one-context rule on EDC's side:

- an offering that serves **exactly one** context (absent, or one id) keeps its
  plain `externalId` as contract definition id and its plain policy ids,
  stamped with that context. Classic and TCK data are therefore unchanged;
- an offering that serves **several or all** contexts is exposed in context X
  under the context-qualified id `<externalId>@X`, and its policies under
  `<policyId>@X`, stamped with X. One shared helper, `ContextQualifiedId`,
  builds and parses these ids;
- parsing tries the full id as a plain `externalId` first, so the common case
  stays one lookup. A plain hit resolves only if that offering serves exactly
  one context. When the plain lookup finds nothing, or finds an offering its
  plain id cannot address, the id is split at the **last** separator; the
  qualified form resolves only if the prefix names a multi-context offering
  whose `participantContextIds` contains X or the wildcard. Policy ids follow
  the same rules;
- the asset index stamps a mapped product with the context the query asks for,
  or with the default context on a context-less `findById`;
- the contract offer id is built from the definition id and the product's
  external id, so the same product gets a distinct offer id per offering and,
  for a multi-context offering, per dataspace.

**Consequences.**

- Each (offering, context) pair has exactly one id, every id resolves to at most
  one definition, and an id qualified with X can only produce a definition
  stamped X — which upstream rejects in any other context.
- **Ambiguous ids are a data error.** A single-context offering whose
  `externalId` (or policy id) is literally `foo@X` collides with the qualified
  id of a multi-context offering `foo` that serves X. When a plain hit contains
  the separator, the lookup also checks the split form; if both resolve it logs
  a `SEVERE` message naming both offerings and returns nothing, the same outcome
  as a cross-context duplicate. The catalog may still list the colliding offer,
  but negotiating it fails until the data is fixed. The operator guide states
  the restriction.
- Offering `externalId`s and policy ids stay globally unique, and by-id lookups
  stay context-less. A duplicate is reported by the existing "multiple … exist"
  check, which now names the id and the offerings' contexts.
- **A context id must not contain the separator `@`.** `CD@X@Y` for a context
  `X@Y` would parse as offering `CD@X` in context `Y`. The participant
  bootstrap rejects such ids (ADR-6). An `externalId` may contain `@`, because
  the full id is tried as a plain id first, unless it collides as described
  above.
- **`:` is not restricted**, in context ids or in offering `externalId`s. Offer
  ids that EDC or our mapper build are Base64-encoded per part before they are
  joined with `:`, so a `:` inside the definition id never reaches the split,
  and DID- or URN-shaped context ids round-trip. A hand-written, unencoded
  offer id containing `:` already failed to parse before this change and is no
  worse now; a qualified id is never issued unencoded, because only
  multi-context offerings get one and those appear only in catalogs we build.
- A negotiation, agreement and usage always belong to exactly one context — the
  one the negotiation ran in — even when the offering serves several. An
  agreement's asset id may name a shared product; that is safe because the
  agreement itself is tagged and the lookup filters by context.

---

## ADR-5 — Our own identity is resolved per context

**Status:** accepted.

**Context.** In classic mode the connector has one identity, taken from
`TMFConfig.getParticipantId()`. In virtual mode each context has its own DID,
and a code path that used the configured value would stamp the wrong identity on
entities of every other dataspace.

**Decision.** On any code path that has a participant context, the own identity
comes from the context (`ParticipantContext.getIdentity()` or
`ParticipantIdentityResolver`), never from the configuration.
`TMFConfig.participantId` / `participantContextId` remain as the classic-mode
default only.

**Consequences.** Quotes, agreements and catalogs carry the DID of the dataspace
they belong to. A single-dataspace deployment behaves exactly as before.

---

## ADR-6 — Contexts are seeded from static configuration at startup

**Status:** accepted.

**Context.** Participant contexts can be created through the management API,
but the FIWARE Data Space Connector is deployed through Helm, and this runtime
has no SQL store for EDC: contexts created at runtime would be lost on the next
restart, and every deployment would need an imperative bootstrap step.

**Decision.** A `participant-bootstrap-extension` reads the contexts from
`fdsc.participants.*` and seeds them at startup. Seeding is an upsert and runs
on every start, so a context that already exists is updated, not duplicated, and
its configuration is replaced by the declared entries. The management API v5
stays available for runtime changes. Startup fails, naming the participant, on a
missing or duplicate id, an id containing the context-qualifier separator `@`
(ADR-4), an unregistered profile, an attempt to set the DID or the profile list
through a plain config entry, or when participants are declared on a runtime
without the virtual participant-context services.

**Consequences.**

- Every context must be declared in configuration; changes made only through the
  management API do not survive a restart.
- Private entries (such as a per-context Vault configuration) are encrypted with
  the configured algorithm, using a key held in the Vault.

---

## ADR-7 — One shared, context-less data plane instance

**Status:** accepted.

**Context.** `controlplane-dcp` deliberately runs the legacy data plane
signaling controller. The modern selector picks a data plane by strict equality
on the participant context, with no wildcard and no shared instance, so under it
each context would need its own registered instance. The legacy controller does
no context filtering at all.

**Decision.** Keep the single, context-less `DataPlaneInstance` that the FDSC
transfer extension registers today. Under the legacy controller it is selectable
in every context. The virtual assembly keeps the legacy controller and excludes
the modern signaling module, so that exactly one `DataFlowController` is
present.

**Consequences.** All dataspaces share one data plane. If a later change moves
to the modern signaling controller, the instance has to be saved once per
context, stamped with that context id. That is out of scope here.

---

## ADR-8 — All contexts share one Vault, separated by secret alias

**Status:** accepted.

**Context.** EDC's Vault abstraction ignores the partition by default. The
HashiCorp Vault implementation reads a per-context configuration entry
(`edc.vault.hashicorp.config`, a JSON settings object) when one is present, and
otherwise falls back to the global configuration, because fallback is enabled by
default. The context id is never added to the secret path.

**Decision.** Contexts share the global Vault configuration, and every context
uses a **distinct STS secret alias**. The alias is configured explicitly per
context, not derived from the context id, so the operator controls the key
layout; the documented convention is `<context id>-sts-client-secret`. An
operator who needs hard isolation sets the context's private config entry
`edc.vault.hashicorp.config` with its own folder — no code is needed for that.

**Consequences.** One Vault to operate. A missing or shared alias means two
dataspaces would authenticate with the same STS secret, which the convention
makes visible in the configuration.

---

## ADR-9 — Per-dataspace trusted issuers as a two-layer check

**Status:** accepted.

**Context.** The upstream `TrustedIssuerRegistry` has no context and no profile
dimension, and the credential validation service checks against it globally. Two
ways to add the dimension were considered:

- a post-validator on the request policy contexts. **Not feasible:** it runs
  *before* token verification, so it cannot see the presented credentials, and
  the later policy contexts carry only the participant agent, with no profile.
- the per-profile id-extraction function that the protocol token validator calls
  on the verified claim token. The claim token's `vc` claim carries the
  credentials, and returning `null` results in 401.

**Decision.** A two-layer check:

1. The trusted-issuer registry is backed by the **union** of the global
   `ebsiTir.tilAddress` and all `ebsiTir.profiles.<profile>.tilAddress`, so the
   upstream check passes for an issuer trusted in any dataspace.
2. Once any profile has its own TIL, **every** profile's id-extraction function
   is wrapped: it returns `null`, and therefore 401, unless every credential
   issuer is trusted by *that* profile's TIL, or by the global TIL for a profile
   without one. Without the second layer, the union of layer 1 would let a
   dataspace accept issuers trusted only by another one.

The upstream profile registry only appends, so a profile cannot be replaced in
place; the wrapping happens at registration time, through a registry that
replaces upstream's default one. Without any `ebsiTir.profiles.*` setting it is
not registered at all and upstream's default stays in place — which is correct,
because with no profile TIL there is nothing to separate.

That the wrapper, rather than a core registry, is the one profiles get
registered on rests on the provider wiring: `TirExtension` names
`DataspaceProfileContextRegistry` in `@Provides`, which both orders it ahead of
every extension that injects the registry and suppresses upstream's
`@Provider(isDefault = true)` in `ControlPlaneDefaultServicesExtension`. If
either side of that changed, profiles would land on an unwrapped registry and a
profile without its own TIL would fall back to the layer-1 union — the exact
hole layer 2 exists to close. `ProfileRegistryWiringTest` asserts both halves,
as a canary for EDC upgrades. It is not a runtime boot test: an end-to-end one
needs the `org.eclipse.edc:junit` fixtures, which this repository does not
depend on today.

**Consequences.**

- A failing or hanging TIL is skipped on lookup — its issuers count as
  untrusted — and is not asked again for a cooldown window, so one dataspace's
  TIL outage does not break the others. This covers the **global** TIL too:
  because the union registry backs the upstream issuer check, an exception
  escaping it would fail inbound verification for every dataspace, including one
  whose own TIL is healthy. An unreachable TIL therefore means "trusts nobody"
  (401), never a 5xx. Registration is not skipped and still fails loudly, so a
  misconfigured TIL address surfaces at startup rather than silently disabling
  trust.
- TIL lookups are not cached: every credential check queries the global TIL and
  every profile TIL, and the profile's own TIL again in layer 2. Operators
  should keep TIL timeouts short and place TILs close to the connector. A
  per-issuer cache is deferred until latency proves to be a problem.
- The profile name becomes part of configuration keys
  (`ebsiTir.profiles.<profile>.*`, `dcp.profiles.<profile>.*`). A name
  containing `:` — such as upstream's default profile — must be escaped in a
  properties file and cannot be written as an environment variable, so the guide
  recommends `:`-free profile names.

---

## ADR-10 — Management API access through OAuth2 tokens

**Status:** accepted.

**Context.** The virtual assembly ships management API v5 with OAuth2
authentication and scope-based authorization. Tokens are checked for signature,
issuer, `nbf`, `exp` and `iat`; there is **no** audience check, and the issuer
check compares for equality, so leaving the issuer unset accepts only tokens
*without* an `iss` claim. Authorization reads two claims: `scope`
(`management-api:read`, `:write`, `:admin`) and `sub`, which must name an
existing context unless the caller is an admin; ownership requires `sub` to
equal the resource's context.

**Decision.** Use the Keycloak that the FIWARE Data Space Connector chart
already deploys. A realm issues the management tokens
(`edc.iam.oauth2.issuer`, `edc.iam.oauth2.jwks.url`), with client scopes
`management-api:admin`, `management-api:read` and `management-api:write`.
Operators use an admin client. A client that may manage only one dataspace gets
`read`/`write` and a hardcoded-claim mapper that sets `sub` to the context id,
because Keycloak otherwise puts the service account's user id there, which names
no context and gets 401.

**Consequences.**

- `edc.iam.oauth2.issuer` must always be set.
- The Keycloak side is chart configuration and is documented in the operator
  guide rather than exercised by a test in this repository. The verification
  stack uses no Keycloak: it publishes a static JWKS and signs its tokens
  locally.

---

## ADR-11 — Virtual DSP assembly: explicit callback address, no root version endpoint

**Status:** accepted.

**Context.** In virtual mode the DSP version metadata is served per context at
`<protocol>/{participantContextId}/.well-known/dspace-version`, listing only
that context's profiles. The classic root endpoint comes from a separate module
that the virtual BOM does not bring in, but which this image used to declare
explicitly. It *would* start in virtual mode and then answer at the root,
without a context, with **every** registered profile — advertising profiles a
caller cannot use. Separately, `web.http.protocol.virtual` changes no routes: it
only appends `/%s` to the callback address when no explicit callback address is
set.

**Decision.** Remove the classic version API from `controlplane-dcp`, and set an
explicit `edc.dsp.callback.address` containing `%s` instead of relying on
`web.http.protocol.virtual`. `controlplane-oid4vc` and `controlplane-tck` stay
in classic mode and keep the classic module.

**Consequences.**

- Version metadata is per context, and the root path returns 404. The
  verification stack asserts this, so the classic module cannot return to the
  virtual assembly unnoticed.
- The callback address the connector advertises contains the context id.
- Routing answers before authentication, which gives the status codes the guide
  and the verification stack rely on (with `edc.dataspace.enable.profiles.all`
  unset or false):

  | Request without an `Authorization` header | Status |
  |---|---|
  | catalog request on a profile associated with the context | **401** |
  | catalog request on a profile not associated with the context | **404** |
  | catalog request on an unknown context | **404** |

  With `edc.dataspace.enable.profiles.all=true` the association check is skipped
  and all three return 401, so the example configuration must not set it.

---

## ADR-12 — `test-extension` stays out of `controlplane-dcp`

**Status:** accepted.

**Context.** The TCK controller and webhook inject a single-participant-context
supplier, which does not exist in virtual mode; a virtual runtime containing
them fails to start on an unresolved dependency, even with the test extension
disabled, because injection is resolved before initialization. The test identity
extension also provides an `IdentityService` and a `Vault`, which compete with
DCP and the HashiCorp Vault on a production assembly.

**Decision.** `controlplane-dcp` does not contain `test-extension`;
`testExtension.*` has no effect there. TCK runs use the `controlplane-tck`
assembly, which stays in classic mode and keeps the extension unchanged.

**Consequences.** The DCP image is a production assembly only. TCK coverage is
unaffected.

---

## ADR-13 — EDR tokens are bound to a transfer first and a dataspace second

**Status:** accepted.

**Context.** `FDSCDcpEndpointDataReferenceService` signs the bearer EDR of a
DCP flow. All participant contexts sign with the **same** key
(`FDSCTransferControlExtension.KEY_NAME`) and publish it through the **same**
JWKS endpoint; `OidConfigController` serves one discovery document per context,
and those documents differ in the `issuer` field alone. The only per-context
part of an EDR is therefore `iss`/`aud`, this connector's DID in that dataspace.

That raises the question of what stops an EDR minted in dataspace A from
opening a route of dataspace B, since the signature verifies either way.

**Decision.** Do not rely on the issuer for that separation. Every route is
bound to **its own transfer**: `TransferMapper` sets `client_id` and
`required_scopes` of the APISIX `openid-connect` plugin to the transfer process
id, and the EDR carries that id in its `scope` claim. A token minted for
transfer X is accepted by the route of transfer X and by no other route.

The issuer stays the second layer. A route points at the discovery document of
the transfer's own context, resolved through the same `TransferContextResolver`
result the EDR is signed with, so the two cannot drift.

**Consequences.** A transfer belongs to exactly one dataspace, so the scope
binding already confines an EDR to the dataspace that issued it: a cross-
dataspace replay would need that dataspace's own transfer token, which is not
an escalation. Key rotation stays one operation for the whole connector.

The price is that the separation lives in the route configuration rather than in
the key material. Two tests keep it honest: `EdrIssuerMatchesDiscoveryTest` pins
the EDR issuer and the discovery document of a transfer to the same context, and
`EdrIsRejectedByForeignRouteTest` mints an EDR for a transfer of one context and
asserts that the route of another transfer — in the same dataspace and in the
other one — does not accept it, by applying both conditions the gateway applies.

That second test models the gateway rather than running one. The
`docker-compose.multi-dataspace.yml` stack carries no APISIX and runs with
`fdscTransfer.enabled=false`, so `verify-multi-dataspace.sh` cannot cover this
end to end; doing so would need a data plane, a provisioned transfer and
therefore the DCP wallet that the script's header already puts out of scope.

Giving every context its own signing key would move the separation into the
cryptography and make the gateway configuration irrelevant to it. That is a
larger change — one JWKS endpoint per context, one rotation per dataspace — and
is deliberately deferred. It is the step to take if the gateway configuration
ever stops being trustworthy.

---

## ADR-14 — EDRs exposed per participant context through v5beta

**Status:** accepted.

**Context.** A consumer needs the EDR of a STARTED transfer to call the
provider's data plane. EDC 0.18.1 has no v5 EDR resource. `EdrCacheApiV3Controller`
is v3 only and is not part of `controlplane-virtual-base-bom`, so
`controlplane-dcp`, which serves only v5beta, had no way to read an EDR.

**Decision — exposure.** `edr-api-extension` adds
`/v5beta/participants/{participantContextId}/edrs` (`EdrApiV5Controller`):
`POST /request`, `GET /{transferProcessId}/dataaddress` and
`DELETE /{transferProcessId}`, with the `management-api:edrs:read|write` scopes.
It adds no partitioning of its own. `edr-store-receiver` already stamps each
`EndpointDataReferenceEntry` with the context of the transfer it was saved for.
Queries add a `participantContextId` criterion. By-id calls go through
`AuthorizationService` with an `EndpointDataReferenceEntry` lookup function
(`EdrOwnershipLookup`), so a foreign context gets 403 and an unknown entry 404,
the same as for every upstream v5 resource.

**Decision — token refresh.** No data plane this project supports renews tokens.
Upstream EDC 0.18.1 has no refresh at all (no `auto_refresh`, no `EdrService`),
and the FDSC provider data plane (APISIX with the JWT from
`FDSCDcpEndpointDataReferenceService`) issues no refresh token. The only
EDC-ecosystem data plane known to renew tokens is Tractus-X. Its EDR carries
`refreshEndpoint`, `refreshToken`, `expiresIn` and `refreshAudience` in the
`https://w3id.org/tractusx/auth/` namespace. Its refresh call needs an
`Authorization` token signed by the consumer, so it is not a bare RFC 6749
`refresh_token` grant.

So only the seam ships. `EdrTokenRefresher.refreshIfNeeded(entry, current)`
returns the address to serve. `EdrManagementApiV5Extension` provides
`NoopEdrTokenRefresher` as the `@Provider(isDefault = true)`. It returns
`current` unchanged and makes no outbound call. `GET .../dataaddress` takes
`auto_refresh` (default `true`), with the same name and default as the Tractus-X
EDR API. When the flag is true, the controller passes the resolved address
through the refresher before transforming it. A deployment with a renewing data
plane registers its own `EdrTokenRefresher` from a separate extension, and that
replaces the default.

A non-default refresher must follow these rules:

1. **Implement a named, existing contract**, such as the Tractus-X one above,
   not an invented one. Persist any renewal itself through
   `EndpointDataReferenceStore.save`, and return `current` unchanged on failure
   so a failed renewal never turns a valid read into an error.
2. **Store an absolute expiry.** When it saves a renewal, write
   `expiresAt = now + expiresIn` into the DataAddress.
   `EndpointDataReferenceStoreImpl.save` keeps the entry's original
   `createdAt`, so an expiry measured from `createdAt` would treat every
   refreshed token as already expired and refresh on every read.
3. **Only call a refresh endpoint on the EDR's own origin** (same scheme, host
   and port as the EDR `endpoint`) or on a configured allow-list. The provider
   chooses the URL. An unchecked one lets any provider make the consumer
   controlplane send requests into its own network (blind SSRF).

**Consequences.** EDR isolation between dataspaces rests on the same
`AuthorizationService` rules as assets or contract negotiations, and is not a
second mechanism. There is no outbound HTTP code and no configuration property
for a feature nobody here runs, and no provider-controlled URL is ever fetched.
Clients can send `auto_refresh` today and will get renewed tokens once a real
refresher is plugged in, with no change on their side. The cost is that a
consumer of a Tractus-X provider receives the cached token and has to re-start
the transfer when it expires.

Two layers test this. `EdrTwoContextIsolationTest` runs EDC's real
`EndpointDataReferenceStoreReceiver`, store and `AuthorizationServiceImpl` and
feeds them STARTED and TERMINATED transfer events of two contexts. It checks
that each context lists and reads only its own EDR, that a foreign one gives 403
or 404, and that a terminated transfer's EDR is gone. `verify-multi-dataspace.sh`
checks the same scope and context rules against the running image. No transfer
starts in that stack, so the listings it sees are empty.

---

## Appendix A — Measured TMForum behaviour

Measured against the TCK stack images (`tmforum-all-in-one:1.5.4`, Scorpio
`java-4.1.10`, postgis), with a local schema server. "Works" means the request
returned exactly the expected entities.

| Question | Result |
|---|---|
| Optional string property `participantContextId` on ProductOffering, Quote and Agreement | **works**: creates with and without it both return 201. The schema is enforced: a number gives 400 `integer found, string expected` (when the schema declares the property; see below) |
| Optional `participantContextId` on Usage | **works**: create gives 201, and `GET …/usage?participantContextId=A` returns only A-tagged usages |
| Optional array property `participantContextIds` on ProductOffering | **works**: `["A","B"]` gives 201; `"A"` or `[1]` gives 400 |
| `GET …?participantContextId=A` (offering, quote, agreement) | **works**: only A-tagged entities are returned and untagged ones are excluded. `.eq=A` is equivalent |
| OR across values | **works**: `participantContextId=A,B` (or `participantContextId=A;participantContextId=B`) |
| `participantContextId=A&participantContextId=B` | means AND: 0 results |
| "equals A **OR absent**" in one request | **not expressible**. The operators are `.eq/.gt/.gte/.lt/.lte/.regex`; there is no `ne` or exists. `participantContextId=` gives 400; `!=`, `%7C`, `%22%22` give 500; `q=` gives 400; regex with `.*` or anchors matches nothing |
| `GET …/productOffering?participantContextIds=A` | **works**: array-contains, so `["A","B"]` matches |
| `GET …/productOffering?participantContextIds=A,*` | **works**: contains A **or** the literal `"*"`. `*` is matched literally (no wildcard) and needs no encoding |
| `;` combining *different* extension properties (`participantContextId=A;externalId=x`) | **500**: do not use |
| Paging with a filter (`&offset=N&limit=M`) | **works**: 15 matches paged as 5/5/5/0 with no duplicates, also for `A,*`, the array filter, and Quote |
| Page size | the default is **10**, so an unpaged filtered query silently truncates. `limit=1000` works; `limit=1001` gives **500** |

**Empty and blank values.** `participantContextIds: []` is stored but reads back
as `[{}]`, and `""` reads back as `""`. Writers omit the property instead;
readers treat a missing, empty or non-string-only value as absent (ADR-2).

**Schema fetching.** TMForum fetches the `@schemaLocation` URL on every create or
update that carries extension properties, and returns 400 if it is unreachable
or 404. Reads succeed without it. Validation runs only when the body has
properties outside the base model, and an extension property without
`@schemaLocation` gives 400. Each value object writes exactly one
`@schemaLocation`:

| Value object | Schema it writes | Property added |
|---|---|---|
| `ExtendableProductOffering` | `external-id.json` (shared with spec, Product, ProductCreate, SpecRef) | `participantContextIds` (array) |
| `ExtendableQuoteVO` / `…CreateVO` / `…UpdateVO` | `contract-negotiation.json` | `participantContextId` |
| `ExtendableQuoteItemVO` | `quote-item.json` | `participantContextId` |
| `ExtendableAgreementVO` / `…CreateVO` / `…UpdateVO` | `agreement.json` | `participantContextId` |
| `ExtendableUsageVO` / `…UpdateVO` | `usage.json` | `participantContextId` |
| `ExtendableUsageCreateVO` | `usage.json` (was `contract-negotiation.json`, a bug) | `participantContextId` |
| `ExtendableProductOfferingTerm` | `contract-definition.json` | `participantContextId` (nothing reads it; the term is embedded in the offering, whose tag governs) |

**Undeclared properties are accepted without a type check** (measured on the same
stack): with a schema that does not declare `participantContextId`, a create
carrying it as a string *or a number* passes validation, and TMForum stores it
and filters on it. Only a schema with `additionalProperties: false` rejects it,
and no schema under `schemas/` sets that. So tagged writes against an older
published schema **succeed** but are not type-checked — which is also why the
`ExtendableUsageCreateVO` mismatch was a silent loss of validation rather than a
rejection: a Usage create against the negotiation schema was accepted with an
invalid transfer state that `usage.json` rejects with 400.

**Query forms in use** (ADR-2, ADR-3):

| Store / context | Query | Filtering |
|---|---|---|
| Quote, Agreement in non-default context X | `?participantContextId=X` | server-side, so paging stays server-side |
| Usage in non-default context X | `?participantContextId=X` | server-side. Measured on create and list only: nothing in the runtime writes or lists Usage today |
| Offerings in non-default context X | `?participantContextIds=X,*` | server-side, so paging stays server-side |
| Any entity in the **default** context | unfiltered | in memory: map (absent → default), then filter |
| By-id lookups (`externalId`, policy id, quote id) | unchanged, context-less | the mapped entity carries its context |

---

## Appendix B — Where the configuration keys are read

Upstream keys named in the operator guide, checked against the
`controlplane-dcp` shaded JAR (EDC `0.18.1`):

| Key | Read by |
|---|---|
| `edc.dataspace.profiles.<k>.*` | `DataspaceProfileConfigurationExtension` |
| `edc.dataspace.enable.profiles.all` | `ConnectorParticipantContextExtension` |
| `edc.dsp.callback.address`, `web.http.protocol.virtual` | `DspApiBaseConfigurationExtension` |
| `edc.iam.dcp.scopes.<k>.{id,type,value,profile}` (`type` `DEFAULT` or `POLICY`) | `DynamicDcpScopeConfigurationExtension` |
| `edc.iam.oauth2.{issuer,jwks.url,jwks.cache.validity}` | `ManagementApiOauth2AuthenticationExtension` |
| `edc.participants.config.encryption.algorithm` | `ParticipantContextConfigServicesExtension` |
| `edc.encryption.aes.key.alias` | `AesEncryptionExtension` |
| `edc.vault.hashicorp.*` (global Vault, shared by all contexts) | `HashicorpVaultConfig` |
| `web.http.path`, `web.http.management.path`, `web.http.protocol.path` (and `.port`) | EDC web server contexts |

Keys of this repository are declared as constants in `ParticipantBootstrapConfig`
(`fdsc.participants.*`), `TMFConfig` (`tmfExtension.*`), `DcpConfig`
(`dcp.scopes.*`, `dcp.profiles.*`) and `TirConfig` (`ebsiTir.*`).

> The EDC documentation on the upstream `main` branch describes features that
> 0.18.1 does **not** have — the `/v5beta/dataspaceprofiles` and
> `/v5beta/dcpscopes` APIs, `trustedIssuers` and `protocol.path` on a profile,
> `edc.iam.sts.type` and `edc.participants.config.validation.strict`. Do not
> design against them.
