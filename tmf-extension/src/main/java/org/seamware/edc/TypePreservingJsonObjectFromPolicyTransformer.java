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

import static jakarta.json.stream.JsonCollectors.toJsonArray;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VALUE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_ACTION_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_AND_CONSTRAINT_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_ASSIGNEE_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_ASSIGNER_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_CONSEQUENCE_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_CONSTRAINT_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_DUTY_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_INCLUDED_IN_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_LEFT_OPERAND_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_OBLIGATION_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_OPERATOR_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_OR_CONSTRAINT_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_PERMISSION_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_POLICY_TYPE_AGREEMENT;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_POLICY_TYPE_OFFER;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_POLICY_TYPE_SET;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_PROHIBITION_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_REFINEMENT_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_REMEDY_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_RIGHT_OPERAND_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_TARGET_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_XONE_CONSTRAINT_ATTRIBUTE;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.participant.spi.ParticipantIdMapper;
import org.eclipse.edc.policy.model.Action;
import org.eclipse.edc.policy.model.AndConstraint;
import org.eclipse.edc.policy.model.AtomicConstraint;
import org.eclipse.edc.policy.model.Constraint;
import org.eclipse.edc.policy.model.Duty;
import org.eclipse.edc.policy.model.Expression;
import org.eclipse.edc.policy.model.LiteralExpression;
import org.eclipse.edc.policy.model.MultiplicityConstraint;
import org.eclipse.edc.policy.model.OrConstraint;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.policy.model.PolicyType;
import org.eclipse.edc.policy.model.Prohibition;
import org.eclipse.edc.policy.model.Rule;
import org.eclipse.edc.policy.model.XoneConstraint;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Transforms a {@link Policy} to an ODRL {@link JsonObject} in expanded JSON-LD form, preserving
 * the original Java types of constraint literal values.
 *
 * <p>This is a drop-in replacement for EDC's {@code JsonObjectFromPolicyTransformer} that fixes the
 * loss of type information in {@link LiteralExpression} values. The upstream implementation calls
 * {@code expression.getValue().toString()} unconditionally, producing JSON strings even for numeric
 * and boolean operands. This version inspects the runtime type and emits the correct JSON value
 * type ({@code JsonNumber} for numbers, {@code JsonValue.TRUE}/{@code FALSE} for booleans).
 */
