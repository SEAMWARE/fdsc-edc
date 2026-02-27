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
package org.seamware.edc.services;

/*-
 * #%L
 * test-extension
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
import org.eclipse.edc.protocol.spi.DefaultParticipantIdExtractionFunction;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.iam.AudienceResolver;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.seamware.edc.TestConfig;

@Provides({
  IdentityService.class,
  AudienceResolver.class,
  DefaultParticipantIdExtractionFunction.class
})
public class TestIdentityExtension implements ServiceExtension {

  private static final String NAME = "Test Identity Extension";

  @Override
  public String name() {
    return NAME;
  }

  @Inject private Monitor monitor;

  @Inject private ObjectMapper objectMapper;

  @Override
  public void initialize(ServiceExtensionContext context) {
    TestConfig testConfig = TestConfig.fromConfig(context.getConfig());
    if (!testConfig.getIdentityConfig().enabled()) {
      monitor.info("Test identity services are not enabled.");
      return;
    }

    context.registerService(IdentityService.class, identityService(context));
    context.registerService(AudienceResolver.class, audienceResolver());
    context.registerService(
        DefaultParticipantIdExtractionFunction.class, defaultParticipantIdExtractionFunction());
  }

  private IdentityService identityService(ServiceExtensionContext context) {
    return new TestIdentityService(monitor, objectMapper, context.getParticipantId());
  }

  private AudienceResolver audienceResolver() {
    return new NoopAudienceResolver(monitor);
  }

  private DefaultParticipantIdExtractionFunction defaultParticipantIdExtractionFunction() {
    return new TestParticipantIdExtractionFunction(monitor);
  }
}
