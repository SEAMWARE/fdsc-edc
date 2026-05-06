

# AgreementSpecCharacteristicVO

A characteristic quality or distinctive feature of an agreement.

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**configurable** | **Boolean** | If true, the Boolean indicates that the characteristic is configurable |  [optional] |
|**description** | **String** | A narrative that explains in detail what the characteristic is |  [optional] |
|**name** | **String** | Name of the characteristic being specified. |  [optional] |
|**valueType** | **String** | A kind of value that the characteristic can take on, such as numeric, text and so forth |  [optional] |
|**specCharacteristicValue** | [**List&lt;AgreementSpecCharacteristicValueVO&gt;**](AgreementSpecCharacteristicValueVO.md) |  |  [optional] |
|**validFor** | [**TimePeriodVO**](TimePeriodVO.md) |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class entity name |  [optional] |



