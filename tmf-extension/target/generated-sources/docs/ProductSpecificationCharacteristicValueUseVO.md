

# ProductSpecificationCharacteristicValueUseVO

A use of the ProductSpecificationCharacteristicValue by a ProductOffering to which additional properties (attributes) apply or override the properties of similar properties contained in ProductSpecificationCharacteristicValue. It should be noted that characteristics which their value(s) addressed by this object must exist in corresponding product specification. The available characteristic values for a ProductSpecificationCharacteristic in a Product specification can be modified at the ProductOffering level. For example, a characteristic 'Color' might have values White, Blue, Green, and Red. But, the list of values can be restricted to e.g. White and Blue in an associated product offering. It should be noted that the list of values in 'ProductSpecificationCharacteristicValueUse' is a strict subset of the list of values as defined in the corresponding product specification characteristics.

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**id** | **String** | Unique ID for the characteristic |  [optional] |
|**description** | **String** | A narrative that explains in detail what the productSpecificationCharacteristic is |  [optional] |
|**maxCardinality** | **Integer** | The maximum number of instances a CharacteristicValue can take on. For example, zero to five phone numbers in a group calling plan, where five is the value for the maxCardinality. |  [optional] |
|**minCardinality** | **Integer** | The minimum number of instances a CharacteristicValue can take on. For example, zero to five phone numbers in a group calling plan, where zero is the value for the minCardinality. |  [optional] |
|**name** | **String** | Name of the associated productSpecificationCharacteristic |  [optional] |
|**valueType** | **String** | A kind of value that the characteristic can take on, such as numeric, text and so forth |  [optional] |
|**productSpecCharacteristicValue** | [**List&lt;CharacteristicValueSpecificationVO&gt;**](CharacteristicValueSpecificationVO.md) | A number or text that can be assigned to a ProductSpecificationCharacteristic. |  [optional] |
|**productSpecification** | [**ProductSpecificationRefVO**](ProductSpecificationRefVO.md) |  |  [optional] |
|**validFor** | [**TimePeriodVO**](TimePeriodVO.md) |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class Extensible name |  [optional] |



