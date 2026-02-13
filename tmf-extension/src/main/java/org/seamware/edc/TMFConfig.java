package org.seamware.edc;

import lombok.EqualsAndHashCode;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.system.configuration.Config;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Configuration of the TMForum Extension.
 */
@EqualsAndHashCode
public class TMFConfig {

    private static final String TMF_EXTENSION_CONFIG_PATH = "tmfExtension";
    private static final String TMF_EXTENSION_CATALOG_CONFIG_PATH = "tmfExtension.catalog";
    private static final URI DEFAULT_SCHEMA_BASE_URI = URI.create("https://raw.githubusercontent.com/wistefan/edc-dsc/refs/heads/init/schemas/");

    // should storage in TMForum be enabled
    private boolean enabled;
    // address of the TMForum Quote API
    private URL quoteApi;
    // address of the TMForum Agreement API
    private URL agreementApi;
    // address of the TMForum Product Order API
    private URL productOrderApi;
    // address of the TMForum Product Catalog API
    private URL productCatalogApi;
    // address of the TMForum Product Inventory API
    private URL productInventoryApi;
    // address of the TMForum Usage Management API
    private URL usageManagementApi;
    // address of the TMForum Party Catalog API
    private URL partyCatalogApi;
    // base uri for the json-schemas to be used when extending the TMForum objects.
    private URI schemaBaseUri;

    // configuration for the catalog endpoint
    private CatalogConfig catalogConfig;

    public boolean isEnabled() {
        return enabled;
    }

    public URL getQuoteApi() {
        return quoteApi;
    }

    public URL getAgreementApi() {
        return agreementApi;
    }

    public URL getProductOrderApi() {
        return productOrderApi;
    }

    public URL getProductCatalogApi() {
        return productCatalogApi;
    }

    public URL getProductInventoryApi() {
        return productInventoryApi;
    }

    public URL getUsageManagementApi() {
        return usageManagementApi;
    }

    public URL getPartyCatalogApi() {
        return partyCatalogApi;
    }

    public URI getSchemaBaseUri() {
        return schemaBaseUri;
    }

    public CatalogConfig getCatalogConfig() {
        return catalogConfig;
    }

    public static TMFConfig fromConfig(Config config) {
        Config tmfExtensionConfig = config.getConfig(TMF_EXTENSION_CONFIG_PATH);

        TMFConfig.Builder tmfConfigBuilder = TMFConfig.Builder.newInstance();
        getNullSafeFromConfig(() -> tmfExtensionConfig.getBoolean("enabled")).ifPresent(tmfConfigBuilder::enabled);
        getNullSafeFromConfig(() -> tmfExtensionConfig.getString("quoteApi")).ifPresent(tmfConfigBuilder::quoteApi);
        getNullSafeFromConfig(() -> tmfExtensionConfig.getString("agreementApi")).ifPresent(tmfConfigBuilder::agreementApi);
        getNullSafeFromConfig(() -> tmfExtensionConfig.getString("productOrderApi")).ifPresent(tmfConfigBuilder::productOrderApi);
        getNullSafeFromConfig(() -> tmfExtensionConfig.getString("productCatalogApi")).ifPresent(tmfConfigBuilder::productCatalogApi);
        getNullSafeFromConfig(() -> tmfExtensionConfig.getString("productInventoryApi")).ifPresent(tmfConfigBuilder::productInventoryApi);
        getNullSafeFromConfig(() -> tmfExtensionConfig.getString("usageManagementApi")).ifPresent(tmfConfigBuilder::usageManagementApi);
        getNullSafeFromConfig(() -> tmfExtensionConfig.getString("partyCatalogApi")).ifPresent(tmfConfigBuilder::partyCatalogApi);
        getNullSafeFromConfig(() -> tmfExtensionConfig.getString("schemaBaseUri")).ifPresent(tmfConfigBuilder::schemaBaseUri);

        CatalogConfig.Builder catalogConfigBuilder = new CatalogConfig.Builder();
        Config catalogConfig = config.getConfig(TMF_EXTENSION_CATALOG_CONFIG_PATH);
        getNullSafeFromConfig(() -> catalogConfig.getBoolean("enabled")).ifPresent(catalogConfigBuilder::enabled);
        tmfConfigBuilder.catalogConfig(catalogConfigBuilder.build());

        return tmfConfigBuilder.build();
    }

    public static class Builder {
        private final TMFConfig tmfConfig;

