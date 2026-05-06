

# CharacteristicSpecificationBaseVO

This class defines a characteristic specification.

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**id** | **String** | Unique ID for the characteristic |  [optional] |
|**configurable** | **Boolean** | If true, the Boolean indicates that the target Characteristic is configurable |  [optional] |
|**description** | **String** | A narrative that explains the CharacteristicSpecification. |  [optional] |
|**extensible** | **Boolean** | An indicator that specifies that the values for the characteristic can be extended by adding new values when instantiating a characteristic for a resource. |  [optional] |
|**isUnique** | **Boolean** | An indicator that specifies if a value is unique for the specification. Possible values are; \&quot;unique while value is in effect\&quot; and \&quot;unique whether value is in effect or not\&quot; |  [optional] |
|**maxCardinality** | **Integer** | The maximum number of instances a CharacteristicValue can take on. For example, zero to five phone numbers in a group calling plan, where five is the value for the maxCardinality. |  [optional] |
|**minCardinality** | **Integer** | The minimum number of instances a CharacteristicValue can take on. For example, zero to five phone numbers in a group calling plan, where zero is the value for the minCardinality. |  [optional] |
|**name** | **String** | A word, term, or phrase by which this characteristic specification is known and distinguished from other characteristic specifications. |  [optional] |
|**regex** | **String** | A rule or principle represented in regular expression used to derive the value of a characteristic value. |  [optional] |
|**valueType** | **String** | A kind of value that the characteristic can take on, such as numeric, text and so forth |  [optional] |
|**validFor** | [**TimePeriodVO**](TimePeriodVO.md) |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class Extensible name |  [optional] |
|**atValueSchemaLocation** | **String** | This (optional) field provides a link to the schema describing the value type. |  [optional] |



