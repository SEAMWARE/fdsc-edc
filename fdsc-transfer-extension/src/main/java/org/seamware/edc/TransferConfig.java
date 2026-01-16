package org.seamware.edc;

import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.system.configuration.Config;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Configuration for TransferControl in the FIWARE DataSpaceConnector
 */
public class TransferConfig {

    private static final String FDSC_TRANSFER_CONFIG = "fdscTransfer";
    private static final String FDSC_TRANSFER_APISIX_CONFIG = "fdscTransfer.apisix";

    // should FDSC TransferControl be enabled
    private boolean enabled;
    // address of the credentialsConfigService to be used for provisioning the services
    private String credentialsConfigAddress;
    // configuration to access Apisix for provisioning the services
    private Apisix apisix;
    // host of the verifier to be used for creating the discovery address
    private String verifierHost;
    // internal host of the verifier to create the .well-known-address
    private String verifierInternalHost;
    // host to make the transfer available at
    private String transferHost;
    // host of the open policy agent to be used by the created service-route
    private String opaHost;
    // host of the odrlPap to be used for creating the policies
    private String odrlPapHost;

    public String getCredentialsConfigAddress() {
        return credentialsConfigAddress;
    }

    public Apisix getApisix() {
        return apisix;
    }

    public String getOpaHost() {
        return opaHost;
    }

    public String getOdrlPapHost() {
        return odrlPapHost;
    }

    public String getTransferHost() {
        return transferHost;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getVerifierHost() {
        return verifierHost;
    }

    public String getVerifierInternalHost() {
        return verifierInternalHost;
    }

    public static TransferConfig fromConfig(Config config) {
        Config transferConfig = config.getConfig(FDSC_TRANSFER_CONFIG);

        TransferConfig.Builder transferConfigBuilder = TransferConfig.Builder.newInstance();
        getNullSafeFromConfig(() -> transferConfig.getBoolean("enabled")).ifPresent(transferConfigBuilder::enabled);
        getNullSafeFromConfig(() -> transferConfig.getString("transferHost")).ifPresent(transferConfigBuilder::transferHost);
        getNullSafeFromConfig(() -> transferConfig.getString("verifierHost")).ifPresent(transferConfigBuilder::verifierHost);
        getNullSafeFromConfig(() -> transferConfig.getString("odrlPapHost")).ifPresent(transferConfigBuilder::odrlPapHost);
        getNullSafeFromConfig(() -> transferConfig.getString("verifierInternalHost")).ifPresent(transferConfigBuilder::verifierInternalHost);
        getNullSafeFromConfig(() -> transferConfig.getString("opaHost")).ifPresent(transferConfigBuilder::opaHost);
        getNullSafeFromConfig(() -> transferConfig.getString("credentialsConfigAddress")).ifPresent(transferConfigBuilder::credentialsConfigAddress);

        Config apisixConfig = config.getConfig(FDSC_TRANSFER_APISIX_CONFIG);
        Apisix.Builder apisixBuilder = new Apisix.Builder();
        getNullSafeFromConfig(() -> apisixConfig.getString("address")).ifPresent(apisixBuilder::address);
        getNullSafeFromConfig(() -> apisixConfig.getString("token")).ifPresent(apisixBuilder::token);
        getNullSafeFromConfig(() -> apisixConfig.getString("httpsProxy")).ifPresent(apisixBuilder::httpsProxy);
        transferConfigBuilder.apisix(apisixBuilder.build());

        return transferConfigBuilder.build();
    }

    /**
     * @param address of the apisix admin-api
     * @param token   to be used when accessing the admin-api
     * @param httpsProxy address of an httpsProxy to be added to the routes. If null, no proxy will be used
     */
    public record Apisix(String address, String token, String httpsProxy) {

        public static class Builder {
            private String address;
            private String token;
            private String httpsProxy;

            public Builder address(String address) {
                this.address = address;
                return this;
            }

            public Builder token(String token) {
                this.token = token;
                return this;
            }

            public Builder httpsProxy(String httpsProxy) {
                this.httpsProxy = httpsProxy;
                return this;
            }


            public Apisix build() {
                Objects.requireNonNull(address, "If FDSC Transfer is enabled, an apisix address needs to be configured.");
                Objects.requireNonNull(token, "If FDSC Transfer is enabled, an apisix admin token needs to be configured.");
                return new Apisix(address, token, httpsProxy);
            }

        }
    }

    public static class Builder {
        private final TransferConfig transferConfig;

        private Builder(TransferConfig transferConfig) {
            this.transferConfig = transferConfig;
        }

        public static Builder newInstance() {
            return new Builder(new TransferConfig());
        }

        public Builder enabled(boolean enabled) {
            transferConfig.enabled = enabled;
            return this;
        }

        public Builder verifierHost(String verifierHost) {
            transferConfig.verifierHost = verifierHost;
            return this;
        }

        public Builder apisix(Apisix apisix) {
            transferConfig.apisix = apisix;
            return this;
        }


        public Builder verifierInternalHost(String verifierInternalHost) {
            transferConfig.verifierInternalHost = verifierInternalHost;
            return this;
        }

        public Builder transferHost(String transferHost) {
            transferConfig.transferHost = transferHost;
            return this;
        }

        public Builder opaHost(String opaHost) {
            transferConfig.opaHost = opaHost;
            return this;
        }

        public Builder odrlPapHost(String odrlPapHost) {
            transferConfig.odrlPapHost = odrlPapHost;
            return this;
        }

        public Builder credentialsConfigAddress(String credentialsConfigAddress) {
            transferConfig.credentialsConfigAddress = credentialsConfigAddress;
            return this;
        }

        public TransferConfig build() {
            if (transferConfig.enabled) {
                Objects.requireNonNull(transferConfig.getApisix(), "If FDSC Transfers are supported, the Apisix Admin access needs to be provided.");
                Objects.requireNonNull(transferConfig.getCredentialsConfigAddress(), "If FDSC Transfers are supported, the Credentials Config Service Address needs to be provided.");
                Objects.requireNonNull(transferConfig.getTransferHost(), "If FDSC Transfers are supported, the host address for the transfers needs to be provided.");
                Objects.requireNonNull(transferConfig.getVerifierHost(), "If FDSC Transfers are supported, the host address for the verifier needs to be provided.");
                Objects.requireNonNull(transferConfig.getOpaHost(), "If FDSC Transfers are supported, the host address for opa to be configured in apisix needs to be provided.");
                Objects.requireNonNull(transferConfig.getOdrlPapHost(), "If FDSC Transfers are supported, the host address for odrl-pap needs to be provided.");

                if (transferConfig.getVerifierInternalHost() == null) {
                    transferConfig.verifierInternalHost = transferConfig.getVerifierHost();
                }
            }

            return transferConfig;
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
