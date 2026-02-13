package org.seamware.edc.store;

import org.eclipse.edc.connector.controlplane.contract.spi.ContractOfferId;
import org.eclipse.edc.connector.controlplane.contract.spi.offer.ConsumerOfferResolver;
import org.eclipse.edc.connector.controlplane.contract.spi.validation.ValidatableConsumerOffer;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.ServiceResult;
import org.jetbrains.annotations.NotNull;
import org.seamware.edc.tmf.ProductCatalogApiClient;

import java.util.Optional;

/**
 * Offer resolver to extract the TMForum entities for a given ID and translates them to a {@link ValidatableConsumerOffer}
 */
public class TMForumConsumerOfferResolver implements ConsumerOfferResolver {

    private final Monitor monitor;
    private final ProductCatalogApiClient productCatalogApiClient;
    private final TMFEdcMapper tmfEdcMapper;

    public TMForumConsumerOfferResolver(Monitor monitor, ProductCatalogApiClient productCatalogApiClient, TMFEdcMapper tmfEdcMapper) {
        this.monitor = monitor;
        this.productCatalogApiClient = productCatalogApiClient;
        this.tmfEdcMapper = tmfEdcMapper;
    }

    @Override
    public @NotNull ServiceResult<ValidatableConsumerOffer> resolveOffer(String offeringId) {
        monitor.debug("Resolve offer " + offeringId);
        Optional<ContractOfferId> optionalContractOfferId = ContractOfferId.parseId(offeringId)
                .asOptional();
        return optionalContractOfferId.map(contractOfferId -> productCatalogApiClient.getProductOfferingByExternalId(offeringId)
                .flatMap(epo -> tmfEdcMapper.consumerOfferFromProductOffering(epo, contractOfferId))
                .map(ServiceResult::success)
                .orElse(ServiceResult.notFound(String.format("Was not able to resolve offering %s.", offeringId)))).orElseGet(() -> ServiceResult.badRequest(String.format("Offering id %s is not valid.", offeringId)));
    }

}
