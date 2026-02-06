package org.seamware.edc;

import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.system.configuration.Config;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public class TirConfig {

    private static final String EBSI_TIR_CONFIG = "ebsiTir";

    private boolean enabled;
    private String tilAddress;

    public String getTilAddress() {
        return tilAddress;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public static TirConfig fromConfig(Config config) {
        Config tirConfig = config.getConfig(EBSI_TIR_CONFIG);

        TirConfig.Builder tirConfigBuilder = TirConfig.Builder.newInstance();
        getNullSafeFromConfig(() -> tirConfig.getBoolean("enabled")).ifPresent(tirConfigBuilder::enabled);
        getNullSafeFromConfig(() -> tirConfig.getString("tilAddress")).ifPresent(tirConfigBuilder::tilAddress);
        
        return tirConfigBuilder.build();
    }

    public static class Builder {
        private final TirConfig tirConfig;

        private Builder(TirConfig tirConfig) {
            this.tirConfig = tirConfig;
        }

        public static Builder newInstance() {
            return new Builder(new TirConfig());
        }

        public Builder tilAddress(String tilAddress) {
            tirConfig.tilAddress = tilAddress;
            return this;
        }

        public Builder enabled(boolean enabled) {
            tirConfig.enabled = enabled;
            return this;
        }

        public TirConfig build() {
            if (tirConfig.isEnabled()) {
                Objects.requireNonNull(tirConfig.getTilAddress(), "The til address needs to be provided, when ebsi tir is enabled.");
            }
            return tirConfig;
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
