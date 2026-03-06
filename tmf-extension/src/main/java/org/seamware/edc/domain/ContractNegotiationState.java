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

import java.util.Objects;

public class ContractNegotiationState {

  private boolean isPending;
  private boolean isLeased;
  private String leasedBy;
  private long leaseExpiry;
  private String controlplane;
  private String state;
  private String correlationId;
  private String counterPartyAddress;

  public boolean isPending() {
    return isPending;
  }

  public String getState() {
    return state;
  }

  public ContractNegotiationState setState(String state) {
    this.state = state;
    return this;
  }

  public ContractNegotiationState setPending(boolean pending) {
    isPending = pending;
    return this;
  }

  public boolean isLeased() {
    return isLeased;
  }

  public ContractNegotiationState setLeased(boolean leased) {
    isLeased = leased;
    return this;
  }

  public String getLeasedBy() {
    return leasedBy;
  }

  public ContractNegotiationState setLeasedBy(String leasedBy) {
    this.leasedBy = leasedBy;
    return this;
  }

  public long getLeaseExpiry() {
    return leaseExpiry;
  }

  public ContractNegotiationState setLeaseExpiry(long leaseExpiry) {
    this.leaseExpiry = leaseExpiry;
    return this;
  }

  public String getCorrelationId() {
    return correlationId;
  }

  public ContractNegotiationState setCorrelationId(String correlationId) {
    this.correlationId = correlationId;
    return this;
  }

  public String getControlplane() {
    return controlplane;
  }

  public ContractNegotiationState setControlplane(String controlplane) {
    this.controlplane = controlplane;
    return this;
  }

  public String getCounterPartyAddress() {
    return counterPartyAddress;
  }

  public ContractNegotiationState setCounterPartyAddress(String counterPartyAddress) {
    this.counterPartyAddress = counterPartyAddress;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ContractNegotiationState that = (ContractNegotiationState) o;
    return isPending == that.isPending
        && isLeased == that.isLeased
        && leaseExpiry == that.leaseExpiry
        && Objects.equals(leasedBy, that.leasedBy)
        && Objects.equals(controlplane, that.controlplane)
        && Objects.equals(state, that.state)
        && Objects.equals(correlationId, that.correlationId)
        && Objects.equals(counterPartyAddress, that.counterPartyAddress);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        isPending,
        isLeased,
        leasedBy,
        leaseExpiry,
        controlplane,
        state,
        correlationId,
        counterPartyAddress);
  }
}
