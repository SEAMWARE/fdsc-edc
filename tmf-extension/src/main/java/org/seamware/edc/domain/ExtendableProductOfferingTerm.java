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
package org.seamware.edc.domain;

import com.fasterxml.jackson.annotation.*;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import org.seamware.edc.SchemaBaseUriHolder;
import org.seamware.tmforum.productcatalog.model.ProductOfferingTermVO;

public class ExtendableProductOfferingTerm extends ProductOfferingTermVO {

  protected static final String CONTRACT_DEFINITION_SCHEMA = "contract-definition.json";

  private Map<String, Object> additionalProperties = new HashMap<>();

  @Override
  public @Nullable URI getAtSchemaLocation() {
    URI current = super.getAtSchemaLocation();
    if (current == null) {
      URI baseUri = SchemaBaseUriHolder.get(); // configurable
      URI resolved = baseUri.resolve(CONTRACT_DEFINITION_SCHEMA);
      setAtSchemaLocation(resolved);
      return resolved;
    }
    return current;
  }

  @JsonAnyGetter
  public Map<String, Object> getAdditionalProperties() {
    return this.additionalProperties;
  }

  @JsonAnySetter
  public void setAdditionalProperties(String propertyKey, Object value) {
    if (this.additionalProperties == null) {
      this.additionalProperties = new HashMap();
    }

    this.additionalProperties.put(propertyKey, value);
  }

  @JsonIgnore
  public ExtendableProductOfferingTerm additionalProperties(
      Map<String, Object> additionalProperties) {
    this.additionalProperties = additionalProperties;
    return this;
  }
}
