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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for {@link ScopeMappingsLoader}. */
class ScopeMappingsLoaderTest {

  private static final String ODRL_NS = "http://www.w3.org/ns/odrl/2/";
  private static final String DSPACE_NS = "https://w3id.org/dspace/2024/1/";

  private final ObjectMapper objectMapper = new ObjectMapper();

  @TempDir Path tempDir;

  @Nested
  @DisplayName("Successful loading")
  class SuccessfulLoading {

    @Test
    @DisplayName("loads a single mapping rule with expanded IRIs")
    void loadsSingleRule() throws IOException {
      String json =
          """
          {
            "mappings": [
              {
                "match": { "http://www.w3.org/ns/odrl/2/leftOperand": "Membership" },
                "scopes": ["contract.negotiation"]
              }
            ]
          }
          """;
      Path file = tempDir.resolve("single-rule.json");
      Files.writeString(file, json);

      List<ScopeMappingRule> rules = ScopeMappingsLoader.load(file.toString(), objectMapper);

      assertEquals(1, rules.size());
      ScopeMappingRule rule = rules.get(0);
      assertEquals(Map.of(ODRL_NS + "leftOperand", "Membership"), rule.match());
      assertEquals(List.of("contract.negotiation"), rule.scopes());
    }

    @Test
    @DisplayName("loads multiple mapping rules")
    void loadsMultipleRules() throws IOException {
      String json =
          """
          {
            "mappings": [
              {
                "match": { "http://www.w3.org/ns/odrl/2/leftOperand": "Membership" },
                "scopes": ["contract.negotiation"]
              },
              {
                "match": { "http://www.w3.org/ns/odrl/2/leftOperand": "http://www.w3.org/ns/odrl/2/dateTime" },
                "scopes": ["transfer.process"]
              }
            ]
          }
          """;
      Path file = tempDir.resolve("multi-rule.json");
      Files.writeString(file, json);

      List<ScopeMappingRule> rules = ScopeMappingsLoader.load(file.toString(), objectMapper);

      assertEquals(2, rules.size());
      assertEquals(Map.of(ODRL_NS + "leftOperand", "Membership"), rules.get(0).match());
      assertEquals(Map.of(ODRL_NS + "leftOperand", ODRL_NS + "dateTime"), rules.get(1).match());
    }

    @Test
    @DisplayName("loads rule with expanded IRIs in both keys and values")
    void loadsExpandedKeysAndValues() throws IOException {
      String json =
          """
          {
            "mappings": [
              {
                "match": { "http://www.w3.org/ns/odrl/2/action": "http://www.w3.org/ns/odrl/2/transfer" },
                "scopes": ["transfer.process"]
              }
            ]
          }
          """;
      Path file = tempDir.resolve("expanded.json");
      Files.writeString(file, json);

      List<ScopeMappingRule> rules = ScopeMappingsLoader.load(file.toString(), objectMapper);

      assertEquals(1, rules.size());
      assertEquals(Map.of(ODRL_NS + "action", ODRL_NS + "transfer"), rules.get(0).match());
    }

    @Test
    @DisplayName("keeps plain string values as-is")
    void keepsPlainValuesAsIs() throws IOException {
      String json =
          """
          {
            "mappings": [
              {
                "match": { "http://www.w3.org/ns/odrl/2/rightOperand": "gold" },
                "scopes": ["contract.negotiation"]
              }
            ]
          }
          """;
      Path file = tempDir.resolve("plain-value.json");
      Files.writeString(file, json);

      List<ScopeMappingRule> rules = ScopeMappingsLoader.load(file.toString(), objectMapper);

      assertEquals(Map.of(ODRL_NS + "rightOperand", "gold"), rules.get(0).match());
    }

    @Test
    @DisplayName("loads rule with different namespace")
    void loadsRuleWithDifferentNamespace() throws IOException {
      String json =
          """
          {
            "mappings": [
              {
                "match": { "http://www.w3.org/ns/odrl/2/leftOperand": "https://w3id.org/dspace/2024/1/membershipType" },
                "scopes": ["contract.negotiation"]
              }
            ]
          }
          """;
      Path file = tempDir.resolve("dspace.json");
      Files.writeString(file, json);

      List<ScopeMappingRule> rules = ScopeMappingsLoader.load(file.toString(), objectMapper);

      assertEquals(1, rules.size());
      assertEquals(
          Map.of(ODRL_NS + "leftOperand", DSPACE_NS + "membershipType"), rules.get(0).match());
    }

