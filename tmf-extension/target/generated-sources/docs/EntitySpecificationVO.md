

# EntitySpecificationVO

EntitySpecification is a class that offers characteristics to describe a type of entity. Entities are generic constructs that may be used to describe bespoke business entities that are not effectively covered by the existing SID model. Functionally, the entity specification acts as a template by which entities may be instantiated and described. By sharing the same specification, these entities would therefore share the same set of characteristics. Note: The ‘configurable’ attribute on the specCharacteristics determines if an entity instantiated from the entity specification can override the value of the attribute. When set to false, the entity instance may not define a value that differs from the value in the specification.

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**id** | **String** | unique identifier |  [optional] |
|**href** | **URI** | Hyperlink reference |  [optional] |
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



