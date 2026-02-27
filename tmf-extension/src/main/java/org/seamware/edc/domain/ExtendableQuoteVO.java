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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;
import org.seamware.edc.SchemaBaseUriHolder;
import org.seamware.tmforum.quote.model.QuoteVO;

/** ExternalID corresponds to negotiation id */
public class ExtendableQuoteVO extends QuoteVO {

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

  @javax.annotation.Nonnull private List<ExtendableQuoteItemVO> quoteItem = new ArrayList<>();

  @JsonProperty("contractNegotiation")
  private ContractNegotiationState contractNegotiationState;

  @javax.annotation.Nonnull
  @JsonProperty(JSON_PROPERTY_QUOTE_ITEM)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public List<ExtendableQuoteItemVO> getExtendableQuoteItem() {
    return quoteItem;
  }

  @JsonProperty(JSON_PROPERTY_QUOTE_ITEM)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setExtendableQuoteItem(
      @javax.annotation.Nonnull List<ExtendableQuoteItemVO> quoteItem) {
    this.quoteItem = quoteItem;
  }

  public ContractNegotiationState getContractNegotiationState() {
    return contractNegotiationState;
  }

  public ExtendableQuoteVO setContractNegotiationState(
      ContractNegotiationState contractNegotiationState) {
    this.contractNegotiationState = contractNegotiationState;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    ExtendableQuoteVO quoteVO = (ExtendableQuoteVO) o;
    return Objects.equals(quoteItem, quoteVO.quoteItem)
        && Objects.equals(contractNegotiationState, quoteVO.contractNegotiationState);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), quoteItem, contractNegotiationState);
  }
}
