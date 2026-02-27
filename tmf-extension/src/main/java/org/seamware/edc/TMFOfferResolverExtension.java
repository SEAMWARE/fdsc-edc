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

import org.eclipse.edc.connector.controlplane.contract.spi.offer.ConsumerOfferResolver;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.seamware.edc.store.TMFEdcMapper;
import org.seamware.edc.store.TMForumConsumerOfferResolver;
import org.seamware.edc.tmf.ProductCatalogApiClient;

/** Extension to resolve offers through TMForum */
@Provides(ConsumerOfferResolver.class)
public class TMFOfferResolverExtension implements ServiceExtension {

  @Override
  public String name() {
    return "TMFOfferResolverExtension";
  }

  @Inject private ProductCatalogApiClient productCatalogApiClient;

  @Inject private TMFEdcMapper tmfEdcMapper;

  @Inject private Monitor monitor;

  @Override
  public void initialize(ServiceExtensionContext context) {
    if (TMFConfig.fromConfig(context.getConfig()).isEnabled()) {
      context.registerService(
          ConsumerOfferResolver.class,
          new TMForumConsumerOfferResolver(monitor, productCatalogApiClient, tmfEdcMapper));
    } else {
      monitor.info(
          "TMFExtension is not enabled, TMForumConsumerOfferResolver will not be registered.");
    }
  }
}
