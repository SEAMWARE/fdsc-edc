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

import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VALUE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_CONSTRAINT_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_PERMISSION_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_RIGHT_OPERAND_ATTRIBUTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.util.Map;
import java.util.stream.Stream;
import org.eclipse.edc.participant.spi.ParticipantIdMapper;
import org.eclipse.edc.policy.model.Action;
import org.eclipse.edc.policy.model.AtomicConstraint;
import org.eclipse.edc.policy.model.LiteralExpression;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Unit tests for {@link TypePreservingJsonObjectFromPolicyTransformer}.
 *
 * <p>Verifies that literal expression values in ODRL constraints preserve their original Java types
 * when transformed to JSON-LD, rather than being coerced to strings.
 */
class TypePreservingJsonObjectFromPolicyTransformerTest {

  private static final String USE_ACTION = "http://www.w3.org/ns/odrl/2/use";
  private static final String LEFT_OPERAND_IRI = "https://example.com/leftOperand";

  private TypePreservingJsonObjectFromPolicyTransformer transformer;
  private TransformerContext context;

  @BeforeEach
  void setUp() {
    JsonBuilderFactory jsonFactory = Json.createBuilderFactory(Map.of());
    ParticipantIdMapper idMapper =
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
    transformer = new TypePreservingJsonObjectFromPolicyTransformer(jsonFactory, idMapper);
    context = mock(TransformerContext.class);
  }

  @Nested
  @DisplayName("Literal expression type preservation")
  class LiteralExpressionTypePreservation {

    /** Provides test arguments for each supported value type with the expected JSON value type. */
    static Stream<Arguments> typedValues() {
      return Stream.of(
          Arguments.of("Integer", 42, JsonValue.ValueType.NUMBER),
          Arguments.of("Long", 100L, JsonValue.ValueType.NUMBER),
          Arguments.of("Double", 3.14, JsonValue.ValueType.NUMBER),
          Arguments.of("Float", 2.5f, JsonValue.ValueType.NUMBER),
          Arguments.of("Boolean true", true, JsonValue.ValueType.TRUE),
          Arguments.of("Boolean false", false, JsonValue.ValueType.FALSE),
          Arguments.of("String", "hello", JsonValue.ValueType.STRING));
    }

    @ParameterizedTest(name = "{0} value preserves JSON type {2}")
    @MethodSource("typedValues")
    @DisplayName("Literal values preserve their JSON type in the right operand")
    void literalExpression_preservesType(
        String label, Object value, JsonValue.ValueType expectedType) {
      Policy policy = buildPolicyWithRightOperand(value);

      JsonObject result = transformer.transform(policy, context);

      JsonValue rightOperandValue = extractRightOperandValue(result);
      assertNotNull(rightOperandValue, "Right operand @value must be present");
      assertEquals(
          expectedType,
          rightOperandValue.getValueType(),
          "Right operand @value should have JSON type " + expectedType);
    }

    @Test
    @DisplayName("Integer value is preserved as exact numeric value")
    void integerValue_preservedExactly() {
      Policy policy = buildPolicyWithRightOperand(42);

      JsonObject result = transformer.transform(policy, context);

      JsonValue rightOperandValue = extractRightOperandValue(result);
      assertEquals(42, ((jakarta.json.JsonNumber) rightOperandValue).intValue());
    }

    @Test
    @DisplayName("Double value is preserved as exact numeric value")
    void doubleValue_preservedExactly() {
      Policy policy = buildPolicyWithRightOperand(3.14);

      JsonObject result = transformer.transform(policy, context);

      JsonValue rightOperandValue = extractRightOperandValue(result);
      assertEquals(3.14, ((jakarta.json.JsonNumber) rightOperandValue).doubleValue(), 0.001);
    }

