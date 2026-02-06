package org.seamware.edc.store;

import org.eclipse.edc.connector.controlplane.catalog.spi.Catalog;
import org.eclipse.edc.connector.controlplane.catalog.spi.CatalogRequestMessage;
import org.eclipse.edc.connector.controlplane.catalog.spi.Dataset;
import org.eclipse.edc.connector.controlplane.services.spi.catalog.CatalogProtocolService;
import org.eclipse.edc.connector.controlplane.services.spi.protocol.ProtocolTokenValidator;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.policy.context.request.spi.RequestCatalogPolicyContext;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.ServiceResult;
import org.jetbrains.annotations.NotNull;
import org.seamware.edc.domain.ExtendableProductOffering;
import org.seamware.edc.domain.ExtendableProductSpecification;
import org.seamware.edc.tmf.ProductCatalogApiClient;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class TMForumBackedCatalogProtocolService implements CatalogProtocolService {

    private final TMFEdcMapper tmfEdcMapper;
    private final ProductCatalogApiClient productCatalogApi;
    private final String participantId;
    private final Monitor monitor;
    private final ProtocolTokenValidator protocolTokenValidator;

    public TMForumBackedCatalogProtocolService(TMFEdcMapper tmfEdcMapper, ProductCatalogApiClient productCatalogApi, String participantId, Monitor monitor, ProtocolTokenValidator protocolTokenValidator) {
        this.tmfEdcMapper = tmfEdcMapper;
        this.productCatalogApi = productCatalogApi;
        this.participantId = participantId;
        this.monitor = monitor;
        this.protocolTokenValidator = protocolTokenValidator;
    }

    @Override
    public @NotNull ServiceResult<Catalog> getCatalog(CatalogRequestMessage catalogRequestMessage, TokenRepresentation tokenRepresentation) {
        ServiceResult<ParticipantAgent> validatedToken = protocolTokenValidator.verify(tokenRepresentation, RequestCatalogPolicyContext::new, catalogRequestMessage);
        if (validatedToken.failed()) {
            return ServiceResult.unauthorized("Request not authorized.");
        }
        List<ExtendableProductOffering> productOfferingVOList = productCatalogApi.getProductOfferings(catalogRequestMessage.getQuerySpec().getOffset(), catalogRequestMessage.getQuerySpec().getLimit());
        Catalog.Builder catalogBuilder = Catalog.Builder.newInstance();
        catalogBuilder.participantId(participantId);

        productOfferingVOList
                .stream()
                .map(this::getProductSpec)
                .map(tmfEdcMapper::getDataService)
                .flatMap(List::stream)
                .forEach(catalogBuilder::dataService);
        productOfferingVOList
                .stream()
                .map(po -> {
                    try {
                        return tmfEdcMapper.datasetFromProductOffering(po, getProductSpec(po));
                    } catch (RuntimeException e) {
                        monitor.info("Offering does not support DSP.", e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
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
