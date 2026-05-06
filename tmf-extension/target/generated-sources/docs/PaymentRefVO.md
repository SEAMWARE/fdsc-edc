

# PaymentRefVO

If an immediate payment has been done at the product order submission, the payment information are captured and stored (as a reference) in the order.

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**id** | **String** | Unique identifier of a related entity. |  |
|**href** | **String** | Reference of the related entity. |  [optional] |
|**name** | **String** | A name for the payment |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class entity name |  [optional] |
|**atReferredType** | **String** | The actual type of the target instance when needed for disambiguation. |  [optional] |



