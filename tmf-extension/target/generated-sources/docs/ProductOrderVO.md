

# ProductOrderVO

A Product Order is a type of order which  can  be used to place an order between a customer and a service provider or between a service provider and a partner and vice versa,

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**id** | **String** | ID created on repository side (OM system) |  [optional] |
|**href** | **String** | Hyperlink to access the order |  [optional] |
|**cancellationDate** | **OffsetDateTime** | Date when the order is cancelled. This is used when order is cancelled.  |  [optional] |
|**cancellationReason** | **String** | Reason why the order is cancelled. This is used when order is cancelled.  |  [optional] |
|**category** | **String** | Used to categorize the order from a business perspective that can be useful for the OM system (e.g. \&quot;enterprise\&quot;, \&quot;residential\&quot;, ...) |  [optional] |
|**completionDate** | **OffsetDateTime** | Date when the order was completed |  [optional] |
|**description** | **String** | Description of the product order |  [optional] |
|**expectedCompletionDate** | **OffsetDateTime** | Expected delivery date amended by the provider |  [optional] |
|**externalId** | **String** | ID given by the consumer and only understandable by him (to facilitate his searches afterwards) |  [optional] |
|**notificationContact** | **String** | Contact attached to the order to send back information regarding this order |  [optional] |
|**orderDate** | **OffsetDateTime** | Date when the order was created |  [optional] |
|**priority** | **String** | A way that can be used by consumers to prioritize orders in OM system (from 0 to 4 : 0 is the highest priority, and 4 the lowest) |  [optional] |
|**requestedCompletionDate** | **OffsetDateTime** | Requested delivery date from the requestor perspective |  [optional] |
|**requestedStartDate** | **OffsetDateTime** | Order fulfillment start date wished by the requestor. This is used when, for any reason, requestor cannot allow seller to begin to operationally begin the fulfillment before a date.  |  [optional] |
|**agreement** | [**List&lt;AgreementRefVO&gt;**](AgreementRefVO.md) | A reference to an agreement defined in the context of the product order |  [optional] |
|**billingAccount** | [**BillingAccountRefVO**](BillingAccountRefVO.md) |  |  [optional] |
|**channel** | [**List&lt;RelatedChannelVO&gt;**](RelatedChannelVO.md) |  |  [optional] |
|**note** | [**List&lt;NoteVO&gt;**](NoteVO.md) |  |  [optional] |
|**orderTotalPrice** | [**List&lt;OrderPriceVO&gt;**](OrderPriceVO.md) |  |  [optional] |
|**payment** | [**List&lt;PaymentRefVO&gt;**](PaymentRefVO.md) |  |  [optional] |
|**productOfferingQualification** | [**List&lt;ProductOfferingQualificationRefVO&gt;**](ProductOfferingQualificationRefVO.md) |  |  [optional] |
|**productOrderItem** | [**List&lt;ProductOrderItemVO&gt;**](ProductOrderItemVO.md) |  |  |
|**quote** | [**List&lt;QuoteRefVO&gt;**](QuoteRefVO.md) |  |  [optional] |
|**relatedParty** | [**List&lt;RelatedPartyVO&gt;**](RelatedPartyVO.md) |  |  [optional] |
|**state** | **ProductOrderStateTypeVO** |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class entity name |  [optional] |