    @Test
    @DisplayName("loads multi-condition rule")
    void loadsMultiConditionRule() throws IOException {
      String json =
          """
          {
            "mappings": [
              {
                "match": {
                  "http://www.w3.org/ns/odrl/2/leftOperand": "http://www.w3.org/ns/odrl/2/dateTime",
                  "http://www.w3.org/ns/odrl/2/operator": "http://www.w3.org/ns/odrl/2/gteq"
                },
                "scopes": ["transfer.process"]
              }
            ]
          }
          """;
      Path file = tempDir.resolve("multi-condition.json");
      Files.writeString(file, json);

      List<ScopeMappingRule> rules = ScopeMappingsLoader.load(file.toString(), objectMapper);

      assertEquals(1, rules.size());
      assertEquals(
          Map.of(
              ODRL_NS + "leftOperand",
              ODRL_NS + "dateTime",
              ODRL_NS + "operator",
              ODRL_NS + "gteq"),
          rules.get(0).match());
    }

    @Test
    @DisplayName("loads rule with multiple scopes")
    void loadsRuleWithMultipleScopes() throws IOException {
      String json =
          """
          {
            "mappings": [
              {
                "match": { "http://www.w3.org/ns/odrl/2/leftOperand": "http://www.w3.org/ns/odrl/2/count" },
                "scopes": ["contract.negotiation", "transfer.process"]
              }
            ]
          }
          """;
      Path file = tempDir.resolve("multi-scope.json");
      Files.writeString(file, json);

      List<ScopeMappingRule> rules = ScopeMappingsLoader.load(file.toString(), objectMapper);

      assertEquals(List.of("contract.negotiation", "transfer.process"), rules.get(0).scopes());
    }

    @Test
    @DisplayName("returns empty list for empty mappings array")
    void returnsEmptyForEmptyMappings() throws IOException {
      Path file = tempDir.resolve("empty.json");
      Files.writeString(
          file,
          """
          { "mappings": [] }
          """);

      List<ScopeMappingRule> rules = ScopeMappingsLoader.load(file.toString(), objectMapper);

      assertTrue(rules.isEmpty());
    }
  }

  @Nested
  @DisplayName("Error handling")
  class ErrorHandling {

    @Test
    @DisplayName("throws when file does not exist")
    void throwsWhenFileNotFound() {
      String missingPath = tempDir.resolve("nonexistent.json").toString();

      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> ScopeMappingsLoader.load(missingPath, objectMapper));

      assertTrue(exception.getMessage().contains("not found"));
      assertTrue(exception.getMessage().contains(missingPath));
    }

    @Test
    @DisplayName("throws when file contains malformed JSON")
    void throwsWhenMalformedJson() throws IOException {
      Path file = tempDir.resolve("malformed.json");
      Files.writeString(file, "{ not valid json");

      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> ScopeMappingsLoader.load(file.toString(), objectMapper));

      assertTrue(exception.getMessage().contains("JSON object"));
    }

    @Test
    @DisplayName("throws when file contains a JSON array instead of object")
    void throwsWhenJsonArray() throws IOException {
      Path file = tempDir.resolve("array.json");
      Files.writeString(file, "[]");

      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> ScopeMappingsLoader.load(file.toString(), objectMapper));

      assertTrue(exception.getMessage().contains("JSON object"));
    }

    @Test
    @DisplayName("throws when mappings key is missing")
    void throwsWhenMappingsMissing() throws IOException {
      Path file = tempDir.resolve("no-mappings.json");
      Files.writeString(
          file,
          """
          { "extra": "ignored" }
          """);

      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> ScopeMappingsLoader.load(file.toString(), objectMapper));

      assertTrue(exception.getMessage().contains("mappings"));
    }

    @Test
    @DisplayName("throws when mappings is not an array")
    void throwsWhenMappingsNotArray() throws IOException {
      Path file = tempDir.resolve("mappings-object.json");
      Files.writeString(
          file,
          """
          { "mappings": {} }
          """);

      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> ScopeMappingsLoader.load(file.toString(), objectMapper));

      assertTrue(exception.getMessage().contains("array"));
    }

    @Test
    @DisplayName("throws when mapping rule is missing match")
    void throwsWhenRuleMissingMatch() throws IOException {
      Path file = tempDir.resolve("no-match.json");
      Files.writeString(
          file,
          """
          { "mappings": [{ "scopes": ["catalog"] }] }
          """);

      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> ScopeMappingsLoader.load(file.toString(), objectMapper));

      assertTrue(exception.getMessage().contains("match"));
    }

    @Test
    @DisplayName("throws when mapping rule is missing scopes")
    void throwsWhenRuleMissingScopes() throws IOException {
      Path file = tempDir.resolve("no-scopes.json");
      Files.writeString(
          file,
          """
          { "mappings": [{ "match": { "http://www.w3.org/ns/odrl/2/leftOperand": "x" } }] }
          """);

      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> ScopeMappingsLoader.load(file.toString(), objectMapper));

      assertTrue(exception.getMessage().contains("scopes"));
    }
  }
}