public class TypePreservingJsonObjectFromPolicyTransformer
    extends AbstractJsonLdTransformer<Policy, JsonObject> {

  private final JsonBuilderFactory jsonFactory;
  private final ParticipantIdMapper participantIdMapper;
  private final boolean participantsAsId;
  private final boolean omitEmptyRules;

  /**
   * Creates a new type-preserving policy-to-JSON-LD transformer with default configuration
   * (participants rendered as plain values, empty rule arrays included).
   *
   * @param jsonFactory the Jakarta JSON builder factory
   * @param participantIdMapper the mapper for converting participant IDs to/from IRIs
   */
  public TypePreservingJsonObjectFromPolicyTransformer(
      JsonBuilderFactory jsonFactory, ParticipantIdMapper participantIdMapper) {
    this(jsonFactory, participantIdMapper, false, false);
  }

  /**
   * Creates a new type-preserving policy-to-JSON-LD transformer.
   *
   * @param jsonFactory the Jakarta JSON builder factory
   * @param participantIdMapper the mapper for converting participant IDs to/from IRIs
   * @param participantsAsId when {@code true}, assignee/assigner are rendered as {@code {"@id":
   *     "..."}} objects; when {@code false}, as plain string values
   * @param omitEmptyRules when {@code true}, empty permission/prohibition/obligation arrays are
   *     omitted from the output
   */
  public TypePreservingJsonObjectFromPolicyTransformer(
      JsonBuilderFactory jsonFactory,
      ParticipantIdMapper participantIdMapper,
      boolean participantsAsId,
      boolean omitEmptyRules) {
    super(Policy.class, JsonObject.class);
    this.jsonFactory = jsonFactory;
    this.participantIdMapper = participantIdMapper;
    this.participantsAsId = participantsAsId;
    this.omitEmptyRules = omitEmptyRules;
  }

  @Override
  public @Nullable JsonObject transform(
      @NotNull Policy policy, @NotNull TransformerContext context) {
    return policy.accept(new TypePreservingVisitor());
  }

  /**
   * Walks the policy object model, transforming it to a {@link JsonObject} while preserving literal
   * value types.
   */
  private class TypePreservingVisitor
      implements Policy.Visitor<JsonObject>,
          Rule.Visitor<JsonObject>,
          Constraint.Visitor<JsonObject>,
          Expression.Visitor<JsonObject> {

    @Override
    public JsonObject visitAndConstraint(AndConstraint andConstraint) {
      return visitMultiplicityConstraint(ODRL_AND_CONSTRAINT_ATTRIBUTE, andConstraint);
    }

    @Override
    public JsonObject visitOrConstraint(OrConstraint orConstraint) {
      return visitMultiplicityConstraint(ODRL_OR_CONSTRAINT_ATTRIBUTE, orConstraint);
    }

    @Override
    public JsonObject visitXoneConstraint(XoneConstraint xoneConstraint) {
      return visitMultiplicityConstraint(ODRL_XONE_CONSTRAINT_ATTRIBUTE, xoneConstraint);
    }

    @Override
    public JsonObject visitAtomicConstraint(AtomicConstraint atomicConstraint) {
      var constraintBuilder = jsonFactory.createObjectBuilder();

      var leftOperand =
          atomicConstraint
              .getLeftExpression()
              .accept((Expression.Visitor<String>) expr -> expr.getValue().toString());
      constraintBuilder.add(
          ODRL_LEFT_OPERAND_ATTRIBUTE,
          jsonFactory
              .createArrayBuilder()
              .add(jsonFactory.createObjectBuilder().add(ID, leftOperand)));

      var operator = atomicConstraint.getOperator().getOdrlRepresentation();
      constraintBuilder.add(
          ODRL_OPERATOR_ATTRIBUTE,
          jsonFactory
              .createArrayBuilder()
              .add(jsonFactory.createObjectBuilder().add(ID, operator)));

      constraintBuilder.add(
          ODRL_RIGHT_OPERAND_ATTRIBUTE, atomicConstraint.getRightExpression().accept(this));

      return constraintBuilder.build();
    }

    @Override
    public JsonObject visitLiteralExpression(LiteralExpression expression) {
      var builder = jsonFactory.createObjectBuilder();
      Object value = expression.getValue();

      if (value instanceof Integer i) {
        builder.add(VALUE, Json.createValue(i.intValue()));
      } else if (value instanceof Long l) {
        builder.add(VALUE, Json.createValue(l.longValue()));
      } else if (value instanceof Double d) {
        builder.add(VALUE, Json.createValue(d.doubleValue()));
      } else if (value instanceof Float f) {
        builder.add(VALUE, Json.createValue(f.doubleValue()));
      } else if (value instanceof Number n) {
        builder.add(VALUE, Json.createValue(n.doubleValue()));
      } else if (value instanceof Boolean b) {
        builder.add(VALUE, b ? JsonValue.TRUE : JsonValue.FALSE);
      } else {
        builder.add(VALUE, Json.createValue(value.toString()));
      }

      return builder.build();
    }

    @Override
    public JsonObject visitPolicy(Policy policy) {
      var builder =
          jsonFactory
              .createObjectBuilder()
              .add(ID, UUID.randomUUID().toString())
              .add(TYPE, getTypeAsString(policy.getType()));

      if (!omitEmptyRules || !policy.getPermissions().isEmpty()) {
        var permissionsBuilder = jsonFactory.createArrayBuilder();
        policy.getPermissions().forEach(p -> permissionsBuilder.add(p.accept(this)));
        builder.add(ODRL_PERMISSION_ATTRIBUTE, permissionsBuilder);
      }

      if (!omitEmptyRules || !policy.getProhibitions().isEmpty()) {
        var prohibitionsBuilder = jsonFactory.createArrayBuilder();
        policy.getProhibitions().forEach(p -> prohibitionsBuilder.add(p.accept(this)));
        builder.add(ODRL_PROHIBITION_ATTRIBUTE, prohibitionsBuilder);
      }

      if (!omitEmptyRules || !policy.getObligations().isEmpty()) {
        var obligationsBuilder = jsonFactory.createArrayBuilder();
        policy.getObligations().forEach(d -> obligationsBuilder.add(d.accept(this)));
        builder.add(ODRL_OBLIGATION_ATTRIBUTE, obligationsBuilder);
      }

      Optional.ofNullable(policy.getAssignee())
          .map(participantIdMapper::toIri)
          .ifPresent(it -> builder.add(ODRL_ASSIGNEE_ATTRIBUTE, visitParticipantId(it)));

      Optional.ofNullable(policy.getAssigner())
          .map(participantIdMapper::toIri)
          .ifPresent(it -> builder.add(ODRL_ASSIGNER_ATTRIBUTE, visitParticipantId(it)));

      Optional.ofNullable(policy.getTarget())
          .ifPresent(
              target ->
                  builder.add(
                      ODRL_TARGET_ATTRIBUTE,
                      jsonFactory
                          .createArrayBuilder()
                          .add(jsonFactory.createObjectBuilder().add(ID, target))));

      return builder.build();
    }

    @Override
    public JsonObject visitPermission(Permission permission) {
      var permissionBuilder = visitRule(permission);

      if (permission.getDuties() != null && !permission.getDuties().isEmpty()) {
        var dutiesBuilder = jsonFactory.createArrayBuilder();
        for (var duty : permission.getDuties()) {
          dutiesBuilder.add(visitDuty(duty));
        }
        permissionBuilder.add(ODRL_DUTY_ATTRIBUTE, dutiesBuilder.build());
      }

      return permissionBuilder.build();
    }

    @Override
    public JsonObject visitProhibition(Prohibition prohibition) {
      var prohibitionBuilder = visitRule(prohibition);

      var remedies = prohibition.getRemedies();
      if (remedies != null && !remedies.isEmpty()) {
        var remediesJson = remedies.stream().map(this::visitDuty).collect(toJsonArray());
        prohibitionBuilder.add(ODRL_REMEDY_ATTRIBUTE, remediesJson);
      }

      return prohibitionBuilder.build();
    }

    @Override
    public JsonObject visitDuty(Duty duty) {
      var obligationBuilder = visitRule(duty);

      var consequences = duty.getConsequences();
      if (consequences != null && !consequences.isEmpty()) {
        var consequencesJson = consequences.stream().map(this::visitDuty).collect(toJsonArray());
        obligationBuilder.add(ODRL_CONSEQUENCE_ATTRIBUTE, consequencesJson);
      }

      return obligationBuilder.build();
    }

    private JsonValue visitParticipantId(String participantId) {
      if (participantsAsId) {
        return jsonFactory.createObjectBuilder().add(ID, participantId).build();
      } else {
        return Json.createValue(participantId);
      }
    }

    private JsonObject visitMultiplicityConstraint(
        String operandType, MultiplicityConstraint multiplicityConstraint) {
      var constraintsBuilder = jsonFactory.createArrayBuilder();
      for (var constraint : multiplicityConstraint.getConstraints()) {
        Optional.of(constraint).map(c -> c.accept(this)).ifPresent(constraintsBuilder::add);
      }

      return jsonFactory.createObjectBuilder().add(operandType, constraintsBuilder.build()).build();
    }

    private JsonObjectBuilder visitRule(Rule rule) {
      var ruleBuilder = jsonFactory.createObjectBuilder();
      ruleBuilder.add(ODRL_ACTION_ATTRIBUTE, visitAction(rule.getAction()));
      if (rule.getConstraints() != null && !rule.getConstraints().isEmpty()) {
        ruleBuilder.add(ODRL_CONSTRAINT_ATTRIBUTE, visitConstraints(rule));
      }
      return ruleBuilder;
    }

    private JsonArray visitConstraints(Rule rule) {
      var constraintsBuilder = jsonFactory.createArrayBuilder();
      for (var constraint : rule.getConstraints()) {
        Optional.of(constraint).map(c -> c.accept(this)).ifPresent(constraintsBuilder::add);
      }
      return constraintsBuilder.build();
    }

    private JsonObject visitAction(@Nullable Action action) {
      var actionBuilder = jsonFactory.createObjectBuilder();
      if (action == null) {
        return actionBuilder.build();
      }
      if (action.getIncludedIn() != null || action.getConstraint() != null) {
        actionBuilder.add(
            ODRL_ACTION_ATTRIBUTE, jsonFactory.createObjectBuilder().add(ID, action.getType()));
        if (action.getIncludedIn() != null) {
          actionBuilder.add(ODRL_INCLUDED_IN_ATTRIBUTE, action.getIncludedIn());
        }
        if (action.getConstraint() != null) {
          actionBuilder.add(ODRL_REFINEMENT_ATTRIBUTE, action.getConstraint().accept(this));
        }
      } else {
        actionBuilder.add(ID, action.getType());
      }
      return actionBuilder.build();
    }

    private String getTypeAsString(PolicyType type) {
      return switch (type) {
        case SET -> ODRL_POLICY_TYPE_SET;
        case OFFER -> ODRL_POLICY_TYPE_OFFER;
        case CONTRACT -> ODRL_POLICY_TYPE_AGREEMENT;
      };
    }
  }
}
