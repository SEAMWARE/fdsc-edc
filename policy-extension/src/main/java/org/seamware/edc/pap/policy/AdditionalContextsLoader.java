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
import java.util.List;
import java.util.Map;

/**
 * Loads additional JSON-LD context objects from a JSON file on disk.
 *
 * <p>The file must contain a JSON array of context objects. Each object defines JSON-LD 1.1 scoped
 * context mappings used by the ODRL-PAP to control how terms are compacted during policy processing
 * (e.g., remapping {@code odrl:use} to {@code dcp:use}).
 *
 * @see <a href="https://www.w3.org/TR/json-ld11/#scoped-contexts">JSON-LD 1.1 Scoped Contexts</a>
 */
public class AdditionalContextsLoader {

  /** TypeReference for parsing a JSON array of context objects. */
  private static final TypeReference<List<Map<String, Object>>> CONTEXT_LIST_TYPE_REFERENCE =
      new TypeReference<>() {};

  private AdditionalContextsLoader() {}

  /**
   * Loads and parses additional JSON-LD contexts from the given file path.
   *
   * @param filePath the path to the JSON file containing a JSON array of context objects
   * @param objectMapper the Jackson object mapper for JSON deserialization
   * @return the parsed list of context objects
   * @throws IllegalArgumentException if the file does not exist, is unreadable, or contains
   *     malformed JSON
   */
  public static List<Map<String, Object>> load(String filePath, ObjectMapper objectMapper) {
    String content;
    try {
      content = Files.readString(Path.of(filePath));
    } catch (NoSuchFileException e) {
      throw new IllegalArgumentException(
          String.format("Additional contexts file not found: %s", filePath), e);
    } catch (IOException e) {
      throw new IllegalArgumentException(
          String.format("Failed to read additional contexts file: %s", filePath), e);
    }

    try {
      return objectMapper.readValue(content, CONTEXT_LIST_TYPE_REFERENCE);
    } catch (IOException e) {
      throw new IllegalArgumentException(
          String.format(
              "Failed to parse additional contexts file as JSON array: %s. "
                  + "The file must contain a JSON array of context objects.",
              filePath),
          e);
    }
  }
}
