

# ClaimsQueryVO

A query to specifies claims in the requested Credential.

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**id** | **String** | REQUIRED if claim_sets is present in the Credential Query; OPTIONAL otherwise. A string identifying the particular claim. The value MUST be a non-empty string consisting of alphanumeric, underscore (_), or hyphen (-) characters. Within the particular claims array, the same id MUST NOT be present more than once. |  [optional] |
|**path** | **List&lt;Object&gt;** | The value MUST be a non-empty array representing a claims path pointer that specifies the path to a claim within the Credential. See https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#name-claims-path-pointer |  [optional] |
|**values** | **List&lt;Object&gt;** | A non-empty array of strings, integers or boolean values that specifies the expected values of the claim. If the values property is present, the Wallet SHOULD return the claim only if the type and value of the claim both match exactly for at least one of the elements in the array. |  [optional] |
|**intentToRetain** | **Boolean** | MDoc specific parameter, ignored for all other types. The flag can be set to inform that the reader wishes to keep(store) the data. In case of false, its data is only used to be dispalyed and verified. |  [optional] |
|**namespace** | **String** | MDoc specific parameter, ignored for all other types. Refers to a namespace inside an mdoc. |  [optional] |
|**claimName** | **String** | MDoc specific parameter, ignored for all other types. Identifier for the data-element in the namespace. |  [optional] |



