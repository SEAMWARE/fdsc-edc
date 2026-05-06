

# RatedProductUsageVO

An occurrence of employing a product for its intended purpose with all rating details

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**isBilled** | **Boolean** | Boolean indicating if usage have been billed or not |  [optional] |
|**isTaxExempt** | **Boolean** | Indicates if the rated amount is exempt of tax |  [optional] |
|**offerTariffType** | **String** | Type of tariff applied |  [optional] |
|**ratingAmountType** | **String** | Type of amount |  [optional] |
|**ratingDate** | **OffsetDateTime** | Date of usage rating |  [optional] |
|**taxRate** | **Float** | Tax rate |  [optional] |
|**usageRatingTag** | **String** | Tag value: [usage]: the usage is always rated outside a usage bundle [included usage]: the usage is rated inside a usage bundle [non included usage]: the usage bundle is exhausted. The usage is rated outside the usage bundle |  [optional] |
|**bucketValueConvertedInAmount** | [**MoneyVO**](MoneyVO.md) |  |  [optional] |
|**productRef** | [**ProductRefVO**](ProductRefVO.md) |  |  [optional] |
|**taxExcludedRatingAmount** | [**MoneyVO**](MoneyVO.md) |  |  [optional] |
|**taxIncludedRatingAmount** | [**MoneyVO**](MoneyVO.md) |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class Extensible name |  [optional] |



