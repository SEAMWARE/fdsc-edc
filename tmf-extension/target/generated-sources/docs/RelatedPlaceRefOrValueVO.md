

# RelatedPlaceRefOrValueVO

Related Entity reference. A related place defines a place described by reference or by value linked to a specific entity. The polymorphic attributes @type, @schemaLocation & @referredType are related to the place entity and not the RelatedPlaceRefOrValue class itself

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**id** | **String** | Unique identifier of the place |  [optional] |
|**href** | **String** | Unique reference of the place |  [optional] |
|**name** | **String** | A user-friendly name for the place, such as [Paris Store], [London Store], [Main Home] |  [optional] |
|**role** | **String** |  |  |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class entity name |  [optional] |
|**atReferredType** | **String** | The actual type of the target instance when needed for disambiguation. |  [optional] |



