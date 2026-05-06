

# POPAlterationVO

Is an amount, usually of money, that modifies the price charged for an order item.

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**id** | **String** | unique identifier |  [optional] |
|**href** | **URI** | Hyperlink reference |  [optional] |
|**description** | **String** | A narrative that explains in detail the semantics of this order item price alteration |  [optional] |
|**name** | **String** | Name given to this price alteration |  [optional] |
|**priceType** | **String** | A category that describes the price such as recurring, one time and usage. |  |
|**priority** | **Integer** | Priority level for applying this alteration among all the defined alterations on the order item price |  [optional] |
|**recurringChargePeriod** | **String** | Could be month, week... |  [optional] |
|**applicationDuration** | [**DurationVO**](DurationVO.md) |  |  [optional] |
|**price** | [**ProductPriceValueVO**](ProductPriceValueVO.md) |  |  |
|**unitOfMeasure** | [**QuantityVO**](QuantityVO.md) |  |  [optional] |
|**validFor** | [**TimePeriodVO**](TimePeriodVO.md) |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class Extensible name |  [optional] |



