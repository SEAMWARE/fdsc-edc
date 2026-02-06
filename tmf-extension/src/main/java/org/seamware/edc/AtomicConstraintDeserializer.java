package org.seamware.edc;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.edc.policy.model.AtomicConstraint;
import org.eclipse.edc.policy.model.LiteralExpression;
import org.eclipse.edc.policy.model.Operator;

import java.io.IOException;

public class AtomicConstraintDeserializer extends JsonDeserializer<AtomicConstraint> {

    @Override
    public AtomicConstraint deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {

        JsonNode node = p.getCodec().readTree(p);

        var leftOperand = node.get("leftOperand").asText();
        var rightOperand = node.get("rightOperand").asText();
        var operatorText = node.get("operator").asText();

        return AtomicConstraint.Builder.newInstance()
                .leftExpression(new LiteralExpression(leftOperand))
                .rightExpression(new LiteralExpression(rightOperand))
                .operator(Operator.valueOf(operatorText.toUpperCase()))
                .build();
    }
}