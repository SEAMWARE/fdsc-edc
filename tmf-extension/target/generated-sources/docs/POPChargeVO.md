

# POPChargeVO

This is representing a product offering price (charge) based on both the basic cost to develop and produce products and the enterprises policy on revenue targets. This price may be further revised through discounting (a Product Offering Price that reflects an alteration).

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**id** | **String** | unique identifier |  [optional] |
|**href** | **URI** | Hyperlink reference |  [optional] |
|**description** | **String** | Description of the productOfferingPrice |  [optional] |
|**lastUpdate** | **OffsetDateTime** | the last update time of this ProductOfferingPrice |  [optional] |
|**lifecycleStatus** | **String** | the lifecycle status of this ProductOfferingPrice |  [optional] |
|**name** | **String** | Name of the productOfferingPrice |  [optional] |
|**priceType** | **String** | A category that describes the price charge, such as recurring, penalty, One time fee and so forth. |  [optional] |
|**recurringChargePeriod** | **String** | The period type to repeat the application of the price Could be month, week... |  [optional] |
|**recurringChargePeriodLength** | **Integer** | the period of the recurring charge:  1, 2, ... .It sets to zero if it is not applicable |  [optional] |
|**version** | **String** | ProductOffering version |  [optional] |
|**constraint** | [**List&lt;ConstraintRefVO&gt;**](ConstraintRefVO.md) | The Constraint resource represents a policy/rule applied to ProductOfferingPrice. |  [optional] |
|**price** | [**ProductPriceValueVO**](ProductPriceValueVO.md) |  |  [optional] |
|**priceAlteration** | [**List&lt;POPAlterationVO&gt;**](POPAlterationVO.md) |  |  [optional] |
|**unitOfMeasure** | [**QuantityVO**](QuantityVO.md) |  |  [optional] |
|**validFor** | [**TimePeriodVO**](TimePeriodVO.md) |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class Extensible name |  [optional] |



