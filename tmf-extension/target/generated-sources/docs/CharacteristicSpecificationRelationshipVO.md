

# CharacteristicSpecificationRelationshipVO

An aggregation, migration, substitution, dependency or exclusivity relationship between/among Characteristic specifications. The specification characteristic is embedded within the specification whose ID and href are in this entity, and identified by its ID.

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**id** | **String** | unique identifier |  [optional] |
|**href** | **URI** | Hyperlink reference |  [optional] |
|**characteristicSpecificationId** | **String** | Unique identifier of the characteristic within the specification |  [optional] |
|**name** | **String** | Name of the target characteristic within the specification |  [optional] |
|**parentSpecificationHref** | **URI** | Hyperlink reference to the parent specification containing the target characteristic |  [optional] |
|**parentSpecificationId** | **String** | Unique identifier of the parent specification containing the target characteristic |  [optional] |
|**relationshipType** | **String** | Type of relationship such as aggregation, migration, substitution, dependency, exclusivity |  [optional] |
|**validFor** | [**TimePeriodVO**](TimePeriodVO.md) |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class Extensible name |  [optional] |



