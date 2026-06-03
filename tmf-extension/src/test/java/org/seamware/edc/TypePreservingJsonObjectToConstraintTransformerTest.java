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

import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VALUE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_LEFT_OPERAND_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_OPERATOR_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_RIGHT_OPERAND_ATTRIBUTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.util.stream.Stream;
import org.eclipse.edc.policy.model.AtomicConstraint;
import org.eclipse.edc.policy.model.Constraint;
import org.eclipse.edc.policy.model.LiteralExpression;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Unit tests for {@link TypePreservingJsonObjectToConstraintTransformer}.
 *
 * <p>Verifies that XSD-typed {@code @value} strings in expanded JSON-LD are parsed back to their
 * original Java types when constructing {@link LiteralExpression} values.
 */
class TypePreservingJsonObjectToConstraintTransformerTest {

  private static final String LEFT_OPERAND_IRI = "https://example.com/leftOperand";
  private static final String EQ_OPERATOR = "http://www.w3.org/ns/odrl/2/eq";

  private TypePreservingJsonObjectToConstraintTransformer transformer;
  private TransformerContext context;

  @BeforeEach
  void setUp() {
    transformer = new TypePreservingJsonObjectToConstraintTransformer();
    context = mock(TransformerContext.class);
    when(context.transform(any(JsonObject.class), eq(Operator.class))).thenReturn(Operator.EQ);
  }

  @Nested
  @DisplayName("XSD typed value restoration from expanded JSON-LD")
  class XsdTypedValueRestoration {

    /**
     * Provides test arguments for XSD typed values as produced by JSON-LD expansion. Each entry
     * contains the XSD type IRI, the string representation, the expected Java type, and the
     * expected value.
     */
    static Stream<Arguments> xsdTypedValues() {
      return Stream.of(
          Arguments.of(
              "xsd:integer", "http://www.w3.org/2001/XMLSchema#integer", "42", Long.class, 42L),
          Arguments.of("xsd:int", "http://www.w3.org/2001/XMLSchema#int", "100", Long.class, 100L),
          Arguments.of(
              "xsd:long",
              "http://www.w3.org/2001/XMLSchema#long",
              "9999999999",
              Long.class,
              9999999999L),
          Arguments.of(
              "xsd:double",
              "http://www.w3.org/2001/XMLSchema#double",
              "3.14E0",
              Double.class,
              3.14),
          Arguments.of(
              "xsd:float", "http://www.w3.org/2001/XMLSchema#float", "2.5", Double.class, 2.5),
          Arguments.of(
              "xsd:decimal",
              "http://www.w3.org/2001/XMLSchema#decimal",
              "99.99",
              Double.class,
              99.99),
          Arguments.of(
              "xsd:boolean true",
              "http://www.w3.org/2001/XMLSchema#boolean",
              "true",
              Boolean.class,
              true),
          Arguments.of(
              "xsd:boolean false",
              "http://www.w3.org/2001/XMLSchema#boolean",
              "false",
              Boolean.class,
              false));
    }

    @ParameterizedTest(name = "{0}: \"{2}\" restored to {3}")
    @MethodSource("xsdTypedValues")
    @DisplayName("XSD typed string values are parsed to their original Java types")
    void xsdTypedValue_restoredToOriginalType(
        String label,
        String xsdType,
        String stringValue,
        Class<?> expectedType,
        Object expectedValue) {
      JsonObject constraint = buildExpandedConstraint(stringValue, xsdType);

      Constraint result = transformer.transform(constraint, context);

      Object rightOperandValue = extractRightOperandValue(result);
      assertInstanceOf(expectedType, rightOperandValue);
      assertEquals(expectedValue, rightOperandValue);
    }

    @Test
    @DisplayName("String value without @type remains a String")
    void stringValue_withoutType_remainsString() {
      JsonObject constraint = buildExpandedConstraintNoType("hello");

      Constraint result = transformer.transform(constraint, context);

      Object rightOperandValue = extractRightOperandValue(result);
      assertInstanceOf(String.class, rightOperandValue);
      assertEquals("hello", rightOperandValue);
    }

    @Test
    @DisplayName("Unknown @type preserves the string value")
    void unknownType_preservesString() {
      JsonObject constraint =
          buildExpandedConstraint("some-value", "http://example.com/unknownType");

      Constraint result = transformer.transform(constraint, context);

      Object rightOperandValue = extractRightOperandValue(result);
      assertInstanceOf(String.class, rightOperandValue);
      assertEquals("some-value", rightOperandValue);
    }

