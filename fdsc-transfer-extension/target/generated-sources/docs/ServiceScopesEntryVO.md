

# ServiceScopesEntryVO


## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**credentials** | [**List&lt;CredentialVO&gt;**](CredentialVO.md) | Trust configuration for the credentials |  |
|**presentationDefinition** | [**PresentationDefinitionVO**](PresentationDefinitionVO.md) |  |  [optional] |
|**dcql** | [**DCQLVO**](DCQLVO.md) |  |  [optional] |
|**flatClaims** | **Boolean** | When set, the claim are flatten to plain JWT-claims before beeing included, instead of keeping the credential/presentation structure, where the claims are under the key vc or vp |  [optional] |



