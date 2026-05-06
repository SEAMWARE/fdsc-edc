

# IndividualCreateVO

Individual represents a single human being (a man, woman or child). The individual can be a customer, an employee or any other person that the organization needs to store information about. Skipped properties: id,href

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**aristocraticTitle** | **String** | e.g. Baron, Graf, Earl,… |  [optional] |
|**birthDate** | **OffsetDateTime** | Birth date |  [optional] |
|**countryOfBirth** | **String** | Country where the individual was born |  [optional] |
|**deathDate** | **OffsetDateTime** | Date of death |  [optional] |
|**familyName** | **String** | Contains the non-chosen or inherited name. Also known as last name in the Western context |  |
|**familyNamePrefix** | **String** | Family name prefix |  [optional] |
|**formattedName** | **String** | A fully formatted name in one string with all of its pieces in their proper place and all of the necessary punctuation. Useful for specific contexts (Chinese, Japanese, Korean,…) |  [optional] |
|**fullName** | **String** | Full name flatten (first, middle, and last names) |  [optional] |
|**gender** | **String** | Gender |  [optional] |
|**generation** | **String** | e.g.. Sr, Jr, III (the third),… |  [optional] |
|**givenName** | **String** | First name of the individual |  |
|**legalName** | **String** | Legal name or birth name (name one has for official purposes) |  [optional] |
|**location** | **String** | Temporary current location od the individual (may be used if the individual has approved its sharing) |  [optional] |
|**maritalStatus** | **String** | Marital status (married, divorced, widow ...) |  [optional] |
|**middleName** | **String** | Middles name or initial |  [optional] |
|**nationality** | **String** | Nationality |  [optional] |
|**placeOfBirth** | **String** | Reference to the place where the individual was born |  [optional] |
|**preferredGivenName** | **String** | Contains the chosen name by which the individual prefers to be addressed. Note: This name may be a name other than a given name, such as a nickname |  [optional] |
|**title** | **String** | Useful for titles (aristocratic, social,...) Pr, Dr, Sir, ... |  [optional] |
|**contactMedium** | [**List&lt;ContactMediumVO&gt;**](ContactMediumVO.md) |  |  [optional] |
|**creditRating** | [**List&lt;PartyCreditProfileVO&gt;**](PartyCreditProfileVO.md) |  |  [optional] |
|**disability** | [**List&lt;DisabilityVO&gt;**](DisabilityVO.md) |  |  [optional] |
|**externalReference** | [**List&lt;ExternalReferenceVO&gt;**](ExternalReferenceVO.md) |  |  [optional] |
|**individualIdentification** | [**List&lt;IndividualIdentificationVO&gt;**](IndividualIdentificationVO.md) |  |  [optional] |
|**languageAbility** | [**List&lt;LanguageAbilityVO&gt;**](LanguageAbilityVO.md) |  |  [optional] |
|**otherName** | [**List&lt;OtherNameIndividualVO&gt;**](OtherNameIndividualVO.md) |  |  [optional] |
|**partyCharacteristic** | [**List&lt;CharacteristicVO&gt;**](CharacteristicVO.md) |  |  [optional] |
|**relatedParty** | [**List&lt;RelatedPartyVO&gt;**](RelatedPartyVO.md) |  |  [optional] |
|**skill** | [**List&lt;SkillVO&gt;**](SkillVO.md) |  |  [optional] |
|**status** | **IndividualStateTypeVO** |  |  [optional] |
|**taxExemptionCertificate** | [**List&lt;TaxExemptionCertificateVO&gt;**](TaxExemptionCertificateVO.md) |  |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class entity name |  [optional] |



