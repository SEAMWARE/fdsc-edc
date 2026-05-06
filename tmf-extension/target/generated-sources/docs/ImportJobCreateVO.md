

# ImportJobCreateVO

Represents a task used to import resources from a file Skipped properties: id,href

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**completionDate** | **OffsetDateTime** | Date at which the job was completed |  [optional] |
|**contentType** | **String** | Indicates the format of the imported data |  [optional] |
|**creationDate** | **OffsetDateTime** | Date at which the job was created |  [optional] |
|**errorLog** | **String** | Reason for failure if status is failed |  [optional] |
|**path** | **String** | URL of the root resource where the content of the file specified by the import job must be applied |  [optional] |
|**url** | **URI** | URL of the file containing the data to be imported |  |
|**status** | **JobStateTypeVO** |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class Extensible name |  [optional] |



