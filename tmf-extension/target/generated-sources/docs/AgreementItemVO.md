

# AgreementItemVO

A part of the agreement expressed in terms of a product offering and possibly including specific terms and conditions.

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**product** | [**List&lt;ProductRefVO&gt;**](ProductRefVO.md) | The list of products indirectly referred by this agreement item (since an agreement item refers primarily to product offerings) |  [optional] |
|**productOffering** | [**List&lt;ProductOfferingRefVO&gt;**](ProductOfferingRefVO.md) | The list of product offerings referred by this agreement item |  [optional] |
|**termOrCondition** | [**List&lt;AgreementTermOrConditionVO&gt;**](AgreementTermOrConditionVO.md) |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class entity name |  [optional] |



