

# CredentialVO

A credential-type with its trust configuration

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**type** | **String** | Type of the credential |  |
|**trustedParticipantsLists** | **List&lt;Object&gt;** |  |  [optional] |
|**trustedIssuersLists** | **List&lt;String&gt;** | A list of (EBSI Trusted Issuers Registry compatible) endpoints to  retrieve the trusted issuers from. The attributes need to be formated to comply with the verifiers requirements.  |  [optional] |
|**holderVerification** | [**HolderVerificationVO**](HolderVerificationVO.md) |  |  [optional] |
|**requireCompliance** | **Boolean** | Does the given credential require a compliancy credential |  [optional] |
|**jwtInclusion** | [**JwtInclusionVO**](JwtInclusionVO.md) |  |  [optional] |



