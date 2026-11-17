/*
 * Copyright 2025 Seamless Middleware Technologies S.L and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.seamware.edc;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.Config;

/** Configuration of the TMForum Extension. */
@EqualsAndHashCode
@Getter
public class TMFConfig {

  /** EDC core setting holding the identity this runtime acts on behalf of. */
  public static final String EDC_PARTICIPANT_ID_SETTING = "edc.participant.id";

  /**
   * Context that owns TMForum entities carrying no context tag. Overrides the context of the
   * runtime's {@code SingleParticipantContextSupplier}; required in virtual mode.
   */
  public static final String DEFAULT_PARTICIPANT_CONTEXT_ID_SETTING =
      "tmfExtension.defaultParticipantContextId";

  /** Entry of a ProductOffering's {@code participantContextIds} that offers it in every context. */
  public static final String ALL_PARTICIPANT_CONTEXTS = "*";

  private static final String TMF_EXTENSION_CONFIG_PATH = "tmfExtension";
  private static final String TMF_EXTENSION_CATALOG_CONFIG_PATH = "tmfExtension.catalog";
  private static final URI DEFAULT_SCHEMA_BASE_URI =
      URI.create("https://raw.githubusercontent.com/wistefan/edc-dsc/refs/heads/init/schemas/");

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

  // identity this runtime acts on behalf of, mirrors edc.participant.id
  private String participantId;
  // context of untagged (legacy) TMForum entities; null when none could be resolved
  private String defaultParticipantContextId;

  // configuration for the catalog endpoint
  private CatalogConfig catalogConfig;

  /**
   * Endpoint-only view for the extensions in this package that need the TMForum URLs and the
   * enabled flag. Leaves {@code participantId}/{@code defaultParticipantContextId} unset, so a
   * value read off this instance cannot silently diverge from the participant context the runtime
   * registers.
   */
  static TMFConfig fromConfig(Config config) {
    return endpointConfigBuilder(config).build();
  }

  /**
   * Builds the config. The participant id comes from {@code participantContext} when the runtime
   * supplies one, else from {@code edc.participant.id}. The default context is the explicit {@link
   * #DEFAULT_PARTICIPANT_CONTEXT_ID_SETTING}, else the supplied context, else unset (logged).
   */
  public static TMFConfig fromConfig(
      Config config, ParticipantContext participantContext, Monitor monitor) {
    TMFConfig.Builder tmfConfigBuilder = endpointConfigBuilder(config);

    if (participantContext != null) {
      tmfConfigBuilder.participantId(participantContext.getIdentity());
    } else {
      tmfConfigBuilder.participantId(
          config.getString(
              EDC_PARTICIPANT_ID_SETTING, ServiceExtensionContext.ANONYMOUS_PARTICIPANT));
    }
    tmfConfigBuilder.defaultParticipantContextId(
        resolveDefaultParticipantContextId(config, participantContext, monitor));

    return tmfConfigBuilder.build();
  }

