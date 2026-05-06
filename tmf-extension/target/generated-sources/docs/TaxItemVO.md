

# TaxItemVO

A tax item is created for each tax rate and tax type used in the bill.

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**id** | **String** | unique identifier |  [optional] |
|**href** | **URI** | Hyperlink reference |  [optional] |
|**taxCategory** | **String** | Tax category |  [optional] |
|**taxRate** | **Float** | Applied rate of the tax |  [optional] |
|**taxAmount** | [**MoneyVO**](MoneyVO.md) |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class Extensible name |  [optional] |



