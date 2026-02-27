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

/*-
 * #%L
 * oid4vc-extension
 * %%
 * Copyright (C) 2025 - 2026 Seamless Middleware Technologies S.L
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.util.*;
import java.util.function.Supplier;
import lombok.EqualsAndHashCode;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.system.configuration.Config;

/** Configuration to support OID4VP when interacting between connectors */
@EqualsAndHashCode
public class OID4VPConfig {

  private static final String OID4VP_CONFIG_PATH = "oid4vp";
  private static final String OID4VP_PROXY_CONFIG_PATH = "oid4vp.proxy";
  private static final String OID4VP_HOLDER_CONFIG_PATH = "oid4vp.holder";
  private static final String OID4VP_HOLDER_KEY_CONFIG_PATH = "oid4vp.holder.key";

  // should OID4VP be enabled?
  private Boolean enabled;
  // allows to configure the usage of a proxy when interacting through OID4VP
  private ProxyConfig proxy;
  // configuration of the holder, presenting the credentials
  private HolderConfig holder;
  // TODO: can this be automatically resolved?
  // clientId to be used in the authorization flow
  private String clientId;
  // scope to be used when authorizing, usually "openid"
  private Set<String> scope;
  // allows to trust all certificates in the auth flow. ONLY FOR TESTING, DO NOT USE IN PRODUCTION
  private Boolean trustAll;
  // folder containing the credentials
  private String credentialsFolder;
  // folder containing additional trust anchors for X509_SAN_DNS resolution. If empty or not
  // configured, the system truststore is used
  private String trustAnchorsFolder;
  // claim to find the organization id in the JWT
  private String organizationClaim;

  public Boolean getEnabled() {
    return enabled;
  }

  public ProxyConfig getProxy() {
    return proxy;
  }

  public String getClientId() {
    return clientId;
  }

  public Set<String> getScope() {
    return scope;
  }

  public HolderConfig getHolder() {
    return holder;
  }

  public String getCredentialsFolder() {
    return credentialsFolder;
  }

  public Boolean getTrustAll() {
    return trustAll;
  }

  public String getTrustAnchorsFolder() {
    return trustAnchorsFolder;
  }

  public String getOrganizationClaim() {
    return organizationClaim;
  }

  public static OID4VPConfig fromConfig(Config config) {
    Config oid4VpConfig = config.getConfig(OID4VP_CONFIG_PATH);

    OID4VPConfig.Builder configBuilder = OID4VPConfig.Builder.newInstance();
    getNullSafeFromConfig(() -> oid4VpConfig.getBoolean("enabled"))
        .ifPresent(configBuilder::enabled);
    getNullSafeFromConfig(() -> oid4VpConfig.getString("clientId"))
        .ifPresent(configBuilder::clientId);
    getNullSafeFromConfig(() -> oid4VpConfig.getBoolean("trustAll"))
        .ifPresent(configBuilder::trustAll);
    getNullSafeFromConfig(() -> oid4VpConfig.getString("scope"))
        .map(scopeString -> scopeString.split(","))
        .map(Arrays::asList)
        .map(HashSet::new)
        .ifPresent(configBuilder::scope);

    getNullSafeFromConfig(() -> oid4VpConfig.getString("credentialsFolder"))
        .ifPresent(configBuilder::credentialsFolder);
    getNullSafeFromConfig(() -> oid4VpConfig.getString("trustAnchorsFolder"))
        .ifPresent(configBuilder::trustAnchorsFolder);
    getNullSafeFromConfig(() -> oid4VpConfig.getString("organizationClaim"))
        .ifPresent(configBuilder::organizationClaim);

    Config holderConfig = config.getConfig(OID4VP_HOLDER_CONFIG_PATH);
    HolderConfig.Builder holderConfigBuilder = new HolderConfig.Builder();
    getNullSafeFromConfig(() -> holderConfig.getString("id")).ifPresent(holderConfigBuilder::id);
    getNullSafeFromConfig(() -> holderConfig.getString("signatureAlgorithm"))
        .ifPresent(holderConfigBuilder::signatureAlgorithm);

    Config holderKeyConfig = config.getConfig(OID4VP_HOLDER_KEY_CONFIG_PATH);
    KeyConfig.Builder keyConfigBuilder = new KeyConfig.Builder();
    getNullSafeFromConfig(() -> holderKeyConfig.getString("type"))
        .ifPresent(keyConfigBuilder::type);
    getNullSafeFromConfig(() -> holderKeyConfig.getString("path"))
        .ifPresent(keyConfigBuilder::path);
    holderConfigBuilder.keyConfigBuilder(keyConfigBuilder);

    Config proxyConfig = config.getConfig(OID4VP_PROXY_CONFIG_PATH);
    ProxyConfig.Builder proxyConfigBuilder = new ProxyConfig.Builder();
    getNullSafeFromConfig(() -> proxyConfig.getBoolean("enabled"))
        .ifPresent(proxyConfigBuilder::enabled);
    getNullSafeFromConfig(() -> proxyConfig.getString("host")).ifPresent(proxyConfigBuilder::host);
    getNullSafeFromConfig(() -> proxyConfig.getInteger("port")).ifPresent(proxyConfigBuilder::port);

    configBuilder.holderConfigBuilder(holderConfigBuilder);
    configBuilder.proxy(proxyConfigBuilder.build());

    return configBuilder.build();
  }

