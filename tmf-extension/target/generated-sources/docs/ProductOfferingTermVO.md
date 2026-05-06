

# ProductOfferingTermVO

A condition under which a ProductOffering is made available to Customers. For instance, a productOffering can be offered with multiple commitment periods.

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**description** | **String** | Description of the productOfferingTerm |  [optional] |
|**name** | **String** | Name of the productOfferingTerm |  [optional] |
|**duration** | [**DurationVO**](DurationVO.md) |  |  [optional] |
|**validFor** | [**TimePeriodVO**](TimePeriodVO.md) |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class Extensible name |  [optional] |



