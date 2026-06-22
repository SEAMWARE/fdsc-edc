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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads scope mapping rules from a JSON configuration file.
 *
 * <p>The file maps ODRL permission and constraint properties to evaluation scopes. All property
 * names and values in {@code match} conditions must use fully expanded IRIs — the same form that
 * appears in expanded JSON-LD policies (e.g., {@code "http://www.w3.org/ns/odrl/2/leftOperand"}
 * rather than {@code "odrl:leftOperand"}). This ensures match conditions align directly with the
 * expanded JSON-LD policy structure without requiring JSON-LD context resolution.
 *
 * <p>Example configuration file:
 *
 * <pre>{@code
 * {
 *   "mappings": [
 *     {
 *       "match": {
 *         "http://www.w3.org/ns/odrl/2/leftOperand": "Membership"
 *       },
 *       "scopes": ["contract.negotiation"]
 *     },
 *     {
 *       "match": {
 *         "http://www.w3.org/ns/odrl/2/leftOperand": "http://www.w3.org/ns/odrl/2/dateTime"
 *       },
 *       "scopes": ["transfer.process"]
 *     }
 *   ]
 * }
 * }</pre>
 *
 * @see ScopeMappingRule
 */
public class ScopeMappingsLoader {

  /** Key for the mappings array in the configuration file. */
  static final String MAPPINGS_KEY = "mappings";

  /** Key for the match conditions within a mapping rule. */
  static final String MATCH_KEY = "match";

  /** Key for the scopes array within a mapping rule. */
  static final String SCOPES_KEY = "scopes";

  private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE =
      new TypeReference<>() {};

  private ScopeMappingsLoader() {}

  /**
   * Loads and parses scope mapping rules from the given file path.
   *
   * <p>All property names and values in match conditions are used as-is and must be fully expanded
   * IRIs matching the expanded JSON-LD form of the policies being evaluated.
   *
   * @param filePath the path to the JSON scope mappings file
   * @param objectMapper the Jackson object mapper for JSON deserialization
   * @return an unmodifiable list of scope mapping rules
   * @throws IllegalArgumentException if the file does not exist, is unreadable, contains malformed
   *     JSON, or has an invalid structure
   */
  public static List<ScopeMappingRule> load(String filePath, ObjectMapper objectMapper) {
    String content = readFile(filePath);
    Map<String, Object> root = parseJson(filePath, content, objectMapper);
    return parseRules(filePath, root);
  }

  private static String readFile(String filePath) {
    try {
      return Files.readString(Path.of(filePath));
    } catch (NoSuchFileException e) {
      throw new IllegalArgumentException(
          String.format("Scope mappings file not found: %s", filePath), e);
    } catch (IOException e) {
      throw new IllegalArgumentException(
          String.format("Failed to read scope mappings file: %s", filePath), e);
    }
  }

  private static Map<String, Object> parseJson(
      String filePath, String content, ObjectMapper objectMapper) {
    try {
      return objectMapper.readValue(content, MAP_TYPE_REFERENCE);
    } catch (IOException e) {
      throw new IllegalArgumentException(
          String.format(
              "Failed to parse scope mappings file as JSON object: %s. "
                  + "The file must contain a JSON object with a 'mappings' array.",
              filePath),
          e);
    }
  }

  @SuppressWarnings("unchecked")
  private static List<ScopeMappingRule> parseRules(String filePath, Map<String, Object> root) {
    Object mappingsObj = root.get(MAPPINGS_KEY);
    if (mappingsObj == null) {
      throw new IllegalArgumentException(
          String.format(
              "Scope mappings file is missing required '%s' array: %s", MAPPINGS_KEY, filePath));
    }
    if (!(mappingsObj instanceof List<?> mappingsList)) {
      throw new IllegalArgumentException(
          String.format(
              "'%s' must be a JSON array in scope mappings file: %s", MAPPINGS_KEY, filePath));
    }

    List<ScopeMappingRule> rules = new ArrayList<>();
    for (int i = 0; i < mappingsList.size(); i++) {
      Object entry = mappingsList.get(i);
      if (!(entry instanceof Map<?, ?> entryMap)) {
        throw new IllegalArgumentException(
            String.format("Mapping rule at index %d must be a JSON object in: %s", i, filePath));
      }
      rules.add(parseRule(filePath, i, (Map<String, Object>) entryMap));
    }
    return Collections.unmodifiableList(rules);
  }

  @SuppressWarnings("unchecked")
  private static ScopeMappingRule parseRule(String filePath, int index, Map<String, Object> entry) {
    Object matchObj = entry.get(MATCH_KEY);
    if (!(matchObj instanceof Map<?, ?> matchMap)) {
      throw new IllegalArgumentException(
          String.format(
              "Mapping rule at index %d is missing required '%s' object in: %s",
              index, MATCH_KEY, filePath));
    }

    Object scopesObj = entry.get(SCOPES_KEY);
    if (!(scopesObj instanceof List<?> scopesList)) {
      throw new IllegalArgumentException(
          String.format(
              "Mapping rule at index %d is missing required '%s' array in: %s",
              index, SCOPES_KEY, filePath));
    }

    Map<String, String> matchConditions = new LinkedHashMap<>();
    for (Map.Entry<?, ?> matchEntry : matchMap.entrySet()) {
      matchConditions.put(matchEntry.getKey().toString(), matchEntry.getValue().toString());
    }

    List<String> scopes = new ArrayList<>();
    for (Object scope : scopesList) {
      scopes.add(scope.toString());
    }

    return new ScopeMappingRule(matchConditions, scopes);
  }
}
