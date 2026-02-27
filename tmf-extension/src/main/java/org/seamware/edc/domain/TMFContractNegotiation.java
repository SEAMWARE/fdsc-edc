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

import java.util.ArrayList;
import java.util.List;

public class TMFContractNegotiation {

  private List<TMFCallbackAddress> callbackAddresses = new ArrayList();
  private String counterPartyId;
  private String counterPartyAddress;
  private String protocol;

  public String getProtocol() {
    return protocol;
  }

  public TMFContractNegotiation setProtocol(String protocol) {
    this.protocol = protocol;
    return this;
  }

  public String getCounterPartyAddress() {
    return counterPartyAddress;
  }

  public TMFContractNegotiation setCounterPartyAddress(String counterPartyAddress) {
    this.counterPartyAddress = counterPartyAddress;
    return this;
  }

  public String getCounterPartyId() {
    return counterPartyId;
  }

  public TMFContractNegotiation setCounterPartyId(String counterPartyId) {
    this.counterPartyId = counterPartyId;
    return this;
  }

  public List<TMFCallbackAddress> getCallbackAddresses() {
    return callbackAddresses;
  }

  public TMFContractNegotiation setCallbackAddresses(List<TMFCallbackAddress> callbackAddresses) {
    this.callbackAddresses = callbackAddresses;
    return this;
  }
}
