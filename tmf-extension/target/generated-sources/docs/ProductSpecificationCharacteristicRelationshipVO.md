

# ProductSpecificationCharacteristicRelationshipVO

An aggregation, migration, substitution, dependency or exclusivity relationship between/among productSpecificationCharacteristics.

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**id** | **String** | the identifier of the associated product specification |  [optional] |
|**href** | **String** | Hyperlink reference to the target product specification |  [optional] |
|**charSpecSeq** | **Integer** | The order in which a CharacteristicSpecification appears within another CharacteristicSpecification that defines a grouping of CharacteristicSpecifications.  For example, a grouping may represent the name of an individual. The given name is first, the middle name is second, and the last name is third. |  [optional] |
|**name** | **String** | Name of the target product specification characteristic |  [optional] |
|**relationshipType** | **String** | Type of relationship such as aggregation, migration, substitution, dependency, exclusivity |  [optional] |
|**validFor** | [**TimePeriodVO**](TimePeriodVO.md) |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class Extensible name |  [optional] |



