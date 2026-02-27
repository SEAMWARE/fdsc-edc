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

import static org.seamware.edc.domain.ExtendableQuoteCreateVO.CONTRACT_NEGOTIATION_SCHEMA;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import org.jetbrains.annotations.Nullable;
import org.seamware.edc.SchemaBaseUriHolder;
import org.seamware.tmforum.usage.model.UsageCreateVO;

public class ExtendableUsageCreateVO extends UsageCreateVO {

  protected static final String USAGE_SCHEMA = "usage.json";

  @Override
  public @Nullable URI getAtSchemaLocation() {
    URI current = super.getAtSchemaLocation();
    if (current == null) {
      URI baseUri = SchemaBaseUriHolder.get(); // configurable
      URI resolved = baseUri.resolve(CONTRACT_NEGOTIATION_SCHEMA);
      setAtSchemaLocation(resolved);
      return resolved;
    }
    return current;
  }

  @JsonProperty("externalId")
  private String externalId;

  @JsonProperty("transferState")
  private String transferState;

  public String getExternalId() {
    return externalId;
  }

  public ExtendableUsageCreateVO setExternalId(String externalId) {
    this.externalId = externalId;
    return this;
  }

  public String getTransferState() {
    return transferState;
  }

  public ExtendableUsageCreateVO setTransferState(String transferState) {
    this.transferState = transferState;
    return this;
  }
}
