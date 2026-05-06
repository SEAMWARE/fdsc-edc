

# MetaDataQueryVO

Defines additional properties requested by the Verifier that apply to the metadata and validity data of the Credential. The properties of this object are defined per Credential Format. If empty, no specific constraints are placed on the metadata or validity of the requested Credential.

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**vctValues** | **List&lt;String&gt;** | SD-JWT and JWT specific parameter. A non-empty array of strings that specifies allowed values for the type of the requested Verifiable Credential.The Wallet MAY return Credentials that inherit from any of the specified types, following the inheritance logic defined in https://datatracker.ietf.org/doc/html/draft-ietf-oauth-sd-jwt-vc-10 |  [optional] |
|**doctypeValue** | **String** | Required for MDoc. String that specifies an allowed value for the doctype of the requested Verifiable Credential. It MUST be a valid doctype identifier as defined in https://www.iso.org/standard/69084.html |  [optional] |
|**typeValues** | **List&lt;List&lt;String&gt;&gt;** | Required for ldp_vc. A non-empty array of string arrays. The Type value of the credential needs to be a subset of at least one of the string-arrays. |  [optional] |



