

# AuthorizationVO

If special discount or special product offering price or specific condition need an approval for ISP sale representative it is described here.

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**givenDate** | **OffsetDateTime** | Date when the authorization (approved or declined) was done |  [optional] |
|**name** | **String** | Name of the required authorization |  [optional] |
|**requestedDate** | **OffsetDateTime** | Date when the authorization is requested for |  [optional] |
|**signatureRepresentation** | **String** | To describe a digital or manual signature |  [optional] |
|**state** | **String** | State of the authorization, such as: approved or declined |  [optional] |
|**approver** | [**List&lt;RelatedPartyVO&gt;**](RelatedPartyVO.md) |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class entity name |  [optional] |



