

# QuoteCreateVO

Quote can be used to negotiate service and product acquisition or modification between a customer and a service provider. Quote contain list of quote items, a reference to customer (partyRole), a list of productOffering and attached prices and conditions. Skipped properties: href,quoteDate,state,effectiveQuoteCompletionDate,quoteAuthorization,quoteTotalPrice,expectedQuoteCompletionDate,validFor

## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**id** | **String** | Unique identifier - attributed by quoting system |  [optional] |
|**category** | **String** | Used to categorize the quote from a business perspective that can be useful for the CRM system (e.g. \&quot;enterprise\&quot;, \&quot;residential\&quot;, ...) |  [optional] |
|**description** | **String** | Description of the quote |  [optional] |
|**expectedFulfillmentStartDate** | **OffsetDateTime** | this is the date wished by the requester to have the requested quote item delivered |  [optional] |
|**externalId** | **String** | ID given by the consumer and only understandable by him (to facilitate his searches afterwards) |  [optional] |
|**instantSyncQuote** | **Boolean** | An indicator which when the value is \&quot;true\&quot; means that requester expects to get quoting result immediately in the response. If the indicator is true then the response code of 200 indicates the operation is successful otherwise a task is created with a response 201.  |  [optional] |
|**requestedQuoteCompletionDate** | **OffsetDateTime** | This is requested date - from quote requester - to get a complete response for this quote |  [optional] |
|**version** | **String** | Quote version - if the customer rejected the quote but  negotiations still open a new version of the quote is managed |  [optional] |
|**agreement** | [**List&lt;AgreementRefVO&gt;**](AgreementRefVO.md) | A reference to an agreement defining the context of the quote |  [optional] |
|**authorization** | [**List&lt;AuthorizationVO&gt;**](AuthorizationVO.md) | An authorization provided for the quote |  [optional] |
|**billingAccount** | [**List&lt;BillingAccountRefVO&gt;**](BillingAccountRefVO.md) | A reference to a billing account to provide quote context information  |  [optional] |
|**contactMedium** | [**List&lt;ContactMediumVO&gt;**](ContactMediumVO.md) | Information contact related to the quote requester |  [optional] |
|**note** | [**List&lt;NoteVO&gt;**](NoteVO.md) | Free form text associated with the quote |  [optional] |
|**productOfferingQualification** | [**List&lt;ProductOfferingQualificationRefVO&gt;**](ProductOfferingQualificationRefVO.md) | A reference to a previously done product offering qualification |  [optional] |
|**quoteItem** | [**List&lt;QuoteItemVO&gt;**](QuoteItemVO.md) | An item of the quote - it is used to descirbe an operation on a product to be quoted |  |
|**relatedParty** | [**List&lt;RelatedPartyVO&gt;**](RelatedPartyVO.md) | A reference to a party playing a role in this quote (customer, seller, requester, etc.) |  [optional] |
|**atBaseType** | **String** | When sub-classing, this defines the super-class |  [optional] |
|**atSchemaLocation** | **URI** | A URI to a JSON-Schema file that defines additional attributes and relationships |  [optional] |
|**atType** | **String** | When sub-classing, this defines the sub-class entity name |  [optional] |



