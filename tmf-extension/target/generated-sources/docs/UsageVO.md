

# UsageVO

An occurrence of employing a Product, Service, or Resource for its intended purpose, which is of interest to the business and can have charges applied to it. It is comprised of characteristics, which represent attributes of usage.

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**id** | **String** | unique identifier |  [optional] |
|**href** | **URI** | Hyperlink reference |  [optional] |
|**description** | **String** | Description of usage |  [optional] |
|**usageDate** | **OffsetDateTime** | Date of usage |  [optional] |
|**usageType** | **String** | Type of usage |  [optional] |
|**ratedProductUsage** | [**List&lt;RatedProductUsageVO&gt;**](RatedProductUsageVO.md) |  |  [optional] |
|**relatedParty** | [**List&lt;RelatedPartyVO&gt;**](RelatedPartyVO.md) |  |  [optional] |
|**status** | **UsageStatusTypeVO** |  |  [optional] |
|**usageCharacteristic** | [**List&lt;UsageCharacteristicVO&gt;**](UsageCharacteristicVO.md) |  |  [optional] |
|**usageSpecification** | [**UsageSpecificationRefVO**](UsageSpecificationRefVO.md) |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class Extensible name |  [optional] |



