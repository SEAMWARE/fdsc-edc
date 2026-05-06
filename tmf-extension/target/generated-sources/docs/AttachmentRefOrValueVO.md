

# AttachmentRefOrValueVO

An attachment by value or by reference. An attachment complements the description of an element, for example through a document, a video, a picture.

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**id** | **String** | Unique identifier for this particular attachment |  [optional] |
|**href** | **URI** | URI for this Attachment |  [optional] |
|**attachmentType** | **String** | Attachment type such as video, picture |  [optional] |
|**content** | **String** | The actual contents of the attachment object, if embedded, encoded as base64 |  [optional] |
|**description** | **String** | A narrative text describing the content of the attachment |  [optional] |
|**mimeType** | **String** | Attachment mime type such as extension file for video, picture and document |  [optional] |
|**name** | **String** | The name of the attachment |  [optional] |
|**url** | **URI** | Uniform Resource Locator, is a web page address (a subset of URI) |  [optional] |
|**size** | [**QuantityVO**](QuantityVO.md) |  |  [optional] |
|**validFor** | [**TimePeriodVO**](TimePeriodVO.md) |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class Extensible name |  [optional] |
|**atReferredType** | **String** | The actual type of the target instance when needed for disambiguation. |  [optional] |



