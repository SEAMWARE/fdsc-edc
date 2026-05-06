

# QuoteItemRelationshipVO

Used to describe relationship between quote item. These relationship could have an impact on pricing and conditions

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**id** | **String** | ID of the related order item (must be in the same quote) |  [optional] |
|**relationshipType** | **String** | Relationship type as relies on, bundles, etc... |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class entity name |  [optional] |