    @Test
    @DisplayName("Boolean true is preserved as JSON true")
    void booleanTrue_preservedAsJsonTrue() {
      Policy policy = buildPolicyWithRightOperand(true);

      JsonObject result = transformer.transform(policy, context);

      JsonValue rightOperandValue = extractRightOperandValue(result);
      assertEquals(JsonValue.TRUE, rightOperandValue);
    }

    @Test
    @DisplayName("Boolean false is preserved as JSON false")
    void booleanFalse_preservedAsJsonFalse() {
      Policy policy = buildPolicyWithRightOperand(false);

      JsonObject result = transformer.transform(policy, context);

      JsonValue rightOperandValue = extractRightOperandValue(result);
      assertEquals(JsonValue.FALSE, rightOperandValue);
    }

    @Test
    @DisplayName("String value is preserved as JSON string")
    void stringValue_preservedAsJsonString() {
      Policy policy = buildPolicyWithRightOperand("test-value");

      JsonObject result = transformer.transform(policy, context);

      JsonValue rightOperandValue = extractRightOperandValue(result);
      assertEquals("test-value", ((jakarta.json.JsonString) rightOperandValue).getString());
    }
  }

  @Nested
  @DisplayName("Policy structure")
  class PolicyStructure {

    @Test
    @DisplayName("Transformed policy contains permission with constraint")
    void transform_policyHasPermissionWithConstraint() {
      Policy policy = buildPolicyWithRightOperand("value");

      JsonObject result = transformer.transform(policy, context);

      assertNotNull(result);
      assertTrue(result.containsKey(ODRL_PERMISSION_ATTRIBUTE));
    }

    @Test
    @DisplayName("Empty permission list is omitted when omitEmptyRules is true")
    void transform_omitEmptyRules() {
      JsonBuilderFactory jsonFactory = Json.createBuilderFactory(Map.of());
      ParticipantIdMapper idMapper =
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
      var omittingTransformer =
          new TypePreservingJsonObjectFromPolicyTransformer(jsonFactory, idMapper, false, true);
      Policy policy = Policy.Builder.newInstance().build();

      JsonObject result = omittingTransformer.transform(policy, context);

      assertNotNull(result);
      assertTrue(
          !result.containsKey(ODRL_PERMISSION_ATTRIBUTE),
          "Empty permission array should be omitted when omitEmptyRules is true");
    }
  }

  /**
   * Builds a minimal {@link Policy} with a single permission containing one atomic constraint whose
   * right operand is the given value.
   */
  private Policy buildPolicyWithRightOperand(Object rightOperandValue) {
    var constraint =
        AtomicConstraint.Builder.newInstance()
            .leftExpression(new LiteralExpression(LEFT_OPERAND_IRI))
            .operator(Operator.EQ)
            .rightExpression(new LiteralExpression(rightOperandValue))
            .build();

    var permission =
        Permission.Builder.newInstance()
            .action(Action.Builder.newInstance().type(USE_ACTION).build())
            .constraint(constraint)
            .build();

    return Policy.Builder.newInstance().permission(permission).build();
  }

  /**
   * Extracts the {@code @value} of the right operand from the first constraint of the first
   * permission in the transformed JSON-LD policy.
   */
  private JsonValue extractRightOperandValue(JsonObject policyJson) {
    var permissions = policyJson.getJsonArray(ODRL_PERMISSION_ATTRIBUTE);
    assertNotNull(permissions, "Policy must have permissions");
    assertEquals(1, permissions.size());

    var permission = permissions.getJsonObject(0);
    var constraints = permission.getJsonArray(ODRL_CONSTRAINT_ATTRIBUTE);
    assertNotNull(constraints, "Permission must have constraints");
    assertEquals(1, constraints.size());

    var constraint = constraints.getJsonObject(0);
    var rightOperand = constraint.getJsonObject(ODRL_RIGHT_OPERAND_ATTRIBUTE);
    assertNotNull(rightOperand, "Constraint must have right operand");

    return rightOperand.get(VALUE);
  }
}
