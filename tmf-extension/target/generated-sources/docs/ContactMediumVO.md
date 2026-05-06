

# ContactMediumVO

Indicates the contact medium that could be used to contact the party.

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**mediumType** | **String** | Type of the contact medium, such as: email address, telephone number, postal address |  [optional] |
|**preferred** | **Boolean** | If true, indicates that is the preferred contact medium |  [optional] |
|**characteristic** | [**MediumCharacteristicVO**](MediumCharacteristicVO.md) |  |  [optional] |
|**validFor** | [**TimePeriodVO**](TimePeriodVO.md) |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class entity name |  [optional] |