    @Test
    @DisplayName("Malformed number with integer @type falls back to string")
    void malformedNumber_fallsBackToString() {
      JsonObject constraint =
          buildExpandedConstraint("not-a-number", "http://www.w3.org/2001/XMLSchema#integer");

      Constraint result = transformer.transform(constraint, context);

      Object rightOperandValue = extractRightOperandValue(result);
      assertInstanceOf(String.class, rightOperandValue);
      assertEquals("not-a-number", rightOperandValue);
    }
  }

  @Nested
  @DisplayName("Native JSON value preservation")
  class NativeJsonValuePreservation {

    @Test
    @DisplayName("JSON number in @value is preserved as number")
    void jsonNumber_preservedDirectly() {
      JsonObject constraint =
          Json.createObjectBuilder()
              .add(
                  ODRL_LEFT_OPERAND_ATTRIBUTE,
                  Json.createArrayBuilder()
                      .add(Json.createObjectBuilder().add(ID, LEFT_OPERAND_IRI)))
              .add(
                  ODRL_OPERATOR_ATTRIBUTE,
                  Json.createArrayBuilder().add(Json.createObjectBuilder().add(ID, EQ_OPERATOR)))
              .add(
                  ODRL_RIGHT_OPERAND_ATTRIBUTE,
                  Json.createArrayBuilder().add(Json.createObjectBuilder().add(VALUE, 42)))
              .build();

      Constraint result = transformer.transform(constraint, context);

      Object rightOperandValue = extractRightOperandValue(result);
      assertInstanceOf(Number.class, rightOperandValue);
      assertEquals(42, ((Number) rightOperandValue).intValue());
    }

    @Test
    @DisplayName("JSON boolean true in value position is preserved")
    void jsonTrue_preserved() {
      JsonObject constraint =
          Json.createObjectBuilder()
              .add(
                  ODRL_LEFT_OPERAND_ATTRIBUTE,
                  Json.createArrayBuilder()
                      .add(Json.createObjectBuilder().add(ID, LEFT_OPERAND_IRI)))
              .add(
                  ODRL_OPERATOR_ATTRIBUTE,
                  Json.createArrayBuilder().add(Json.createObjectBuilder().add(ID, EQ_OPERATOR)))
              .add(
                  ODRL_RIGHT_OPERAND_ATTRIBUTE,
                  Json.createArrayBuilder().add(Json.createObjectBuilder().add(VALUE, true)))
              .build();

      Constraint result = transformer.transform(constraint, context);

      Object rightOperandValue = extractRightOperandValue(result);
      assertEquals(true, rightOperandValue);
    }
  }

  /**
   * Builds an expanded JSON-LD constraint with a typed {@code @value} and {@code @type}, as
   * produced by JSON-LD expansion of numeric or boolean values.
   */
  private JsonObject buildExpandedConstraint(String value, String xsdType) {
    return Json.createObjectBuilder()
        .add(
            ODRL_LEFT_OPERAND_ATTRIBUTE,
            Json.createArrayBuilder().add(Json.createObjectBuilder().add(ID, LEFT_OPERAND_IRI)))
        .add(
            ODRL_OPERATOR_ATTRIBUTE,
            Json.createArrayBuilder().add(Json.createObjectBuilder().add(ID, EQ_OPERATOR)))
        .add(
            ODRL_RIGHT_OPERAND_ATTRIBUTE,
            Json.createArrayBuilder()
                .add(Json.createObjectBuilder().add(VALUE, value).add(TYPE, xsdType)))
        .build();
  }

  /** Builds an expanded JSON-LD constraint with a plain string {@code @value} and no @type. */
  private JsonObject buildExpandedConstraintNoType(String value) {
    return Json.createObjectBuilder()
        .add(
            ODRL_LEFT_OPERAND_ATTRIBUTE,
            Json.createArrayBuilder().add(Json.createObjectBuilder().add(ID, LEFT_OPERAND_IRI)))
        .add(
            ODRL_OPERATOR_ATTRIBUTE,
            Json.createArrayBuilder().add(Json.createObjectBuilder().add(ID, EQ_OPERATOR)))
        .add(
            ODRL_RIGHT_OPERAND_ATTRIBUTE,
            Json.createArrayBuilder().add(Json.createObjectBuilder().add(VALUE, value)))
        .build();
  }

  /** Extracts the right operand value from a transformed {@link AtomicConstraint}. */
  private Object extractRightOperandValue(Constraint constraint) {
    assertNotNull(constraint);
    assertInstanceOf(AtomicConstraint.class, constraint);
    var atomic = (AtomicConstraint) constraint;
    var rightExpression = atomic.getRightExpression();
    assertInstanceOf(LiteralExpression.class, rightExpression);
    return ((LiteralExpression) rightExpression).getValue();
  }
}
