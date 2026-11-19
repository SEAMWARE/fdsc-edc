# Release Notes: Multi-dataspace support in `controlplane-dcp`

## Overview

`controlplane-dcp` can now take part in several dataspaces at once. It runs in
EDC 0.18.1's virtual participant-context mode: each dataspace is a participant
context with its own DID, STS client, DCP scopes and Trusted Issuers List, and
all dataspaces share one TMForum and one ODRL-PAP. A product is shared, and
each offering states the dataspaces it is offered in.

This is a **breaking release for the DCP image** (PR label `major`). The DSP and
management API URLs change. `controlplane-oid4vc` and `controlplane-tck` are
unchanged and stay in classic single-participant mode.

The operator guide, with the configuration reference and step-by-step
migration, is [`docs/multi-dataspace.md`](../../docs/multi-dataspace.md). The
design record is
[`docs/multi-dataspace-design.md`](../../docs/multi-dataspace-design.md).

## Breaking changes (DCP image only)

### 1. DSP endpoints change

| | Before | After |
|---|---|---|
| DSP endpoint | `<protocol>/2025-1/...` | `<protocol>/<participant context id>/<profile>/...` |
| DSP version metadata | `<protocol>/.well-known/dspace-version` | `<protocol>/<participant context id>/.well-known/dspace-version` |
| Callback address | `edc.dsp.callback.address=http://host/protocol` | must contain `%s`: `http://host/protocol/%s` |

Counter-parties must use the new address. Requests for a profile that is not
associated with the context, or for an unknown context, return 404.

### 2. Management API v5 with OAuth2

The management API v3 and v4 are removed from the DCP image. Use v5
(`/v5beta/participants/<context id>/...`). Every request needs an OAuth2 JWT,
verified with `edc.iam.oauth2.issuer` and `edc.iam.oauth2.jwks.url`, with
the scopes `management-api:read`/`write`, plus `management-api:admin` for
access to every context. Without `admin`, the token's `sub` must equal a
context id.

### 3. Identity and STS are configured per dataspace

The DID and the STS client (`edc.iam.sts.oauth.*`) move into the per-context
configuration `fdsc.participants.<key>.*`. Each context needs a distinct STS
secret alias, by convention `<context id>-sts-client-secret`.

### 4. Required new settings

- `tmfExtension.defaultParticipantContextId`: the context that owns existing,
  untagged TMForum data. Without it that data is invisible in virtual mode.
- `edc.dataspace.profiles.*`: declare the profiles, including upstream's
  `http-dsp-profile-2025-1` if a context should keep using it, because
  declaring any profile drops the implicit default.
- `tmfExtension.schemaBaseUri`: point it at a copy of `schemas/` that TMForum
  can reach. The default external URL lacks the new `participantContextId(s)`
  properties.

### 5. Removed from the DCP image

- `test-extension` (`testExtension.*` has no effect). DSP TCK runs use
  `controlplane-tck`.
- The root DSP version endpoint (`dsp-version-http-api`).

## New features

- **Participant contexts from configuration.** The new
  `participant-bootstrap-extension` seeds one context per
  `fdsc.participants.<key>` at startup (`.id`, `.identity`, `.profiles`,
  `.config.*`, `.privateConfig.*`). It updates existing contexts and does not
  duplicate them. Startup fails on an unknown profile, on a context id
  containing `@`, or on a DID entry that contradicts `.identity`.
- **Dataspace-aware TMForum storage.** Offerings carry an optional
  `participantContextIds` array (context ids or `*`); quotes, quote items,
  agreements and usages carry an optional `participantContextId`. The stores
  query and filter by context, and the connector tags what it writes.
  ProductSpecifications (products) stay shared.
- **Offer a product in one, several or all dataspaces.** An untagged offering
  stays in the default context only. `["A"]`, `["A","B"]` and `["*"]` offer it in
  A, in A and B, or in every context. An offering made in several contexts
  appears in context X as `<id>@X`.
- **Per-dataspace DCP scopes and trusted issuers.** `dcp.profiles.<profile>.scopes.*`
  overrides the global per-request-type `dcp.scopes.*` for one profile, and
  `ebsiTir.profiles.<profile>.tilAddress` gives a profile its own Trusted
  Issuers List. Without these keys behaviour is unchanged.
- **Per-dataspace transfer identity.** EDR tokens are signed with the transfer's
  own context identity. APISIX routes point at the per-context discovery
  document `/.well-known/openid-configuration/<base64url(context id)>`, and the
  bare path serves the default context.
- **Shared ODRL-PAP can tell dataspaces apart.** The PAP input of an agreement
  includes its `participantContextId`.
- **Example and verification.** `config/multi-dataspace/edc.properties`
  configures two dataspaces. `docker-compose.multi-dataspace.yml` with
  `scripts/verify-multi-dataspace.sh` checks the setup end to end, and runs as
  the `multi-dataspace` CI job.

## Compatibility

- Existing TMForum data needs no migration. Untagged entities belong to
  `tmfExtension.defaultParticipantContextId`.
- The new schema properties are optional, so existing TMForum entities stay
  valid.
- Classic deployments (`controlplane-oid4vc`, `controlplane-tck`) behave as
  before. The classic DSP TCK job is unchanged and green.
