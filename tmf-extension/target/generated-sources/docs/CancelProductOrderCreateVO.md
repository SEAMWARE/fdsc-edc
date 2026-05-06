

# CancelProductOrderCreateVO

Request for cancellation an existing product order Skipped properties: id,href,state,effectiveCancellationDate

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**cancellationReason** | **String** | Reason why the order is cancelled. |  [optional] |
|**requestedCancellationDate** | **OffsetDateTime** | Date when the submitter wants the order to be cancelled |  [optional] |
|**productOrder** | [**ProductOrderRefVO**](ProductOrderRefVO.md) |  |  |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class entity name |  [optional] |



