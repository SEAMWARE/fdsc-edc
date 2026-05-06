

# QuotePriceVO

Description of price and discount awarded

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**description** | **String** | Description of the quote/quote item price |  [optional] |
|**name** | **String** | Name of the quote /quote item price |  [optional] |
|**priceType** | **String** | indicate if the price is for recurrent or no-recurrent charge |  [optional] |
|**recurringChargePeriod** | **String** | Used for recurring charge to indicate period (month, week, etc..) |  [optional] |
|**unitOfMeasure** | **String** | Unit of Measure if price depending on it (Gb, SMS volume, etc..) |  [optional] |
|**price** | [**PriceVO**](PriceVO.md) |  |  [optional] |
|**priceAlteration** | [**List&lt;PriceAlterationVO&gt;**](PriceAlterationVO.md) |  |  [optional] |
|**productOfferingPrice** | [**ProductOfferingPriceRefVO**](ProductOfferingPriceRefVO.md) |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class entity name |  [optional] |



