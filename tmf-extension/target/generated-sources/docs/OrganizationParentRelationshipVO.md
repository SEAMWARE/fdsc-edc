

# OrganizationParentRelationshipVO

Parent references of an organization in a structure of organizations.

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**relationshipType** | **String** | Type of the relationship. Could be juridical, hierarchical, geographical, functional for example. |  [optional] |
|**organization** | [**OrganizationRefVO**](OrganizationRefVO.md) |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class entity name |  [optional] |



