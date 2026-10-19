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

import static org.eclipse.edc.protocol.dsp.spi.type.Dsp2025Constants.DSP_TRANSFORMER_CONTEXT_V_2025_1;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.eclipse.edc.connector.controlplane.transform.odrl.OdrlTransformersFactory;
import org.eclipse.edc.connector.controlplane.transform.odrl.from.JsonObjectFromPolicyTransformer;
import org.eclipse.edc.participant.spi.ParticipantIdMapper;
import org.eclipse.edc.policy.model.Constraint;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transform.TypeTransformerRegistryImpl;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TMFContractNegotiationExtensionTransformerTest {

  private static final ParticipantIdMapper IDENTITY_MAPPER =
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

  @Spy
  private TypeTransformerRegistry typeTransformerRegistry =
      new TypeTransformerRegistryImpl(mock(Monitor.class));

  @Mock private TypeManager typeManager;
  @Mock private TMFConfig tmfConfig;
  @InjectMocks private TMFContractNegotiationExtension extension;

  private static Stream<Arguments> registries() {
    return Stream.of(
        Arguments.of(
            "default context", (Function<TypeTransformerRegistry, TypeTransformerRegistry>) r -> r),
        Arguments.of(
            "DSP 2025/1 context",
            (Function<TypeTransformerRegistry, TypeTransformerRegistry>)
                r -> r.forContext(DSP_TRANSFORMER_CONTEXT_V_2025_1)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("registries")
  public void testPrepare_replaces_the_policy_transformers_edc_registered(
      String name, Function<TypeTransformerRegistry, TypeTransformerRegistry> scope) {
    when(tmfConfig.isEnabled()).thenReturn(true);
    TypeTransformerRegistry registry = scope.apply(typeTransformerRegistry);
    // what EDC registers while the extensions initialize
    registry.register(
        new JsonObjectFromPolicyTransformer(Json.createBuilderFactory(Map.of()), IDENTITY_MAPPER));
    OdrlTransformersFactory.jsonObjectToOdrlTransformers(IDENTITY_MAPPER)
        .forEach(registry::register);

    extension.prepare();

    assertInstanceOf(
        TypePreservingJsonObjectFromPolicyTransformer.class,
        registry.transformerFor(Policy.Builder.newInstance().build(), JsonObject.class),
        "Policies should be serialized with their value types preserved.");
    assertInstanceOf(
        TypePreservingJsonObjectToConstraintTransformer.class,
        registry.transformerFor(Json.createObjectBuilder().build(), Constraint.class),
        "Constraints should be read with their value types preserved.");
  }

  @Test
  public void testPrepare_registers_nothing_when_the_extension_is_disabled() {
    when(tmfConfig.isEnabled()).thenReturn(false);

    extension.prepare();

    verify(typeTransformerRegistry, never()).register(any());
    verify(typeTransformerRegistry, never()).forContext(any());
  }
}
