package org.seamware.edc.store;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import org.seamware.edc.domain.ExtendableAgreementCreateVO;
import org.seamware.edc.domain.ExtendableAgreementUpdateVO;
import org.seamware.edc.domain.ExtendableAgreementVO;
import org.seamware.edc.domain.ExtendableQuoteItemVO;
import org.seamware.edc.domain.ExtendableQuoteUpdateVO;
import org.seamware.edc.domain.ExtendableQuoteVO;
import org.seamware.edc.domain.ExtendableUsageCreateVO;
import org.seamware.edc.domain.ExtendableUsageUpdateVO;
import org.seamware.edc.domain.ExtendableUsageVO;
import org.seamware.tmforum.agreement.model.AgreementAuthorizationVO;
import org.seamware.tmforum.agreement.model.AgreementItemVO;
import org.seamware.tmforum.agreement.model.CharacteristicVO;
import org.seamware.tmforum.productorder.model.AgreementRefVO;
import org.seamware.tmforum.productorder.model.NoteVO;
import org.seamware.tmforum.productorder.model.OrderPriceVO;
import org.seamware.tmforum.productorder.model.PaymentRefVO;
import org.seamware.tmforum.productorder.model.ProductOfferingQualificationRefVO;
import org.seamware.tmforum.productorder.model.ProductOrderItemVO;
import org.seamware.tmforum.productorder.model.ProductOrderUpdateVO;
import org.seamware.tmforum.productorder.model.ProductOrderVO;
import org.seamware.tmforum.productorder.model.QuoteRefVO;
import org.seamware.tmforum.productorder.model.RelatedChannelVO;
import org.seamware.tmforum.productorder.model.RelatedPartyVO;
import org.seamware.tmforum.quote.model.AuthorizationVO;
import org.seamware.tmforum.quote.model.BillingAccountRefVO;
import org.seamware.tmforum.quote.model.ContactMediumVO;
import org.seamware.tmforum.quote.model.QuoteItemVO;
import org.seamware.tmforum.quote.model.QuotePriceVO;
import org.seamware.tmforum.usage.model.RatedProductUsageVO;
import org.seamware.tmforum.usage.model.UsageCharacteristicVO;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-05-06T10:29:09+0000",
    comments = "version: 1.4.2.Final, compiler: javac, environment: Java 21.0.10 (Eclipse Adoptium)"
)
public class TMFObjectMapperImpl implements TMFObjectMapper {

    @Override
    public ProductOrderUpdateVO map(ProductOrderVO productOrderVO) {
        if ( productOrderVO == null ) {
            return null;
        }

        ProductOrderUpdateVO productOrderUpdateVO = new ProductOrderUpdateVO();

        productOrderUpdateVO.setCancellationDate( productOrderVO.getCancellationDate() );
        productOrderUpdateVO.setCancellationReason( productOrderVO.getCancellationReason() );
        productOrderUpdateVO.setCategory( productOrderVO.getCategory() );
        productOrderUpdateVO.setCompletionDate( productOrderVO.getCompletionDate() );
        productOrderUpdateVO.setDescription( productOrderVO.getDescription() );
        productOrderUpdateVO.setExpectedCompletionDate( productOrderVO.getExpectedCompletionDate() );
        productOrderUpdateVO.setExternalId( productOrderVO.getExternalId() );
        productOrderUpdateVO.setNotificationContact( productOrderVO.getNotificationContact() );
        productOrderUpdateVO.setPriority( productOrderVO.getPriority() );
        productOrderUpdateVO.setRequestedCompletionDate( productOrderVO.getRequestedCompletionDate() );
        productOrderUpdateVO.setRequestedStartDate( productOrderVO.getRequestedStartDate() );
        List<AgreementRefVO> list = productOrderVO.getAgreement();
        if ( list != null ) {
            productOrderUpdateVO.setAgreement( new ArrayList<AgreementRefVO>( list ) );
        }
        productOrderUpdateVO.setBillingAccount( productOrderVO.getBillingAccount() );
        List<RelatedChannelVO> list1 = productOrderVO.getChannel();
        if ( list1 != null ) {
            productOrderUpdateVO.setChannel( new ArrayList<RelatedChannelVO>( list1 ) );
        }
        List<NoteVO> list2 = productOrderVO.getNote();
        if ( list2 != null ) {
            productOrderUpdateVO.setNote( new ArrayList<NoteVO>( list2 ) );
        }
        List<OrderPriceVO> list3 = productOrderVO.getOrderTotalPrice();
        if ( list3 != null ) {
            productOrderUpdateVO.setOrderTotalPrice( new ArrayList<OrderPriceVO>( list3 ) );
        }
        List<PaymentRefVO> list4 = productOrderVO.getPayment();
        if ( list4 != null ) {
            productOrderUpdateVO.setPayment( new ArrayList<PaymentRefVO>( list4 ) );
        }
        List<ProductOfferingQualificationRefVO> list5 = productOrderVO.getProductOfferingQualification();
        if ( list5 != null ) {
            productOrderUpdateVO.setProductOfferingQualification( new ArrayList<ProductOfferingQualificationRefVO>( list5 ) );
        }
        List<ProductOrderItemVO> list6 = productOrderVO.getProductOrderItem();
        if ( list6 != null ) {
            productOrderUpdateVO.setProductOrderItem( new ArrayList<ProductOrderItemVO>( list6 ) );
        }
        List<QuoteRefVO> list7 = productOrderVO.getQuote();
        if ( list7 != null ) {
            productOrderUpdateVO.setQuote( new ArrayList<QuoteRefVO>( list7 ) );
        }
        List<RelatedPartyVO> list8 = productOrderVO.getRelatedParty();
        if ( list8 != null ) {
            productOrderUpdateVO.setRelatedParty( new ArrayList<RelatedPartyVO>( list8 ) );
        }
        productOrderUpdateVO.setState( productOrderVO.getState() );
        productOrderUpdateVO.setAtBaseType( productOrderVO.getAtBaseType() );
        productOrderUpdateVO.setAtSchemaLocation( productOrderVO.getAtSchemaLocation() );
        productOrderUpdateVO.setAtType( productOrderVO.getAtType() );

        return productOrderUpdateVO;
    }

