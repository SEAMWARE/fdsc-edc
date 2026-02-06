package org.seamware.edc.store;

import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.asset.spi.index.AssetIndex;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.seamware.edc.domain.ExtendableProductOffering;
import org.seamware.edc.domain.ExtendableProductSpecification;
import org.seamware.edc.tmf.ProductCatalogApiClient;
import org.seamware.tmforum.productcatalog.model.ProductOfferingVO;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class TMFBackedAssetIndex implements AssetIndex {

    private final Monitor monitor;
    private final ProductCatalogApiClient productCatalogApiClient;
    private final TMFEdcMapper tmfEdcMapper;
    private final CriterionOperatorRegistry criterionOperatorRegistry;

    public TMFBackedAssetIndex(Monitor monitor, ProductCatalogApiClient productCatalogApiClient, TMFEdcMapper tmfEdcMapper, CriterionOperatorRegistry criterionOperatorRegistry) {
        this.monitor = monitor;
        this.productCatalogApiClient = productCatalogApiClient;
        this.tmfEdcMapper = tmfEdcMapper;
        this.criterionOperatorRegistry = criterionOperatorRegistry;
    }

    @Override
    public Stream<Asset> queryAssets(QuerySpec querySpec) {
        // TODO: implement
        throw new UnsupportedOperationException("Querying for assets currently is not supported");
    }

    @Override
    public Asset findById(String s) {
        return productCatalogApiClient
                .getProductSpecByExternalId(s)
                .map(tmfEdcMapper::assetFromProductSpec)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .orElseThrow(() -> new IllegalArgumentException(String.format("Asset %s does not exist.", s)));
    }

    private Optional<ExtendableProductSpecification> getProductSpec(ExtendableProductOffering offeringVO) {
        if (offeringVO.getExtendableProductSpecification() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(productCatalogApiClient.getProductSpecification(offeringVO.getExtendableProductSpecification().getId()));
    }

    @Override
    public StoreResult<Void> create(Asset asset) {
        // TODO: implement
        throw new UnsupportedOperationException();
    }

    @Override
    public StoreResult<Asset> deleteById(String s) {
        // TODO: implement
        throw new UnsupportedOperationException();
    }

    @Override
    public long countAssets(List<Criterion> criteria) {

        Predicate<Asset> predicate = criteria.stream()
                .map(criterionOperatorRegistry::<Asset>toPredicate)
                .reduce(x -> true, Predicate::and);

        List<Asset> assets = new ArrayList<>();
        int offset = 0;
        boolean moreOfferingsAvailable = true;
        while (moreOfferingsAvailable) {
            List<ExtendableProductSpecification> productSpecifications = productCatalogApiClient.getProductSpecifications(offset, 100);


            productSpecifications.stream()
                    .map(tmfEdcMapper::assetFromProductSpec)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .filter(predicate)
                    .findAny()
                    .ifPresent(assets::add);
            moreOfferingsAvailable = productSpecifications.size() == 100;
            offset += 100;
        }
        return assets.size();
    }

    @Override
    public StoreResult<Asset> updateAsset(Asset asset) {
        // TODO: implement
        throw new UnsupportedOperationException();
    }

    @Override
    public DataAddress resolveForAsset(String s) {
        return Optional.ofNullable(findById(s))
                .map(Asset::getDataAddress)
                .orElseThrow(() -> new IllegalArgumentException(String.format("Was not able to resolve asset %s to data address.", s)));
    }
}
