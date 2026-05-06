

# ProductPriceVO

An amount, usually of money, that represents the actual price paid by a Customer for a purchase, a rent or a lease of a Product. The price is valid for a defined period of time.

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**description** | **String** | A narrative that explains in detail the semantics of this product price. |  [optional] |
|**name** | **String** | A short descriptive name such as \&quot;Subscription price\&quot;. |  [optional] |
|**priceType** | **String** | A category that describes the price, such as recurring, discount, allowance, penalty, and so forth. |  |
|**recurringChargePeriod** | **String** | Could be month, week... |  [optional] |
|**unitOfMeasure** | **String** | Could be minutes, GB... |  [optional] |
|**billingAccount** | [**BillingAccountRefVO**](BillingAccountRefVO.md) |  |  [optional] |
|**price** | [**PriceVO**](PriceVO.md) |  |  |
|**productOfferingPrice** | [**ProductOfferingPriceRefVO**](ProductOfferingPriceRefVO.md) |  |  [optional] |
|**productPriceAlteration** | [**List&lt;PriceAlterationVO&gt;**](PriceAlterationVO.md) |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class entity name |  [optional] |



