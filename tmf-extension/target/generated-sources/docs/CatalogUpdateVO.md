

# CatalogUpdateVO

A collection of Product Offerings, intended for a specific DistributionChannel, enhanced with additional information such as SLA parameters, invoicing and shipping details Skipped properties: id,href,lastUpdate,@type,@baseType

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**catalogType** | **String** | Indicates if the catalog is a product, service or resource catalog |  [optional] |
|**description** | **String** | Description of this catalog |  [optional] |
|**lifecycleStatus** | **String** | Used to indicate the current lifecycle status |  [optional] |
|**name** | **String** | Name of the catalog |  [optional] |
|**version** | **String** | Catalog version |  [optional] |
|**category** | [**List&lt;CategoryRefVO&gt;**](CategoryRefVO.md) | List of root categories contained in this catalog |  [optional] |
|**relatedParty** | [**List&lt;RelatedPartyVO&gt;**](RelatedPartyVO.md) | List of parties involved in this catalog |  [optional] |
|**validFor** | [**TimePeriodVO**](TimePeriodVO.md) |  |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |



