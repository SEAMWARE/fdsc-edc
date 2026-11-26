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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.edc.boot.system.DependencyGraph;
import org.eclipse.edc.connector.controlplane.services.spi.catalog.CatalogProtocolService;
import org.eclipse.edc.connector.controlplane.services.spi.protocol.ProtocolTokenValidator;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.runtime.metamodel.annotation.Requires;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.seamware.edc.store.TMFEdcMapper;
import org.seamware.edc.store.TMForumBackedCatalogProtocolService;
import org.seamware.edc.tmf.ProductCatalogApiClient;

public class CatalogProtocolServiceExtensionTest {

  private static final String TMF_ENABLED_KEY = "tmfExtension.enabled";
  private static final String TMF_CATALOG_ENABLED_KEY = "tmfExtension.catalog.enabled";
  private static final String TEST_API_URL = "http://tmforum.test";
  // TMFConfig rejects an enabled extension without these endpoints.
  private static final List<String> TMF_API_KEYS =
      List.of(
          "tmfExtension.quoteApi",
          "tmfExtension.agreementApi",
          "tmfExtension.productOrderApi",
          "tmfExtension.productCatalogApi",
          "tmfExtension.productInventoryApi",
          "tmfExtension.usageManagementApi",
          "tmfExtension.partyCatalogApi");

  @Test
  public void testAnnotations_provide_and_do_not_require_the_catalog_protocol_service() {
    Provides provides = CatalogProtocolServiceExtension.class.getAnnotation(Provides.class);
    assertNotNull(provides, "The extension must declare @Provides.");
    assertTrue(
        Arrays.asList(provides.value()).contains(CatalogProtocolService.class),
        "@Provides must name CatalogProtocolService so the DSP catalog controllers inject later.");

    Requires requires = CatalogProtocolServiceExtension.class.getAnnotation(Requires.class);
    assertTrue(
        requires == null || !Arrays.asList(requires.value()).contains(CatalogProtocolService.class),
        "@Requires(CatalogProtocolService) next to @Provides creates a dependency cycle.");
  }

  @ParameterizedTest(name = "tmfExtension.enabled={0}, catalog.enabled={1}")
  @CsvSource({"true,true,true", "true,false,false", "false,true,false", "false,false,false"})
  public void testInitialize_registers_the_tmf_catalog_only_when_enabled(
      boolean tmfEnabled, boolean catalogEnabled, boolean expectRegistration) {
    Map<String, String> settings = new HashMap<>();
    settings.put(TMF_ENABLED_KEY, String.valueOf(tmfEnabled));
    settings.put(TMF_CATALOG_ENABLED_KEY, String.valueOf(catalogEnabled));
    TMF_API_KEYS.forEach(key -> settings.put(key, TEST_API_URL));
    ServiceExtensionContext context = mock(ServiceExtensionContext.class);
    when(context.getConfig()).thenReturn(ConfigFactory.fromMap(settings));

    newExtension().initialize(context);

    if (expectRegistration) {
      verify(context)
          .registerService(
              eq(CatalogProtocolService.class), any(TMForumBackedCatalogProtocolService.class));
    } else {
      verify(context, never()).registerService(eq(CatalogProtocolService.class), any());
    }
  }

  @Test
  public void testDependencyGraph_orders_stock_provider_then_tmf_catalog_then_injector() {
    ServiceExtensionContext context = mock(ServiceExtensionContext.class);
    // satisfies the remaining @Inject points without stub providers for each of them
    when(context.hasService(any())).thenReturn(true);
    ServiceExtension injector = new CatalogInjectingStub();
    ServiceExtension tmfCatalog = new CatalogProtocolServiceExtension();
    ServiceExtension stockProvider = new StockProviderStub();

    // deliberately in the wrong order
    DependencyGraph graph =
        DependencyGraph.of(context, List.of(injector, tmfCatalog, stockProvider));

    assertTrue(graph.isValid(), "The dependency graph should resolve all injection points.");
    List<ServiceExtension> sorted = graph.getExtensions();
    int stockIndex = sorted.indexOf(stockProvider);
    int tmfIndex = sorted.indexOf(tmfCatalog);
    int injectorIndex = sorted.indexOf(injector);
    assertTrue(
        stockIndex < tmfIndex,
        "The stock provider must run first, or its @Provider result replaces the TMForum catalog.");
    assertTrue(
        tmfIndex < injectorIndex,
        "The DSP catalog controller must be injected after the TMForum catalog is registered.");
  }

  private static CatalogProtocolServiceExtension newExtension() {
    CatalogProtocolServiceExtension extension = new CatalogProtocolServiceExtension();
    extension.productCatalogApi = mock(ProductCatalogApiClient.class);
    extension.monitor = mock(Monitor.class);
    extension.objectMapper = new ObjectMapper();
    extension.tmfEdcMapper = mock(TMFEdcMapper.class);
    extension.protocolTokenValidator = mock(ProtocolTokenValidator.class);
    extension.policyEngine = mock(PolicyEngine.class);
    return extension;
  }

  /** Stands in for the DSP catalog API extensions, which copy the service into a controller. */
  static class CatalogInjectingStub implements ServiceExtension {
    @Inject CatalogProtocolService catalogProtocolService;
  }

  /** Stands in for EDC's ControlPlaneServicesExtension. */
  static class StockProviderStub implements ServiceExtension {
    @Provider
    public CatalogProtocolService catalogProtocolService() {
      return mock(CatalogProtocolService.class);
    }

    @Provider
    public ProtocolTokenValidator protocolTokenValidator() {
      return mock(ProtocolTokenValidator.class);
    }
  }
}
