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

import static org.seamware.edc.domain.ExtendableProduct.EXTERNAL_ID_SCHEMA;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;
import org.seamware.edc.SchemaBaseUriHolder;
import org.seamware.tmforum.productcatalog.model.ProductOfferingVO;

public class ExtendableProductOffering extends ProductOfferingVO {

  @Override
  public @Nullable URI getAtSchemaLocation() {
    URI current = super.getAtSchemaLocation();
    if (current == null) {
      URI baseUri = SchemaBaseUriHolder.get(); // configurable
      URI resolved = baseUri.resolve(EXTERNAL_ID_SCHEMA);
      setAtSchemaLocation(resolved);
      return resolved;
    }
    return current;
  }

  /** Corresponds to data-set id */
  @JsonProperty("externalId")
  private String externalId;

  private ExtendableProductSpecificationRef productSpecification;

  private List<ExtendableProductOfferingTerm> productOfferingTerm = new ArrayList<>();

  @javax.annotation.Nullable
  @JsonProperty(JSON_PROPERTY_PRODUCT_OFFERING_TERM)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public List<ExtendableProductOfferingTerm> getExtendableProductOfferingTerm() {
    return productOfferingTerm;
  }

  @JsonProperty(JSON_PROPERTY_PRODUCT_OFFERING_TERM)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public void setExtendableProductOfferingTerm(
      @javax.annotation.Nonnull List<ExtendableProductOfferingTerm> productOfferingTerm) {
    this.productOfferingTerm = productOfferingTerm;
  }

  @javax.annotation.Nullable
  @JsonProperty(JSON_PROPERTY_PRODUCT_SPECIFICATION)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public ExtendableProductSpecificationRef getExtendableProductSpecification() {
    return productSpecification;
  }

  @JsonProperty(JSON_PROPERTY_PRODUCT_SPECIFICATION)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public void setExtendableProductSpecification(
      @javax.annotation.Nullable ExtendableProductSpecificationRef productSpecification) {
    this.productSpecification = productSpecification;
  }

  public String getExternalId() {
    return externalId;
  }

  public ExtendableProductOffering setExternalId(String externalId) {
    this.externalId = externalId;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    ExtendableProductOffering that = (ExtendableProductOffering) o;
    return Objects.equals(externalId, that.externalId)
        && Objects.equals(productSpecification, that.productSpecification)
        && Objects.equals(productOfferingTerm, that.productOfferingTerm);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), externalId, productSpecification, productOfferingTerm);
  }
}
