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
package org.seamware.edc.bootstrap;

import static org.seamware.edc.bootstrap.ParticipantBootstrapConfig.DATASPACE_PROFILES_ENTRY;
import static org.seamware.edc.bootstrap.ParticipantBootstrapConfig.DID_OVERRIDE_ENTRIES;
import static org.seamware.edc.bootstrap.ParticipantBootstrapConfig.PARTICIPANTS_CONFIG;
import static org.seamware.edc.bootstrap.ParticipantBootstrapConfig.PARTICIPANT_DID_ENTRY;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.edc.participantcontext.spi.config.model.ParticipantContextConfiguration;
import org.eclipse.edc.participantcontext.spi.config.service.ParticipantContextConfigService;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContextState;
import org.eclipse.edc.protocol.spi.DataspaceProfileContext;
import org.eclipse.edc.protocol.spi.DataspaceProfileContextRegistry;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.ServiceFailure;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.seamware.edc.bootstrap.ParticipantBootstrapConfig.Participant;

/**
 * Seeds the participant contexts declared in {@link ParticipantBootstrapConfig} at startup.
 * Upserts: existing contexts are updated and their configuration is replaced by the declared one.
 * The participant-context services are optional, so the extension is a no-op on a classic runtime.
 */
@Extension(ParticipantBootstrapExtension.NAME)
public class ParticipantBootstrapExtension implements ServiceExtension {

  public static final String NAME = "FDSC Participant Bootstrap Extension";

  /** Separator of {@code ContextQualifiedId} in tmf-extension; a context id must not contain it. */
  static final String CONTEXT_QUALIFIER_SEPARATOR = "@";

  @Inject(required = false)
  private ParticipantContextService participantContextService;

  @Inject(required = false)
  private ParticipantContextConfigService participantContextConfigService;

  @Inject(required = false)
  private DataspaceProfileContextRegistry dataspaceProfileContextRegistry;

  @Inject private Monitor monitor;

  @Override
  public String name() {
    return NAME;
  }

  private List<Participant> participants = List.of();

  @Override
  public void initialize(ServiceExtensionContext context) {
    participants = ParticipantBootstrapConfig.fromConfig(context.getConfig());
    if (participants.isEmpty()) {
      return;
    }
    requireServices();
    participants.forEach(ParticipantBootstrapExtension::validate);
    requireUniqueIds(participants);
  }

  private void requireServices() {
    var missing =
        Stream.of(
                missingService(participantContextService, ParticipantContextService.class),
                missingService(
                    participantContextConfigService, ParticipantContextConfigService.class),
                missingService(
                    dataspaceProfileContextRegistry, DataspaceProfileContextRegistry.class))
            .flatMap(Optional::stream)
            .toList();
    if (!missing.isEmpty()) {
      throw new EdcException(
          "%s declares participants, but the runtime does not provide %s; it requires the virtual participant-context services"
              .formatted(PARTICIPANTS_CONFIG, missing));
    }
  }

  private static Optional<String> missingService(Object service, Class<?> type) {
    return service == null ? Optional.of(type.getSimpleName()) : Optional.empty();
  }

  // Profiles are registered during initialize(), so they are only checked from prepare() on.
  @Override
  public void prepare() {
    participants.forEach(this::requireRegisteredProfiles);
    participants.forEach(this::seed);
  }

  private static void validate(Participant participant) {
    if (participant.id().contains(CONTEXT_QUALIFIER_SEPARATOR)) {
      throw fail(
          participant,
          "context id '%s' must not contain '%s'"
              .formatted(participant.id(), CONTEXT_QUALIFIER_SEPARATOR));
    }
    for (Map<String, String> entries : List.of(participant.config(), participant.privateConfig())) {
      DID_OVERRIDE_ENTRIES.stream()
          .filter(entries::containsKey)
          .filter(key -> !participant.identity().equals(entries.get(key)))
          .findFirst()
          .ifPresent(
              key -> {
                throw fail(
                    participant,
                    "'%s' must equal the identity '%s'; set the DID only via .identity"
                        .formatted(key, participant.identity()));
              });
      if (entries.containsKey(DATASPACE_PROFILES_ENTRY)) {
        throw fail(
            participant, "'%s' must not be set; use .profiles".formatted(DATASPACE_PROFILES_ENTRY));
      }
    }
  }

  private void requireRegisteredProfiles(Participant participant) {
    List<String> unknownProfiles =
        participant.profiles().stream()
            .filter(profile -> dataspaceProfileContextRegistry.getProfile(profile) == null)
            .toList();
    if (!unknownProfiles.isEmpty()) {
      throw fail(
          participant,
          "unknown dataspace profile(s) %s, registered are %s"
              .formatted(
                  unknownProfiles,
                  dataspaceProfileContextRegistry.getProfiles().stream()
                      .map(DataspaceProfileContext::name)
                      .toList()));
    }
  }

  private static void requireUniqueIds(List<Participant> participants) {
    var ids = new HashSet<String>();
    participants.stream()
        .filter(participant -> !ids.add(participant.id()))
        .findFirst()
        .ifPresent(
            participant -> {
              throw fail(participant, "duplicate context id '%s'".formatted(participant.id()));
            });
  }

  private void seed(Participant participant) {
    upsertContext(participant);
    upsertConfiguration(participant);
    monitor.info(
        "Seeded participant context '%s' (%s) with profiles %s"
            .formatted(participant.id(), participant.identity(), participant.profiles()));
  }

  private void upsertContext(Participant participant) {
    var existing = participantContextService.getParticipantContext(participant.id());
    if (existing.succeeded()) {
      // toBuilder() does not copy the identity, so it is always set from the declaration.
      var updated = existing.getContent().toBuilder().identity(participant.identity()).build();
      participantContextService
          .updateParticipantContext(updated)
          .orElseThrow(
              failure -> fail(participant, "update failed: " + failure.getFailureDetail()));
    } else if (existing.reason() == ServiceFailure.Reason.NOT_FOUND) {
      var created =
          ParticipantContext.Builder.newInstance()
              .participantContextId(participant.id())
              .identity(participant.identity())
              .state(ParticipantContextState.ACTIVATED)
              .build();
      participantContextService
          .createParticipantContext(created)
          .orElseThrow(
              failure -> fail(participant, "create failed: " + failure.getFailureDetail()));
    } else {
      throw fail(participant, "lookup failed: " + existing.getFailureDetail());
    }
  }

  private void upsertConfiguration(Participant participant) {
    var entries = new HashMap<>(participant.config());
    entries.put(PARTICIPANT_DID_ENTRY, participant.identity());
    if (!participant.profiles().isEmpty()) {
      entries.put(
          DATASPACE_PROFILES_ENTRY,
          ParticipantBootstrapConfig.joinProfiles(participant.profiles()));
    }
    var builder =
        ParticipantContextConfiguration.Builder.newInstance()
            .participantContextId(participant.id())
            .entries(entries)
            .privateEntries(new HashMap<>(participant.privateConfig()));
    var existing = participantContextConfigService.get(participant.id());
    if (existing.succeeded()) {
      builder.createdAt(existing.getContent().getCreatedAt());
    }
    participantContextConfigService
        .save(builder.build())
        .orElseThrow(
            failure -> fail(participant, "saving config failed: " + failure.getFailureDetail()));
  }

  private static EdcException fail(Participant participant, String reason) {
    return new EdcException(
        "Cannot seed participant %s.%s: %s"
            .formatted(PARTICIPANTS_CONFIG, participant.key(), reason));
  }
}
