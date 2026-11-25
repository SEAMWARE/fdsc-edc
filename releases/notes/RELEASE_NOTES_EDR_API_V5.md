# Release Notes: EDRs through the v5beta management API (`controlplane-dcp` 2.1.0)

## Overview

`controlplane-dcp` 2.x serves only management API v5beta, and EDC 0.18.1 has no
v5 resource for endpoint data references (EDRs). A consumer therefore had no way
to read the endpoint and token of a STARTED transfer. The new
`edr-api-extension` adds that resource for each participant context.

This is a **non-breaking** addition (PR label `minor`). It is released as
**2.1.0** of `quay.io/seamware/fdsc-edc-controlplane-dcp`. `controlplane-oid4vc`
and `controlplane-tck` are unchanged.

## New endpoints

| Method and path | Scope | Purpose |
|---|---|---|
| `POST /v5beta/participants/<context id>/edrs/request` | `management-api:read` | Query the context's EDR entries with a QuerySpec (`{}` for all) |
| `GET /v5beta/participants/<context id>/edrs/<transfer id>/dataaddress` | `management-api:read` | The EDR: `endpoint`, `authorization`, `endpointType` |
| `DELETE /v5beta/participants/<context id>/edrs/<transfer id>` | `management-api:write` | Remove the entry and its cached token |

- **Isolation.** Each EDR belongs to the context of the transfer it was stored
  for. A token without `management-api:admin` sees only its own context's EDRs:
  another context's EDR gives 403 through that context's path and 404 through
  its own. Entries list their `participantContextId`.
- **`auto_refresh`.** `dataaddress` accepts `auto_refresh` (default `true`), as
  the Tractus-X EDR API does. The default refresher does nothing, because no data
  plane this connector supports renews tokens. Deployments can plug in their own
  `EdrTokenRefresher`; ADR-14 lists the rules it must follow.
- **No new configuration.** The resource is part of the DCP image and uses the
  existing `edc.iam.oauth2.*` settings.

## Documentation and verification

- Operator guide: [Endpoint data references](../../docs/multi-dataspace.md#endpoint-data-references-edrs).
- Design: [ADR-14](../../docs/multi-dataspace-design.md#adr-14--edrs-exposed-per-participant-context-through-v5beta).
- `scripts/verify-multi-dataspace.sh` checks the scopes and context isolation of
  the new endpoints. `EdrTwoContextIsolationTest` covers the STARTED and
  TERMINATED path for two contexts in-process.
