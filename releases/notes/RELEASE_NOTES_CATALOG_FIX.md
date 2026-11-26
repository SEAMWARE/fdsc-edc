# Release Notes: DSP catalog requests in virtual participant contexts (`controlplane-dcp` 2.1.1)

## Overview

In `controlplane-dcp` 2.1.0 every DSP catalog request
(`POST <protocol>/<context id>/<profile>/catalog/request`) failed. The provider
logged `Querying for contract definitions is currently not supported` and
answered 500. The consumer usually did not see that 500:

1. The provider's DSP catalog controller answered **500**.
2. The proxy in front of the provider retried the request with the **same**
   DCP token.
3. DCP rejects a token it has already seen, so the provider logged
   `JWT id ... was already used` and answered the retry with **401**.
4. The consumer reported **502** or **401 Unauthorized** for the catalog
   request.

The 401 points to identity or trust problems, but the real cause was the
first 500.

This is a bug fix (PR label `patch`), released as **2.1.1** of
`quay.io/seamware/fdsc-edc-controlplane-dcp`.

## Fix

- **The TMForum catalog service is the one the DSP controllers use.** With
  `tmfExtension.catalog.enabled=true`, the catalog extension replaced EDC's
  `CatalogProtocolService`, but EDC's dependency graph could not see the
  replacement. Whether the DSP catalog controllers got the TMForum service or
  EDC's stock one depended on classpath order. In the DCP image they got the
  stock one. The extension now declares that it provides the service, so EDC
  always injects the TMForum service.
- **`ContractDefinitionStore.findAll` works.** The TMForum-backed store used to
  throw on queries. It now builds one contract definition per product offering
  of the queried participant context and applies the query's filters, sorting
  and paging. This serves:
  - EDC's stock catalog path, which is used when
    `tmfExtension.catalog.enabled=false`;
  - v5beta management queries of contract definitions
    (`POST /v5beta/participants/<context id>/contractdefinitions/request`).

  Each definition returned by `findAll` selects only its offering's asset
  (the product specification's `externalId`). Without that selector the stock
  catalog would have offered every asset under every offering's policy.

## Affected images

| Image | Change |
|---|---|
| `controlplane-dcp` | Catalog requests work again |
| `controlplane-oid4vc`, `controlplane-tck` | Same code. These images happened to load the TMForum service in the right order, so their behaviour does not change |

## Upgrade

Replace the image tag with 2.1.1. No configuration changes are needed.

If consumers still get `401 Unauthorized` on catalog requests after the
upgrade, see
[Limitations and pitfalls](../../docs/multi-dataspace.md#limitations-and-pitfalls)
in the operator guide.
