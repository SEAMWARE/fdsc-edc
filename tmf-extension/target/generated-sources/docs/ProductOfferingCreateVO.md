

# ProductOfferingCreateVO

Represents entities that are orderable from the provider of the catalog, this resource includes pricing information. Skipped properties: id,href

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**description** | **String** | Description of the productOffering |  [optional] |
|**isBundle** | **Boolean** | isBundle determines whether a productOffering represents a single productOffering (false), or a bundle of productOfferings (true). |  [optional] |
|**isSellable** | **Boolean** | A flag indicating if this product offer can be sold stand-alone for sale or not. If this flag is false it indicates that the offer can only be sold within a bundle. |  [optional] |
|**lastUpdate** | **OffsetDateTime** | Date and time of the last update |  [optional] |
|**lifecycleStatus** | **String** | Used to indicate the current lifecycle status |  [optional] |
|**name** | **String** | Name of the productOffering |  |
|**statusReason** | **String** | A string providing a complementary information on the value of the lifecycle status attribute. |  [optional] |
|**version** | **String** | ProductOffering version |  [optional] |
|**agreement** | [**List&lt;AgreementRefVO&gt;**](AgreementRefVO.md) | An agreement represents a contract or arrangement, either written or verbal and sometimes enforceable by law, such as a service level agreement or a customer price agreement. An agreement involves a number of other business entities, such as products, services, and resources and/or their specifications. |  [optional] |
|**attachment** | [**List&lt;AttachmentRefOrValueVO&gt;**](AttachmentRefOrValueVO.md) | Complements the description of an element (for instance a product) through video, pictures... |  [optional] |
|**bundledProductOffering** | [**List&lt;BundledProductOfferingVO&gt;**](BundledProductOfferingVO.md) | A type of ProductOffering that belongs to a grouping of ProductOfferings made available to the market. It inherits of all attributes of ProductOffering. |  [optional] |
|**category** | [**List&lt;CategoryRefVO&gt;**](CategoryRefVO.md) | The category resource is used to group product offerings, service and resource candidates in logical containers. Categories can contain other categories and/or product offerings, resource or service candidates. |  [optional] |
|**channel** | [**List&lt;ChannelRefVO&gt;**](ChannelRefVO.md) | The channel defines the channel for selling product offerings. |  [optional] |
|**marketSegment** | [**List&lt;MarketSegmentRefVO&gt;**](MarketSegmentRefVO.md) | provides references to the corresponding market segment as target of product offerings. A market segment is grouping of Parties, GeographicAreas, SalesChannels, and so forth. |  [optional] |
|**place** | [**List&lt;PlaceRefVO&gt;**](PlaceRefVO.md) | Place defines the places where the products are sold or delivered. |  [optional] |
|**prodSpecCharValueUse** | [**List&lt;ProductSpecificationCharacteristicValueUseVO&gt;**](ProductSpecificationCharacteristicValueUseVO.md) | A use of the ProductSpecificationCharacteristicValue by a ProductOffering to which additional properties (attributes) apply or override the properties of similar properties contained in ProductSpecificationCharacteristicValue. It should be noted that characteristics which their value(s) addressed by this object must exist in corresponding product specification. The available characteristic values for a ProductSpecificationCharacteristic in a Product specification can be modified at the ProductOffering level. For example, a characteristic &#39;Color&#39; might have values White, Blue, Green, and Red. But, the list of values can be restricted to e.g. White and Blue in an associated product offering. It should be noted that the list of values in &#39;ProductSpecificationCharacteristicValueUse&#39; is a strict subset of the list of values as defined in the corresponding product specification characteristics. |  [optional] |
|**productOfferingPrice** | [**List&lt;ProductOfferingPriceRefOrValueVO&gt;**](ProductOfferingPriceRefOrValueVO.md) | An amount, usually of money, that is asked for or allowed when a ProductOffering is bought, rented, or leased. The price is valid for a defined period of time and may not represent the actual price paid by a customer. |  [optional] |
|**productOfferingRelationship** | [**List&lt;ProductOfferingRelationshipVO&gt;**](ProductOfferingRelationshipVO.md) | A relationship between this product offering and other product offerings. |  [optional] |
|**productOfferingTerm** | [**List&lt;ProductOfferingTermVO&gt;**](ProductOfferingTermVO.md) | A condition under which a ProductOffering is made available to Customers. For instance, a productOffering can be offered with multiple commitment periods. |  [optional] |
|**productSpecification** | [**ProductSpecificationRefVO**](ProductSpecificationRefVO.md) |  |  [optional] |
|**resourceCandidate** | [**ResourceCandidateRefVO**](ResourceCandidateRefVO.md) |  |  [optional] |
|**serviceCandidate** | [**ServiceCandidateRefVO**](ServiceCandidateRefVO.md) |  |  [optional] |
|**serviceLevelAgreement** | [**SLARefVO**](SLARefVO.md) |  |  [optional] |
|**validFor** | [**TimePeriodVO**](TimePeriodVO.md) |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class Extensible name |  [optional] |



