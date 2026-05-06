

# ProductOfferingRelationshipVO

A relationship between two product Offerings.

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**id** | **String** | unique identifier |  [optional] |
|**href** | **URI** | Hyperlink reference |  [optional] |
|**name** | **String** | Name of the related entity. |  [optional] |
|**relationshipType** | **String** | Type of relationship between product offerings such as requires, exchangableTo, optionalFor |  [optional] |
|**role** | **String** | The association role for the source product offering |  [optional] |
|**validFor** | [**TimePeriodVO**](TimePeriodVO.md) |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class Extensible name |  [optional] |
|**atReferredType** | **String** | The actual type of the target instance when needed for disambiguation. |  [optional] |



