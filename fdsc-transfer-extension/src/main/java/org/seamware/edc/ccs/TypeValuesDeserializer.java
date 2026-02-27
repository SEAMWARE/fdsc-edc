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
package org.seamware.edc.ccs;

/*-
 * #%L
 * fdsc-transfer-extension
 * %%
 * Copyright (C) 2025 - 2026 Seamless Middleware Technologies S.L
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// Json-LD flattening might produce different "looks" of type_values.
public class TypeValuesDeserializer extends JsonDeserializer<List<List<String>>> {

  @Override
  public List<List<String>> deserialize(JsonParser p, DeserializationContext ctxt)
      throws IOException {

    ObjectCodec codec = p.getCodec();
    JsonNode node = codec.readTree(p);

    List<List<String>> result = new ArrayList<>();

    if (node.isTextual()) {
      // "type_values": "value"
      result.add(List.of(node.asText()));
    } else if (node.isArray()) {
      for (JsonNode element : node) {
        if (element.isTextual()) {
          // "type_values": ["value"]
          result.add(List.of(element.asText()));
        } else if (element.isArray()) {
          // "type_values": [["value"], ["other"]]
          List<String> inner = new ArrayList<>();
          for (JsonNode innerNode : element) {
            inner.add(innerNode.asText());
          }
          result.add(inner);
        }
      }
    }

    return result;
  }
}
