

# EntitySpecificationRelationshipVO

A migration, substitution, dependency or exclusivity relationship between/among entity specifications.

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**id** | **String** | unique identifier |  [optional] |
|**href** | **URI** | Hyperlink reference |  [optional] |
|**name** | **String** | Name of the related entity. |  [optional] |
|**relationshipType** | **String** | Type of relationship such as migration, substitution, dependency, exclusivity |  |
|**role** | **String** | The association role for this entity specification |  [optional] |
|**associationSpec** | [**AssociationSpecificationRefVO**](AssociationSpecificationRefVO.md) |  |  [optional] |
|**validFor** | [**TimePeriodVO**](TimePeriodVO.md) |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class Extensible name |  [optional] |
|**atReferredType** | **String** | The actual type of the target instance when needed for disambiguation. |  [optional] |



