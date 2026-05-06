

# AgreementSpecificationRelationshipVO

A relationship between agreement specifications. Typical relationships are substitution and dependency.

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**id** | **String** | Unique identifier of a related entity. |  [optional] |
|**href** | **String** | Reference of the related entity. |  [optional] |
|**name** | **String** | Name of the related entity. |  [optional] |
|**relationshipType** | **String** | Type of relationship such as, substitution or dependency. |  [optional] |
|**validFor** | [**TimePeriodVO**](TimePeriodVO.md) |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class entity name |  [optional] |
|**atReferredType** | **String** | The actual type of the target instance when needed for disambiguation. |  [optional] |