  /**
   * @param id of the holder, usually a did
   * @param kid of to be used for signing. if nothing is provided, the id will be used
   * @param keyConfig configuration of the signing key
   * @param signatureAlgorithm algorithm to be used for signing the vp-token. defaults to ECDH-ES
   */
  public record HolderConfig(
      String id, String kid, KeyConfig keyConfig, String signatureAlgorithm) {

    public static class Builder {
      private String id;
      private String kid;
      private KeyConfig keyConfig;
      private KeyConfig.Builder keyConfigBuilder;
      private String signatureAlgorithm = "ECDH-ES";

      public Builder id(String id) {
        this.id = id;
        return this;
      }

      public Builder kid(String kid) {
        this.kid = kid;
        return this;
      }

      public Builder signatureAlgorithm(String signatureAlgorithm) {
        this.signatureAlgorithm = signatureAlgorithm;
        return this;
      }

      public Builder keyConfig(KeyConfig keyConfig) {
        this.keyConfig = keyConfig;
        return this;
      }

      public Builder keyConfigBuilder(KeyConfig.Builder keyConfBuilder) {
        this.keyConfigBuilder = keyConfBuilder;
        return this;
      }

      public HolderConfig build() {
        Objects.requireNonNull(id, "When using OID4VP, the holder-id needs to be configured.");
        Objects.requireNonNull(
            signatureAlgorithm,
            "When using OID4VP, a valid signature algorithm has to be configured for the holder.");
        if (keyConfig == null && keyConfigBuilder != null) {
          keyConfig = keyConfigBuilder.build();
        }
        Objects.requireNonNull(
            keyConfig, "When using OID4VP, the holder-key needs to be configured.");

        if (kid == null) {
          // in many cases, kid and id are the same, thus we are setting it as a default
          kid = id;
        }
        return new HolderConfig(id, kid, keyConfig, signatureAlgorithm);
      }
    }
  }

  /**
   * @param enabled should a proxy be used
   * @param host of the proxy
   * @param port of the proxy
   */
  public record ProxyConfig(boolean enabled, String host, int port) {
    public static class Builder {
      private boolean enabled;
      private String host;
      private int port = 8888;

      public Builder enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
      }

      public Builder host(String host) {
        this.host = host;
        return this;
      }

      public Builder port(int port) {
        this.port = port;
        return this;
      }

