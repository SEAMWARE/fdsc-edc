package org.seamware.edc.tmf;

import okhttp3.mockwebserver.RecordedRequest;
import org.eclipse.edc.policy.model.*;
import org.eclipse.edc.web.spi.exception.BadGatewayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.seamware.edc.domain.ExtendableAgreementVO;
import org.seamware.edc.domain.ExtendableProductOffering;
import org.seamware.edc.domain.ExtendableProductOfferingTerm;
import org.seamware.edc.domain.ExtendableProductSpecification;
import org.seamware.edc.store.TMFEdcMapper;
import org.seamware.tmforum.productcatalog.model.ProductOfferingTermVO;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProductCatalogApiClientTest extends AbstractApiTest {

    private static final String TEST_SPEC_ID = "test-spec";
    private static final String TEST_ASSET_ID = "test-asset";
    private static final String TEST_OFFER_ID = "test-offer";

    private TMFEdcMapper tmfEdcMapper;
    private ProductCatalogApiClient productCatalogApiClient;

    @Override
    public void setupConcreteClient(String baseUrl) {
        tmfEdcMapper = mock(TMFEdcMapper.class);
        productCatalogApiClient = new ProductCatalogApiClient(monitor, okHttpClient, baseUrl, objectMapper, tmfEdcMapper);
    }

    @Test
    public void testGetProductOfferings_success() throws Exception {
        List<ExtendableProductOffering> testOfferings = getOfferings(5);
        mockResponse(200, testOfferings);

        assertEquals(testOfferings, productCatalogApiClient.getProductOfferings(0, 5), "All offering should have been returned.");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("/productOffering?offset=0&limit=5", recordedRequest.getPath());
    }

    @Test
    public void testGetProductOfferings_success_empty() throws Exception {
        mockResponse(200, List.of());

        assertEquals(List.of(), productCatalogApiClient.getProductOfferings(0, 5), "All offering should have been returned.");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("/productOffering?offset=0&limit=5", recordedRequest.getPath());
    }

    @Test
    public void testGetProductOfferings_invalid_content() throws Exception {

        mockResponse(200, "invalid");

        assertThrows(BadGatewayException.class, () -> productCatalogApiClient.getProductOfferings(0, 5),
                "If the server returns something invalid, a BadGateWay should be thrown.");
    }

    @ParameterizedTest(name = "Failure code {0}")
    @ValueSource(ints = {400, 401, 403, 404, 500})
    public void testGetProductOfferings_bad_response(int responseCode) throws Exception {
        mockResponse(responseCode);
        assertThrows(BadGatewayException.class, () -> productCatalogApiClient.getProductOfferings(0, 5), "If the server returns something unsuccessful, a BadGateWay should be thrown.");
    }

    @Test
    public void testGetProductSpecifications_success() throws Exception {
        List<ExtendableProductSpecification> testSpecs = getSpecs(5);
        mockResponse(200, testSpecs);

        assertEquals(testSpecs, productCatalogApiClient.getProductSpecifications(0, 5), "All specs should have been returned.");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("/productSpecification?offset=0&limit=5", recordedRequest.getPath());
    }

    @Test
    public void testGetProductSpecifications_success_empty() throws Exception {
        mockResponse(200, List.of());
        assertEquals(List.of(), productCatalogApiClient.getProductSpecifications(0, 5), "All specs should have been returned.");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("/productSpecification?offset=0&limit=5", recordedRequest.getPath());
    }

    @Test
    public void testGetProductSpecifications_invalid_content() throws Exception {
        mockResponse(200, "invalid");
        assertThrows(BadGatewayException.class, () -> productCatalogApiClient.getProductSpecifications(0, 5),
                "If the server returns something invalid, a BadGateWay should be thrown.");
    }

    @ParameterizedTest(name = "Failure code {0}")
    @ValueSource(ints = {400, 401, 403, 404, 500})
    public void testGetProductSpecifications_bad_response(int responseCode) throws Exception {
        mockResponse(responseCode);
        assertThrows(BadGatewayException.class, () -> productCatalogApiClient.getProductSpecifications(0, 5),
                "If the server returns something unsuccessful, a BadGateWay should be thrown.");
    }

    @Test
    public void testGetProductSpecification_success() throws Exception {
        ExtendableProductSpecification testSpec = getProductSpecification();
        mockResponse(200, testSpec);

        assertEquals(testSpec, productCatalogApiClient.getProductSpecification(TEST_SPEC_ID), "The spec should have been returned.");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("/productSpecification/" + TEST_SPEC_ID, recordedRequest.getPath());
    }

    @Test
    public void testGetProductSpecification_invalid_content() throws Exception {
        mockResponse(200, "invalid");
        assertThrows(BadGatewayException.class, () -> productCatalogApiClient.getProductSpecification(TEST_SPEC_ID),
                "If the server returns something invalid, a BadGateWay should be thrown.");
    }

    @ParameterizedTest(name = "Failure code {0}")
    @ValueSource(ints = {400, 401, 403, 404, 500})
    public void testGetProductSpecification_bad_response(int responseCode) throws Exception {
        mockResponse(responseCode);
        assertThrows(BadGatewayException.class, () -> productCatalogApiClient.getProductSpecification(TEST_SPEC_ID),
                "If the server returns something unsuccessful, a BadGateWay should be thrown.");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("getValidPolicyConfigs")
    public void testGetByPolicyId_success(String name, List<ExtendableProductOffering> mockOfferings, String requestedPolicyId, Optional<Policy> expectedPolicy) throws Exception {

        when(tmfEdcMapper.fromOdrl(any())).thenAnswer(invocation -> {
            Map<String, Object> policyMap = invocation.getArgument(0);
            Object policyId = ((Map<String, Object>) policyMap.get("extensibleProperties")).get("http://www.w3.org/ns/odrl/2/uid");
            if (policyId.equals(requestedPolicyId)) {
                return expectedPolicy.get();
            }
            return null;
        });

        mockResponse(200, mockOfferings);

        Optional<Policy> returnedPolicy = productCatalogApiClient.getByPolicyId(requestedPolicyId);
        assertEquals(expectedPolicy.isPresent(), returnedPolicy.isPresent());
        if (expectedPolicy.isPresent()) {
            assertEquals(expectedPolicy.get(), returnedPolicy.get(), "The correct policy should be returned.");
        }
    }

    private static Stream<Arguments> getValidPolicyConfigs() {
        return Stream.of(
                Arguments.of(
                        "The policy should correctly be returned.",
                        List.of(getOfferingWithPolicy(new ContractDefintion(getTestPolicy("access"), getTestPolicy("contract")))),
                        "access",
                        Optional.of(getTestPolicy("access"))
                ),
                Arguments.of(
                        "The policy should correctly be returned, when multiple offers exist.",
                        List.of(getOfferingWithPolicy(new ContractDefintion(getTestPolicy("other-access"), getTestPolicy("other-contract"))), getOfferingWithPolicy(new ContractDefintion(getTestPolicy("access"), getTestPolicy("contract")))),
                        "access",
                        Optional.of(getTestPolicy("access"))
                ),
                Arguments.of(
                        "If no offers exist, no policy should be returned.",
                        List.of(),
                        "access",
                        Optional.empty()
                ),
                Arguments.of(
                        "If no such policy exists, nothing should be returned.",
                        List.of(getOfferingWithPolicy(new ContractDefintion(getTestPolicy("other-access"), getTestPolicy("other-contract")))),
                        "access",
                        Optional.empty()
                ),
                Arguments.of(
                        "If no offers with policies exist, nothing should be returned.",
                        getOfferings(5),
                        "access",
                        Optional.empty()
                )
        );
    }


    @Test
    public void testGetByPolicyId_duplicate_id() throws Exception {
        Policy accessPolicy = getTestPolicy("access");
        Policy contractPolicy = getTestPolicy("contract");

        when(tmfEdcMapper.fromOdrl(any())).thenAnswer(invocation -> {
            Map<String, Object> policyMap = invocation.getArgument(0);
            Object policyId = ((Map<String, Object>) policyMap.get("extensibleProperties")).get("http://www.w3.org/ns/odrl/2/uid");
            if (policyId.equals("access")) {
                return accessPolicy;
            }
            if (policyId.equals("contract")) {
                return contractPolicy;
            }
            return null;
        });

        mockResponse(200, List.of(
                getOfferingWithPolicy(new ContractDefintion(accessPolicy, contractPolicy)),
                getOfferingWithPolicy(new ContractDefintion(accessPolicy, getTestPolicy("else")))));


        assertThrows(BadGatewayException.class, () -> productCatalogApiClient.getByPolicyId("access"),
                "If a duplicate id is received, a BadGateWay should be thrown.");
    }

    @Test
    public void testGetByPolicyId_invalid_content() throws Exception {
        mockResponse(200, "invalid");
        assertThrows(BadGatewayException.class, () -> productCatalogApiClient.getByPolicyId("test-policy"),
                "If the server returns something invalid, a BadGateWay should be thrown.");
    }

    @ParameterizedTest(name = "Failure code {0}")
    @ValueSource(ints = {400, 401, 403, 404, 500})
    public void testGetByPolicyId_bad_response(int responseCode) throws Exception {
        mockResponse(responseCode);
        assertThrows(BadGatewayException.class, () -> productCatalogApiClient.getByPolicyId("test-policy"),
                "If the server returns something unsuccessful, a BadGateWay should be thrown.");
    }

    @Test
    public void testGetProductSpecByExternalId_success() throws Exception {
        ExtendableProductSpecification testSpec = getProductSpecification(TEST_ASSET_ID);
        mockResponse(200, List.of(testSpec));

        assertEquals(testSpec, productCatalogApiClient.getProductSpecByExternalId(TEST_ASSET_ID).get(), "The correct spec should be returned.");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("/productSpecification?externalId=" + TEST_ASSET_ID, recordedRequest.getPath());
    }

    @Test
    public void testGetProductSpecByExternalId_success_no_spec() throws Exception {
        mockResponse(200, List.of());
        assertTrue(productCatalogApiClient.getProductSpecByExternalId(TEST_ASSET_ID).isEmpty(), "No spec should have been returned.");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("/productSpecification?externalId=" + TEST_ASSET_ID, recordedRequest.getPath());
    }

    @Test
    public void testGetProductSpecByExternalId_failure_to_many_specs() throws Exception {
        mockResponse(200, getSpecs(2));
        assertThrows(BadGatewayException.class, () -> productCatalogApiClient.getProductSpecByExternalId(TEST_ASSET_ID), "If the server returns something invalid, a BadGateWay should be thrown.");
    }


    @Test
    public void testGetProductSpecByExternalId_invalid_content() throws Exception {
        mockResponse(200, "invalid");
        assertThrows(BadGatewayException.class, () -> productCatalogApiClient.getProductSpecByExternalId(TEST_ASSET_ID), "If the server returns something invalid, a BadGateWay should be thrown.");
    }

    @ParameterizedTest(name = "Failure code {0}")
    @ValueSource(ints = {400, 401, 403, 404, 500})
    public void testGetProductSpecByExternalId_bad_response(int responseCode) throws Exception {
        mockResponse(responseCode);
        assertThrows(BadGatewayException.class, () -> productCatalogApiClient.getProductSpecByExternalId(TEST_ASSET_ID), "If the server returns something unsuccessful, a BadGateWay should be thrown.");
    }

    @Test
    public void testGetProductOfferingByExternalId_success() throws Exception {
        ExtendableProductOffering testOffering = getProductOffering(TEST_OFFER_ID);
        mockResponse(200, List.of(testOffering));

        assertEquals(testOffering, productCatalogApiClient.getProductOfferingByExternalId(TEST_ASSET_ID).get(), "The correct offer should be returned.");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("/productOffering?externalId=" + TEST_ASSET_ID, recordedRequest.getPath());
    }

    @Test
    public void testGetProductOfferingByExternalId_success_no_spec() throws Exception {
        mockResponse(200, List.of());
        assertTrue(productCatalogApiClient.getProductOfferingByExternalId(TEST_ASSET_ID).isEmpty(), "No offer should have been returned.");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("/productOffering?externalId=" + TEST_ASSET_ID, recordedRequest.getPath());
    }

    @Test
    public void testGetProductOfferingByExternalId_failure_to_many_offers() throws Exception {
        mockResponse(200, getOfferings(2));
        assertThrows(BadGatewayException.class, () -> productCatalogApiClient.getProductOfferingByExternalId(TEST_ASSET_ID), "If the server returns something invalid, a BadGateWay should be thrown.");
    }

    @Test
    public void testGetProductOfferingByExternalId_invalid_content() throws Exception {
        mockResponse(200, "invalid");
        assertThrows(BadGatewayException.class, () -> productCatalogApiClient.getProductOfferingByExternalId(TEST_ASSET_ID), "If the server returns something invalid, a BadGateWay should be thrown.");
    }

    @ParameterizedTest(name = "Failure code {0}")
    @ValueSource(ints = {400, 401, 403, 404, 500})
    public void testGetProductOfferingByExternalId_bad_response(int responseCode) throws Exception {
        mockResponse(responseCode);
        assertThrows(BadGatewayException.class, () -> productCatalogApiClient.getProductOfferingByExternalId(TEST_ASSET_ID), "If the server returns something unsuccessful, a BadGateWay should be thrown.");
    }

    private static List<ExtendableProductOffering> getOfferings(int num) {
        List<ExtendableProductOffering> extendableProductOfferings = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            extendableProductOfferings.add(getProductOffering());
        }
        return extendableProductOfferings;
    }

    private static List<ExtendableProductSpecification> getSpecs(int num) {
        List<ExtendableProductSpecification> extendableProductSpecifications = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            extendableProductSpecifications.add(getProductSpecification());
        }
        return extendableProductSpecifications;
    }

    private static ExtendableProductOffering getProductOffering() {
        ExtendableProductOffering extendableProductOffering = new ExtendableProductOffering();
        extendableProductOffering.setAtSchemaLocation(URI.create("http://base.uri/external-id.json"));
        return extendableProductOffering;
    }

    private static ExtendableProductOffering getProductOffering(String externalId) {
        ExtendableProductOffering extendableProductOffering = new ExtendableProductOffering();
        extendableProductOffering.setAtSchemaLocation(URI.create("http://base.uri/external-id.json"));
        extendableProductOffering.setExternalId(externalId);
        return extendableProductOffering;
    }

    private static ExtendableProductSpecification getProductSpecification() {
        ExtendableProductSpecification extendableProductSpecification = new ExtendableProductSpecification();
        extendableProductSpecification.setAtSchemaLocation(URI.create("http://base.uri/external-id.json"));
        return extendableProductSpecification;
    }

    private static ExtendableProductSpecification getProductSpecification(String externalId) {
        ExtendableProductSpecification extendableProductSpecification = new ExtendableProductSpecification();
        extendableProductSpecification.setAtSchemaLocation(URI.create("http://base.uri/external-id.json"));
        extendableProductSpecification.setExternalId(externalId);
        return extendableProductSpecification;
    }

    protected static ExtendableProductOffering getOfferingWithPolicy(ContractDefintion contractDefintion) {
        ExtendableProductOffering extendableProductOffering = getProductOffering();

        ExtendableProductOfferingTerm extendableProductOfferingTerm = new ExtendableProductOfferingTerm();
        extendableProductOfferingTerm.setAtSchemaLocation(URI.create("http://base.uri/contract-definition.json"));
        extendableProductOfferingTerm.setName("edc:contractDefinition");
        extendableProductOfferingTerm.setAdditionalProperties("accessPolicy", contractDefintion.accessPolicy());
        extendableProductOfferingTerm.setAdditionalProperties("contractPolicy", contractDefintion.contractPolicy());
        extendableProductOffering.setExtendableProductOfferingTerm(List.of(extendableProductOfferingTerm));
        return extendableProductOffering;
    }

    private record ContractDefintion(Policy accessPolicy, Policy contractPolicy) {
    }

    protected static Policy getTestPolicy(String id) {
        return Policy.Builder.newInstance()
                .type(PolicyType.CONTRACT)
                .assigner("assigner")
                .assignee("assignee")
                .permission(getTestPermission())
                .extensibleProperty("http://www.w3.org/ns/odrl/2/uid", id)
                .build();
    }

    protected static Permission getTestPermission() {
        return Permission.Builder.newInstance()
                .action(getUse())
                .constraint(getTestConstraint())
                .build();
    }

    protected static Constraint getTestConstraint() {
        return AtomicConstraint.Builder.newInstance()
                .leftExpression(new LiteralExpression("odrl:dayOfWeek"))
                .operator(Operator.EQ)
                .rightExpression(new LiteralExpression(6))
                .build();
    }


    protected static Action getUse() {
        return Action.Builder.newInstance().type("use").build();
    }
}