  /**
   * Deliberately ignores {@code edc.participant.*} without a supplier: in virtual mode those are
   * per-context entries, not the runtime's identity.
   */
  private static String resolveDefaultParticipantContextId(
      Config config, ParticipantContext participantContext, Monitor monitor) {
    String explicitContextId =
        blankToNull(config.getString(DEFAULT_PARTICIPANT_CONTEXT_ID_SETTING, null));
    String suppliedContextId =
        Optional.ofNullable(participantContext)
            .map(ParticipantContext::getParticipantContextId)
            .map(TMFConfig::blankToNull)
            .orElse(null);
    if (explicitContextId != null) {
      if (suppliedContextId != null && !suppliedContextId.equals(explicitContextId)) {
        monitor.warning(
            String.format(
                "%s=%s differs from the runtime's participant context %s. Untagged TMForum"
                    + " entities are assigned to %s.",
                DEFAULT_PARTICIPANT_CONTEXT_ID_SETTING,
                explicitContextId,
                suppliedContextId,
                explicitContextId));
      }
      return explicitContextId;
    }
    if (suppliedContextId == null) {
      monitor.warning(
          String.format(
              "Neither %s nor a single participant context is available. Quotes and agreements"
                  + " without a participant context stay invisible; offerings without one are"
                  + " offered under context-qualified ids only.",
              DEFAULT_PARTICIPANT_CONTEXT_ID_SETTING));
    }
    return suppliedContextId;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  /**
   * @deprecated use {@link #getDefaultParticipantContextId()}; entities carry their own context.
   */
  @Deprecated
  public String getParticipantContextId() {
    return defaultParticipantContextId;
  }

  private static TMFConfig.Builder endpointConfigBuilder(Config config) {
    Config tmfExtensionConfig = config.getConfig(TMF_EXTENSION_CONFIG_PATH);

    TMFConfig.Builder tmfConfigBuilder = TMFConfig.Builder.newInstance();
    getNullSafeFromConfig(() -> tmfExtensionConfig.getBoolean("enabled"))
        .ifPresent(tmfConfigBuilder::enabled);
    getNullSafeFromConfig(() -> tmfExtensionConfig.getString("quoteApi"))
        .ifPresent(tmfConfigBuilder::quoteApi);
    getNullSafeFromConfig(() -> tmfExtensionConfig.getString("agreementApi"))
        .ifPresent(tmfConfigBuilder::agreementApi);
    getNullSafeFromConfig(() -> tmfExtensionConfig.getString("productOrderApi"))
        .ifPresent(tmfConfigBuilder::productOrderApi);
    getNullSafeFromConfig(() -> tmfExtensionConfig.getString("productCatalogApi"))
        .ifPresent(tmfConfigBuilder::productCatalogApi);
    getNullSafeFromConfig(() -> tmfExtensionConfig.getString("productInventoryApi"))
        .ifPresent(tmfConfigBuilder::productInventoryApi);
    getNullSafeFromConfig(() -> tmfExtensionConfig.getString("usageManagementApi"))
        .ifPresent(tmfConfigBuilder::usageManagementApi);
    getNullSafeFromConfig(() -> tmfExtensionConfig.getString("partyCatalogApi"))
        .ifPresent(tmfConfigBuilder::partyCatalogApi);
    getNullSafeFromConfig(() -> tmfExtensionConfig.getString("schemaBaseUri"))
        .ifPresent(tmfConfigBuilder::schemaBaseUri);

    CatalogConfig.Builder catalogConfigBuilder = new CatalogConfig.Builder();
    Config catalogConfig = config.getConfig(TMF_EXTENSION_CATALOG_CONFIG_PATH);
    getNullSafeFromConfig(() -> catalogConfig.getBoolean("enabled"))
        .ifPresent(catalogConfigBuilder::enabled);
    tmfConfigBuilder.catalogConfig(catalogConfigBuilder.build());

    return tmfConfigBuilder;
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

    public Builder participantId(String participantId) {
      tmfConfig.participantId = participantId;
      return this;
    }

    public Builder defaultParticipantContextId(String defaultParticipantContextId) {
      tmfConfig.defaultParticipantContextId = defaultParticipantContextId;
      return this;
    }

    public Builder quoteApi(String quoteApi) {
      try {
        tmfConfig.quoteApi = URI.create(quoteApi).toURL();
      } catch (MalformedURLException e) {
        throw new IllegalArgumentException(
            String.format("%s is not a valid url for the quote api.", quoteApi), e);
      }
      return this;
    }

    public Builder agreementApi(String agreementApi) {
      try {
        tmfConfig.agreementApi = URI.create(agreementApi).toURL();
      } catch (MalformedURLException e) {
        throw new IllegalArgumentException(
            String.format("%s is not a valid url for the agreement api.", agreementApi), e);
      }
      return this;
    }

    public Builder productOrderApi(String productOrderApi) {
      try {
        tmfConfig.productOrderApi = URI.create(productOrderApi).toURL();
      } catch (MalformedURLException e) {
        throw new IllegalArgumentException(
            String.format("%s is not a valid url for the productOrder api.", productOrderApi), e);
      }
      return this;
    }

    public Builder productCatalogApi(String productCatalogApi) {
      try {
        tmfConfig.productCatalogApi = URI.create(productCatalogApi).toURL();
      } catch (MalformedURLException e) {
        throw new IllegalArgumentException(
            String.format("%s is not a valid url for the productCatalog api.", productCatalogApi),
            e);
      }
      return this;
    }

    public Builder productInventoryApi(String productInventoryApi) {
      try {
        tmfConfig.productInventoryApi = URI.create(productInventoryApi).toURL();
      } catch (MalformedURLException e) {
        throw new IllegalArgumentException(
            String.format(
                "%s is not a valid url for the productInventory api.", productInventoryApi),
            e);
      }
      return this;
    }

    public Builder usageManagementApi(String usageManagementApi) {
      try {
        tmfConfig.usageManagementApi = URI.create(usageManagementApi).toURL();
      } catch (MalformedURLException e) {
        throw new IllegalArgumentException(
            String.format("%s is not a valid url for the usageManagement api.", usageManagementApi),
            e);
      }
      return this;
    }

    public Builder partyCatalogApi(String partyCatalogApi) {
      try {
        tmfConfig.partyCatalogApi = URI.create(partyCatalogApi).toURL();
      } catch (MalformedURLException e) {
        throw new IllegalArgumentException(
            String.format("%s is not a valid url for the partyCatalog api.", partyCatalogApi), e);
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
        Objects.requireNonNull(
            tmfConfig.quoteApi, "If TMFExtension is enabled, a valid quoteApi has to be provided.");
        Objects.requireNonNull(
            tmfConfig.agreementApi,
            "If TMFExtension is enabled, a valid agreementApi has to be provided.");
        Objects.requireNonNull(
            tmfConfig.productOrderApi,
            "If TMFExtension is enabled, a valid productOrderApi has to be provided.");
        Objects.requireNonNull(
            tmfConfig.productCatalogApi,
            "If TMFExtension is enabled, a valid productCatalogApi has to be provided.");
        Objects.requireNonNull(
            tmfConfig.productInventoryApi,
            "If TMFExtension is enabled, a valid productInventoryApi has to be provided.");
        Objects.requireNonNull(
            tmfConfig.usageManagementApi,
            "If TMFExtension is enabled, a valid usageManagementApi has to be provided.");
        Objects.requireNonNull(
            tmfConfig.partyCatalogApi,
            "If TMFExtension is enabled, a valid partyCatalogApi has to be provided.");
        Objects.requireNonNull(
            tmfConfig.catalogConfig,
            "If TMFExtension is enabled, a valid catalog config has to be provided.");
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
