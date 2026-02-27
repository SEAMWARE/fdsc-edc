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

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import org.jetbrains.annotations.Nullable;
import org.seamware.edc.SchemaBaseUriHolder;
import org.seamware.tmforum.quote.model.QuoteItemVO;

public class ExtendableQuoteItemVO extends QuoteItemVO {

  protected static final String QUOTE_ITEM_SCHEMA = "quote-item.json";

  @Override
  public @Nullable URI getAtSchemaLocation() {
    URI current = super.getAtSchemaLocation();
    if (current == null) {
      URI baseUri = SchemaBaseUriHolder.get(); // configurable
      URI resolved = baseUri.resolve(QUOTE_ITEM_SCHEMA);
      setAtSchemaLocation(resolved);
      return resolved;
    }
    return current;
  }

  @JsonProperty("policy")
  private Object policy;

  /** Corresponds to the (dsp)offer id */
  @JsonProperty("externalId")
  private String externalId;

  /** Corresponds to the (dsp) dataset id included in the corresponding offer */
  @JsonProperty("datasetId")
  private String datasetId;

  public Object getPolicy() {
    return policy;
  }

  public ExtendableQuoteItemVO setPolicy(Object policy) {
    this.policy = policy;
    return this;
  }

  public String getExternalId() {
    return externalId;
  }

  public ExtendableQuoteItemVO setExternalId(String externalId) {
    this.externalId = externalId;
    return this;
  }

  public String getDatasetId() {
    return datasetId;
  }

  public ExtendableQuoteItemVO setDatasetId(String datasetId) {
    this.datasetId = datasetId;
    return this;
  }
}
