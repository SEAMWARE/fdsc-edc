package org.seamware.edc.store;

import org.eclipse.edc.connector.controlplane.catalog.spi.Catalog;
import org.eclipse.edc.connector.controlplane.catalog.spi.CatalogRequestMessage;
import org.eclipse.edc.connector.controlplane.catalog.spi.Dataset;
import org.eclipse.edc.connector.controlplane.services.spi.catalog.CatalogProtocolService;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.result.ServiceResult;
import org.jetbrains.annotations.NotNull;
import org.seamware.edc.domain.ExtendableProductOffering;
import org.seamware.edc.domain.ExtendableProductSpecification;
import org.seamware.edc.tmf.ProductCatalogApiClient;

import java.util.List;
import java.util.Optional;

public class TMForumBackedCatalogProtocolService implements CatalogProtocolService {

    private final TMFEdcMapper tmfEdcMapper;
    private final ProductCatalogApiClient productCatalogApi;
    private final String participantId;

    public TMForumBackedCatalogProtocolService(TMFEdcMapper tmfEdcMapper, ProductCatalogApiClient productCatalogApi, String participantId) {
        this.tmfEdcMapper = tmfEdcMapper;
        this.productCatalogApi = productCatalogApi;
        this.participantId = participantId;
    }

    @Override
    public @NotNull ServiceResult<Catalog> getCatalog(CatalogRequestMessage catalogRequestMessage, TokenRepresentation tokenRepresentation) {
        List<ExtendableProductOffering> productOfferingVOList = productCatalogApi.getProductOfferings(catalogRequestMessage.getQuerySpec().getOffset(), catalogRequestMessage.getQuerySpec().getLimit());
        Catalog.Builder catalogBuilder = Catalog.Builder.newInstance();
        catalogBuilder.participantId(participantId);

        productOfferingVOList
                .stream()
                .map(this::getProductSpec)
                .map(tmfEdcMapper::getDataService)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(catalogBuilder::dataService);
        productOfferingVOList
                .stream()
                .map(po -> tmfEdcMapper.datasetFromProductOffering(po, getProductSpec(po)))
                .forEach(catalogBuilder::dataset);
        return ServiceResult.success(catalogBuilder.build());

    }

    @Override
    public @NotNull ServiceResult<Dataset> getDataset(String datasetId, TokenRepresentation tokenRepresentation, String protocol) {

        return productCatalogApi.getProductOfferingByExternalId(datasetId)
                .map(po -> tmfEdcMapper.datasetFromProductOffering(po, getProductSpec(po)))
                .map(ServiceResult::success)
                .orElse(ServiceResult.notFound(String.format("No dataset with id %s exists.", datasetId)));
    }

    private Optional<ExtendableProductSpecification> getProductSpec(ExtendableProductOffering offeringVO) {
        if (offeringVO.getExtendableProductSpecification() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(productCatalogApi.getProductSpecification(offeringVO.getExtendableProductSpecification().getId()));
    }

}
