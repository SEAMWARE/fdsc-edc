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
package org.seamware.edc.policy;

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

import java.time.Clock;
import java.time.LocalDate;
import org.eclipse.edc.participant.spi.ParticipantAgentPolicyContext;
import org.eclipse.edc.policy.engine.spi.AtomicConstraintRuleFunction;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.spi.monitor.Monitor;

public class DayOfWeekEvaluator<C extends ParticipantAgentPolicyContext>
    implements AtomicConstraintRuleFunction<Permission, C> {

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
      throw new IllegalArgumentException(
          String.format(
              "%s is not a valid dayOfWeek. Type is %s", rightValue, rightValue.getClass()));
    }

    int today = LocalDate.now(clock).getDayOfWeek().getValue();

    return switch (operator) {
      case EQ -> today == dayOfWeek;
      case GT -> today > dayOfWeek;
      case LT -> today < dayOfWeek;
      case LEQ -> today <= dayOfWeek;
      case GEQ -> today >= dayOfWeek;
      default ->
          throw new IllegalArgumentException(
              String.format("Operator %s is not supported for dayOfWeek.", operator));
    };
  }
}
