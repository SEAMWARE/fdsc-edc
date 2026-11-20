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
package org.seamware.edc.store;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.edc.spi.query.Criterion;
import org.jetbrains.annotations.Nullable;

/** Reads the participant context a store query is scoped to from its criteria. */
public final class ContextCriteria {

  // left operand and operator of ParticipantResource.filterByParticipantContextId
  static final String PARTICIPANT_CONTEXT_ID_PROPERTY = "participantContextId";
  static final String EQUALS_OPERATOR = "=";

  private ContextCriteria() {}

  /** The context of the first {@code participantContextId = <ctx>} criterion, if any. */
  public static Optional<String> requestedContext(Collection<Criterion> criteria) {
    return criteria.stream()
        .filter(c -> PARTICIPANT_CONTEXT_ID_PROPERTY.equals(c.getOperandLeft()))
        .filter(c -> EQUALS_OPERATOR.equals(c.getOperator()))
        .map(Criterion::getOperandRight)
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .findFirst();
  }

  /**
   * The context to filter TMForum by server-side. Empty for no context and for the default one,
   * which has to be fetched unfiltered so its untagged legacy entities are kept (ADR-3).
   */
  public static Optional<String> serverSideContext(
      Collection<Criterion> criteria, @Nullable String defaultParticipantContextId) {
    return requestedContext(criteria)
        .filter(context -> !Objects.equals(context, defaultParticipantContextId));
  }
}
