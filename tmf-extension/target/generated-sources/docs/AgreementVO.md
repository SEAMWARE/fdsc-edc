

# AgreementVO

An agreement represents a contract or arrangement, either written or verbal and sometimes enforceable by law, such as a service level agreement or a customer price agreement. An agreement involves a number of other business entities, such as products, services, and resources and/or their specifications.

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**id** | **String** | Unique identifier for the agreement |  [optional] |
|**href** | **String** | Unique url identifying the agreement as a resource |  [optional] |
|**agreementType** | **String** | The type of the agreement. For example commercial |  |
|**description** | **String** | Narrative that explains the agreement and details about the it , such as why the agreement is taking place. |  [optional] |
|**documentNumber** | **Integer** | A reference number assigned to an Agreement that follows a prescribed numbering system. |  [optional] |
|**initialDate** | **OffsetDateTime** | Date at which the agreement was initialized |  [optional] |
|**name** | **String** | A human-readable name for the agreement |  |
|**statementOfIntent** | **String** | An overview and goals of the Agreement |  [optional] |
|**status** | **String** | The current status of the agreement. Typical values are: in process, approved and rejected |  [optional] |
|**version** | **String** | A string identifying the version of the agreement |  [optional] |
|**agreementAuthorization** | [**List&lt;AgreementAuthorizationVO&gt;**](AgreementAuthorizationVO.md) |  |  [optional] |
|**agreementItem** | [**List&lt;AgreementItemVO&gt;**](AgreementItemVO.md) |  |  |
|**agreementPeriod** | [**TimePeriodVO**](TimePeriodVO.md) |  |  [optional] |
|**agreementSpecification** | [**AgreementSpecificationRefVO**](AgreementSpecificationRefVO.md) |  |  [optional] |
|**associatedAgreement** | [**List&lt;AgreementRefVO&gt;**](AgreementRefVO.md) |  |  [optional] |
|**characteristic** | [**List&lt;CharacteristicVO&gt;**](CharacteristicVO.md) |  |  [optional] |
|**completionDate** | [**TimePeriodVO**](TimePeriodVO.md) |  |  [optional] |
|**engagedParty** | [**List&lt;RelatedPartyVO&gt;**](RelatedPartyVO.md) |  |  |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class entity name |  [optional] |



