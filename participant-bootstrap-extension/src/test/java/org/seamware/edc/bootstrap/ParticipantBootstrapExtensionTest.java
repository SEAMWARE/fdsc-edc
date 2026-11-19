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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.eclipse.edc.participantcontext.spi.config.model.ParticipantContextConfiguration;
import org.eclipse.edc.participantcontext.spi.config.service.ParticipantContextConfigService;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContextState;
import org.eclipse.edc.protocol.spi.DataspaceProfileContext;
import org.eclipse.edc.protocol.spi.DataspaceProfileContextRegistry;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParticipantBootstrapExtensionTest {

  private static final String PROFILE_A = "dsp-a";
  private static final String PROFILE_B = "dsp-b";
  private static final String UNKNOWN_PROFILE = "dsp-unknown";
  private static final String CTX_A = "ctx-a";
  private static final String CTX_B = "ctx-b";
  private static final String DID_A = "did:web:a.example";
  private static final String DID_B = "did:web:b.example";
  private static final String STS_URL_KEY = "edc.iam.sts.oauth.token.url";
  private static final String STS_URL = "https://sts.a.example/token";
  private static final String STS_SECRET_KEY = "edc.iam.sts.oauth.client.secret";
  private static final String STS_SECRET = "secret";
  private static final String OTHER_DID = "did:web:other.example";
  private static final long EXISTING_CREATED_AT = 42L;
  private static final String FAILURE_DETAIL = "store unavailable";

  @Mock private ParticipantContextService participantContextService;
  @Mock private ParticipantContextConfigService participantContextConfigService;
  @Mock private DataspaceProfileContextRegistry dataspaceProfileContextRegistry;
  @Mock private Monitor monitor;
  @Mock private ServiceExtensionContext context;

  @InjectMocks private ParticipantBootstrapExtension extension;

  @BeforeEach
  void setUp() {
    var profile = mock(DataspaceProfileContext.class);
    lenient().when(dataspaceProfileContextRegistry.getProfile(PROFILE_A)).thenReturn(profile);
    lenient().when(dataspaceProfileContextRegistry.getProfile(PROFILE_B)).thenReturn(profile);
    lenient().when(dataspaceProfileContextRegistry.getProfiles()).thenReturn(List.of());
    lenient()
        .when(participantContextService.getParticipantContext(anyString()))
        .thenReturn(ServiceResult.notFound("not found"));
    lenient()
        .when(participantContextService.createParticipantContext(any()))
        .thenAnswer(invocation -> ServiceResult.success(invocation.getArgument(0)));
    lenient()
        .when(participantContextService.updateParticipantContext(any()))
        .thenReturn(ServiceResult.success());
    lenient()
        .when(participantContextConfigService.get(anyString()))
        .thenReturn(ServiceResult.notFound("not found"));
    lenient().when(participantContextConfigService.save(any())).thenReturn(ServiceResult.success());
  }

  @Test
  void seedsTwoParticipantsWithConfigAndProfiles() {
    var config = participant("a", CTX_A, DID_A, PROFILE_A + ", " + PROFILE_B);
    config.put(key("a", "config." + STS_URL_KEY), STS_URL);
    config.put(key("a", "privateConfig." + STS_SECRET_KEY), STS_SECRET);
    config.putAll(participant("b", CTX_B, DID_B, PROFILE_B));

    start(config);

    var contexts = ArgumentCaptor.forClass(ParticipantContext.class);
    verify(participantContextService, times(2)).createParticipantContext(contexts.capture());
    assertEquals(
        Map.of(CTX_A, DID_A, CTX_B, DID_B),
        Map.of(
            contexts.getAllValues().get(0).getParticipantContextId(),
            contexts.getAllValues().get(0).getIdentity(),
            contexts.getAllValues().get(1).getParticipantContextId(),
            contexts.getAllValues().get(1).getIdentity()));
    contexts
        .getAllValues()
        .forEach(ctx -> assertEquals(ParticipantContextState.ACTIVATED, ctx.getStateAsEnum()));

    var configA = savedConfig(CTX_A);
    assertEquals(
        Map.of(
            STS_URL_KEY,
            STS_URL,
            ParticipantBootstrapConfig.PARTICIPANT_DID_ENTRY,
            DID_A,
            ParticipantBootstrapConfig.DATASPACE_PROFILES_ENTRY,
            PROFILE_A + "," + PROFILE_B),
        configA.getEntries());
    assertEquals(Map.of(STS_SECRET_KEY, STS_SECRET), configA.getPrivateEntries());

    var configB = savedConfig(CTX_B);
    assertEquals(
        PROFILE_B, configB.getEntries().get(ParticipantBootstrapConfig.DATASPACE_PROFILES_ENTRY));
    assertEquals(DID_B, configB.getEntries().get(ParticipantBootstrapConfig.PARTICIPANT_DID_ENTRY));
  }

  @Test
  void reSeedingUpdatesExistingContextAndConfigInsteadOfDuplicating() {
    var existingContext =
        ParticipantContext.Builder.newInstance()
            .participantContextId(CTX_A)
            .identity(OTHER_DID)
            .state(ParticipantContextState.ACTIVATED)
            .build();
    when(participantContextService.getParticipantContext(CTX_A))
        .thenReturn(ServiceResult.success(existingContext));
    when(participantContextConfigService.get(CTX_A))
        .thenReturn(
            ServiceResult.success(
                ParticipantContextConfiguration.Builder.newInstance()
                    .participantContextId(CTX_A)
                    .createdAt(EXISTING_CREATED_AT)
                    .entries(Map.of(STS_URL_KEY, STS_URL))
                    .build()));

    start(participant("a", CTX_A, DID_A, PROFILE_A));

    verify(participantContextService, never()).createParticipantContext(any());
    var updated = ArgumentCaptor.forClass(ParticipantContext.class);
    verify(participantContextService).updateParticipantContext(updated.capture());
    assertEquals(CTX_A, updated.getValue().getParticipantContextId());
    assertEquals(DID_A, updated.getValue().getIdentity());

    var saved = savedConfig(CTX_A);
    assertEquals(EXISTING_CREATED_AT, saved.getCreatedAt());
    assertEquals(DID_A, saved.getEntries().get(ParticipantBootstrapConfig.PARTICIPANT_DID_ENTRY));
  }

  @Test
  void unknownProfileFailsStartup() {
    extension.initialize(contextWith(participant("a", CTX_A, DID_A, UNKNOWN_PROFILE)));

    var exception = assertThrows(EdcException.class, extension::prepare);

    assertTrue(exception.getMessage().contains(UNKNOWN_PROFILE));
    verifyNoInteractions(participantContextService, participantContextConfigService);
  }

  static Stream<Arguments> didEntries() {
    return ParticipantBootstrapConfig.DID_OVERRIDE_ENTRIES.stream()
        .flatMap(
            entry ->
                Stream.of(
                    Arguments.of("config." + entry, OTHER_DID, false),
                    Arguments.of("privateConfig." + entry, OTHER_DID, false),
                    Arguments.of("config." + entry, DID_A, true)));
  }

  @ParameterizedTest
  @MethodSource("didEntries")
  void didEntryMustMatchIdentity(String entry, String value, boolean accepted) {
    var config = participant("a", CTX_A, DID_A, PROFILE_A);
    config.put(key("a", entry), value);

    if (accepted) {
      start(config);
      assertEquals(
          DID_A,
          savedConfig(CTX_A).getEntries().get(ParticipantBootstrapConfig.PARTICIPANT_DID_ENTRY));
    } else {
      var exception = assertThrows(EdcException.class, () -> start(config));
      var edcKey = entry.substring(entry.indexOf('.') + 1);
      assertTrue(exception.getMessage().contains("'%s' must equal".formatted(edcKey)));
      verifyNoInteractions(participantContextService, participantContextConfigService);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {CTX_A, DID_A, "urn:connector:a"})
  void validContextIdsAreSeeded(String contextId) {
    start(participant("a", contextId, DID_A, PROFILE_A));

    var created = ArgumentCaptor.forClass(ParticipantContext.class);
    verify(participantContextService).createParticipantContext(created.capture());
    assertEquals(contextId, created.getValue().getParticipantContextId());
  }

  @ParameterizedTest
  @ValueSource(strings = {"a@b", "ctx@"})
  void contextIdWithQualifierSeparatorFailsStartup(String contextId) {
    var config = participant("a", contextId, DID_A, PROFILE_A);

    assertThrows(EdcException.class, () -> start(config));
    verifyNoInteractions(participantContextService, participantContextConfigService);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {ParticipantBootstrapConfig.ID_KEY, ParticipantBootstrapConfig.IDENTITY_KEY})
  void missingRequiredSettingFailsStartup(String missingKey) {
    var config = participant("a", CTX_A, DID_A, PROFILE_A);
    config.remove(key("a", missingKey));

    var exception = assertThrows(EdcException.class, () -> start(config));
    assertTrue(exception.getMessage().contains(key("a", missingKey)));
  }

  @Test
  void duplicateContextIdFailsStartup() {
    var config = participant("a", CTX_A, DID_A, PROFILE_A);
    config.putAll(participant("b", CTX_A, DID_B, PROFILE_B));

    assertThrows(EdcException.class, () -> start(config));
    verifyNoInteractions(participantContextService, participantContextConfigService);
  }

  @Test
  void seedsNothingWithoutParticipantKeys() {
    start(Map.of("edc.participant.id", CTX_A));

    verifyNoInteractions(
        participantContextService,
        participantContextConfigService,
        dataspaceProfileContextRegistry);
  }

  @Test
  void noKeysWithoutParticipantContextServicesIsNoOp() {
    var classicExtension = new ParticipantBootstrapExtension();

    classicExtension.initialize(contextWith(Map.of("edc.participant.id", CTX_A)));
    classicExtension.prepare();
  }

  @Test
  void declaredParticipantsWithoutParticipantContextServicesFailStartup() {
    var classicExtension = new ParticipantBootstrapExtension();
    var config = contextWith(participant("a", CTX_A, DID_A, PROFILE_A));

    var exception = assertThrows(EdcException.class, () -> classicExtension.initialize(config));

    assertTrue(exception.getMessage().contains(ParticipantContextService.class.getSimpleName()));
  }

  enum ServiceFailurePoint {
    LOOKUP,
    CREATE,
    UPDATE,
    SAVE_CONFIG
  }

  @ParameterizedTest
  @EnumSource(ServiceFailurePoint.class)
  void serviceFailureFailsStartup(ServiceFailurePoint failurePoint) {
    switch (failurePoint) {
      case LOOKUP ->
          when(participantContextService.getParticipantContext(CTX_A))
              .thenReturn(ServiceResult.unexpected(FAILURE_DETAIL));
      case CREATE ->
          when(participantContextService.createParticipantContext(any()))
              .thenReturn(ServiceResult.unexpected(FAILURE_DETAIL));
      case UPDATE -> {
        when(participantContextService.getParticipantContext(CTX_A))
            .thenReturn(
                ServiceResult.success(
                    ParticipantContext.Builder.newInstance()
                        .participantContextId(CTX_A)
                        .identity(DID_A)
                        .state(ParticipantContextState.ACTIVATED)
                        .build()));
        when(participantContextService.updateParticipantContext(any()))
            .thenReturn(ServiceResult.unexpected(FAILURE_DETAIL));
      }
      case SAVE_CONFIG ->
          when(participantContextConfigService.save(any()))
              .thenReturn(ServiceResult.unexpected(FAILURE_DETAIL));
    }

    var exception =
        assertThrows(EdcException.class, () -> start(participant("a", CTX_A, DID_A, PROFILE_A)));

    assertTrue(exception.getMessage().contains(FAILURE_DETAIL));
  }

  private void start(Map<String, String> config) {
    extension.initialize(contextWith(config));
    extension.prepare();
  }

  private ServiceExtensionContext contextWith(Map<String, String> config) {
    when(context.getConfig()).thenReturn(ConfigFactory.fromMap(config));
    return context;
  }

  private ParticipantContextConfiguration savedConfig(String contextId) {
    var saved = ArgumentCaptor.forClass(ParticipantContextConfiguration.class);
    verify(participantContextConfigService, atLeastOnce()).save(saved.capture());
    return saved.getAllValues().stream()
        .filter(config -> contextId.equals(config.getParticipantContextId()))
        .findFirst()
        .orElseThrow();
  }

  private static Map<String, String> participant(
      String nodeKey, String id, String identity, String profiles) {
    var config = new HashMap<String, String>();
    config.put(key(nodeKey, ParticipantBootstrapConfig.ID_KEY), id);
    config.put(key(nodeKey, ParticipantBootstrapConfig.IDENTITY_KEY), identity);
    config.put(key(nodeKey, ParticipantBootstrapConfig.PROFILES_KEY), profiles);
    return config;
  }

  private static String key(String nodeKey, String suffix) {
    return String.join(".", ParticipantBootstrapConfig.PARTICIPANTS_CONFIG, nodeKey, suffix);
  }
}
