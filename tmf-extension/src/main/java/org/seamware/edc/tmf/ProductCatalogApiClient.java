package org.seamware.edc.tmf;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.eclipse.edc.connector.controlplane.contract.spi.ContractOfferId;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.monitor.Monitor;
import org.seamware.edc.SchemaBaseUriHolder;
import org.seamware.edc.domain.ExtendableProductOffering;
import org.seamware.edc.domain.ExtendableProductOfferingTerm;
import org.seamware.edc.domain.ExtendableProductSpecification;
import org.seamware.edc.store.ContractOfferIdParser;
import org.seamware.edc.store.TMFEdcMapper;
import org.seamware.tmforum.productcatalog.model.ProductSpecificationVO;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.seamware.edc.domain.ExtendableProduct.EXTERNAL_ID_SCHEMA;
import static org.seamware.edc.store.TMFEdcMapper.*;

/**
 * Client implementation to interact with the TMForum Product Catalog API
 */
public class ProductCatalogApiClient extends ApiClient {

    private static final String PRODUCT_OFFERING_ID_PREFIX = "urn:ngsi-ld:product-offering";
    private static final String PRODUCT_OFFERING_PATH = "productOffering";
    private static final String PRODUCT_SPECIFICATION_PATH = "productSpecification";

    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final TMFEdcMapper tmfEdcMapper;

    public ProductCatalogApiClient(Monitor monitor, OkHttpClient okHttpClient, String baseUrl, ObjectMapper objectMapper, TMFEdcMapper tmfEdcMapper) {
        super(monitor, okHttpClient);
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
        this.tmfEdcMapper = tmfEdcMapper;
    }