      public ProxyConfig build() {
        if (enabled) {
          Objects.requireNonNull(
              host, "If a proxy should be used for OID4VP, the proxy host needs to be configured.");
        }
        return new ProxyConfig(enabled, host, port);
      }
    }
  }

  /**
   * @param type of the key - needs to be compatible with the signature algorithm
   * @param path to the key in pem form
   */
  public record KeyConfig(String type, String path) {
    public static class Builder {
      private String type;
      private String path;

      public Builder type(String type) {
        this.type = type;
        return this;
      }

      public Builder path(String path) {
        this.path = path;
        return this;
      }

      public KeyConfig build() {
        Objects.requireNonNull(type, "For OID4VP, a valid key type needs to be configured.");
        Objects.requireNonNull(path, "For OID4VP, a valid key path needs to be configured.");
        return new KeyConfig(type, path);
      }
    }
  }

  public static class Builder {
    private final OID4VPConfig oid4VPConfig;
    private HolderConfig.Builder holderConfigBuilder;
    private ProxyConfig.Builder proxyConfigBuilder;

    private Builder(OID4VPConfig oid4VPConfig) {
      this.oid4VPConfig = oid4VPConfig;
      // set defaults
      this.oid4VPConfig.enabled = true;
      this.oid4VPConfig.clientId = "dsp-connector";
      this.oid4VPConfig.scope = Set.of("openid");
      this.oid4VPConfig.trustAll = false;
      this.oid4VPConfig.credentialsFolder = "credentials";
      this.oid4VPConfig.organizationClaim = "verifiableCredential.issuer";
    }

    public static Builder newInstance() {
      return new Builder(new OID4VPConfig());
    }

    public Builder enabled(boolean enabled) {
      oid4VPConfig.enabled = enabled;
      return this;
    }

    public Builder trustAll(boolean trustAll) {
      oid4VPConfig.trustAll = trustAll;
      return this;
    }

    public Builder clientId(String clientId) {
      oid4VPConfig.clientId = clientId;
      return this;
    }

    public Builder scope(Set<String> scope) {
      oid4VPConfig.scope = scope;
      return this;
    }

    public Builder credentialsFolder(String credentialsFolder) {
      oid4VPConfig.credentialsFolder = credentialsFolder;
      return this;
    }

    public Builder trustAnchorsFolder(String trustAnchorsFolder) {
      oid4VPConfig.trustAnchorsFolder = trustAnchorsFolder;
      return this;
    }

    public Builder organizationClaim(String organizationClaim) {
      oid4VPConfig.organizationClaim = organizationClaim;
      return this;
    }

    public Builder proxy(ProxyConfig proxy) {
      oid4VPConfig.proxy = proxy;
      return this;
    }

    public Builder proxyConfigBuilder(ProxyConfig.Builder proxyConfigBuilder) {
      this.proxyConfigBuilder = proxyConfigBuilder;
      return this;
    }

    public Builder holderConfigBuilder(HolderConfig.Builder holderConfigBuilder) {
      this.holderConfigBuilder = holderConfigBuilder;
      return this;
    }

    public Builder holder(HolderConfig holder) {
      oid4VPConfig.holder = holder;
      return this;
    }

    public OID4VPConfig build() {
      if (oid4VPConfig.enabled) {
        if (holderConfigBuilder != null) {
          oid4VPConfig.holder = holderConfigBuilder.build();
        }
        if (proxyConfigBuilder != null) {
          oid4VPConfig.proxy = proxyConfigBuilder.build();
        }
        Objects.requireNonNull(
            oid4VPConfig.holder,
            "When OID4VP is enabled, a holder configuration has to be provided.");
        Objects.requireNonNull(
            oid4VPConfig.credentialsFolder,
            "When OID4VP is enabled, a folder containing credentials has to be provided.");
        Objects.requireNonNull(
            oid4VPConfig.clientId, "When OID4VP is enabled, a clientId has to be provided.");
        Objects.requireNonNull(
            oid4VPConfig.scope, "When OID4VP is enabled, a scope has to be provided.");
      }
      return oid4VPConfig;
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
