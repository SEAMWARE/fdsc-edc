

# AgreementSpecificationUpdateVO

A template of an agreement that can be used when establishing partnerships Skipped properties: id,href

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**description** | **String** | A narrative that explains in detail what the agreement specification is about |  [optional] |
|**isBundle** | **Boolean** | If true, this agreement specification is a grouping of other agreement specifications. The list of bundled agreement specifications is provided by the specificationRelationship property |  [optional] |
|**lastUpdate** | **OffsetDateTime** | Date and time of the last update |  [optional] |
|**lifecycleStatus** | **String** | Indicates the current lifecycle status |  [optional] |
|**name** | **String** | Name of the agreement specification |  |
|**version** | **String** | Agreement specification version |  [optional] |
|**attachment** | [**List&lt;AttachmentRefOrValueVO&gt;**](AttachmentRefOrValueVO.md) |  |  |
|**relatedParty** | [**List&lt;RelatedPartyVO&gt;**](RelatedPartyVO.md) |  |  [optional] |
|**serviceCategory** | [**CategoryRefVO**](CategoryRefVO.md) |  |  [optional] |
|**specificationCharacteristic** | [**List&lt;AgreementSpecCharacteristicVO&gt;**](AgreementSpecCharacteristicVO.md) |  |  [optional] |
|**specificationRelationship** | [**List&lt;AgreementSpecificationRelationshipVO&gt;**](AgreementSpecificationRelationshipVO.md) |  |  [optional] |
|**validFor** | [**TimePeriodVO**](TimePeriodVO.md) |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class entity name |  [optional] |



