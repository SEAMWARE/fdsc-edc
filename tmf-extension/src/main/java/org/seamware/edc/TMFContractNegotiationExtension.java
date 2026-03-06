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

import static org.eclipse.edc.spi.constants.CoreConstants.JSON_LD;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import java.time.Clock;
import java.util.Map;
import okhttp3.OkHttpClient;
import org.eclipse.edc.connector.controlplane.asset.spi.index.AssetIndex;
import org.eclipse.edc.connector.controlplane.asset.spi.index.DataAddressResolver;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.offer.store.ContractDefinitionStore;
import org.eclipse.edc.connector.controlplane.policy.spi.store.PolicyDefinitionStore;
import org.eclipse.edc.connector.controlplane.transform.odrl.OdrlTransformersFactory;
import org.eclipse.edc.connector.controlplane.transform.odrl.from.JsonObjectFromPolicyTransformer;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.participant.spi.ParticipantIdMapper;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.protocol.spi.DataspaceProfileContextRegistry;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.runtime.metamodel.annotation.Requires;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.transform.transformer.edc.to.JsonValueToGenericTypeTransformer;
import org.seamware.edc.store.*;
import org.seamware.edc.tmf.*;

/** Extension to store and track ContractNegotiations in TMForum */
@Requires({CriterionOperatorRegistry.class})
@Provides({
  TMFEdcMapper.class,
  ObjectMapper.class,
  QuoteApiClient.class,
  AgreementApiClient.class,
  ProductOrderApiClient.class,
  ProductCatalogApiClient.class,
  ProductInventoryApiClient.class,
  ParticipantResolver.class,
  ContractNegotiationStore.class,
  ContractDefinitionStore.class,
  PolicyDefinitionStore.class,
  AssetIndex.class,
  DataAddressResolver.class
})
public class TMFContractNegotiationExtension implements ServiceExtension {

  public static final String SCHEMA_BASE_URI_PROP = "schemaBaseUri";
  private static final String NAME = "TMFExtension";

  @Inject private OkHttpClient okHttpClient;
  @Inject private DataspaceProfileContextRegistry dataspaceProfileContextRegistry;
  @Inject private Monitor monitor;
  @Inject private TypeManager typeManager;

  private ObjectMapper objectMapper;

  private ContractDefinitionStore contractDefinitionStore;
  private PolicyDefinitionStore policyDefinitionStore;
  private AssetIndex assetIndex;

  private QuoteApiClient quoteApi;
  private AgreementApiClient agreementApi;
  private ProductOrderApiClient productOrderApi;
  private ParticipantResolver participantResolver;
  private ProductCatalogApiClient productCatalogApi;
  private ProductInventoryApiClient productInventoryApi;
  private ContractNegotiationStore contractNegotiationStore;
  private TMFEdcMapper tmfEdcMapper;

  private TMFConfig tmfConfig;

  @Inject private CriterionOperatorRegistry criterionOperatorRegistry;

  @Inject private Clock clock;

  @Inject private TypeTransformerRegistry typeTransformerRegistry;

  @Inject private JsonLd jsonLd;

  @Override
  public String name() {
    return NAME;
  }

  @Provider
  public TMFConfig tmfConfig(ServiceExtensionContext context) {
    if (tmfConfig == null) {
      tmfConfig = TMFConfig.fromConfig(context.getConfig());
    }
    return tmfConfig;
  }

  @Override
  public void initialize(ServiceExtensionContext context) {

    TMFConfig config = tmfConfig(context);
    if (!config.isEnabled()) {
      monitor.info("TMF extension is not enabled.");
      return;
    }
    JsonBuilderFactory jsonBuilderFactory = Json.createBuilderFactory(Map.of());
    ParticipantIdMapper participantIdMapper =
        new ParticipantIdMapper() {
          @Override
          public String toIri(String s) {
            return s;
          }

          @Override
          public String fromIri(String s) {
            return s;
          }
        };
    OdrlTransformersFactory.jsonObjectToOdrlTransformers(participantIdMapper)
        .forEach(typeTransformerRegistry::register);
    typeTransformerRegistry.register(new JsonValueToGenericTypeTransformer(typeManager, JSON_LD));
    typeTransformerRegistry.register(
        new JsonObjectFromPolicyTransformer(
            jsonBuilderFactory,
            participantIdMapper,
            new JsonObjectFromPolicyTransformer.TransformerConfig(true, true)));

    SchemaBaseUriHolder.configure(config.getSchemaBaseUri());
    context.registerService(TMFEdcMapper.class, tmfEdcMapper(config));
    context.registerService(ObjectMapper.class, objectMapper());
    context.registerService(QuoteApiClient.class, quoteApi(context, config));
    context.registerService(AgreementApiClient.class, agreementApi(config));
    context.registerService(ProductOrderApiClient.class, productOrderApi(config));
    context.registerService(ProductCatalogApiClient.class, productCatalogApi(config));
    context.registerService(ProductInventoryApiClient.class, productInventoryApi(config));
    context.registerService(ParticipantResolver.class, participantResolver(config));
    context.registerService(
        ContractNegotiationStore.class, contractNegotiationStore(context, config));
    context.registerService(ContractDefinitionStore.class, contractDefinitionStore(config));
    context.registerService(PolicyDefinitionStore.class, policyDefinitionStore(config));
    context.registerService(AssetIndex.class, assetIndex(config));
    context.registerService(DataAddressResolver.class, dataAddressResolver(config));
  }

