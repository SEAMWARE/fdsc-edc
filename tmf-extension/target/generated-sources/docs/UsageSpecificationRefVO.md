

# UsageSpecificationRefVO

UsageSpecification reference. UsageSpecification is a detailed description of a usage event that are of interest to the business and can have charges applied to it. It is comprised of characteristics, which define all attributes known for a particular type of usage.

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**id** | **String** | unique identifier |  |
|**href** | **URI** | Hyperlink reference |  [optional] |
|**name** | **String** | The name of the usage specification |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class Extensible name |  [optional] |
|**atReferredType** | **String** | The actual type of the target instance when needed for disambiguation. |  [optional] |



