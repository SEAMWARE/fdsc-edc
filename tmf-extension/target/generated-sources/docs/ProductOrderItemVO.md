

# ProductOrderItemVO

An identified part of the order. A product order is decomposed into one or more order items.

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**id** | **String** | Identifier of the line item (generally it is a sequence number 01, 02, 03, ...) |  |
|**quantity** | **Integer** | Quantity ordered |  [optional] |
|**action** | **OrderItemActionTypeVO** |  |  |
|**appointment** | [**AppointmentRefVO**](AppointmentRefVO.md) |  |  [optional] |
|**billingAccount** | [**BillingAccountRefVO**](BillingAccountRefVO.md) |  |  [optional] |
|**itemPrice** | [**List&lt;OrderPriceVO&gt;**](OrderPriceVO.md) |  |  [optional] |
|**itemTerm** | [**List&lt;OrderTermVO&gt;**](OrderTermVO.md) |  |  [optional] |
|**itemTotalPrice** | [**List&lt;OrderPriceVO&gt;**](OrderPriceVO.md) |  |  [optional] |
|**payment** | [**List&lt;PaymentRefVO&gt;**](PaymentRefVO.md) |  |  [optional] |
|**product** | [**ProductRefOrValueVO**](ProductRefOrValueVO.md) |  |  [optional] |
|**productOffering** | [**ProductOfferingRefVO**](ProductOfferingRefVO.md) |  |  [optional] |
|**productOfferingQualificationItem** | [**ProductOfferingQualificationItemRefVO**](ProductOfferingQualificationItemRefVO.md) |  |  [optional] |
|**productOrderItem** | [**List&lt;ProductOrderItemVO&gt;**](ProductOrderItemVO.md) |  |  [optional] |
|**productOrderItemRelationship** | [**List&lt;OrderItemRelationshipVO&gt;**](OrderItemRelationshipVO.md) |  |  [optional] |
|**qualification** | [**List&lt;ProductOfferingQualificationRefVO&gt;**](ProductOfferingQualificationRefVO.md) |  |  [optional] |
|**quoteItem** | [**QuoteItemRefVO**](QuoteItemRefVO.md) |  |  [optional] |
|**state** | **ProductOrderItemStateTypeVO** |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class entity name |  [optional] |



