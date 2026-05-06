

# OrganizationVO

Organization represents a group of people identified by shared interests or purpose. Examples include business, department and enterprise. Because of the complex nature of many businesses, both organizations and organization units are represented by the same data.

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**id** | **String** | Unique identifier of the organization |  |
|**href** | **String** | Hyperlink to access the organization |  [optional] |
|**isHeadOffice** | **Boolean** | If value is true, the organization is the head office |  [optional] |
|**isLegalEntity** | **Boolean** | If value is true, the organization is a legal entity known by a national referential. |  [optional] |
|**name** | **String** | Organization name (department name for example) |  [optional] |
|**nameType** | **String** | Type of the name : Co, Inc, Ltd,… |  [optional] |
|**organizationType** | **String** | Type of Organization (company, department...) |  [optional] |
|**tradingName** | **String** | Name that the organization (unit) trades under |  [optional] |
|**contactMedium** | [**List&lt;ContactMediumVO&gt;**](ContactMediumVO.md) |  |  [optional] |
|**creditRating** | [**List&lt;PartyCreditProfileVO&gt;**](PartyCreditProfileVO.md) |  |  [optional] |
|**existsDuring** | [**TimePeriodVO**](TimePeriodVO.md) |  |  [optional] |
|**externalReference** | [**List&lt;ExternalReferenceVO&gt;**](ExternalReferenceVO.md) |  |  [optional] |
|**organizationChildRelationship** | [**List&lt;OrganizationChildRelationshipVO&gt;**](OrganizationChildRelationshipVO.md) |  |  [optional] |
|**organizationIdentification** | [**List&lt;OrganizationIdentificationVO&gt;**](OrganizationIdentificationVO.md) |  |  [optional] |
|**organizationParentRelationship** | [**OrganizationParentRelationshipVO**](OrganizationParentRelationshipVO.md) |  |  [optional] |
|**otherName** | [**List&lt;OtherNameOrganizationVO&gt;**](OtherNameOrganizationVO.md) |  |  [optional] |
|**partyCharacteristic** | [**List&lt;CharacteristicVO&gt;**](CharacteristicVO.md) |  |  [optional] |
|**relatedParty** | [**List&lt;RelatedPartyVO&gt;**](RelatedPartyVO.md) |  |  [optional] |
|**status** | **OrganizationStateTypeVO** |  |  [optional] |
|**taxExemptionCertificate** | [**List&lt;TaxExemptionCertificateVO&gt;**](TaxExemptionCertificateVO.md) |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class entity name |  [optional] |