    @Override
    public ExtendableAgreementCreateVO map(ExtendableAgreementVO extendableAgreementVO) {
        if ( extendableAgreementVO == null ) {
            return null;
        }

        ExtendableAgreementCreateVO extendableAgreementCreateVO = new ExtendableAgreementCreateVO();

        extendableAgreementCreateVO.setAgreementType( extendableAgreementVO.getAgreementType() );
        extendableAgreementCreateVO.setDescription( extendableAgreementVO.getDescription() );
        extendableAgreementCreateVO.setDocumentNumber( extendableAgreementVO.getDocumentNumber() );
        extendableAgreementCreateVO.setInitialDate( extendableAgreementVO.getInitialDate() );
        extendableAgreementCreateVO.setName( extendableAgreementVO.getName() );
        extendableAgreementCreateVO.setStatementOfIntent( extendableAgreementVO.getStatementOfIntent() );
        extendableAgreementCreateVO.setStatus( extendableAgreementVO.getStatus() );
        extendableAgreementCreateVO.setVersion( extendableAgreementVO.getVersion() );
        List<AgreementAuthorizationVO> list = extendableAgreementVO.getAgreementAuthorization();
        if ( list != null ) {
            extendableAgreementCreateVO.setAgreementAuthorization( new ArrayList<AgreementAuthorizationVO>( list ) );
        }
        List<AgreementItemVO> list1 = extendableAgreementVO.getAgreementItem();
        if ( list1 != null ) {
            extendableAgreementCreateVO.setAgreementItem( new ArrayList<AgreementItemVO>( list1 ) );
        }
        extendableAgreementCreateVO.setAgreementPeriod( extendableAgreementVO.getAgreementPeriod() );
        extendableAgreementCreateVO.setAgreementSpecification( extendableAgreementVO.getAgreementSpecification() );
        List<org.seamware.tmforum.agreement.model.AgreementRefVO> list2 = extendableAgreementVO.getAssociatedAgreement();
        if ( list2 != null ) {
            extendableAgreementCreateVO.setAssociatedAgreement( new ArrayList<org.seamware.tmforum.agreement.model.AgreementRefVO>( list2 ) );
        }
        List<CharacteristicVO> list3 = extendableAgreementVO.getCharacteristic();
        if ( list3 != null ) {
            extendableAgreementCreateVO.setCharacteristic( new ArrayList<CharacteristicVO>( list3 ) );
        }
        extendableAgreementCreateVO.setCompletionDate( extendableAgreementVO.getCompletionDate() );
        List<org.seamware.tmforum.agreement.model.RelatedPartyVO> list4 = extendableAgreementVO.getEngagedParty();
        if ( list4 != null ) {
            extendableAgreementCreateVO.setEngagedParty( new ArrayList<org.seamware.tmforum.agreement.model.RelatedPartyVO>( list4 ) );
        }
        extendableAgreementCreateVO.setAtBaseType( extendableAgreementVO.getAtBaseType() );
        extendableAgreementCreateVO.setAtSchemaLocation( extendableAgreementVO.getAtSchemaLocation() );
        extendableAgreementCreateVO.setAtType( extendableAgreementVO.getAtType() );
        extendableAgreementCreateVO.setNegotiationId( extendableAgreementVO.getNegotiationId() );
        extendableAgreementCreateVO.setExternalId( extendableAgreementVO.getExternalId() );

        return extendableAgreementCreateVO;
    }

