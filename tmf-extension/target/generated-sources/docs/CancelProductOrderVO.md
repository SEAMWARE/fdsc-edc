

# CancelProductOrderVO

Request for cancellation an existing product order

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**id** | **String** | id of the cancellation request (this is not an order id) |  [optional] |
|**href** | **String** | Hyperlink to access the cancellation request |  [optional] |
|**cancellationReason** | **String** | Reason why the order is cancelled. |  [optional] |
|**effectiveCancellationDate** | **OffsetDateTime** | Date when the order is cancelled. |  [optional] |
|**requestedCancellationDate** | **OffsetDateTime** | Date when the submitter wants the order to be cancelled |  [optional] |
|**productOrder** | [**ProductOrderRefVO**](ProductOrderRefVO.md) |  |  |
|**state** | **TaskStateTypeVO** |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class entity name |  [optional] |



