

# PartyVO

Generic Party structure used to define commonalities between sub concepts of Individual and Organization.

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**id** | **String** | Unique identifier of the organization |  [optional] |
|**href** | **String** | Hyperlink to access the organization |  [optional] |
|**contactMedium** | [**List&lt;ContactMediumVO&gt;**](ContactMediumVO.md) |  |  [optional] |
|**creditRating** | [**List&lt;PartyCreditProfileVO&gt;**](PartyCreditProfileVO.md) |  |  [optional] |
|**externalReference** | [**List&lt;ExternalReferenceVO&gt;**](ExternalReferenceVO.md) |  |  [optional] |
|**partyCharacteristic** | [**List&lt;CharacteristicVO&gt;**](CharacteristicVO.md) |  |  [optional] |
|**relatedParty** | [**List&lt;RelatedPartyVO&gt;**](RelatedPartyVO.md) |  |  [optional] |
|**taxExemptionCertificate** | [**List&lt;TaxExemptionCertificateVO&gt;**](TaxExemptionCertificateVO.md) |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class entity name |  [optional] |



