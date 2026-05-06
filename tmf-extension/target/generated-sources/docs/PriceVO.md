

# PriceVO

Provides all amounts (tax included, duty free, tax rate), used currency and percentage to apply for Price Alteration.

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**percentage** | **Float** | Percentage to apply for ProdOfferPriceAlteration |  [optional] |
|**taxRate** | **Float** | Tax rate |  [optional] |
|**dutyFreeAmount** | [**MoneyVO**](MoneyVO.md) |  |  [optional] |
|**taxIncludedAmount** | [**MoneyVO**](MoneyVO.md) |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class entity name |  [optional] |



