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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.eclipse.edc.spi.types.domain.DataAddress;

public class FDSCDataAddress extends DataAddress {

  private static final String CLIENT_ID = "clientId";
  private static final String TYPE = "FDSC";

  private FDSCDataAddress() {
    super();
    this.setType(TYPE);
  }

  @JsonIgnore
  public String getClientId() {
    return getStringProperty(CLIENT_ID);
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static final class Builder extends DataAddress.Builder<FDSCDataAddress, Builder> {

    private Builder() {
      super(new FDSCDataAddress());
    }

    @JsonCreator
    public static Builder newInstance() {
      return new Builder();
    }

    public Builder clientId(String clientId) {
      this.property(CLIENT_ID, clientId);
      return this;
    }

    @Override
    public FDSCDataAddress build() {
      this.type(TYPE);
      return address;
    }
  }
}
