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

import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VALUE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_AND_CONSTRAINT_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_CONSTRAINT_TYPE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_LEFT_OPERAND_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_OPERATOR_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_OR_CONSTRAINT_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_RIGHT_OPERAND_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_XONE_CONSTRAINT_ATTRIBUTE;

import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.policy.model.AndConstraint;
import org.eclipse.edc.policy.model.AtomicConstraint;
import org.eclipse.edc.policy.model.Constraint;
import org.eclipse.edc.policy.model.LiteralExpression;
import org.eclipse.edc.policy.model.MultiplicityConstraint;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.OrConstraint;
import org.eclipse.edc.policy.model.XoneConstraint;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Converts an ODRL constraint {@link JsonObject} in JSON-LD expanded form to a {@link Constraint},
 * preserving the original types of literal values.
 *
 * <p>This is a drop-in replacement for EDC's {@code JsonObjectToConstraintTransformer} that fixes
 * the loss of type information during JSON-LD expansion. The JSON-LD expansion algorithm normalizes
 * typed values into {@code {"@value": "42", "@type": "xsd:integer"}} form — the numeric value
 * becomes a string with an XSD type annotation. The upstream transformer ignores the {@code @type}
 * and always returns the {@code @value} as a Java {@link String}, losing the original type.
 *
 * <p>This version inspects the {@code @type} annotation when the {@code @value} is a string and
 * parses it back to the appropriate Java type ({@link Integer}, {@link Long}, {@link Double},
 * {@link Boolean}).
 */
