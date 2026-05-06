

# TrustedAuthorityQueryVO

An object representing information that helps to identify an authority or the trust framework that certifies Issuers. A Credential is identified as a match to a Trusted Authorities Query if it matches with one of the provided values in one of the provided types.

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**type** | **String** | A string uniquely identifying the type of information about the issuer trust framework. - aki - etsi_tl - openid_federation |  |
|**values** | **List&lt;String&gt;** | A non-empty array of strings, where each string (value) contains information specific to the used Trusted Authorities Query type that allows the identification of an issuer, a trust framework, or a federation that an issuer belongs to. |  |



