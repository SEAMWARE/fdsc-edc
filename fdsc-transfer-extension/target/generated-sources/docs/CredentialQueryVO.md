

# CredentialQueryVO

A Credential Query is an object representing a request for a presentation of one or more matching Credentials

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**id** | **String** | A string identifying the Credential in the response and, if provided, the constraints in credential_sets. The value MUST be a non-empty string consisting of alphanumeric, underscore (_), or hyphen (-) characters. Within the Authorization Request, the same id MUST NOT be present more than once. |  [optional] |
|**format** | [**FormatEnum**](#FormatEnum) | A string that specifies the format of the requested Credential. |  [optional] |
|**multiple** | **Boolean** | A boolean which indicates whether multiple Credentials can be returned for this Credential Query. If omitted, the default value is false. |  [optional] |
|**claims** | [**List&lt;ClaimsQueryVO&gt;**](ClaimsQueryVO.md) | A non-empty array of objects  that specifies claims in the requested Credential. Verifiers MUST NOT point to the same claim more than once in a single query. Wallets SHOULD ignore such duplicate claim queries. |  [optional] |
|**meta** | [**MetaDataQueryVO**](MetaDataQueryVO.md) |  |  [optional] |
|**requireCryptographicHolderBinding** | **Boolean** | A boolean which indicates whether the Verifier requires a Cryptographic Holder Binding proof. The default value is true, i.e., a Verifiable Presentation with Cryptographic Holder Binding is required. If set to false, the Verifier accepts a Credential without Cryptographic Holder Binding proof. |  [optional] |
|**claimSets** | **List&lt;List&lt;String&gt;&gt;** | A non-empty array containing arrays of identifiers for elements in claims that specifies which combinations of claims for the Credential are requested. |  [optional] |
|**trustedAuthorities** | [**List&lt;TrustedAuthorityQueryVO&gt;**](TrustedAuthorityQueryVO.md) | A non-empty array of objects  that specifies expected authorities or trust frameworks that certify Issuers, that the Verifier will accept. Every Credential returned by the Wallet SHOULD match at least one of the conditions present in the corresponding trusted_authorities array if present. |  [optional] |



## Enum: FormatEnum

| Name | Value |
|---- | -----|
| MSO_MDOC | &quot;mso_mdoc&quot; |
| VC_SD_JWT | &quot;vc+sd-jwt&quot; |
| DC_SD_JWT | &quot;dc+sd-jwt&quot; |
| LDP_VC | &quot;ldp_vc&quot; |
| JWT_VC_JSON | &quot;jwt_vc_json&quot; |