  public TMFEdcMapper tmfEdcMapper(TMFConfig config) {
    if (tmfEdcMapper == null) {
      tmfEdcMapper =
          new TMFEdcMapper(
              monitor,
              objectMapper(),
              participantResolver(config),
              typeTransformerRegistry,
              jsonLd,
              clock);
    }
    return tmfEdcMapper;
  }

  public ObjectMapper objectMapper() {
    if (objectMapper == null) {
      objectMapper = new ObjectMapper();
      objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
      objectMapper.addMixIn(Policy.Builder.class, UnknownPropertyMixin.class);
      objectMapper.registerModule(new JavaTimeModule());
    }
    return objectMapper;
  }

  public QuoteApiClient quoteApi(ServiceExtensionContext context, TMFConfig tmfConfig) {
    if (quoteApi == null) {

      quoteApi =
          new QuoteApiClient(
              monitor,
              okHttpClient,
              context.getConfig().getString("edc.hostname"),
              tmfConfig.getQuoteApi().toString(),
              objectMapper());
    }
    return quoteApi;
  }

  public AgreementApiClient agreementApi(TMFConfig tmfConfig) {
    if (agreementApi == null) {
      agreementApi =
          new AgreementApiClient(
              monitor, okHttpClient, tmfConfig.getAgreementApi().toString(), objectMapper());
    }
    return agreementApi;
  }

  public ProductOrderApiClient productOrderApi(TMFConfig tmfConfig) {
    if (productOrderApi == null) {
      productOrderApi =
          new ProductOrderApiClient(
              monitor, okHttpClient, tmfConfig.getProductOrderApi().toString(), objectMapper());
    }
    return productOrderApi;
  }

  public ProductCatalogApiClient productCatalogApi(TMFConfig tmfConfig) {
    if (productCatalogApi == null) {
      productCatalogApi =
          new ProductCatalogApiClient(
              monitor,
              okHttpClient,
              tmfConfig.getProductCatalogApi().toString(),
              objectMapper(),
              tmfEdcMapper(tmfConfig));
    }
    return productCatalogApi;
  }

  public ProductInventoryApiClient productInventoryApi(TMFConfig tmfConfig) {
    if (productInventoryApi == null) {
      productInventoryApi =
          new ProductInventoryApiClient(
              monitor, okHttpClient, tmfConfig.getProductInventoryApi().toString(), objectMapper());
    }
    return productInventoryApi;
  }

  public ParticipantResolver participantResolver(TMFConfig tmfConfig) {
    if (participantResolver == null) {
      participantResolver =
          new ParticipantResolver(
              monitor, okHttpClient, tmfConfig.getPartyCatalogApi().toString(), objectMapper());
    }
    return participantResolver;
  }

  public ContractNegotiationStore contractNegotiationStore(
      ServiceExtensionContext serviceExtensionContext, TMFConfig tmfConfig) {
    if (contractNegotiationStore == null) {

      String controlplane = serviceExtensionContext.getConfig().getString("edc.hostname");
      contractNegotiationStore =
          new TMFBackedContractNegotiationStore(
              monitor,
              objectMapper(),
              quoteApi(serviceExtensionContext, tmfConfig),
              agreementApi(tmfConfig),
              productOrderApi(tmfConfig),
              productCatalogApi(tmfConfig),
              productInventoryApi(tmfConfig),
              participantResolver(tmfConfig),
              tmfEdcMapper(tmfConfig),
              serviceExtensionContext.getParticipantId(),
              controlplane,
              criterionOperatorRegistry,
              new TMFBackedLeaseHolder(
                  quoteApi(serviceExtensionContext, tmfConfig), controlplane, clock, monitor));
    }
    return contractNegotiationStore;
  }

  public ContractDefinitionStore contractDefinitionStore(TMFConfig tmfConfig) {
    if (contractDefinitionStore == null) {
      contractDefinitionStore =
          new TMFBackedContractDefinitionStore(
              monitor, productCatalogApi(tmfConfig), tmfEdcMapper(tmfConfig));
    }
    return contractDefinitionStore;
  }

  public PolicyDefinitionStore policyDefinitionStore(TMFConfig tmfConfig) {
    if (policyDefinitionStore == null) {
      policyDefinitionStore =
          new TMFBackedPolicyDefinitionStore(monitor, productCatalogApi(tmfConfig));
    }
    return policyDefinitionStore;
  }

  public AssetIndex assetIndex(TMFConfig tmfConfig) {
    if (assetIndex == null) {
      assetIndex =
          new TMFBackedAssetIndex(
              monitor,
              productCatalogApi(tmfConfig),
              tmfEdcMapper(tmfConfig),
              criterionOperatorRegistry);
    }
    return assetIndex;
  }

  public DataAddressResolver dataAddressResolver(TMFConfig tmfConfig) {
    return assetIndex(tmfConfig);
  }
}
