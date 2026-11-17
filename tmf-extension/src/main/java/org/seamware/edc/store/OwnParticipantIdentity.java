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

import org.eclipse.edc.participantcontext.spi.identity.ParticipantIdentityResolver;
import org.jetbrains.annotations.Nullable;

/**
 * This connector's own participant id in a participant context (decision 5 of the multi-dataspace
 * design). Uses the fixed classic id only when the runtime registers no {@link
 * ParticipantIdentityResolver}.
 */
public class OwnParticipantIdentity {

  private final @Nullable ParticipantIdentityResolver participantIdentityResolver;
  private final String fallbackParticipantId;

  public OwnParticipantIdentity(
      @Nullable ParticipantIdentityResolver participantIdentityResolver,
      String fallbackParticipantId) {
    this.participantIdentityResolver = participantIdentityResolver;
    this.fallbackParticipantId = fallbackParticipantId;
  }

  /** The same id in every context, as on a classic runtime without the connector core. */
  public static OwnParticipantIdentity fixed(String participantId) {
    return new OwnParticipantIdentity(null, participantId);
  }

  /** Throws {@link IllegalStateException} when the resolver knows no identity for the context. */
  public String participantId(@Nullable String participantContextId, @Nullable String protocol) {
    if (participantIdentityResolver == null) {
      return fallbackParticipantId;
    }
    String participantId =
        participantContextId == null
            ? null
            : participantIdentityResolver.getParticipantId(participantContextId, protocol);
    if (participantId == null) {
      throw new IllegalStateException(
          String.format("No own participant id is known for context %s.", participantContextId));
    }
    return participantId;
  }
}
