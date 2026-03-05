# FIWARE Data Space Connector EDC Extension

Set of extensions for EDC, supporting:

* use [TMForum](github.com/FIWARE/tmforum-api) as storage backend to provide integration with TMForum based flows
* provision data transfers at the [FIWARE Data Space Connector](https://github.com/FIWARE/data-space-connector)
* authentication/authoriaztion based on [OpenId4VP](https://openid.net/specs/openid-4-verifiable-presentations-1_0.html)
* authentication/authoriaztion based on DCP

Two images are published:

* [fdsc-edc-controlplane-oid4vc](https://quay.io/repository/seamware/fdsc-edc-controlplane-oid4vc) to provide integration with TMForum and provisioning at the FIWARE Data Space Connector, secured via OID4VP
* [fdsc-edc-controlplane-dcp](https://quay.io/repository/seamware/fdsc-edc-controlplane-dcp) to provide integration with TMForum and provisioning at the FIWARE Data Space Connector, secured via DCP