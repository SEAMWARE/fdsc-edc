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
package org.seamware.edc.pap.policy;

import java.util.List;
import java.util.Map;

/**
 * A rule that maps ODRL permission or constraint properties to evaluation scopes.
 *
 * <p>A rule matches a permission when all conditions in {@link #match()} are satisfied:
 * permission-level conditions (e.g., {@code odrl:action}) match the permission object directly,
 * while constraint-level conditions (e.g., {@code odrl:leftOperand}, {@code odrl:operator}, {@code
 * odrl:rightOperand}) must all be satisfied by the <b>same</b> constraint within the permission.
 *
 * <p>Property names and values in the match map use expanded IRIs, as resolved by the {@code
 * @context} block in the scope mappings configuration file.
 *
 * @param match property-value conditions (expanded IRIs) that must all be satisfied for this rule
 *     to match a permission
 * @param scopes the evaluation scopes assigned when this rule matches (e.g., {@code
 *     "contract.negotiation"}, {@code "transfer.process"})
 */
public record ScopeMappingRule(Map<String, String> match, List<String> scopes) {

  /**
   * Creates a scope mapping rule with defensive copies.
   *
   * @param match property-value conditions
   * @param scopes evaluation scopes
   */
  public ScopeMappingRule {
    match = Map.copyOf(match);
    scopes = List.copyOf(scopes);
  }
}
