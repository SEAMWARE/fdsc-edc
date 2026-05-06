

# NoteVO

Extra information about a given entity

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**id** | **String** | Identifier of the note within its containing entity (may or may not be globally unique, depending on provider implementation) |  [optional] |
|**author** | **String** | Author of the note |  [optional] |
|**date** | **OffsetDateTime** | Date of the note |  [optional] |
|**text** | **String** | Text of the note |  |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class entity name |  [optional] |



