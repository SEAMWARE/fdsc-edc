# Release Notes: Eclipse EDC 0.14.1 → 0.18.1 upgrade

## Overview

The FDSC-EDC extensions and both controlplane images (`controlplane-oid4vc`,
`controlplane-dcp`) were upgraded from Eclipse EDC `0.14.1` to `0.18.1`, crossing
seven upstream releases (0.15.0, 0.15.1, 0.16.0, 0.16.1, 0.17.0, 0.18.0, 0.18.1).

This is a **breaking upgrade**. It drops support for DSP 2024/1 counterparties,
requires co-deployed Eclipse IdentityHub to move to 0.18.1 in lockstep, and opens a
new HTTP listener on the production controlplane images. The sections below are the
operator-facing changes; the full engineering analysis is in
[`docs/migration/EDC-0.18.1-UPGRADE.md`](../../docs/migration/EDC-0.18.1-UPGRADE.md).

## Action required before upgrading

### 1. Eclipse IdentityHub must move to 0.18.1 in lockstep

The Connector and IdentityHub share the DCP wire contract. 0.18.1 exists on both
sides and they must be upgraded together — an 0.14.1 IdentityHub will not
interoperate with a 0.18.1 controlplane. IdentityHub is version-aligned with the
Connector on the same release tags (note: IdentityHub has no 0.16.1; the Connector's
0.16.1 is a standalone backport). No IdentityHub coordinate lives in this
repository, so this is an external deployment coordination item.

### 2. New `signaling` HTTP listener on the production images (port 8182)

The production assemblies now use `data-plane-signaling`, which **unconditionally**
registers a fifth HTTP listener. There is no feature flag. Pin and expose it
explicitly in your deployment:

| Setting key | Default | Notes |
|---|---|---|
| `web.http.signaling.port` | `8182` | New listener; does not collide with the connector's other ports (8080/8181/8282/8687). |
| `web.http.signaling.path` | `/api/signaling` | |
| `web.http.signaling.public.uri` | `http://localhost:<port><path>` | Set only when the data plane reaches the control plane across a container/pod boundary, where `localhost` is wrong. |

The surrounding FIWARE Data Space Connector deployment chart (outside this
repository) must publish this port and, if the connector is fronted by a PEP,
route it. Symptom if missed: the data plane cannot reach the control plane's
registration API.

> The TCK runtime image (`controlplane-tck`) deliberately excludes
> `data-plane-signaling` and therefore does **not** open this listener. The change
> applies to the production `controlplane-oid4vc` / `controlplane-dcp` images only.

### 3. New participant-context id configuration

EDC 0.15.0 introduced the Participant Context. This connector runs in
single-participant ("classic") mode and the id is stamped onto every control-plane
entity, so set it explicitly and keep it stable across restarts:

| Setting key | Required | Value for FDSC |
|---|---|---|
| `edc.participant.context.id` | optional (`required = false`) — falls back to `edc.participant.id` | pin equal to `edc.participant.id` (e.g. `urn:connector:fdsc-edc`) |

Leaving it unset does not fail startup — which is exactly how a wrong-but-silent
value gets shipped. Set it explicitly.

### 4. The DSP version discovery endpoint is now unauthenticated

At 0.18.1 the DSP version endpoint (`/.well-known/dspace-version`) is served
without a token, and this connector's outbound version requests go out unscoped. A
PEP fronting the connector (e.g. APISIX in the FDSC stack) **must stop requiring a
token on that path**, or version discovery breaks. The disclosure is negligible
(only supported protocol versions are returned).

## Breaking changes for counterparties

### DSP 2024/1 (and 0.8) support removed

Upstream deleted the DSP 0.8 and 2024/1 protocol modules in 0.16.0. This connector
now speaks **DSP 2025/1 only**. Any external counterparty still on DSP 2024/1 or 0.8
can no longer negotiate with it — this is an external coordination item, not a code
change, and counterparties must be upgraded.

> Note: the ODRL vocabulary IRI `https://w3id.org/dspace/2024/1/membershipType` in
> `config/tck/scope-mappings.json` is **policy vocabulary, not protocol wiring**, and
> is intentionally retained.

### Pre-0.17 counterparty version endpoints

A counterparty still running a pre-0.17 EDC whose version endpoint *requires* an
authenticated, scoped request will reject this connector's now-unscoped version
request. Confirm interop against real counterparties.

## Configuration changes

| Key | Change | Action |
|---|---|---|
| `edc.participant.context.id` | new (0.15.0) | add, pinned to `edc.participant.id` |
| `web.http.signaling.{port,path,public.uri}` | new listener on the production images | add and publish (see above) |
| `dcp.scopes.version` | **retired** — the `RequestVersionPolicyContext` it bound to was removed upstream | delete from deployments; it is now ignored, not rejected, so existing configs keep loading |
| `edc.iam.issuer.id` → `edc.participant.did` | upstream rename (0.17.0); old key still honoured | relevant only to external DCP deployments that set it |
| `edc.dsp.well-known-path.enabled` | **removed** upstream (0.17.0) | external configs must drop it |

This repository sets none of the last two keys; they are listed for operators whose
out-of-repo configuration might.

## Transparent / no-action changes

- **HTTP callback delivery still works.** The discontinued `callback-http-dispatcher`
  module was dropped; the HTTP transport moved into `callback-event-dispatcher` at
  0.18.0. Consumer-supplied `CallbackAddress` notifications are still delivered.
- **TMForum persistence is unchanged.** The participant-context id is derived from
  configuration and stamped at mapping time — it is not persisted to TMForum, so no
  TMForum schema change is required.
- **No SQL/DDL migrations here.** This connector stores through TMForum and ships no
  SQL stores. The `BREAKING CHANGE` schema deltas upstream flagged in this range
  (new lease model, `agr_agreement_id`, `data_address_alias`, `claims`,
  `private_entries`, …) are not actionable for FDSC-EDC — but any **co-deployed EDC
  runtime that does use the SQL stores** needs those migrations.

## Internal / developer-facing changes

- `TMFLeaseContext` was removed as dead code — nothing injected a `LeaseContext`;
  leasing runs through `LeaseHolder` / `TMFBackedLeaseHolder`.
- `ContractAgreement` now carries a DSP-visible `agreement-id` (distinct from the EDC
  row id) and counter-party `claims`, both persisted as TMForum Agreement
  characteristics. Agreements written by a 0.14.1 connector and the TCK seed data
  keep resolving (the external id is used as a fallback).
- Identity modules renamed upstream: `identity-trust-*` → `decentralized-claims-*`
  (package `org.eclipse.edc.iam.decentralizedclaims.*`).
- `DataFlowManager` was removed; `DataFlowController` is now a single injected
  service. A new `controlplane-tck` assembly (production OID4VC minus
  `data-plane-signaling`) is used for the DSP TCK so the test-only
  `NoopDataFlowController` is selected; production images are unchanged.
- The DSP TCK runner is pinned to `1.0.1` (the version EDC 0.18.1 itself pins).

## Conformance

The DSP TCK suite is fully green at 0.18.1 (matching the 0.14.1 baseline) on DSP TCK
`1.0.1` against DSP `2025-1`.
