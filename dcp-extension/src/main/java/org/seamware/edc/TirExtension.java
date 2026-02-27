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
 * dcp-extension
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

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import org.eclipse.edc.iam.verifiablecredentials.spi.validation.TrustedIssuerRegistry;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.seamware.edc.tir.EbsiTrustedIssuersRegistry;
import org.seamware.edc.tir.TirClient;

@Provides({TrustedIssuerRegistry.class})
public class TirExtension implements ServiceExtension {

  private TirClient tirClient;
  private TrustedIssuerRegistry trustedIssuerRegistry;

  @Inject private Monitor monitor;

  @Inject private OkHttpClient okHttpClient;

  @Inject private ObjectMapper objectMapper;

  @Override
  public void initialize(ServiceExtensionContext context) {
    TirConfig tirConfig = TirConfig.fromConfig(context.getConfig());
    if (tirConfig.isEnabled()) {
      context.registerService(TrustedIssuerRegistry.class, trustedIssuerRegistry(context));
    }
  }

  public TrustedIssuerRegistry trustedIssuerRegistry(ServiceExtensionContext context) {
    if (trustedIssuerRegistry == null) {
      trustedIssuerRegistry = new EbsiTrustedIssuersRegistry(tirClient(context));
    }
    return trustedIssuerRegistry;
  }

  public TirClient tirClient(ServiceExtensionContext context) {
    if (tirClient == null) {
      TirConfig tirConfig = TirConfig.fromConfig(context.getConfig());
      tirClient = new TirClient(monitor, okHttpClient, tirConfig.getTilAddress(), objectMapper);
    }
    return tirClient;
  }
}
