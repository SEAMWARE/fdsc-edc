

# UsageSpecificationCreateVO

A detailed description of a usage event that are of interest to the business and can have charges applied to it. It is comprised of characteristics, which define all attributes known for a particular type of usage. Skipped properties: id,href

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**description** | **String** | Description of the specification |  [optional] |
|**isBundle** | **Boolean** | isBundle determines whether specification represents a single specification (false), or a bundle of specifications (true). |  [optional] |
|**lastUpdate** | **OffsetDateTime** | Date and time of the last update of the specification |  [optional] |
|**lifecycleStatus** | **String** | Used to indicate the current lifecycle status of this catalog item |  [optional] |
|**name** | **String** | Name given to the specification |  [optional] |
|**version** | **String** | specification version |  [optional] |
|**attachment** | [**List&lt;AttachmentRefOrValueVO&gt;**](AttachmentRefOrValueVO.md) | Attachments that may be of relevance to this specification, such as picture, document, media |  [optional] |
|**constraint** | [**List&lt;ConstraintRefVO&gt;**](ConstraintRefVO.md) | This is a list of constraint references applied to this specification |  [optional] |
|**entitySpecRelationship** | [**List&lt;EntitySpecificationRelationshipVO&gt;**](EntitySpecificationRelationshipVO.md) | Relationship to another specification |  [optional] |
|**relatedParty** | [**List&lt;RelatedPartyVO&gt;**](RelatedPartyVO.md) | Parties who manage or otherwise have an interest in this specification |  [optional] |
|**specCharacteristic** | [**List&lt;CharacteristicSpecificationVO&gt;**](CharacteristicSpecificationVO.md) | List of characteristics that the entity can take |  [optional] |
|**targetEntitySchema** | [**TargetEntitySchemaVO**](TargetEntitySchemaVO.md) |  |  [optional] |
|**validFor** | [**TimePeriodVO**](TimePeriodVO.md) |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class Extensible name |  [optional] |



