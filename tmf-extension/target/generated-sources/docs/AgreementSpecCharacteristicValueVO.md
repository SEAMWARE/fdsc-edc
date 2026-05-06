

# AgreementSpecCharacteristicValueVO

A number or text that can be assigned to an agreement specification characteristic.

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**_default** | **Boolean** | Indicates if the value is the default value for a characteristic |  [optional] |
|**unitOfMeasure** | **String** | Unit of measure for the characteristic, such as minutes, gigabytes (GB) and so on. |  [optional] |
|**valueFrom** | **String** | The low range value that a characteristic can take on |  [optional] |
|**valueTo** | **String** | The upper range value that a characteristic can take on |  [optional] |
|**valueType** | **String** | A kind of value that the characteristic can take on, such as numeric, text, and so forth |  [optional] |
|**validFor** | [**TimePeriodVO**](TimePeriodVO.md) |  |  [optional] |
|**value** | **Object** |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class entity name |  [optional] |



