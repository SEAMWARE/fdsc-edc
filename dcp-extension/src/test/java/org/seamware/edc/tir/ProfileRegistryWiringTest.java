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
package org.seamware.edc.tir;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.eclipse.edc.connector.controlplane.ControlPlaneDefaultServicesExtension;
import org.eclipse.edc.protocol.spi.DataspaceProfileContextRegistry;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.junit.jupiter.api.Test;
import org.seamware.edc.TirExtension;

/**
 * The per-profile trust check only bites if live DSP traffic resolves its id extraction function on
 * {@link ProfileTrustCheckingContextRegistry}. Profiles are registered by upstream extensions that
 * {@code @Inject} the registry, so what decides this is the provider wiring: {@link TirExtension}
 * declares the registry in {@code @Provides}, which both orders it before every injector and
 * suppresses upstream's default provider. If either side of that ever changes, profiles would be
 * registered on an unwrapped core registry, and a profile without its own TIL would silently fall
 * back to the union-of-all-TILs check — accepting an issuer trusted only by another dataspace.
 *
 * <p>These assertions are the canary for that, in particular across EDC upgrades. They are not a
 * substitute for booting a runtime; see ADR-9.
 */
class ProfileRegistryWiringTest {

  @Test
  void tirExtensionDeclaresTheProfileRegistryAsProvided() {
    Provides provides = TirExtension.class.getAnnotation(Provides.class);

    assertNotNull(provides, "TirExtension must declare what it provides.");
    assertTrue(
        List.of(provides.value()).contains(DataspaceProfileContextRegistry.class),
        "Without @Provides, injectors are not ordered after TirExtension and upstream's default"
            + " provider supplies an unwrapped registry.");
  }

  @Test
  void upstreamProvidesTheRegistryOnlyAsADefault() {
    List<Method> providers =
        Arrays.stream(ControlPlaneDefaultServicesExtension.class.getDeclaredMethods())
            .filter(m -> m.getReturnType().equals(DataspaceProfileContextRegistry.class))
            .filter(m -> m.isAnnotationPresent(Provider.class))
            .toList();

    assertEquals(1, providers.size(), "Expected exactly one upstream provider of the registry.");
    assertTrue(
        providers.get(0).getAnnotation(Provider.class).isDefault(),
        "Upstream must provide the registry as a default only. A non-default provider would"
            + " compete with TirExtension and could win, leaving profiles unwrapped.");
  }

  @Test
  void theWrapperIsADropInForTheCoreRegistry() {
    // Upstream injectors take the core type, so the wrapper has to be assignable to it.
    assertTrue(
        DataspaceProfileContextRegistry.class.isAssignableFrom(
            ProfileTrustCheckingContextRegistry.class));
  }
}
