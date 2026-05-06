

# ExportJobCreateVO

Represents a task used to export resources to a file Skipped properties: id,href

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**completionDate** | **OffsetDateTime** | Data at which the job was completed |  [optional] |
|**contentType** | **String** | The format of the exported data |  [optional] |
|**creationDate** | **OffsetDateTime** | Date at which the job was created |  [optional] |
|**errorLog** | **String** | Reason for failure |  [optional] |
|**path** | **String** | URL of the root resource acting as the source for streaming content to the file specified by the export job |  [optional] |
|**query** | **String** | Used to scope the exported data |  [optional] |
|**url** | **URI** | URL of the file containing the data to be exported |  |
|**status** | **JobStateTypeVO** |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class Extensible name |  [optional] |



