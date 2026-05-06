

# PriceAlterationVO

Is an amount, usually of money, that modifies the price charged for an order item.

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**applicationDuration** | **Integer** | Duration during which the alteration applies on the order item price (for instance 2 months free of charge for the recurring charge) |  [optional] |
|**description** | **String** | A narrative that explains in detail the semantics of this order item price alteration |  [optional] |
|**name** | **String** | Name of the order item price alteration |  [optional] |
|**priceType** | **String** | A category that describes the price such as recurring, one time and usage. |  |
|**priority** | **Integer** | Priority level for applying this alteration among all the defined alterations on the order item price |  [optional] |
|**recurringChargePeriod** | **String** | Could be month, week... |  [optional] |
|**unitOfMeasure** | **String** | Could be minutes, GB... |  [optional] |
|**price** | [**PriceVO**](PriceVO.md) |  |  |
|**productOfferingPrice** | [**ProductOfferingPriceRefVO**](ProductOfferingPriceRefVO.md) |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class entity name |  [optional] |



