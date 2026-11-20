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

import java.util.Optional;

/**
 * Contract definition or policy id of an offering that serves several contexts, as exposed in one
 * of them: {@code <id>@<participantContextId>} (ADR-4 of the multi-dataspace design records).
 *
 * <p>An EDC contract or policy definition belongs to exactly one participant context, while {@code
 * ContractDefinitionStore.findById} and {@code PolicyDefinitionStore.findById} take no context and
 * {@code ContractNegotiationProtocolServiceImpl} compares the resolved definition's context with
 * the negotiation's. The context therefore has to travel inside the id itself.
 */
public record ContextQualifiedId(String id, String participantContextId) {

  public static final String CONTEXT_QUALIFIER_SEPARATOR = "@";

  /** Splits at the last separator, so the plain id may itself contain one. */
  public static Optional<ContextQualifiedId> parse(String qualifiedId) {
    if (qualifiedId == null) {
      return Optional.empty();
    }
    int separatorIndex = qualifiedId.lastIndexOf(CONTEXT_QUALIFIER_SEPARATOR);
    if (separatorIndex <= 0 || separatorIndex == qualifiedId.length() - 1) {
      return Optional.empty();
    }
    return Optional.of(
        new ContextQualifiedId(
            qualifiedId.substring(0, separatorIndex),
            qualifiedId.substring(separatorIndex + CONTEXT_QUALIFIER_SEPARATOR.length())));
  }

  public static String qualify(String id, String participantContextId) {
    return id + CONTEXT_QUALIFIER_SEPARATOR + participantContextId;
  }

  @Override
  public String toString() {
    return qualify(id, participantContextId);
  }
}
