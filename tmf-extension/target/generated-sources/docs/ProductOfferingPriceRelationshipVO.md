

# ProductOfferingPriceRelationshipVO

Describes a non-composite relationship between product offering prices. For example one price might be an discount alteration for another price.

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**id** | **String** | unique identifier |  [optional] |
|**href** | **URI** | Hyperlink reference |  [optional] |
|**name** | **String** | Name of the related entity. |  [optional] |
|**relationshipType** | **String** | type of the relationship, for example override, discount, etc. |  [optional] |
|**role** | **String** | The association role for the source product offering price |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class Extensible name |  [optional] |
|**atReferredType** | **String** | The actual type of the target instance when needed for disambiguation. |  [optional] |



