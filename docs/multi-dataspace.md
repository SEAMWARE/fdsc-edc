# Running `controlplane-dcp` in several dataspaces

This guide is for operators. It explains how one `controlplane-dcp` runtime
takes part in several dataspaces at once, how to configure it, and how to
migrate an existing single-dataspace deployment.

`controlplane-dcp` runs in EDC 0.18.1's **virtual participant-context mode**.
`controlplane-oid4vc` and `controlplane-tck` keep the classic single-participant
mode, and nothing in this guide applies to them.

The design behind it (verified upstream behaviour, decisions, trade-offs) is in
[`multi-dataspace-design.md`](multi-dataspace-design.md). A complete example
configuration is in
[`config/multi-dataspace/edc.properties`](../config/multi-dataspace/edc.properties),
and [`docker-compose.multi-dataspace.yml`](../docker-compose.multi-dataspace.yml)
runs it against a TMForum.

> **Breaking change.** The DCP image's DSP URLs and its management API change.
> See [Migrating a single-dataspace deployment](#migrating-a-single-dataspace-deployment).

## Contents

- [Concepts](#concepts)
- [DSP URL layout](#dsp-url-layout)
- [Per context or per profile](#per-context-or-per-profile)
- [Configuration reference](#configuration-reference)
- [Management API v5 and OAuth2](#management-api-v5-and-oauth2)
- [TMForum data](#tmforum-data)
- [Offering a product in one, several or all dataspaces](#offering-a-product-in-one-several-or-all-dataspaces)
- [DCP scopes](#dcp-scopes)
- [Walkthrough of the example configuration](#walkthrough-of-the-example-configuration)
- [Migrating a single-dataspace deployment](#migrating-a-single-dataspace-deployment)
- [Verifying a deployment](#verifying-a-deployment)
- [Limitations and pitfalls](#limitations-and-pitfalls)

## Concepts

**Dataspace.** A group of participants with a shared trust framework: who
issues membership credentials, which issuers are trusted, which DSP version is
spoken. The connector joins each dataspace with its own identity.

**Participant context.** One dataspace is one participant context. The context
holds what identifies the connector *in that dataspace*: its DID and its STS
client. Negotiations, agreements and transfers belong to exactly one context.
Contexts are seeded from configuration at startup
(`participant-bootstrap-extension`, see [`fdsc.participants.*`](#fdscparticipants)).
If you use the same DID in two dataspaces, still configure two contexts, so
that their data and policies stay apart.

**Dataspace profile.** A profile describes *how* the connector talks DSP: the
protocol version and binding, the JSON-LD namespace and contexts. A profile
does not carry a DID, an STS or trusted issuers. A context is associated with
one or more profiles and is reachable only through them. This repository adds
two per-profile settings on top: DCP scopes and the EBSI Trusted Issuers List
(TIL).

In short: a **dataspace** maps to a **participant context** (who we are there),
which speaks one or more **profiles** (how we talk there).

## DSP URL layout

```
<web.http.protocol.path>/<participant context id>/<profile name>/<catalog|negotiations|transfers>/...
```

With the example configuration, dataspace A's catalog is at

```
http://edc:8282/protocol/dataspace-a/dsp-dataspace-a/catalog/request
```

- A request for a profile that is not associated with the context, or for an
  unknown context, returns **404**. A request for an associated profile
  without a valid DCP token returns **401**.
- DSP version metadata is served per context at
  `<web.http.protocol.path>/<participant context id>/.well-known/dspace-version`.
  It lists only that context's profiles. There is no root
  `<web.http.protocol.path>/.well-known/dspace-version` any more.
- The callback address the connector advertises must contain the context.
  Set `edc.dsp.callback.address` with a `%s`, which is replaced by the
  participant context id, e.g. `http://edc:8282/protocol/%s`.
  `web.http.protocol.virtual=true` is only a fallback for deployments without
  an explicit callback address: it then appends `/%s` to the default one. It
  changes no routes, so do not set it when `edc.dsp.callback.address` is set.
- Publish each context's DSP address (for example in the DID document's
  service entry, or wherever your dataspace announces connectors) with both
  the context id and the profile name in it.

The OpenID discovery document that APISIX uses to verify EDR tokens is also
per context: `/.well-known/openid-configuration/<base64url(context id)>` on the
default API (`web.http.path`). The URL-safe Base64 encoding has no padding.
The bare `/.well-known/openid-configuration` answers for the default context
(`tmfExtension.defaultParticipantContextId`) and returns 404 if none is
configured. Routes created by `fdsc-transfer-extension` point at the right
document automatically. All contexts share one JWKS.

## Per context or per profile

| Setting | Scope | Where it is configured |
|---|---|---|
| DID (`edc.participant.did`) | per context | `fdsc.participants.<key>.identity` |
| STS token URL, client id, client secret alias (`edc.iam.sts.oauth.*`) | per context | `fdsc.participants.<key>.config.*` |
| Profiles the context is reachable through (`edc.dataspace.profiles`) | per context | `fdsc.participants.<key>.profiles` |
| Vault settings (`edc.vault.hashicorp.config`) | per context, optional | `fdsc.participants.<key>.privateConfig.*` |
| DSP version, binding, JSON-LD namespace and contexts | per profile | `edc.dataspace.profiles.<key>.*` |
| DCP scopes (upstream) | per profile | `edc.iam.dcp.scopes.<key>.*` |
| DCP scopes per request type | global default, per-profile override | `dcp.scopes.*`, `dcp.profiles.<profile>.scopes.*` |
| Trusted Issuers List | global default, per-profile override | `ebsiTir.tilAddress`, `ebsiTir.profiles.<profile>.tilAddress` |
| TMForum, ODRL-PAP, Vault, FDSC transfer (`fdscTransfer.*`) | shared by all dataspaces | as for a single-dataspace deployment |

DCP scopes and the TIL are keyed by *profile*, not by context. Upstream DCP
picks scopes from the request's profile, and the trust check runs where only
the profile is known. Give each dataspace its own profile when its scopes or
TIL differ, as in the example.

## Configuration reference

### `fdsc.participants.*`

Read by `participant-bootstrap-extension` (`ParticipantBootstrapConfig`). Each
`<key>` declares one participant context. The key is only a label for the
settings; the context id is `.id`.

| Key | Required | Meaning |
|---|---|---|
| `fdsc.participants.<key>.id` | yes | Participant context id. Appears in DSP URLs and in TMForum tags. Must not contain `@`. `:` is allowed, so DID- or URN-shaped ids work |
| `fdsc.participants.<key>.identity` | yes | The context's DID. Stored as the context identity and as the `edc.participant.did` config entry. This is the only place to set the DID |
| `fdsc.participants.<key>.profiles` | no | Comma-separated profile names (`edc.dataspace.profiles.<k>.name`). Stored as the context's `edc.dataspace.profiles` entry. A context with no profile is unreachable over DSP |
| `fdsc.participants.<key>.config.<edcKey>` | no | Plain context config entries, e.g. `edc.iam.sts.oauth.token.url`, `edc.iam.sts.oauth.client.id`, `edc.iam.sts.oauth.client.secret.alias` |
| `fdsc.participants.<key>.privateConfig.<edcKey>` | no | Sensitive context config entries, e.g. `edc.vault.hashicorp.config`. Stored encrypted (see below) |

Startup fails with a message naming the participant when:

- `.id` or `.identity` is missing or blank, or two participants share an `.id`;
- `.id` contains `@`;
- a profile in `.profiles` is not registered (check the `edc.dataspace.profiles.*` names);
- `.config.` or `.privateConfig.` sets `edc.participant.id`,
  `edc.participant.did` or `edc.iam.issuer.id` to a value other than
  `.identity` (an equal value is accepted);
- `.config.` or `.privateConfig.` sets `edc.dataspace.profiles` (use `.profiles`);
- participants are declared on a runtime without the virtual
  participant-context services (i.e. not `controlplane-dcp`).

Seeding is an upsert and runs on every start. A context that already exists,
for example one created through the management API, is updated and not
duplicated. Its configuration is **replaced** by the declared entries, so
changes made at runtime through the management API to a declared context's
config are lost on restart. Without the SQL store (see
[Limitations](#limitations-and-pitfalls)) contexts live in memory; contexts
created only through the management API are gone after a restart.

**Private entries** are encrypted with the algorithm
`edc.participants.config.encryption.algorithm` (the example uses `aes`, with
the key read from the Vault under `edc.encryption.aes.key.alias`).

**STS secrets.** All contexts share the global Vault configuration
(`edc.vault.hashicorp.*`). The STS client secret is resolved per context from
`edc.iam.sts.oauth.client.secret.alias`, so give every context a distinct
alias. The convention is `<context id>-sts-client-secret`. The alias is set
explicitly, not derived, so you control the Vault key layout. For hard
isolation, set the context's private entry `edc.vault.hashicorp.config` (a
JSON `HashicorpVaultSettings` object, e.g. with its own `folder`). Without it
the context falls back to the global Vault settings, because
`edc.vault.hashicorp.allowfallback` defaults to `true`.

### `edc.dataspace.profiles.*`

Upstream keys (`DataspaceProfileConfigurationExtension`). Each `<key>`
registers one profile:

| Key | Meaning |
|---|---|
| `edc.dataspace.profiles.<key>.name` | Profile name. Appears in DSP URLs, in `fdsc.participants.<key>.profiles` and in the per-profile `dcp.profiles.*`, `ebsiTir.profiles.*` and `edc.iam.dcp.scopes.*.profile` settings |
| `edc.dataspace.profiles.<key>.protocol.version` | DSP version, e.g. `2025-1` |
| `edc.dataspace.profiles.<key>.protocol.binding` | DSP binding, e.g. `HTTPS` |
| `edc.dataspace.profiles.<key>.protocol.namespace` | DSP JSON-LD namespace, e.g. `https://w3id.org/dspace/2025/1/` |
| `edc.dataspace.profiles.<key>.jsonld.context.urls` | Comma-separated JSON-LD context URLs |

- Upstream's default profile `http-dsp-profile-2025-1` is registered only while
  **no** profile is configured. As soon as you declare any profile, declare
  the default one explicitly too if a context should keep using it (the
  example does).
- Use profile names without `:`. The name is part of the
  `dcp.profiles.<profile>.*` and `ebsiTir.profiles.<profile>.*` keys. A `:`
  must be escaped as `\:` in a `.properties` file and cannot be written in an
  environment variable at all.
- Do not set `edc.dataspace.enable.profiles.all=true`. It skips the
  context–profile association, so every context becomes reachable through
  every profile.

### DSP callback address

| Key | Meaning |
|---|---|
| `edc.dsp.callback.address` | Must contain `%s`, replaced by the participant context id, e.g. `http://edc:8282/protocol/%s` |
| `web.http.protocol.virtual` | Fallback only: appends `/%s` to the default callback address when `edc.dsp.callback.address` is not set |

### TMForum

All dataspaces share one TMForum (`tmfExtension.*`, unchanged). Two settings
matter for multi-dataspace operation:

| Key | Meaning |
|---|---|
| `tmfExtension.defaultParticipantContextId` | Context that owns TMForum entities without a context tag (legacy data). **Required in virtual mode**: without it untagged offerings, quotes and agreements are invisible, and a warning is logged at startup. Set it to the context of the dataspace the deployment served before |
| `tmfExtension.schemaBaseUri` | Base URL of the JSON schemas written as `@schemaLocation` on extended TMForum entities. **Must point at a copy of this repository's `schemas/` that TMForum can reach** (see below) |

**Publish the schemas.** TMForum fetches the `@schemaLocation` of every create
or update that carries extension properties, and answers 400 if the URL is
unreachable. The default `schemaBaseUri` is an external GitHub URL that does
not contain the new `participantContextId(s)` properties: writes against it
still succeed, but the new properties are not type-checked, and a TMForum
without internet access rejects every extended write. Serve `schemas/` from a
web server inside your cluster (the compose file uses a static nginx) or
publish it at a stable URL, and set `tmfExtension.schemaBaseUri` to that base,
including the trailing `/`. Your TMForum seeding scripts must use the same base
for the `@schemaLocation`s they write.

`tmfExtension.*` keys are camelCase and therefore cannot be set through
environment variables. Use the properties file or JVM system properties
(`-DtmfExtension.schemaBaseUri=...`).

### DCP and TIR

| Key | Meaning |
|---|---|
| `dcp.scopes.{catalog,negotiation,transfer}` | Scope requested for that request type in every profile without an override |
| `dcp.profiles.<profile>.scopes.{catalog,negotiation,transfer}` | Replaces the global scope of that request type for requests in `<profile>`. Unset request types fall back to `dcp.scopes.*` |
| `edc.iam.dcp.scopes.<key>.{id,type,value,profile}` | Upstream scopes. `type=DEFAULT` adds `value` to *every* request (catalog, negotiation and transfer) of `profile` |
| `ebsiTir.tilAddress` | Global Trusted Issuers List. Used for profiles without their own TIL |
| `ebsiTir.profiles.<profile>.tilAddress` | TIL of one profile. A request in that profile is accepted only if every credential issuer is trusted by this TIL |

See [DCP scopes](#dcp-scopes) for which scope mechanism to use. With no
`dcp.profiles.*` and `ebsiTir.profiles.*` set, DCP and TIR behave exactly as in
a single-dataspace deployment.

How the TIL check works with per-profile TILs:

1. An issuer passes the general credential check if any TIL (global or any
   profile's) trusts it.
2. The profile check then rejects a request (401) unless every credential
   issuer is trusted by that profile's own TIL, or by the global TIL for a
   profile without one. So a dataspace never accepts an issuer that only
   another dataspace trusts.

A profile TIL that fails or times out is skipped (its issuers count as
untrusted) and not asked again for 30 seconds, so one dataspace's TIL outage
does not break the others. A failing global TIL still fails the request. TIL
lookups are not cached: each credential check queries the global TIL and every
profile TIL. Keep TIL HTTP timeouts short and place the TILs close to the
connector.

## Management API v5 and OAuth2

The management API is now **v5** (`/v5beta/...`) and requires an OAuth2 JWT.
The v3 and v4 APIs are gone from the DCP image. Resources are scoped by
context:

```
<web.http.management.path>/v5beta/participants                                   # contexts (admin)
<web.http.management.path>/v5beta/participants/<context id>/assets/request       # query assets
<web.http.management.path>/v5beta/participants/<context id>/assets/<asset id>
<web.http.management.path>/v5beta/participants/<context id>/contractnegotiations/...
```

There is no collection `GET` on assets; query with `POST .../assets/request`
and a QuerySpec body (`{}` for all).

| Key | Meaning |
|---|---|
| `edc.iam.oauth2.issuer` | Expected `iss` of management tokens. **Always set it**: if unset, only tokens *without* `iss` are accepted |
| `edc.iam.oauth2.jwks.url` | JWKS used to verify the token signature |
| `edc.iam.oauth2.jwks.cache.validity` | JWKS cache time (default 5 minutes) |

Tokens are checked for signature, `iss`, `exp`, `nbf` and `iat`. There is no
audience check. The claims used are:

- `scope` (space-separated): `management-api:read` and `management-api:write`
  for the endpoints, `management-api:admin` for access to every context and to
  `/v5beta/participants`;
- `sub`: with `management-api:admin` it can be anything; without it, `sub` must
  equal an existing context id, and the token can only reach that context's
  resources.

**With Keycloak** (the FIWARE Data Space Connector chart already deploys one):

1. Create a realm (or reuse one) for management access:
   `edc.iam.oauth2.issuer=<keycloak>/realms/<realm>` and
   `edc.iam.oauth2.jwks.url=<keycloak>/realms/<realm>/protocol/openid-connect/certs`.
2. Create the client scopes `management-api:admin`, `management-api:read` and
   `management-api:write`, included in the token's `scope` claim.
3. Operators use a client with the admin scope.
4. A client that should manage only one dataspace gets `read`/`write` (not
   `admin`) and a *hardcoded claim* mapper that sets `sub` to the context id.
   Keycloak otherwise puts the service account's user id into `sub`, which
   names no context, and every request gets 401.

## TMForum data

All dataspaces share one TMForum. Products are shared; everything that happens
*in* a dataspace is tagged with the context it belongs to:

| TMForum entity | EDC entity | Tag |
|---|---|---|
| ProductSpecification | Asset | none: a product is shared by all dataspaces |
| ProductOffering (and its embedded policies) | ContractDefinition, PolicyDefinition | `participantContextIds`: array of context ids, or `["*"]` |
| Quote, QuoteItem | ContractNegotiation | `participantContextId` |
| Agreement | ContractAgreement | `participantContextId` |
| Usage | TransferProcess | `participantContextId` |
| Product, ProductOrder, ProductSpecificationRef | — | none: reached only through a tagged Quote or Agreement |

- The connector writes the tag on quotes, agreements and usages itself. You tag
  only the offerings you create.
- The properties are optional in the schemas, so existing data stays valid.
- **Default-context rule:** an entity without a tag (or with an empty tag)
  belongs to the context named by `tmfExtension.defaultParticipantContextId`.
  All data of an existing single-dataspace deployment is untagged and therefore
  stays in that context, with no data migration.
- Write `participantContextIds` only with at least one entry. TMForum stores an
  empty array as `[{}]`; the connector treats it as absent, but omit it instead.

## Offering a product in one, several or all dataspaces

Model the product once, as one ProductSpecification. Then decide per offering
where it is offered with its `participantContextIds`:

| `participantContextIds` | Offered in |
|---|---|
| absent or empty | the default context only |
| `["dataspace-a"]` | dataspace A only |
| `["dataspace-a", "dataspace-b"]` | dataspaces A and B |
| `["*"]` | every context, including contexts added later |

- **Same policies everywhere:** one ProductOffering whose
  `participantContextIds` lists the dataspaces, or `["*"]`.
- **Different policies per dataspace:** one ProductOffering per dataspace, each
  tagged with its dataspace and each referencing the same ProductSpecification.
- **Untagged offerings stay in the default context only.** Absent does not mean
  "everywhere": adding a second dataspace never exposes existing offers there
  until you tag them.

Example offering (abridged) made in A and B:

```json
{
  "name": "weather-data-offer",
  "externalId": "weather-data-offer",
  "@schemaLocation": "https://schemas.example.org/fdsc-edc/external-id.json",
  "productSpecification": { "id": "<TMForum id of the ProductSpecification>" },
  "participantContextIds": ["dataspace-a", "dataspace-b"]
}
```

The `productOfferingTerm` with the access and contract policies is the same as
for a single-dataspace offering and is omitted here;
[`scripts/init-tmforum-multi-dataspace.sh`](../scripts/init-tmforum-multi-dataspace.sh)
creates complete offerings for every case in the table above.

**Ids.** Offering `externalId`s and policy ids must stay globally unique across
all dataspaces; a duplicate is a data error and the lookup fails. EDC gives a
contract definition exactly one context, so the ids it sees are:

- an offering for **one** context (untagged, or one id) keeps its plain
  `externalId` as contract definition id, and its policies keep their ids;
- an offering for **several or all** contexts appears in context X as
  `<externalId>@X`, and its policies as `<policyId>@X`.

Consequently **no other offering or policy id may equal
`<multi-context id>@<context>`**. If a single-context offering's `externalId`
is literally `foo@dataspace-b` while a multi-context offering `foo` serves
`dataspace-b`, both resolve to the same id. The connector logs a `SEVERE`
message naming both offerings, and negotiating that id fails until the data is
fixed. Context ids must not contain `@` (startup fails otherwise); offering ids
may, as long as they do not collide in this way.

## DCP scopes

Two mechanisms add scopes to the DCP token request. Both run on every request
and their results are unioned, so they add up and never replace each other.

- **A scope a profile needs on every request** (catalog, negotiation and
  transfer alike): use the upstream keys.

  ```properties
  edc.iam.dcp.scopes.b-membership.id=b-membership
  edc.iam.dcp.scopes.b-membership.type=DEFAULT
  edc.iam.dcp.scopes.b-membership.value=org.eclipse.dspace.dcp.vc.type:DataspaceBMembershipCredential:read
  edc.iam.dcp.scopes.b-membership.profile=dsp-dataspace-b
  ```

- **A scope that differs per request type**: use `dcp.scopes.*` (all profiles)
  and `dcp.profiles.<profile>.scopes.*` (one profile). Upstream cannot express
  this, because a `DEFAULT` scope is added to all three request types.

  ```properties
  dcp.scopes.catalog=org.eclipse.dspace.dcp.vc.type:MembershipCredential:read
  dcp.scopes.negotiation=org.eclipse.dspace.dcp.vc.type:MembershipCredential:read
  dcp.scopes.transfer=org.eclipse.dspace.dcp.vc.type:MembershipCredential:read
  dcp.profiles.dsp-dataspace-b.scopes.transfer=org.eclipse.dspace.dcp.vc.type:DataTransferCredential:read
  ```

- `dcp.profiles.<profile>.scopes.<type>` is also the only way to stop a profile
  from requesting the global `dcp.scopes.<type>`: the override replaces it for
  that profile. An upstream scope cannot remove it. This is why the example
  configuration overrides all three types for dataspace B.
- **Never configure the same scope in both mechanisms.** It would be requested
  twice, and a later change to one of them would silently leave the other in
  place.

## Walkthrough of the example configuration

[`config/multi-dataspace/edc.properties`](../config/multi-dataspace/edc.properties)
configures two dataspaces, A and B, sharing one TMForum, ODRL-PAP and Vault.
Host names, DIDs and URLs are placeholders.

1. **Runtime and web contexts.** The usual `web.http.*` ports and paths.
   `edc.participant.id` is dataspace A's DID; it is used only as the identity of
   untagged TMForum entities in the default context, not as a runtime-wide
   identity.
2. **DSP callback address.** `edc.dsp.callback.address=http://edc:8282/protocol/%s`;
   `web.http.protocol.virtual` is not set.
3. **Dataspace profiles.** Three DSP 2025-1 profiles: upstream's default
   `http-dsp-profile-2025-1` (declared explicitly, because declaring any
   profile drops the implicit one), `dsp-dataspace-a` and `dsp-dataspace-b`.
4. **Participant contexts.**
   - `dataspace-a` with DID `did:web:dataspace-a.example.org`, its own STS
     client, and profiles `dsp-dataspace-a` and `http-dsp-profile-2025-1`;
   - `dataspace-b` with DID `did:web:dataspace-b.example.org`, its own STS
     client, and only profile `dsp-dataspace-b`. So
     `/protocol/dataspace-b/http-dsp-profile-2025-1/...` returns 404.
   - The STS secret aliases follow `<context id>-sts-client-secret`.
5. **Management API v5.** `edc.iam.oauth2.issuer` and `edc.iam.oauth2.jwks.url`
   point at a Keycloak realm `edc-management`.
6. **Vault and private participant config.** One HashiCorp Vault for all
   contexts. Private participant config entries are encrypted with AES, using
   the key stored in the Vault under `participant-config-aes-key`.
7. **DCP scopes.** Every profile requests `MembershipCredential` for catalog,
   negotiation and transfer. `dsp-dataspace-b` overrides all three with
   `DataspaceBMembershipCredential`, so B's counter-parties are asked only for
   B's credential.
8. **EBSI TIR.** `ebsiTir.tilAddress` is A's TIL and serves every profile
   without its own; `dsp-dataspace-b` trusts only the issuers of its own TIL.
9. **TMForum.** One shared TMForum. `tmfExtension.defaultParticipantContextId=dataspace-a`
   puts all untagged entities into dataspace A. Set `tmfExtension.schemaBaseUri`
   to your hosted `schemas/` (the compose file passes it as a JVM property).
10. **FDSC transfer and ODRL-PAP.** Configured as for one dataspace and shared
    by both. The ODRL-PAP input of an agreement carries its
    `participantContextId`, so PAP policies can tell dataspaces apart.

## Migrating a single-dataspace deployment

An existing `controlplane-dcp` deployment runs in classic mode. Upgrading the
image switches it to virtual mode, and **these things change for everyone**:

| What | Before | After |
|---|---|---|
| DSP endpoint | `<protocol>/2025-1/...` | `<protocol>/<context id>/<profile>/...` |
| DSP version metadata | `<protocol>/.well-known/dspace-version` | `<protocol>/<context id>/.well-known/dspace-version` |
| Callback address | `edc.dsp.callback.address=http://host/protocol` | must contain `%s`: `http://host/protocol/%s` |
| Management API | v3/v4 | v5 (`/v5beta/participants/<context id>/...`) with an OAuth2 JWT |
| Identity and STS | global `edc.participant.id`, `edc.iam.sts.oauth.*` | per context, in `fdsc.participants.<key>.*` |
| OpenID discovery for APISIX | `/.well-known/openid-configuration` | additionally `/.well-known/openid-configuration/<base64url(context id)>`; the bare path serves the default context |
| `test-extension` | bundled | removed from the DCP image (`testExtension.*` has no effect); TCK runs use `controlplane-tck` |

Steps:

1. **Pick a context id** for the dataspace you are already in, e.g.
   `dataspace-a`. It becomes part of your DSP URL, so choose it once. It must
   not contain `@`.
2. **Keep your data in it.** Set
   `tmfExtension.defaultParticipantContextId=<that id>`. All existing,
   untagged offerings, quotes, agreements and usages belong to it; no TMForum
   data migration is needed. Keep `edc.participant.id` at your current DID.
3. **Publish the schemas** from `schemas/` where TMForum can reach them and
   set `tmfExtension.schemaBaseUri` to that URL (see [TMForum](#tmforum)).
4. **Declare the profile(s).** Declare `http-dsp-profile-2025-1` explicitly,
   plus any profile of your own, with `edc.dataspace.profiles.*`.
5. **Declare the context** with `fdsc.participants.<key>.*`:
   - `.id`: the id from step 1;
   - `.identity`: your current DID;
   - `.profiles`: the profiles from step 4;
   - `.config.edc.iam.sts.oauth.token.url`, `.config.edc.iam.sts.oauth.client.id`
     and `.config.edc.iam.sts.oauth.client.secret.alias`: your current global
     STS settings.
6. **Set the callback address** with `%s` and remove
   `web.http.protocol.virtual` if you set it.
7. **Set up management access**: configure `edc.iam.oauth2.issuer` and
   `edc.iam.oauth2.jwks.url` (see
   [Management API v5 and OAuth2](#management-api-v5-and-oauth2)), and move
   every management client to the v5 paths and to OAuth2 tokens.
8. **Tell your counter-parties** the new DSP address
   (`<protocol>/<context id>/<profile>`), e.g. by updating the service entry
   of your DID document. Requests to the old `<protocol>/2025-1/...` paths
   return 404.
9. **Update the APISIX discovery URL** only if you configured it by hand;
   routes created by the connector already use the right one.
10. **Verify** with the checks in [Verifying a deployment](#verifying-a-deployment).

To **add a second dataspace** afterwards: declare its profile (with its own
`dcp.profiles.*` / `ebsiTir.profiles.*` overrides if needed), declare a second
`fdsc.participants.<key>` with its DID, STS client and a distinct STS secret
alias, store that secret in the Vault, and tag the offerings it should see with
`participantContextIds` (or `["*"]`). Existing untagged offerings stay in the
first dataspace.

## Verifying a deployment

- Startup logs one `Seeded participant context '<id>' (<DID>) with profiles [...]`
  line per context.
- `GET <management>/v5beta/participants` with an admin token lists every
  context.
- `GET <protocol>/<context id>/.well-known/dspace-version` returns 200 and
  lists that context's profiles.
- `POST <protocol>/<context id>/<profile>/catalog/request` without a token
  returns 401 for an associated profile and 404 otherwise.
- `GET <tmforum>/productCatalogManagement/v4/productOffering?participantContextIds=<context id>,*`
  lists the offerings made in that dataspace (the untagged ones of the default
  context are not included).

[`scripts/verify-multi-dataspace.sh`](../scripts/verify-multi-dataspace.sh)
runs these checks and more against the compose stack:

```bash
mvn package -pl controlplane-dcp -am -DskipTests
docker compose -f docker-compose.multi-dataspace.yml up --build -d --wait
docker compose -f docker-compose.multi-dataspace.yml run --rm verify
docker compose -f docker-compose.multi-dataspace.yml down -v
```

## Limitations and pitfalls

- **In-memory context store.** Contexts and their config live in memory and
  are re-seeded from `fdsc.participants.*` on every start. Declare every
  context in configuration; changes made only through the management API do
  not survive a restart.
- **One data plane for all contexts.** The DCP image keeps the legacy data
  plane signaling with one context-less data plane instance, which serves
  every context.
- **Environment variables.** EDC maps `FOO_BAR_BAZ` to `foo.bar.baz`, so
  camelCase keys (`fdsc.participants.<key>.privateConfig.*`, `tmfExtension.*`,
  `ebsiTir.*`, `odrlPap.*`, `fdscTransfer.*`), participant keys containing `_`,
  and profile names containing `:` cannot be set through environment
  variables. Use the properties file (`EDC_FS_CONFIG`) or JVM system
  properties.
- **Profile names with `:`** must be escaped (`\:`) in properties keys. Prefer
  `:`-free names for profiles with per-profile overrides.
- **TIL latency** adds up across dataspaces, see [DCP and TIR](#dcp-and-tir).
- **Shared ODRL-PAP.** Policies of all dataspaces are evaluated by one PAP; use
  the agreement's `participantContextId` in PAP policies where dataspaces must
  differ.
