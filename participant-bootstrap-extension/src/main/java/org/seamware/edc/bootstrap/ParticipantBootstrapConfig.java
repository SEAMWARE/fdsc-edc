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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.system.configuration.Config;

/** Participant contexts declared under {@code fdsc.participants.<key>.*}, seeded at startup. */
public final class ParticipantBootstrapConfig {

  public static final String PARTICIPANTS_CONFIG = "fdsc.participants";
  public static final String ID_KEY = "id";
  public static final String IDENTITY_KEY = "identity";
  public static final String PROFILES_KEY = "profiles";
  public static final String CONFIG_KEY = "config";
  public static final String PRIVATE_CONFIG_KEY = "privateConfig";

  /** Per-context config entry associating the context with its dataspace profiles (CSV). */
  public static final String DATASPACE_PROFILES_ENTRY = "edc.dataspace.profiles";

  /** Per-context config entry {@code DidConfigProvider} reads the context's DID from. */
  public static final String PARTICIPANT_DID_ENTRY = "edc.participant.did";

  /** Per-context entries {@code DidConfigProvider} prefers over {@link #PARTICIPANT_DID_ENTRY}. */
  public static final List<String> DID_OVERRIDE_ENTRIES =
      List.of("edc.participant.id", PARTICIPANT_DID_ENTRY, "edc.iam.issuer.id");

  private static final String PROFILES_SEPARATOR = ",";

  private ParticipantBootstrapConfig() {}

  /** One declared participant context; {@code key} is the config node it was read from. */
  public record Participant(
      String key,
      String id,
      String identity,
      List<String> profiles,
      Map<String, String> config,
      Map<String, String> privateConfig) {}

  /** Reads all declared participants; empty when no {@code fdsc.participants.*} key is set. */
  public static List<Participant> fromConfig(Config config) {
    return config
        .getConfig(PARTICIPANTS_CONFIG)
        .partition()
        .map(ParticipantBootstrapConfig::participant)
        .toList();
  }

  private static Participant participant(Config participantConfig) {
    String key = participantConfig.currentNode();
    return new Participant(
        key,
        required(participantConfig, key, ID_KEY),
        required(participantConfig, key, IDENTITY_KEY),
        Arrays.stream(participantConfig.getString(PROFILES_KEY, "").split(PROFILES_SEPARATOR))
            .map(String::trim)
            .filter(profile -> !profile.isEmpty())
            .toList(),
        Map.copyOf(participantConfig.getConfig(CONFIG_KEY).getRelativeEntries()),
        Map.copyOf(participantConfig.getConfig(PRIVATE_CONFIG_KEY).getRelativeEntries()));
  }

  private static String required(Config participantConfig, String participantKey, String key) {
    String value = participantConfig.getString(key, null);
    if (Objects.requireNonNullElse(value, "").isBlank()) {
      throw new EdcException(
          "Missing required setting %s.%s.%s".formatted(PARTICIPANTS_CONFIG, participantKey, key));
    }
    return value.trim();
  }

  /** Joins profile names into the CSV form of {@link #DATASPACE_PROFILES_ENTRY}. */
  public static String joinProfiles(List<String> profiles) {
    return String.join(PROFILES_SEPARATOR, profiles);
  }
}
