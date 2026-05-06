

# AppointmentRefVO

Refers an appointment, such as a Customer presentation or internal meeting or site visit

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**id** | **String** | The identifier of the referred appointment |  |
|**href** | **String** | The reference of the appointment |  [optional] |
|**description** | **String** | An explanatory text regarding the appointment made with a party |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class entity name |  [optional] |
|**atReferredType** | **String** | The actual type of the target instance when needed for disambiguation |  [optional] |



