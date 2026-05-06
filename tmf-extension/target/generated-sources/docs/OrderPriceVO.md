

# OrderPriceVO

An amount, usually of money, that represents the actual price paid by the Customer for this item or this order

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**description** | **String** | A narrative that explains in detail the semantics of this order item price. |  [optional] |
|**name** | **String** | A short descriptive name such as \&quot;Subscription price\&quot;. |  [optional] |
|**priceType** | **String** | A category that describes the price, such as recurring, discount, allowance, penalty, and so forth |  [optional] |
|**recurringChargePeriod** | **String** | Could be month, week... |  [optional] |
|**unitOfMeasure** | **String** | Could be minutes, GB... |  [optional] |
|**billingAccount** | [**BillingAccountRefVO**](BillingAccountRefVO.md) |  |  [optional] |
|**price** | [**PriceVO**](PriceVO.md) |  |  [optional] |
|**priceAlteration** | [**List&lt;PriceAlterationVO&gt;**](PriceAlterationVO.md) | a strucuture used to describe a price alteration |  [optional] |
|**productOfferingPrice** | [**ProductOfferingPriceRefVO**](ProductOfferingPriceRefVO.md) |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class entity name |  [optional] |