    @Override
    public ExtendableAgreementUpdateVO mapToUpdate(ExtendableAgreementVO extendableAgreementVO) {
        if ( extendableAgreementVO == null ) {
            return null;
        }

        ExtendableAgreementUpdateVO extendableAgreementUpdateVO = new ExtendableAgreementUpdateVO();

        extendableAgreementUpdateVO.setAgreementType( extendableAgreementVO.getAgreementType() );
        extendableAgreementUpdateVO.setDescription( extendableAgreementVO.getDescription() );
        extendableAgreementUpdateVO.setDocumentNumber( extendableAgreementVO.getDocumentNumber() );
        extendableAgreementUpdateVO.setInitialDate( extendableAgreementVO.getInitialDate() );
        extendableAgreementUpdateVO.setName( extendableAgreementVO.getName() );
        extendableAgreementUpdateVO.setStatementOfIntent( extendableAgreementVO.getStatementOfIntent() );
        extendableAgreementUpdateVO.setStatus( extendableAgreementVO.getStatus() );
        extendableAgreementUpdateVO.setVersion( extendableAgreementVO.getVersion() );
        List<AgreementAuthorizationVO> list = extendableAgreementVO.getAgreementAuthorization();
        if ( list != null ) {
            extendableAgreementUpdateVO.setAgreementAuthorization( new ArrayList<AgreementAuthorizationVO>( list ) );
        }
        List<AgreementItemVO> list1 = extendableAgreementVO.getAgreementItem();
        if ( list1 != null ) {
            extendableAgreementUpdateVO.setAgreementItem( new ArrayList<AgreementItemVO>( list1 ) );
        }
        extendableAgreementUpdateVO.setAgreementPeriod( extendableAgreementVO.getAgreementPeriod() );
        extendableAgreementUpdateVO.setAgreementSpecification( extendableAgreementVO.getAgreementSpecification() );
        List<org.seamware.tmforum.agreement.model.AgreementRefVO> list2 = extendableAgreementVO.getAssociatedAgreement();
        if ( list2 != null ) {
            extendableAgreementUpdateVO.setAssociatedAgreement( new ArrayList<org.seamware.tmforum.agreement.model.AgreementRefVO>( list2 ) );
        }
        List<CharacteristicVO> list3 = extendableAgreementVO.getCharacteristic();
        if ( list3 != null ) {
            extendableAgreementUpdateVO.setCharacteristic( new ArrayList<CharacteristicVO>( list3 ) );
        }
        List<org.seamware.tmforum.agreement.model.RelatedPartyVO> list4 = extendableAgreementVO.getEngagedParty();
        if ( list4 != null ) {
            extendableAgreementUpdateVO.setEngagedParty( new ArrayList<org.seamware.tmforum.agreement.model.RelatedPartyVO>( list4 ) );
        }
        extendableAgreementUpdateVO.setAtBaseType( extendableAgreementVO.getAtBaseType() );
        extendableAgreementUpdateVO.setAtSchemaLocation( extendableAgreementVO.getAtSchemaLocation() );
        extendableAgreementUpdateVO.setAtType( extendableAgreementVO.getAtType() );
        extendableAgreementUpdateVO.setNegotiationId( extendableAgreementVO.getNegotiationId() );
        extendableAgreementUpdateVO.setExternalId( extendableAgreementVO.getExternalId() );

        return extendableAgreementUpdateVO;
    }

