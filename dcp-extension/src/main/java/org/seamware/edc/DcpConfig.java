package org.seamware.edc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.system.configuration.Config;

import java.util.Optional;
import java.util.function.Supplier;

public class DcpConfig {

    private static final String DCP_CONFIG = "dcp";
    private static final String DCP_SCOPES_CONFIG = "dcp.scopes";
    private boolean enabled;
    private Scopes scopes;

    public boolean isEnabled() {
        return enabled;
    }

    public Scopes getScopes() {
        return scopes;
    }

    public static DcpConfig fromConfig(Config config) {
        Config dcpConfig = config.getConfig(DCP_CONFIG);
        DcpConfig.Builder dcpConfigBuilder = DcpConfig.Builder.newInstance();

        getNullSafeFromConfig(() -> dcpConfig.getBoolean("enabled")).ifPresent(dcpConfigBuilder::enabled);

        Config scopesConfig = config.getConfig(DCP_SCOPES_CONFIG);
        Scopes.Builder scopesBuilder = new Scopes.Builder();
        getNullSafeFromConfig(() -> scopesConfig.getString("catalog")).ifPresent(scopesBuilder::catalog);
        getNullSafeFromConfig(() -> scopesConfig.getString("negotiation")).ifPresent(scopesBuilder::negotiation);
        getNullSafeFromConfig(() -> scopesConfig.getString("transfer")).ifPresent(scopesBuilder::transfer);
        getNullSafeFromConfig(() -> scopesConfig.getString("version")).ifPresent(scopesBuilder::version);

        dcpConfigBuilder.scopes(scopesBuilder.build());
        return dcpConfigBuilder.build();
    }

    public record Scopes(String catalog, String negotiation, String transfer, String version) {
        public static class Builder {

            private String catalog;
            private String negotiation;
            private String transfer;
            private String version;

            public Builder catalog(String catalog) {
                this.catalog = catalog;
                return this;
            }

            public Builder negotiation(String negotiation) {
                this.negotiation = negotiation;
                return this;
            }

            public Builder transfer(String transfer) {
                this.transfer = transfer;
                return this;
            }

            public Builder version(String version) {
                this.version = version;
                return this;
            }

            public Scopes build() {
                if (catalog == null) {
                    catalog = "";
                }
                if (negotiation == null) {
                    negotiation = "";
                }
                if (transfer == null) {
                    transfer = "";
                }
                if (version == null) {
                    version = "";
                }
                return new Scopes(catalog, negotiation, transfer, version);
            }
        }
    }

    public static class Builder {

        private final DcpConfig dcpConfig;

        public Builder(DcpConfig dcpConfig) {
            this.dcpConfig = dcpConfig;
        }

        public static Builder newInstance() {
            return new Builder(new DcpConfig());
        }

        public Builder enabled(boolean enabled) {
            dcpConfig.enabled = enabled;
            return this;
        }

        public Builder scopes(Scopes scopes) {
            dcpConfig.scopes = scopes;
            return this;
        }

        public DcpConfig build() {
            return dcpConfig;
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
