

# CredentialSetQueryVO

A Credential Set Query is an object representing a request for one or more Credentials to satisfy a particular use case with the Verifier.

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**options** | **List&lt;List&lt;String&gt;&gt;** | A non-empty array, where each value in the array is a list of Credential Query identifiers representing one set of Credentials that satisfies the use case. The value of each element in the options array is a non-empty array of identifiers which reference elements in credentials. |  [optional] |
|**required** | **Boolean** | A boolean which indicates whether this set of Credentials is required to satisfy the particular use case at the Verifier. |  [optional] |
|**purpose** | **Object** | A string, number or object specifying the purpose of the query. This specification does not define a specific structure or specific values for this property. The purpose is intended to be used by the Verifier to communicate the reason for the query to the Wallet. The Wallet MAY use this information to show the user the reason for the request. |  [optional] |