    @Override
    public ExtendableQuoteUpdateVO map(ExtendableQuoteVO quoteVO) {
        if ( quoteVO == null ) {
            return null;
        }

        ExtendableQuoteUpdateVO extendableQuoteUpdateVO = new ExtendableQuoteUpdateVO();

        extendableQuoteUpdateVO.setCategory( quoteVO.getCategory() );
        extendableQuoteUpdateVO.setDescription( quoteVO.getDescription() );
        extendableQuoteUpdateVO.setEffectiveQuoteCompletionDate( quoteVO.getEffectiveQuoteCompletionDate() );
        extendableQuoteUpdateVO.setExpectedFulfillmentStartDate( quoteVO.getExpectedFulfillmentStartDate() );
        extendableQuoteUpdateVO.setExpectedQuoteCompletionDate( quoteVO.getExpectedQuoteCompletionDate() );
        extendableQuoteUpdateVO.setExternalId( quoteVO.getExternalId() );
        extendableQuoteUpdateVO.setInstantSyncQuote( quoteVO.getInstantSyncQuote() );
        extendableQuoteUpdateVO.setRequestedQuoteCompletionDate( quoteVO.getRequestedQuoteCompletionDate() );
        extendableQuoteUpdateVO.setVersion( quoteVO.getVersion() );
        List<org.seamware.tmforum.quote.model.AgreementRefVO> list = quoteVO.getAgreement();
        if ( list != null ) {
            extendableQuoteUpdateVO.setAgreement( new ArrayList<org.seamware.tmforum.quote.model.AgreementRefVO>( list ) );
        }
        List<AuthorizationVO> list1 = quoteVO.getAuthorization();
        if ( list1 != null ) {
            extendableQuoteUpdateVO.setAuthorization( new ArrayList<AuthorizationVO>( list1 ) );
        }
        List<BillingAccountRefVO> list2 = quoteVO.getBillingAccount();
        if ( list2 != null ) {
            extendableQuoteUpdateVO.setBillingAccount( new ArrayList<BillingAccountRefVO>( list2 ) );
        }
        List<ContactMediumVO> list3 = quoteVO.getContactMedium();
        if ( list3 != null ) {
            extendableQuoteUpdateVO.setContactMedium( new ArrayList<ContactMediumVO>( list3 ) );
        }
        List<org.seamware.tmforum.quote.model.NoteVO> list4 = quoteVO.getNote();
        if ( list4 != null ) {
            extendableQuoteUpdateVO.setNote( new ArrayList<org.seamware.tmforum.quote.model.NoteVO>( list4 ) );
        }
        List<org.seamware.tmforum.quote.model.ProductOfferingQualificationRefVO> list5 = quoteVO.getProductOfferingQualification();
        if ( list5 != null ) {
            extendableQuoteUpdateVO.setProductOfferingQualification( new ArrayList<org.seamware.tmforum.quote.model.ProductOfferingQualificationRefVO>( list5 ) );
        }
        List<QuoteItemVO> list6 = quoteVO.getQuoteItem();
        if ( list6 != null ) {
            extendableQuoteUpdateVO.setQuoteItem( new ArrayList<QuoteItemVO>( list6 ) );
        }
        List<QuotePriceVO> list7 = quoteVO.getQuoteTotalPrice();
        if ( list7 != null ) {
            extendableQuoteUpdateVO.setQuoteTotalPrice( new ArrayList<QuotePriceVO>( list7 ) );
        }
        List<org.seamware.tmforum.quote.model.RelatedPartyVO> list8 = quoteVO.getRelatedParty();
        if ( list8 != null ) {
            extendableQuoteUpdateVO.setRelatedParty( new ArrayList<org.seamware.tmforum.quote.model.RelatedPartyVO>( list8 ) );
        }
        extendableQuoteUpdateVO.setState( quoteVO.getState() );
        extendableQuoteUpdateVO.setValidFor( quoteVO.getValidFor() );
        extendableQuoteUpdateVO.setAtBaseType( quoteVO.getAtBaseType() );
        extendableQuoteUpdateVO.setAtSchemaLocation( quoteVO.getAtSchemaLocation() );
        extendableQuoteUpdateVO.setAtType( quoteVO.getAtType() );
        List<ExtendableQuoteItemVO> list9 = quoteVO.getExtendableQuoteItem();
        if ( list9 != null ) {
            extendableQuoteUpdateVO.setExtendableQuoteItem( new ArrayList<ExtendableQuoteItemVO>( list9 ) );
        }
        extendableQuoteUpdateVO.setContractNegotiationState( quoteVO.getContractNegotiationState() );

        return extendableQuoteUpdateVO;
    }

