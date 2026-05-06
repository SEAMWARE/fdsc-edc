

# OrganizationIdentificationVO

Represents our registration of information used as proof of identity by an organization

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**identificationId** | **String** | Identifier |  [optional] |
|**identificationType** | **String** | Type of identification information used to identify the company in a country or internationally |  [optional] |
|**issuingAuthority** | **String** | Authority which has issued the identifier (chamber of commerce...) |  [optional] |
|**issuingDate** | **OffsetDateTime** | Date at which the identifier was issued |  [optional] |
|**attachment** | [**AttachmentRefOrValueVO**](AttachmentRefOrValueVO.md) |  |  [optional] |
|**validFor** | [**TimePeriodVO**](TimePeriodVO.md) |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class entity name |  [optional] |