        private Builder(TMFConfig tmfConfig) {
            this.tmfConfig = tmfConfig;
        }

        public static Builder newInstance() {
            return new Builder(new TMFConfig());
        }

        public Builder schemaBaseUri(String schemaBaseUri) {
            tmfConfig.schemaBaseUri = URI.create(schemaBaseUri);
            return this;
        }

        public Builder quoteApi(String quoteApi) {
            try {
                tmfConfig.quoteApi = URI.create(quoteApi).toURL();
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException(String.format("%s is not a valid url for the quote api.", quoteApi), e);
            }
            return this;
        }

        public Builder agreementApi(String agreementApi) {
            try {
                tmfConfig.agreementApi = URI.create(agreementApi).toURL();
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException(String.format("%s is not a valid url for the agreement api.", agreementApi), e);
            }
            return this;
        }

        public Builder productOrderApi(String productOrderApi) {
            try {
                tmfConfig.productOrderApi = URI.create(productOrderApi).toURL();
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException(String.format("%s is not a valid url for the productOrder api.", productOrderApi), e);
            }
            return this;
        }

        public Builder productCatalogApi(String productCatalogApi) {
            try {
                tmfConfig.productCatalogApi = URI.create(productCatalogApi).toURL();
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException(String.format("%s is not a valid url for the productCatalog api.", productCatalogApi), e);
            }
            return this;
        }

        public Builder productInventoryApi(String productInventoryApi) {
            try {
                tmfConfig.productInventoryApi = URI.create(productInventoryApi).toURL();
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException(String.format("%s is not a valid url for the productInventory api.", productInventoryApi), e);
            }
            return this;
        }

        public Builder usageManagementApi(String usageManagementApi) {
            try {
                tmfConfig.usageManagementApi = URI.create(usageManagementApi).toURL();
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException(String.format("%s is not a valid url for the usageManagement api.", usageManagementApi), e);
            }
            return this;
        }

        public Builder partyCatalogApi(String partyCatalogApi) {
            try {
                tmfConfig.partyCatalogApi = URI.create(partyCatalogApi).toURL();
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException(String.format("%s is not a valid url for the partyCatalog api.", partyCatalogApi), e);
            }
            return this;
        }

        public Builder catalogConfig(CatalogConfig catalogConfig) {
            tmfConfig.catalogConfig = catalogConfig;
            return this;
        }

        public Builder enabled(boolean enabled) {
            tmfConfig.enabled = enabled;
            return this;
        }

        public TMFConfig build() {
            if (tmfConfig.enabled) {
                Objects.requireNonNull(tmfConfig.quoteApi, "If TMFExtension is enabled, a valid quoteApi has to be provided.");
                Objects.requireNonNull(tmfConfig.agreementApi, "If TMFExtension is enabled, a valid agreementApi has to be provided.");
                Objects.requireNonNull(tmfConfig.productOrderApi, "If TMFExtension is enabled, a valid productOrderApi has to be provided.");
                Objects.requireNonNull(tmfConfig.productCatalogApi, "If TMFExtension is enabled, a valid productCatalogApi has to be provided.");
                Objects.requireNonNull(tmfConfig.productInventoryApi, "If TMFExtension is enabled, a valid productInventoryApi has to be provided.");
                Objects.requireNonNull(tmfConfig.usageManagementApi, "If TMFExtension is enabled, a valid usageManagementApi has to be provided.");
                Objects.requireNonNull(tmfConfig.partyCatalogApi, "If TMFExtension is enabled, a valid partyCatalogApi has to be provided.");
                Objects.requireNonNull(tmfConfig.catalogConfig, "If TMFExtension is enabled, a valid catalog config has to be provided.");
                if (tmfConfig.schemaBaseUri == null) {
                    tmfConfig.schemaBaseUri = DEFAULT_SCHEMA_BASE_URI;
                }
            }
            return tmfConfig;
        }
    }

    /**
     * @param enabled should the catalog contents be provided through TMForum
     */
    public record CatalogConfig(boolean enabled) {
        public static class Builder {

            private boolean enabled;

            public Builder enabled(boolean enabled) {
                this.enabled = enabled;
                return this;
            }

            public CatalogConfig build() {
                return new CatalogConfig(enabled);
            }
        }
    }

    private static <T> Optional<T> getNullSafeFromConfig(Supplier<T> fromConfig) {
        try {
            return Optional.of(fromConfig.get());
        } catch (EdcException e) {
            return Optional.empty();
        }
    }
}
