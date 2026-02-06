package org.seamware.edc.policy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.participant.spi.ParticipantAgentPolicyContext;
import org.eclipse.edc.policy.engine.spi.AtomicConstraintRuleFunction;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.spi.monitor.Monitor;

import java.time.Clock;
import java.time.LocalDate;

public class DayOfWeekEvaluator<C extends ParticipantAgentPolicyContext> implements AtomicConstraintRuleFunction<Permission, C> {

    private final Monitor monitor;
    private final Clock clock;

    public DayOfWeekEvaluator(Monitor monitor, Clock clock) {
        this.monitor = monitor;
        this.clock = clock;
    }

    // evaluate the day of week. 1=Monday, 7=Sunday
    @Override
    public boolean evaluate(Operator operator, Object rightValue, Permission rule, C context) {

        monitor.debug(String.format("Evaluate dayOfWeek %s is %s than today.", rightValue, operator));

        Integer dayOfWeek = null;

        if (rightValue instanceof String stringOperand) {
            dayOfWeek = Integer.valueOf(stringOperand);
        } else if (rightValue instanceof Number numberOperand) {
            dayOfWeek = numberOperand.intValue();
        } else {
            throw new IllegalArgumentException(String.format("%s is not a valid dayOfWeek. Type is %s", rightValue, rightValue.getClass()));
        }

        int today = LocalDate.now(clock).getDayOfWeek().getValue();

        return switch (operator) {
            case EQ -> today == dayOfWeek;
            case GT -> today > dayOfWeek;
            case LT -> today < dayOfWeek;
            case LEQ -> today <= dayOfWeek;
            case GEQ -> today >= dayOfWeek;
            default -> throw new IllegalArgumentException(String.format("Operator %s is not supported for dayOfWeek.", operator));
        };

    }
}