    @Override
    public ExtendableUsageUpdateVO map(ExtendableUsageVO extendableUsageVO) {
        if ( extendableUsageVO == null ) {
            return null;
        }

        ExtendableUsageUpdateVO extendableUsageUpdateVO = new ExtendableUsageUpdateVO();

        extendableUsageUpdateVO.setDescription( extendableUsageVO.getDescription() );
        extendableUsageUpdateVO.setUsageDate( extendableUsageVO.getUsageDate() );
        extendableUsageUpdateVO.setUsageType( extendableUsageVO.getUsageType() );
        List<RatedProductUsageVO> list = extendableUsageVO.getRatedProductUsage();
        if ( list != null ) {
            extendableUsageUpdateVO.setRatedProductUsage( new ArrayList<RatedProductUsageVO>( list ) );
        }
        List<org.seamware.tmforum.usage.model.RelatedPartyVO> list1 = extendableUsageVO.getRelatedParty();
        if ( list1 != null ) {
            extendableUsageUpdateVO.setRelatedParty( new ArrayList<org.seamware.tmforum.usage.model.RelatedPartyVO>( list1 ) );
        }
        extendableUsageUpdateVO.setStatus( extendableUsageVO.getStatus() );
        List<UsageCharacteristicVO> list2 = extendableUsageVO.getUsageCharacteristic();
        if ( list2 != null ) {
            extendableUsageUpdateVO.setUsageCharacteristic( new ArrayList<UsageCharacteristicVO>( list2 ) );
        }
        extendableUsageUpdateVO.setUsageSpecification( extendableUsageVO.getUsageSpecification() );
        extendableUsageUpdateVO.setAtBaseType( extendableUsageVO.getAtBaseType() );
        extendableUsageUpdateVO.setAtSchemaLocation( extendableUsageVO.getAtSchemaLocation() );
        extendableUsageUpdateVO.setAtType( extendableUsageVO.getAtType() );
        extendableUsageUpdateVO.setExternalId( extendableUsageVO.getExternalId() );
        extendableUsageUpdateVO.setTransferState( extendableUsageVO.getTransferState() );

        return extendableUsageUpdateVO;
    }

    @Override
    public ExtendableUsageCreateVO mapToCreate(ExtendableUsageVO extendableUsageVO) {
        if ( extendableUsageVO == null ) {
            return null;
        }

        ExtendableUsageCreateVO extendableUsageCreateVO = new ExtendableUsageCreateVO();

        extendableUsageCreateVO.setDescription( extendableUsageVO.getDescription() );
        extendableUsageCreateVO.setUsageDate( extendableUsageVO.getUsageDate() );
        extendableUsageCreateVO.setUsageType( extendableUsageVO.getUsageType() );
        List<RatedProductUsageVO> list = extendableUsageVO.getRatedProductUsage();
        if ( list != null ) {
            extendableUsageCreateVO.setRatedProductUsage( new ArrayList<RatedProductUsageVO>( list ) );
        }
        List<org.seamware.tmforum.usage.model.RelatedPartyVO> list1 = extendableUsageVO.getRelatedParty();
        if ( list1 != null ) {
            extendableUsageCreateVO.setRelatedParty( new ArrayList<org.seamware.tmforum.usage.model.RelatedPartyVO>( list1 ) );
        }
        extendableUsageCreateVO.setStatus( extendableUsageVO.getStatus() );
        List<UsageCharacteristicVO> list2 = extendableUsageVO.getUsageCharacteristic();
        if ( list2 != null ) {
            extendableUsageCreateVO.setUsageCharacteristic( new ArrayList<UsageCharacteristicVO>( list2 ) );
        }
        extendableUsageCreateVO.setUsageSpecification( extendableUsageVO.getUsageSpecification() );
        extendableUsageCreateVO.setAtBaseType( extendableUsageVO.getAtBaseType() );
        extendableUsageCreateVO.setAtSchemaLocation( extendableUsageVO.getAtSchemaLocation() );
        extendableUsageCreateVO.setAtType( extendableUsageVO.getAtType() );
        extendableUsageCreateVO.setExternalId( extendableUsageVO.getExternalId() );
        extendableUsageCreateVO.setTransferState( extendableUsageVO.getTransferState() );

        return extendableUsageCreateVO;
    }
}
