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

import java.util.function.Supplier;
import org.eclipse.edc.participantcontext.spi.identity.ParticipantIdentityResolver;
import org.jetbrains.annotations.Nullable;

/**
 * This connector's own participant id in a participant context (ADR-5 of the multi-dataspace design
 * records). Uses the fixed classic id when the runtime registers no {@link
 * ParticipantIdentityResolver}, or when the resolver knows no identity for the default context.
 */
public class OwnParticipantIdentity {

  private final Supplier<@Nullable ParticipantIdentityResolver> participantIdentityResolver;
  private final String fallbackParticipantId;
  private final @Nullable String defaultParticipantContextId;

  public OwnParticipantIdentity(
      @Nullable ParticipantIdentityResolver participantIdentityResolver,
      String fallbackParticipantId) {
    this(() -> participantIdentityResolver, fallbackParticipantId, null);
  }

  /**
   * Looks the resolver up on every call, so it can come from the service registry after boot: an
   * {@code @Inject} would close a dependency cycle through the connector participant-context core.
   * The fallback id also covers {@code defaultParticipantContextId}, which on a classic runtime may
   * be an explicit key the resolver does not know.
   */
  public OwnParticipantIdentity(
      Supplier<@Nullable ParticipantIdentityResolver> participantIdentityResolver,
      String fallbackParticipantId,
      @Nullable String defaultParticipantContextId) {
    this.participantIdentityResolver = participantIdentityResolver;
    this.fallbackParticipantId = fallbackParticipantId;
    this.defaultParticipantContextId = defaultParticipantContextId;
  }

  /** The same id in every context, as on a classic runtime without the connector core. */
  public static OwnParticipantIdentity fixed(String participantId) {
    return new OwnParticipantIdentity((ParticipantIdentityResolver) null, participantId);
  }

  /**
   * Throws {@link IllegalStateException} when the resolver knows no identity for a non-default
   * context.
   */
  public String participantId(@Nullable String participantContextId, @Nullable String protocol) {
    ParticipantIdentityResolver resolver = participantIdentityResolver.get();
    if (resolver == null) {
      return fallbackParticipantId;
    }
    String participantId =
        participantContextId == null
            ? null
            : resolver.getParticipantId(participantContextId, protocol);
    if (participantId == null
        && participantContextId != null
        && participantContextId.equals(defaultParticipantContextId)) {
      return fallbackParticipantId;
    }
    if (participantId == null) {
      throw new IllegalStateException(
          String.format("No own participant id is known for context %s.", participantContextId));
    }
    return participantId;
  }
}
