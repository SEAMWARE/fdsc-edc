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
package org.seamware.edc.otel;

/**
 * Role of this connector in a negotiation or a transfer, as emitted in {@code participant.role}.
 * Every method returns {@code null} when the role cannot be determined, so the attribute is
 * omitted.
 */
public final class ParticipantRole {

  public static final String PROVIDER = "provider";
  public static final String CONSUMER = "consumer";

  private static final String PROCESS_TYPE_PROVIDER = "PROVIDER";
  private static final String PROCESS_TYPE_CONSUMER = "CONSUMER";

  private ParticipantRole() {}

  /**
   * Maps an EDC process type, as carried by transfer events ({@code PROVIDER} or {@code CONSUMER}),
   * to a role.
   */
  public static String fromProcessType(String processType) {
    if (PROCESS_TYPE_PROVIDER.equals(processType)) {
      return PROVIDER;
    }
    if (PROCESS_TYPE_CONSUMER.equals(processType)) {
      return CONSUMER;
    }
    return null;
  }

  /**
   * Role of {@code selfId} given the provider and consumer of an agreement, by exact string
   * comparison.
   */
  public static String fromParties(String selfId, String providerId, String consumerId) {
    if (selfId == null) {
      return null;
    }
    if (selfId.equals(providerId)) {
      return PROVIDER;
    }
    if (selfId.equals(consumerId)) {
      return CONSUMER;
    }
    return null;
  }

  /**
   * Role in a negotiation, from the assigner of its last offer, which is always the provider: this
   * connector is the provider when it is the assigner, and the consumer when the counterparty is.
   */
  public static String fromOfferAssigner(String selfId, String assigner, String counterPartyId) {
    if (assigner == null) {
      return null;
    }
    if (assigner.equals(selfId)) {
      return PROVIDER;
    }
    if (assigner.equals(counterPartyId)) {
      return CONSUMER;
    }
    return null;
  }
}
