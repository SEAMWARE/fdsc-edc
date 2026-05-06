

# ProductOfferingPriceCreateVO

Is based on both the basic cost to develop and produce products and the enterprises policy on revenue targets. This price may be further revised through discounting (a Product Offering Price that reflects an alteration). The price, applied for a productOffering may also be influenced by the productOfferingTerm, the customer selected, eg: a productOffering can be offered with multiple terms, like commitment periods for the contract. The price may be influenced by this productOfferingTerm. A productOffering may be cheaper with a 24 month commitment than with a 12 month commitment. Skipped properties: id,href

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**description** | **String** | Description of the productOfferingPrice |  [optional] |
|**isBundle** | **Boolean** | A flag indicating if this ProductOfferingPrice is composite (bundle) or not |  [optional] |
|**lastUpdate** | **OffsetDateTime** | the last update time of this ProductOfferingPrice |  [optional] |
|**lifecycleStatus** | **String** | the lifecycle status of this ProductOfferingPrice |  [optional] |
|**name** | **String** | Name of the productOfferingPrice |  |
|**percentage** | **Float** | Percentage to apply if this Product Offering Price is an Alteration (such as a Discount) |  [optional] |
|**priceType** | **String** | A category that describes the price, such as recurring, discount, allowance, penalty, and so forth. |  [optional] |
|**recurringChargePeriodLength** | **Integer** | the period of the recurring charge:  1, 2, ... .It sets to zero if it is not applicable |  [optional] |
|**recurringChargePeriodType** | **String** | The period to repeat the application of the price Could be month, week... |  [optional] |
|**version** | **String** | ProductOfferingPrice version |  [optional] |
|**bundledPopRelationship** | [**List&lt;BundledProductOfferingPriceRelationshipVO&gt;**](BundledProductOfferingPriceRelationshipVO.md) | this object represents a bundle relationship from a bundle product offering price (parent) to a simple product offering price (child). A simple product offering price may participate in more than one bundle relationship. |  [optional] |
|**constraint** | [**List&lt;ConstraintRefVO&gt;**](ConstraintRefVO.md) | The Constraint resource represents a policy/rule applied to ProductOfferingPrice. |  [optional] |
|**place** | [**List&lt;PlaceRefVO&gt;**](PlaceRefVO.md) | Place defines the places where the products are sold or delivered. |  [optional] |
|**popRelationship** | [**List&lt;ProductOfferingPriceRelationshipVO&gt;**](ProductOfferingPriceRelationshipVO.md) | Product Offering Prices related to this Product Offering Price, for example a price alteration such as allowance or discount |  [optional] |
|**price** | [**MoneyVO**](MoneyVO.md) |  |  [optional] |
|**pricingLogicAlgorithm** | [**List&lt;PricingLogicAlgorithmVO&gt;**](PricingLogicAlgorithmVO.md) | The PricingLogicAlgorithm entity represents an instantiation of an interface specification to external rating function (without a modeled behavior in SID). Some of the parameters of the interface definition may be already set (such as price per unit) and some may be gathered during the rating process from the event (such as call duration) or from ProductCharacteristicValues (such as assigned bandwidth). |  [optional] |
|**prodSpecCharValueUse** | [**List&lt;ProductSpecificationCharacteristicValueUseVO&gt;**](ProductSpecificationCharacteristicValueUseVO.md) | A use of the ProductSpecificationCharacteristicValue by a ProductOfferingPrice to which additional properties (attributes) apply or override the properties of similar properties contained in ProductSpecificationCharacteristicValue. It should be noted that characteristics which their value(s) addressed by this object must exist in corresponding product specification. The available characteristic values for a ProductSpecificationCharacteristic in a Product specification can be modified at the ProductOffering and ProcuctOfferingPrice level. The list of values in ProductSpecificationCharacteristicValueUse is a strict subset of the list of values as defined in the corresponding product specification characteristics. |  [optional] |
|**productOfferingTerm** | [**List&lt;ProductOfferingTermVO&gt;**](ProductOfferingTermVO.md) | A list of conditions under which a ProductOfferingPrice is made available to Customers. For instance, a Product Offering Price can be offered with multiple commitment periods. |  [optional] |
|**tax** | [**List&lt;TaxItemVO&gt;**](TaxItemVO.md) | An amount of money levied on the price of a Product by a legislative body. |  [optional] |
|**unitOfMeasure** | [**QuantityVO**](QuantityVO.md) |  |  [optional] |
|**validFor** | [**TimePeriodVO**](TimePeriodVO.md) |  |  [optional] |
|**atBaseType** | **String** | the immediate base class type of this product offering price |  [optional] |
|**atSchemaLocation** | **String** | hyperlink reference to the schema describing this resource |  [optional] |
|**atType** | **String** | The class type of this Product offering price |  [optional] |



