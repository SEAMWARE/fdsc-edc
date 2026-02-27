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
import java.util.Objects;
import org.jetbrains.annotations.Nullable;
import org.seamware.edc.SchemaBaseUriHolder;
import org.seamware.tmforum.agreement.model.AgreementVO;

public class ExtendableAgreementVO extends AgreementVO {

  protected static final String AGREEMENT_JSON = "agreement.json";

  @Override
  public @Nullable URI getAtSchemaLocation() {
    URI current = super.getAtSchemaLocation();
    if (current == null) {
      URI baseUri = SchemaBaseUriHolder.get(); // configurable
      URI resolved = baseUri.resolve(AGREEMENT_JSON);
      setAtSchemaLocation(resolved);
      return resolved;
    }
    return current;
  }

  @JsonProperty("externalId")
  private String externalId;

  @JsonProperty("negotiationId")
  private String negotiationId;

  public String getNegotiationId() {
    return negotiationId;
  }

  public ExtendableAgreementVO setNegotiationId(String negotiationId) {
    this.negotiationId = negotiationId;
    return this;
  }

  public String getExternalId() {
    return externalId;
  }

  public ExtendableAgreementVO setExternalId(String externalId) {
    this.externalId = externalId;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    ExtendableAgreementVO that = (ExtendableAgreementVO) o;
    return Objects.equals(externalId, that.externalId)
        && Objects.equals(negotiationId, that.negotiationId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), externalId, negotiationId);
  }
}
