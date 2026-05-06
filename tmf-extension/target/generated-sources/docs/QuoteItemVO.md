

# QuoteItemVO

A quote items describe an action to be performed on a productOffering or a product in order to get pricing elements and condition.

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**id** | **String** | Identifier of the quote item (generally it is a sequence number 01, 02, 03, ...) |  [optional] |
|**action** | **String** | Action to be performed on this quote item (add, modify, remove, etc.) |  [optional] |
|**quantity** | **Integer** | Quantity asked for this quote item |  [optional] |
|**state** | **String** | State of the quote item : described in the state machine diagram |  [optional] |
|**appointment** | [**List&lt;AppointmentRefVO&gt;**](AppointmentRefVO.md) | A reference to appointment(s) associated with this quote item |  [optional] |
|**attachment** | [**List&lt;AttachmentRefOrValueVO&gt;**](AttachmentRefOrValueVO.md) | A reference to attachment(s) associated with this quote item |  [optional] |
|**note** | [**List&lt;NoteVO&gt;**](NoteVO.md) | Free form text associated with the quote item |  [optional] |
|**product** | [**ProductRefOrValueVO**](ProductRefOrValueVO.md) |  |  [optional] |
|**productOffering** | [**ProductOfferingRefVO**](ProductOfferingRefVO.md) |  |  [optional] |
|**productOfferingQualificationItem** | [**ProductOfferingQualificationItemRefVO**](ProductOfferingQualificationItemRefVO.md) |  |  [optional] |
|**quoteItem** | [**List&lt;QuoteItemVO&gt;**](QuoteItemVO.md) | A structure to embedded quote item within quote item |  [optional] |
|**quoteItemAuthorization** | [**List&lt;AuthorizationVO&gt;**](AuthorizationVO.md) | Authorization related to this quote item |  [optional] |
|**quoteItemPrice** | [**List&lt;QuotePriceVO&gt;**](QuotePriceVO.md) | Price for this quote item |  [optional] |
|**quoteItemRelationship** | [**List&lt;QuoteItemRelationshipVO&gt;**](QuoteItemRelationshipVO.md) | A relationship from item within a quote |  [optional] |
|**relatedParty** | [**List&lt;RelatedPartyVO&gt;**](RelatedPartyVO.md) | A reference to a party playing a role in this quote item |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class entity name |  [optional] |



