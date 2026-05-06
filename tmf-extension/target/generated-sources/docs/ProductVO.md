

# ProductVO

A product offering procured by a customer or other interested party playing a party role. A product is realized as one or more service(s) and / or resource(s).

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**id** | **String** | Unique identifier of the product |  [optional] |
|**href** | **String** | Reference of the product |  [optional] |
|**description** | **String** | Is the description of the product. It could be copied from the description of the Product Offering. |  [optional] |
|**isBundle** | **Boolean** | If true, the product is a ProductBundle which is an instantiation of a BundledProductOffering. If false, the product is a ProductComponent which is an instantiation of a SimpleProductOffering. |  [optional] |
|**isCustomerVisible** | **Boolean** | If true, the product is visible by the customer. |  [optional] |
|**name** | **String** | Name of the product. It could be the same as the name of the product offering |  [optional] |
|**orderDate** | **OffsetDateTime** | Is the date when the product was ordered |  [optional] |
|**productSerialNumber** | **String** | Is the serial number for the product. This is typically applicable to tangible products e.g. Broadband Router. |  [optional] |
|**startDate** | **OffsetDateTime** | Is the date from which the product starts |  [optional] |
|**terminationDate** | **OffsetDateTime** | Is the date when the product was terminated |  [optional] |
|**agreement** | [**List&lt;AgreementItemRefVO&gt;**](AgreementItemRefVO.md) |  |  [optional] |
|**billingAccount** | [**BillingAccountRefVO**](BillingAccountRefVO.md) |  |  [optional] |
|**place** | [**List&lt;RelatedPlaceRefOrValueVO&gt;**](RelatedPlaceRefOrValueVO.md) |  |  [optional] |
|**product** | [**List&lt;ProductRefOrValueVO&gt;**](ProductRefOrValueVO.md) |  |  [optional] |
|**productCharacteristic** | [**List&lt;CharacteristicVO&gt;**](CharacteristicVO.md) |  |  [optional] |
|**productOffering** | [**ProductOfferingRefVO**](ProductOfferingRefVO.md) |  |  [optional] |
|**productOrderItem** | [**List&lt;RelatedProductOrderItemVO&gt;**](RelatedProductOrderItemVO.md) |  |  [optional] |
|**productPrice** | [**List&lt;ProductPriceVO&gt;**](ProductPriceVO.md) |  |  [optional] |
|**productRelationship** | [**List&lt;ProductRelationshipVO&gt;**](ProductRelationshipVO.md) |  |  [optional] |
|**productSpecification** | [**ProductSpecificationRefVO**](ProductSpecificationRefVO.md) |  |  [optional] |
|**productTerm** | [**List&lt;ProductTermVO&gt;**](ProductTermVO.md) |  |  [optional] |
|**realizingResource** | [**List&lt;ResourceRefVO&gt;**](ResourceRefVO.md) |  |  [optional] |
|**realizingService** | [**List&lt;ServiceRefVO&gt;**](ServiceRefVO.md) |  |  [optional] |
|**relatedParty** | [**List&lt;RelatedPartyVO&gt;**](RelatedPartyVO.md) |  |  [optional] |
|**status** | **ProductStatusTypeVO** |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class entity name |  [optional] |