    /**
     * Get all product offerings, supporting pagination
     */
    public List<ExtendableProductOffering> getProductOfferings(int offset, int limit) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        urlBuilder.addPathSegment(PRODUCT_OFFERING_PATH);
        urlBuilder.addQueryParameter(OFFSET_PARAM, String.valueOf(offset));
        urlBuilder.addQueryParameter(LIMIT_PARAM, String.valueOf(limit));
        Request request = new Request.Builder().url(urlBuilder.build()).build();
        try (ResponseBody responseBody = executeRequest(request)) {
            return objectMapper
                    .readValue(responseBody.bytes(), new TypeReference<>() {
                    });
        } catch (IOException e) {
            throw new IllegalArgumentException("Was not able to get product offerings.", e);
        }
    }

    /**
     * Get all product specifications, supporting pagination
     */
    public List<ExtendableProductSpecification> getProductSpecifications(int offset, int limit) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        urlBuilder.addPathSegment(PRODUCT_SPECIFICATION_PATH);
        urlBuilder.addQueryParameter(OFFSET_PARAM, String.valueOf(offset));
        urlBuilder.addQueryParameter(LIMIT_PARAM, String.valueOf(limit));
        Request request = new Request.Builder().url(urlBuilder.build()).build();
        try (ResponseBody responseBody = executeRequest(request)) {
            return objectMapper
                    .readValue(responseBody.bytes(), new TypeReference<List<ExtendableProductSpecification>>() {
                    });
        } catch (IOException e) {
            throw new IllegalArgumentException("Was not able to get product specifications.", e);
        }
    }

    /**
     * Get the product specification by its id
     */
    public ExtendableProductSpecification getProductSpecification(String specId) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        urlBuilder.addPathSegment(PRODUCT_SPECIFICATION_PATH);
        urlBuilder.addPathSegment(specId);
        Request request = new Request.Builder().url(urlBuilder.build()).build();
        try (ResponseBody responseBody = executeRequest(request)) {
            return objectMapper
                    .readValue(responseBody.bytes(), ExtendableProductSpecification.class);
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Was not able to get product specification %s.", specId), e);
        }
    }


    /**
     * Get the policy by its id. Policies are stored inside product-offerings
     */
    public Policy getByPolicyId(String policyId) {

        Optional<Policy> optionalPolicy = Optional.empty();
        boolean posAvailable = true;
        int offset = 0;
        while (optionalPolicy.isEmpty() && posAvailable) {
            HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
            urlBuilder.addPathSegment(PRODUCT_OFFERING_PATH);
            urlBuilder.addQueryParameter("productOfferingTerm.name", "edc:contractDefinition");
            urlBuilder.addQueryParameter("offset", String.valueOf(offset));
            urlBuilder.addQueryParameter("limit", "100");

            Request request = new Request.Builder().url(urlBuilder.build()).build();
            try (ResponseBody responseBody = executeRequest(request)) {
                List<ExtendableProductOffering> extendableProductOfferings = objectMapper.readValue(responseBody.bytes(), new TypeReference<>() {
                });
                optionalPolicy = extendableProductOfferings
                        .stream()
                        .map(ExtendableProductOffering::getExtendableProductOfferingTerm)
                        .flatMap(List::stream)
                        .filter(extendableProductOfferingTerm -> extendableProductOfferingTerm.getName().equals(POT_NAME_CONTRACT_DEFINITION))
                        .map(epot -> getFromOfferingTerm(epot, policyId).orElse(null))
                        .filter(Objects::nonNull)
                        .findAny();
                posAvailable = extendableProductOfferings.size() == 100;
                offset += 100;
            } catch (IOException e) {
                throw new IllegalArgumentException("Was not able to get product offerings.", e);
            }
        }
        if (optionalPolicy.isEmpty()) {
            throw new IllegalArgumentException("No policy found.");
        }
        return optionalPolicy.get();
    }

    private Optional<Policy> getFromOfferingTerm(ExtendableProductOfferingTerm extendableProductOfferingTerm, String policyId) {
        Optional<Policy> optionalContractPolicy = getPolicyByIdAndType(extendableProductOfferingTerm, policyId, CONTRACT_POLICY_KEY);
        if (optionalContractPolicy.isPresent()) {
            return optionalContractPolicy;
        }

        return getPolicyByIdAndType(extendableProductOfferingTerm, policyId, ACCESS_POLICY_KEY);
    }

    private Optional<Policy> getPolicyByIdAndType(ExtendableProductOfferingTerm extendableProductOfferingTerm, String policyId, String policyType) {
        return Optional.ofNullable(extendableProductOfferingTerm.getAdditionalProperties())
                .map(ap -> ap.get(policyType))
                .map(tmfEdcMapper::fromOdrl)
                .map(p -> {
                    if (Optional.ofNullable(p.getExtensibleProperties())
                            .map(eP -> eP.get(UID_KEY))
                            .filter(uid -> uid.equals(policyId))
                            .isPresent()) {
                        return p;
                    } else {
                        return null;
                    }
                });
    }

    /**
     * Return a product specification by its external id(the asset-id)
     */
    public Optional<ExtendableProductSpecification> getProductSpecByExternalId(String externalId) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        urlBuilder.addPathSegment(PRODUCT_SPECIFICATION_PATH);
        urlBuilder.addQueryParameter("externalId", externalId);
        Request request = new Request.Builder().url(urlBuilder.build()).build();
        try (ResponseBody responseBody = executeRequest(request)) {
            List<ExtendableProductSpecification> productSpecificationVOS = objectMapper.readValue(responseBody.bytes(), new TypeReference<List<ExtendableProductSpecification>>() {
            });
            if (productSpecificationVOS.size() > 1) {
                throw new IllegalArgumentException(String.format("Multiple specifications for id %s exist. External Ids need to be unique.", externalId));
            }
            if (productSpecificationVOS.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(productSpecificationVOS.getFirst());
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Was not able to get specifications for external id %s", externalId), e);
        }
    }


    /**
     * Return a product offering by its the asset-id. The asset-id is embedded in the externalId(the offering-id) and therefor all offerings need to be checked.
     */
    public Optional<ExtendableProductOffering> getProductOfferingByAssetId(String assetId) {
        monitor.info("Get offering for asset id " + assetId);
        boolean offeringsAvailable = true;
        int offset = 0;
        while (offeringsAvailable) {

            HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
            urlBuilder.addPathSegment(PRODUCT_OFFERING_PATH);
            urlBuilder.addQueryParameter("atSchemaLocation", SchemaBaseUriHolder.get().resolve(EXTERNAL_ID_SCHEMA).toString());
            urlBuilder.addQueryParameter(LIMIT_PARAM, "100");
            urlBuilder.addQueryParameter(OFFSET_PARAM, String.valueOf(offset));
            Request request = new Request.Builder().url(urlBuilder.build()).build();
            try (ResponseBody responseBody = executeRequest(request)) {
                List<ExtendableProductOffering> extendableProductOfferings = objectMapper.readValue(responseBody.bytes(), new TypeReference<List<ExtendableProductOffering>>() {
                });
                Optional<ExtendableProductOffering> optionalExtendableProductOffering = extendableProductOfferings
                        .stream()
                        .filter(extendableProductOffering -> ContractOfferIdParser.parseId(extendableProductOffering.getExternalId())
                                .asOptional()
                                .map(ContractOfferIdParser.ContractOfferWithUid::contractOfferId)
                                .map(ContractOfferId::assetIdPart)
                                .orElse("invalid").equals(assetId))
                        .findFirst();
                if (optionalExtendableProductOffering.isPresent()) {
                    return optionalExtendableProductOffering;
                }
                offeringsAvailable = extendableProductOfferings.size() == 100;
                offset += 100;
            } catch (
                    IOException e) {
                throw new IllegalArgumentException("Failed to fetch offerings", e);
            }
        }
        return Optional.empty();
    }

    /**
     * Return an offering by its external id(offering-id)
     */
    public Optional<ExtendableProductOffering> getProductOfferingByExternalId(String externalId) {
        monitor.info("Get offering for external id " + externalId);
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        urlBuilder.addPathSegment(PRODUCT_OFFERING_PATH);
        urlBuilder.addQueryParameter("externalId", externalId);
        Request request = new Request.Builder().url(urlBuilder.build()).build();
        try (ResponseBody responseBody = executeRequest(request)) {
            List<ExtendableProductOffering> extendableProductOfferings = objectMapper.readValue(responseBody.bytes(), new TypeReference<List<ExtendableProductOffering>>() {
            });
            if (extendableProductOfferings.size() > 1) {
                throw new IllegalArgumentException(String.format("Multiple offerings for id %s exist. External Ids need to be unique.", externalId));
            }
            if (extendableProductOfferings.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(extendableProductOfferings.getFirst());
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Was not able to get offering for external id %s", externalId), e);
        }
    }

}
