package org.seamware.edc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TMFConfigTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @ParameterizedTest(name = "Config from {0}")
    @MethodSource("getValidConfig")
    public void testValidConfig(String testFile, TMFConfig expectedConfig) throws IOException {
        Config testConfig = fromFile(testFile);
        assertEquals(TMFConfig.fromConfig(testConfig), expectedConfig, "The config should have successfully been read.");
    }

    @ParameterizedTest(name = "Config from {0}")
    @MethodSource("getInvalidConfig")
    public void testInvalidConfig(String testFile, String expectedMessage) throws IOException {
        Config testConfig = fromFile(testFile);
        var e = assertThrows(NullPointerException.class, () -> TMFConfig.fromConfig(testConfig));
        assertEquals(expectedMessage, e.getMessage());
    }

    private static Stream<Arguments> getInvalidConfig() {
        return Stream.of(
                Arguments.of("invalid/1.properties", "If TMFExtension is enabled, a valid quoteApi has to be provided."),
                Arguments.of("invalid/2.properties", "If TMFExtension is enabled, a valid agreementApi has to be provided."),
                Arguments.of("invalid/3.properties", "If TMFExtension is enabled, a valid productOrderApi has to be provided."),
                Arguments.of("invalid/4.properties", "If TMFExtension is enabled, a valid productCatalogApi has to be provided."),
                Arguments.of("invalid/5.properties", "If TMFExtension is enabled, a valid productInventoryApi has to be provided."),
                Arguments.of("invalid/6.properties", "If TMFExtension is enabled, a valid usageManagementApi has to be provided."),
                Arguments.of("invalid/7.properties", "If TMFExtension is enabled, a valid partyCatalogApi has to be provided.")
        );
    }

    private static Stream<Arguments> getValidConfig() throws IOException {
        List<Arguments> arguments = new ArrayList<>();
        TMFConfig tmfConfig1 = TMFConfig.Builder.newInstance()
                .enabled(true)
                .quoteApi("http://quote-api.de")
                .agreementApi("http://agreement-api.de")
                .productOrderApi("http://product-order-api.de")
                .productCatalogApi("http://product-catalog-api.de")
                .productInventoryApi("http://product-inventory-api.de")
                .usageManagementApi("http://usage-management-api.de")
                .partyCatalogApi("http://party-catalog-api.de")
                .schemaBaseUri("http://schema-base-api.de")
                .catalogConfig(new TMFConfig.CatalogConfig.Builder().enabled(true).build())
                .build();
        arguments.add(Arguments.of("valid/1.properties", tmfConfig1));

        TMFConfig tmfConfig2 = TMFConfig.Builder.newInstance()
                .enabled(true)
                .quoteApi("http://quote-api.de")
                .agreementApi("http://agreement-api.de")
                .productOrderApi("http://product-order-api.de")
                .productCatalogApi("http://product-catalog-api.de")
                .productInventoryApi("http://product-inventory-api.de")
                .usageManagementApi("http://usage-management-api.de")
                .partyCatalogApi("http://party-catalog-api.de")
                .schemaBaseUri("http://schema-base-api.de")
                .catalogConfig(new TMFConfig.CatalogConfig.Builder().enabled(false).build())
                .build();
        arguments.add(Arguments.of("valid/2.properties", tmfConfig2));

        TMFConfig tmfConfig3 = TMFConfig.Builder.newInstance()
                .enabled(false)
                .catalogConfig(new TMFConfig.CatalogConfig.Builder().enabled(false).build())
                .build();
        arguments.add(Arguments.of("valid/3.properties", tmfConfig3));

        TMFConfig tmfConfig4 = TMFConfig.Builder.newInstance()
                .enabled(true)
                .quoteApi("http://quote-api.de")
                .agreementApi("http://agreement-api.de")
                .productOrderApi("http://product-order-api.de")
                .productCatalogApi("http://product-catalog-api.de")
                .productInventoryApi("http://product-inventory-api.de")
                .usageManagementApi("http://usage-management-api.de")
                .partyCatalogApi("http://party-catalog-api.de")
                .schemaBaseUri("https://raw.githubusercontent.com/wistefan/edc-dsc/refs/heads/init/schemas/")
                .catalogConfig(new TMFConfig.CatalogConfig.Builder().enabled(true).build())
                .build();
        arguments.add(Arguments.of("valid/4.properties", tmfConfig4));

        return arguments.stream();
    }

    public static Config fromFile(String file) throws IOException {
        Properties properties = new Properties();
        properties.load(TMFConfigTest.class.getClassLoader().getResourceAsStream(file));
        return ConfigFactory.fromProperties(properties);
    }
}