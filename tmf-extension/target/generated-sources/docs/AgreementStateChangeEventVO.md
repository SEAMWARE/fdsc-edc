

# AgreementStateChangeEventVO

The notification data structure

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**id** | **String** | Identifier of the resource involved in the event |  [optional] |
|**href** | **String** | Reference of the resource involved in the event |  [optional] |
|**eventId** | **String** | The identifier of the notification. |  [optional] |
|**eventTime** | **OffsetDateTime** | Time of the event occurrence. |  [optional] |
|**eventType** | **String** | The type of the notification. |  [optional] |
|**correlationId** | **String** | The correlation id for this event. |  [optional] |
|**domain** | **String** | The domain of the event. |  [optional] |
|**title** | **String** | The title of the event. |  [optional] |
|**description** | **String** | An explanatory of the event. |  [optional] |
|**priority** | **String** | A priority. |  [optional] |
|**timeOcurred** | **OffsetDateTime** | The time the event occured. |  [optional] |
|**event** | [**AgreementStateChangeEventPayloadVO**](AgreementStateChangeEventPayloadVO.md) |  |  [optional] |



