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

/** Unit tests for {@link AdditionalContextsLoader}. */
class AdditionalContextsLoaderTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @TempDir Path tempDir;

  @Nested
  @DisplayName("Successful loading")
  class SuccessfulLoading {

    @Test
    @DisplayName("loads a single scoped context object from file")
    void loadsSingleContext() throws IOException {
      String json =
          """
          [
            {
              "odrl:action": {
                "@id": "http://www.w3.org/ns/odrl/2/action",
                "@type": "@id",
                "@context": {
                  "odrl": null,
                  "dcp": { "@id": "http://www.w3.org/ns/odrl/2/", "@prefix": true }
                }
              }
            }
          ]
          """;
      Path file = tempDir.resolve("single-context.json");
      Files.writeString(file, json);

      List<Map<String, Object>> result =
          AdditionalContextsLoader.load(file.toString(), objectMapper);

      assertEquals(1, result.size());
      assertTrue(result.get(0).containsKey("odrl:action"));
    }

    @Test
    @DisplayName("loads multiple context objects from file")
    void loadsMultipleContexts() throws IOException {
      String json =
          """
          [
            {
              "odrl:action": {
                "@id": "http://www.w3.org/ns/odrl/2/action",
                "@type": "@id",
                "@context": {
                  "odrl": null,
                  "dcp": { "@id": "http://www.w3.org/ns/odrl/2/", "@prefix": true }
                }
              }
            },
            {
              "odrl:leftOperand": {
                "@id": "http://www.w3.org/ns/odrl/2/leftOperand",
                "@type": "@id",
                "@context": {
                  "odrl": null,
                  "dcp": { "@id": "http://www.w3.org/ns/odrl/2/", "@prefix": true }
                }
              }
            }
          ]
          """;
      Path file = tempDir.resolve("multi-context.json");
      Files.writeString(file, json);

      List<Map<String, Object>> result =
          AdditionalContextsLoader.load(file.toString(), objectMapper);

      assertEquals(2, result.size());
      assertTrue(result.get(0).containsKey("odrl:action"));
      assertTrue(result.get(1).containsKey("odrl:leftOperand"));
    }

    @Test
    @DisplayName("returns empty list for empty JSON array")
    void returnsEmptyForEmptyArray() throws IOException {
      Path file = tempDir.resolve("empty.json");
      Files.writeString(file, "[]");

      List<Map<String, Object>> result =
          AdditionalContextsLoader.load(file.toString(), objectMapper);

      assertTrue(result.isEmpty());
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
              () -> AdditionalContextsLoader.load(missingPath, objectMapper));

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
              () -> AdditionalContextsLoader.load(file.toString(), objectMapper));

      assertTrue(exception.getMessage().contains("JSON array"));
    }

    @Test
    @DisplayName("throws when file contains a JSON object instead of array")
    void throwsWhenJsonObject() throws IOException {
      Path file = tempDir.resolve("object.json");
      Files.writeString(
          file,
          """
          {"odrl:action": {"@id": "http://www.w3.org/ns/odrl/2/action"}}
          """);

      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> AdditionalContextsLoader.load(file.toString(), objectMapper));

      assertTrue(exception.getMessage().contains("JSON array"));
    }
  }
}
