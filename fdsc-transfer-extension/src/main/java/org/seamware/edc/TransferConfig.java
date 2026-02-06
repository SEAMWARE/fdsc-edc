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
    private static final String FDSC_TRANSFER_OID4VC_CONFIG = "fdscTransfer.oid4vc";
    private static final String FDSC_TRANSFER_DCP_CONFIG = "fdscTransfer.dcp";
    private static final String FDSC_TRANSFER_DCP_OID_CONFIG = "fdscTransfer.dcp.oid";

    // should FDSC TransferControl be enabled
    private boolean enabled;
    // configuration to access Apisix for provisioning the services
    private Apisix apisix;
    // confiuration for provisioning transfers with OID4VC enabled
    private Oid4Vc oid4Vc;
    // confiuration for provisioning transfers with DCP enabled
    private Dcp dcp;
    // host to make the transfer available at
    private String transferHost;


    public Apisix getApisix() {
        return apisix;
    }

    public Oid4Vc getOid4Vc() {
        return oid4Vc;
    }

    public Dcp getDcp() {
        return dcp;
    }

    public String getTransferHost() {
        return transferHost;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public static TransferConfig fromConfig(Config config) {
        Config transferConfig = config.getConfig(FDSC_TRANSFER_CONFIG);

        TransferConfig.Builder transferConfigBuilder = TransferConfig.Builder.newInstance();
        getNullSafeFromConfig(() -> transferConfig.getBoolean("enabled")).ifPresent(transferConfigBuilder::enabled);
        getNullSafeFromConfig(() -> transferConfig.getString("transferHost")).ifPresent(transferConfigBuilder::transferHost);

        Config oid4vcConfig = config.getConfig(FDSC_TRANSFER_OID4VC_CONFIG);
        Oid4Vc.Builder oid4VcBuilder = new Oid4Vc.Builder();
        getNullSafeFromConfig(() -> oid4vcConfig.getBoolean("enabled")).ifPresent(oid4VcBuilder::enabled);
        getNullSafeFromConfig(() -> oid4vcConfig.getString("verifierHost")).ifPresent(oid4VcBuilder::verifierHost);
        getNullSafeFromConfig(() -> oid4vcConfig.getString("odrlPapHost")).ifPresent(oid4VcBuilder::odrlPapHost);
        getNullSafeFromConfig(() -> oid4vcConfig.getString("verifierInternalHost")).ifPresent(oid4VcBuilder::verifierInternalHost);
        getNullSafeFromConfig(() -> oid4vcConfig.getString("opaHost")).ifPresent(oid4VcBuilder::opaHost);
        getNullSafeFromConfig(() -> oid4vcConfig.getString("credentialsConfigAddress")).ifPresent(oid4VcBuilder::credentialsConfigAddress);


        Config dcpOidConfig = config.getConfig(FDSC_TRANSFER_DCP_OID_CONFIG);
        OidConfig.Builder oidConfigBuilder = new OidConfig.Builder();
        getNullSafeFromConfig(() -> dcpOidConfig.getString("host")).ifPresent(oidConfigBuilder::host);
        getNullSafeFromConfig(() -> dcpOidConfig.getString("openIdPath")).ifPresent(oidConfigBuilder::openIdPath);
        getNullSafeFromConfig(() -> dcpOidConfig.getString("jwksPath")).ifPresent(oidConfigBuilder::jwksPath);

        Config dcpConfig = config.getConfig(FDSC_TRANSFER_DCP_CONFIG);
        Dcp.Builder dcpBuilder = new Dcp.Builder();
        getNullSafeFromConfig(() -> dcpConfig.getBoolean("enabled")).ifPresent(dcpBuilder::enabled);
        dcpBuilder.oidConfigBuilder(oidConfigBuilder);

        Config apisixConfig = config.getConfig(FDSC_TRANSFER_APISIX_CONFIG);
        Apisix.Builder apisixBuilder = new Apisix.Builder();
        getNullSafeFromConfig(() -> apisixConfig.getString("address")).ifPresent(apisixBuilder::address);
        getNullSafeFromConfig(() -> apisixConfig.getString("token")).ifPresent(apisixBuilder::token);
        getNullSafeFromConfig(() -> apisixConfig.getString("httpsProxy")).ifPresent(apisixBuilder::httpsProxy);

        transferConfigBuilder.apisix(apisixBuilder.build());
        transferConfigBuilder.oid4Vc(oid4VcBuilder.build());
        transferConfigBuilder.dcp(dcpBuilder.build());

        return transferConfigBuilder.build();
    }

    /**
     * @param address    of the apisix admin-api
     * @param token      to be used when accessing the admin-api
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

    /**
     * Configuration for provisioning data transfers using OID4VC
     *
     * @param enabled                  - should OID4VC be enabled
     * @param credentialsConfigAddress - address of the credentialsConfigService to be used for provisioning the services
     * @param verifierHost             - host of the verifier to be used for creating the discovery address
     * @param verifierInternalHost     - internal host of the verifier to create the .well-known-address
     * @param opaHost                  - host of the open policy agent to be used by the created service-route
     * @param odrlPapHost              - host of the odrlPap to be used for creating the policies
     */
    public record Oid4Vc(boolean enabled, String credentialsConfigAddress, String verifierHost, String verifierInternalHost, String opaHost, String odrlPapHost) {

        public static class Builder {
            private boolean enabled;
            private String credentialsConfigAddress;
            private String verifierHost;
            private String verifierInternalHost;
            private String opaHost;
            private String odrlPapHost;

            public Oid4Vc.Builder enabled(boolean enabled) {
                this.enabled = enabled;
                return this;
            }

            public Oid4Vc.Builder credentialsConfigAddress(String credentialsConfigAddress) {
                this.credentialsConfigAddress = credentialsConfigAddress;
                return this;
            }

            public Oid4Vc.Builder verifierHost(String verifierHost) {
                this.verifierHost = verifierHost;
                return this;
            }

            public Oid4Vc.Builder verifierInternalHost(String verifierInternalHost) {
                this.verifierInternalHost = verifierInternalHost;
                return this;
            }

            public Oid4Vc.Builder opaHost(String opaHost) {
                this.opaHost = opaHost;
                return this;
            }

            public Oid4Vc.Builder odrlPapHost(String odrlPapHost) {
                this.odrlPapHost = odrlPapHost;
                return this;
            }


            public Oid4Vc build() {
                if (enabled) {
                    Objects.requireNonNull(credentialsConfigAddress, "If FDSC Transfer is enabled, an apisix address needs to be configured.");
                    Objects.requireNonNull(verifierHost, "If FDSC Transfers are supported, the host address for the verifier needs to be provided.");
                    Objects.requireNonNull(opaHost, "If FDSC Transfers are supported, the host address for opa to be configured in apisix needs to be provided.");
                    Objects.requireNonNull(odrlPapHost, "If FDSC Transfers are supported, the host address for odrl-pap needs to be provided.");

                    if (verifierInternalHost == null) {
                        verifierInternalHost = verifierHost;
                    }
                }
                return new Oid4Vc(enabled, credentialsConfigAddress, verifierHost, verifierInternalHost, opaHost, odrlPapHost);
            }

        }
    }

    public record OidConfig(String host, String openIdPath, String jwksPath) {
        public static class Builder {
            private String host;
            private String openIdPath;
            private String jwksPath;

            public Builder host(String host) {
                this.host = host;
                return this;
            }

            public Builder openIdPath(String openIdPath) {
                this.openIdPath = openIdPath;
                return this;
            }

            public Builder jwksPath(String jwksPath) {
                this.jwksPath = jwksPath;
                return this;
            }

            public OidConfig build() {

                Objects.requireNonNull(host, "If DCP is enabled, the OID host needs to be configured.");
                if (openIdPath == null) {
                    openIdPath = "/.well-known/openid-configuration";
                }
                if (jwksPath == null) {
                    jwksPath = "/.well-known/jwks";
                }
                return new OidConfig(host, openIdPath, jwksPath);
            }
        }
    }

    public record Dcp(boolean enabled, OidConfig oidConfig) {

        public static class Builder {
            private boolean enabled;
            private OidConfig.Builder oidConfigBuilder;

            public Dcp.Builder enabled(boolean enabled) {
                this.enabled = enabled;
                return this;
            }

            public Dcp.Builder oidConfigBuilder(OidConfig.Builder oidConfigBuilder) {
                this.oidConfigBuilder = oidConfigBuilder;
                return this;
            }


            public Dcp build() {
                if (enabled) {
                    Objects.requireNonNull(oidConfigBuilder, "If DCP is enabled, the OID endpoints have to be configured.");
                    OidConfig oidConfig = oidConfigBuilder.build();
                    return new Dcp(enabled, oidConfig);
                }
                return new Dcp(enabled, null);
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

        public Builder apisix(Apisix apisix) {
            transferConfig.apisix = apisix;
            return this;
        }

        public Builder transferHost(String transferHost) {
            transferConfig.transferHost = transferHost;
            return this;
        }

        public Builder oid4Vc(Oid4Vc oid4Vc) {
            transferConfig.oid4Vc = oid4Vc;
            return this;
        }

        public Builder dcp(Dcp dcp) {
            transferConfig.dcp = dcp;
            return this;
        }

        public TransferConfig build() {
            if (transferConfig.enabled) {
                Objects.requireNonNull(transferConfig.getApisix(), "If FDSC Transfers are supported, the Apisix Admin access needs to be provided.");
                Objects.requireNonNull(transferConfig.getTransferHost(), "If FDSC Transfers are supported, the host address for the transfers needs to be provided.");
                if (transferConfig.oid4Vc == null) {
                    transferConfig.oid4Vc = new Oid4Vc.Builder().enabled(false).build();
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
