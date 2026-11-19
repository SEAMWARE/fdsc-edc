# FDSC Controlplane DCP

Implementation of the FIWARE Dataspace Connector compatible EDC Controlplane to be secured by the Decentralized Claims Protocol. Assembles all required extensions.

The controlplane runs in EDC's **virtual participant-context mode** and can take part in several dataspaces at once: each dataspace is a participant context, seeded from `fdsc.participants.*` by `participant-bootstrap-extension`. See the operator guide [docs/multi-dataspace.md](../docs/multi-dataspace.md) and the example configuration [config/multi-dataspace/edc.properties](../config/multi-dataspace/edc.properties).

> **Breaking change.** Compared to earlier releases of this image:
> - the management API is v5 (`/v5beta/participants/<context id>/...`) and requires an OAuth2 JWT (`edc.iam.oauth2.issuer`, `edc.iam.oauth2.jwks.url`); v3 and v4 are gone;
> - DSP endpoints move from `<protocol>/2025-1/...` to `<protocol>/<context id>/<profile>/...`, and `edc.dsp.callback.address` must contain `%s`;
> - the DID and STS settings are configured per participant context;
> - `test-extension` is no longer bundled; DSP TCK runs use `controlplane-tck`.
>
> Migration steps are in [docs/multi-dataspace.md](../docs/multi-dataspace.md#migrating-a-single-dataspace-deployment).
