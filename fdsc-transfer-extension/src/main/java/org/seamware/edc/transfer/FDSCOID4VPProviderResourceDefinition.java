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
package org.seamware.edc.transfer;

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

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.ResourceDefinition;

@JsonTypeName("dataspaceconnector:fdscoid4vpproviderresourcedefinition")
@JsonDeserialize(builder = FDSCOID4VPProviderResourceDefinition.Builder.class)
public class FDSCOID4VPProviderResourceDefinition extends ResourceDefinition {

  private String assetId;

  public String getAssetId() {
    return assetId;
  }

  @Override
  public Builder toBuilder() {
    return initializeBuilder(new Builder()).assetId(assetId);
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class Builder
      extends ResourceDefinition.Builder<FDSCOID4VPProviderResourceDefinition, Builder> {

    private Builder() {
      super(new FDSCOID4VPProviderResourceDefinition());
    }

    @JsonCreator
    public static Builder newInstance() {
      return new Builder();
    }

    public Builder assetId(String assetId) {
      resourceDefinition.assetId = assetId;
      return this;
    }

    @Override
    protected void verify() {
      super.verify();
      requireNonNull(resourceDefinition.assetId, "assetId");
    }
  }
}
