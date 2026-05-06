

# CatalogCreateVO

A collection of Product Offerings, intended for a specific DistributionChannel, enhanced with additional information such as SLA parameters, invoicing and shipping details Skipped properties: id,href

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**catalogType** | **String** | Indicates if the catalog is a product, service or resource catalog |  [optional] |
|**description** | **String** | Description of this catalog |  [optional] |
|**lastUpdate** | **OffsetDateTime** | Date and time of the last update |  [optional] |
|**lifecycleStatus** | **String** | Used to indicate the current lifecycle status |  [optional] |
|**name** | **String** | Name of the catalog |  |
|**version** | **String** | Catalog version |  [optional] |
|**category** | [**List&lt;CategoryRefVO&gt;**](CategoryRefVO.md) | List of root categories contained in this catalog |  [optional] |
|**relatedParty** | [**List&lt;RelatedPartyVO&gt;**](RelatedPartyVO.md) | List of parties involved in this catalog |  [optional] |
|**validFor** | [**TimePeriodVO**](TimePeriodVO.md) |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class Extensible name |  [optional] |