public class TypePreservingJsonObjectToConstraintTransformer
    extends AbstractJsonLdTransformer<JsonObject, Constraint> {

  /** XSD namespace prefix for XML Schema Datatypes. */
  static final String XSD_NAMESPACE = "http://www.w3.org/2001/XMLSchema#";

  /** XSD integer types that should be parsed as {@link Long}. */
  static final Set<String> XSD_INTEGER_TYPES =
      Set.of(
          XSD_NAMESPACE + "integer",
          XSD_NAMESPACE + "int",
          XSD_NAMESPACE + "long",
          XSD_NAMESPACE + "short",
          XSD_NAMESPACE + "byte",
          XSD_NAMESPACE + "nonNegativeInteger",
          XSD_NAMESPACE + "positiveInteger",
          XSD_NAMESPACE + "nonPositiveInteger",
          XSD_NAMESPACE + "negativeInteger",
          XSD_NAMESPACE + "unsignedLong",
          XSD_NAMESPACE + "unsignedInt",
          XSD_NAMESPACE + "unsignedShort",
          XSD_NAMESPACE + "unsignedByte");

  /** XSD floating-point types that should be parsed as {@link Double}. */
  static final Set<String> XSD_DECIMAL_TYPES =
      Set.of(XSD_NAMESPACE + "double", XSD_NAMESPACE + "float", XSD_NAMESPACE + "decimal");

  /** XSD boolean type. */
  static final String XSD_BOOLEAN = XSD_NAMESPACE + "boolean";

  private final Map<String, Supplier<MultiplicityConstraint.Builder<?, ?>>> operands =
      Map.of(
          ODRL_AND_CONSTRAINT_ATTRIBUTE, AndConstraint.Builder::newInstance,
          ODRL_OR_CONSTRAINT_ATTRIBUTE, OrConstraint.Builder::newInstance,
          ODRL_XONE_CONSTRAINT_ATTRIBUTE, XoneConstraint.Builder::newInstance);

  public TypePreservingJsonObjectToConstraintTransformer() {
    super(JsonObject.class, Constraint.class);
  }

  @Override
  public @Nullable Constraint transform(
      @NotNull JsonObject object, @NotNull TransformerContext context) {
    var logicalConstraint = transformLogicalConstraint(object, context);
    if (logicalConstraint != null) {
      return logicalConstraint;
    }
    return transformAtomicConstraint(object, context);
  }

  private AtomicConstraint transformAtomicConstraint(
      @NotNull JsonObject object, @NotNull TransformerContext context) {
    var builder = AtomicConstraint.Builder.newInstance();

    if (!transformMandatoryString(
        object.get(ODRL_LEFT_OPERAND_ATTRIBUTE),
        s -> builder.leftExpression(new LiteralExpression(s)),
        context)) {
      context
          .problem()
          .missingProperty()
          .type(ODRL_CONSTRAINT_TYPE)
          .property(ODRL_LEFT_OPERAND_ATTRIBUTE)
          .report();
      return null;
    }

    var jsonOperator = object.get(ODRL_OPERATOR_ATTRIBUTE);
    if (jsonOperator == null) {
      context
          .problem()
          .missingProperty()
          .type(ODRL_CONSTRAINT_TYPE)
          .property(ODRL_OPERATOR_ATTRIBUTE)
          .report();
      return null;
    }

    builder.operator(transformObject(jsonOperator, Operator.class, context));

    var rightOperand = extractComplexValue(object.get(ODRL_RIGHT_OPERAND_ATTRIBUTE));
    builder.rightExpression(new LiteralExpression(rightOperand));

    return builderResult(builder::build, context);
  }

  @Nullable
  private MultiplicityConstraint transformLogicalConstraint(
      @NotNull JsonObject object, @NotNull TransformerContext context) {
    return operands.entrySet().stream()
        .filter(entry -> object.containsKey(entry.getKey()))
        .findFirst()
        .map(
            entry -> {
              var builder =
                  entry
                      .getValue()
                      .get()
                      .constraints(
                          transformArray(object.get(entry.getKey()), Constraint.class, context));
              return builderResult(builder::build, context);
            })
        .orElse(null);
  }

  /**
   * Extracts a value from a JSON-LD value node, preserving type information from {@code @type}
   * annotations.
   *
   * <p>Handles the following JSON-LD forms:
   *
   * <ul>
   *   <li>Arrays: single-element arrays are unwrapped; multi-element arrays returned as-is
   *   <li>Objects with {@code @value}: the value is extracted and, if present, the {@code @type}
   *       annotation is used to parse string values back to their original Java types
   *   <li>Scalars: extracted directly via {@link #extractValue(JsonValue)}
   * </ul>
   *
   * @param root the JSON-LD value node to extract from
   * @return the extracted Java value with preserved type information
   */
  private Object extractComplexValue(JsonValue root) {
    switch (root.getValueType()) {
      case ARRAY -> {
        var array = root.asJsonArray();
        if (array.size() != 1) {
          return array;
        }
        return extractComplexValue(array.get(0));
      }
      case OBJECT -> {
        var jsonObject = root.asJsonObject();
        var valueProp = jsonObject.get(VALUE);
        if (valueProp != null) {
          var typeProp = jsonObject.get(TYPE);
          return extractTypedValue(valueProp, typeProp);
        }
      }
      default -> {
        return extractValue(root);
      }
    }
    return extractValue(root);
  }

  /**
   * Extracts a value from a {@code @value} node, using the {@code @type} annotation to restore the
   * original Java type when the value has been normalized to a string by JSON-LD expansion.
   *
   * @param value the {@code @value} property from a JSON-LD value node
   * @param type the {@code @type} property, or {@code null} if absent
   * @return the value parsed to its original Java type, or as-is if no type annotation is present
   */
  private Object extractTypedValue(@NotNull JsonValue value, @Nullable JsonValue type) {
    if (value.getValueType() != JsonValue.ValueType.STRING || type == null) {
      return extractValue(value);
    }

    String typeIri = (type instanceof JsonString jsonStr) ? jsonStr.getString() : type.toString();
    String stringValue = ((JsonString) value).getString();

    try {
      if (XSD_INTEGER_TYPES.contains(typeIri)) {
        return Long.parseLong(stringValue);
      }
      if (XSD_DECIMAL_TYPES.contains(typeIri)) {
        return Double.parseDouble(stringValue);
      }
      if (XSD_BOOLEAN.equals(typeIri)) {
        return Boolean.parseBoolean(stringValue);
      }
    } catch (NumberFormatException e) {
      // fall through to return the raw string
    }

    return stringValue;
  }

  /**
   * Extracts the scalar value from a JSON value node. Numbers, booleans, and strings are converted
   * to their Java equivalents; all other types are returned as-is.
   *
   * @param value the JSON value to extract
   * @return the Java representation of the value
   */
  private Object extractValue(JsonValue value) {
    switch (value.getValueType()) {
      case STRING -> {
        return ((JsonString) value).getString();
      }
      case NUMBER -> {
        return ((JsonNumber) value).numberValue();
      }
      case TRUE -> {
        return true;
      }
      case FALSE -> {
        return false;
      }
      default -> {
        return value;
      }
    }
  }
}
