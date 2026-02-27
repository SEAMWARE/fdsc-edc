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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.edc.iam.identitytrust.spi.scope.ScopeExtractor;
import org.eclipse.edc.iam.identitytrust.spi.scope.ScopeExtractorRegistry;
import org.eclipse.edc.policy.context.request.spi.RequestPolicyContext;
import org.eclipse.edc.policy.model.*;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;

public class CredentialScopeExtractorRegistry implements ScopeExtractorRegistry {

  private final String CREDENTIAL_READ_SCOPE = "read";

  private final Monitor monitor;

  public CredentialScopeExtractorRegistry(Monitor monitor) {
    this.monitor = monitor;
  }

  @Override
  public void registerScopeExtractor(ScopeExtractor extractor) {
    // NO-OP;
  }

  @Override
  public Result<Set<String>> extractScopes(Policy policy, RequestPolicyContext policyContext) {

    Set<String> scopesFromPermission =
        Optional.ofNullable(policy.getPermissions()).orElse(List.of()).stream()
            .map(this::getScopesFromPermission)
            .flatMap(List::stream)
            .collect(Collectors.toSet());
    monitor.debug("Extracted scopes " + scopesFromPermission);
    policyContext.requestScopeBuilder().scopes(scopesFromPermission);
    return Result.success(scopesFromPermission);
  }

  private List<String> getScopesFromPermission(Permission permission) {

    return permission.getDuties().stream()
        .map(this::getVcTypesFromDuty)
        .flatMap(List::stream)
        .map(vcType -> String.format("%s:%s", vcType, CREDENTIAL_READ_SCOPE))
        .toList();
  }

  private List<String> getVcTypesFromDuty(Duty duty) {

    String dutyType = duty.getAction().getType();
    if (dutyType.equals("odrl:present") || dutyType.equals("present")) {
      return duty.getConstraints().stream()
          .map(this::vcTypeFromConstraint)
          .flatMap(List::stream)
          .map(vcType -> String.format("%s:%s", "org.eclipse.tractusx.vc.type", vcType))
          .toList();
    }
    return List.of();
  }

  private List<String> vcTypeFromConstraint(Constraint constraint) {
    List<String> types = new ArrayList<>();
    if (constraint instanceof AndConstraint andConstraint) {
      andConstraint.getConstraints().stream()
          .map(this::vcTypeFromConstraint)
          .forEach(types::addAll);
    }
    if (constraint instanceof AtomicConstraint atomicConstraint) {
      vcTypeFromAtomicConstraint(atomicConstraint).ifPresent(types::add);
    }
    return types;
  }

  private Optional<String> vcTypeFromAtomicConstraint(AtomicConstraint atomicConstraint) {
    if (atomicConstraint.getLeftExpression() instanceof LiteralExpression literalLeftExpression
        && literalLeftExpression.asString().equals("vc:type")
        && atomicConstraint.getOperator() == Operator.EQ
        && atomicConstraint.getRightExpression()
            instanceof LiteralExpression literalRightExpression) {
      return Optional.ofNullable(literalRightExpression.asString());
    }
    return Optional.empty();
  }
}
