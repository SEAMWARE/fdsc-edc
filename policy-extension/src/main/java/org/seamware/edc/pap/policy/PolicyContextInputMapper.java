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
package org.seamware.edc.pap.policy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.TransferProcessPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.participant.spi.ParticipantAgentPolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyContext;
import org.seamware.pap.model.GenericJsonInputVO;

/**
 * Maps an EDC {@link PolicyContext} to a {@link GenericJsonInputVO} for the ODRL-PAP's JSON payload
 * evaluation.
 *
 * <p>The mapper targets EDC's <b>Layer 2</b> policy scopes ({@code catalog}, {@code
 * contract.negotiation}, {@code transfer.process}), which are evaluated <b>after</b> token
 * verification. At this layer, the {@link ParticipantAgentPolicyContext} carries the authenticated
 * {@link ParticipantAgent} with verified VerifiableCredential claims.
 *
 * <p>The resulting {@link GenericJsonInputVO} contains:
 *
 * <ul>
 *   <li><b>Payload</b> — the policy scope and, for transfer contexts, the contract agreement and
 *       evaluation timestamp
 *   <li><b>Subject</b> — the authenticated participant identity and all verified credential claims
 *       (including the {@code "vc"} key with the list of VerifiableCredentials for DCP)
 * </ul>
 */
public class PolicyContextInputMapper {

  /** Payload key for the policy scope. */
  static final String KEY_SCOPE = "scope";

  /** Subject key for the authenticated participant identity. */
  static final String KEY_IDENTITY = "identity";

  /** Subject key for the participant's verified claims. */
  static final String KEY_CLAIMS = "claims";

  /** Payload key for the contract agreement in transfer process contexts. */
  static final String KEY_CONTRACT_AGREEMENT = "contractAgreement";

  /** Key for the agreement ID within the contract agreement object. */
  static final String KEY_AGREEMENT_ID = "id";

  /** Key for the asset ID within the contract agreement object. */
  static final String KEY_AGREEMENT_ASSET_ID = "assetId";

  /** Key for the provider ID within the contract agreement object. */
  static final String KEY_AGREEMENT_PROVIDER_ID = "providerId";

  /** Key for the consumer ID within the contract agreement object. */
  static final String KEY_AGREEMENT_CONSUMER_ID = "consumerId";

  /** Key for the signing date within the contract agreement object. */
  static final String KEY_AGREEMENT_SIGNING_DATE = "contractSigningDate";

  /** Payload key for the current evaluation timestamp (ISO-8601). */
  static final String KEY_NOW = "now";

  /** TypeReference for converting VerifiableCredential objects to JSON-compatible maps. */
  private static final TypeReference<List<Map<String, Object>>> VC_LIST_TYPE_REFERENCE =
      new TypeReference<>() {};

  private final ObjectMapper objectMapper;

  /**
   * Creates a new mapper instance.
   *
   * @param objectMapper the Jackson object mapper used to serialize VerifiableCredential objects to
   *     JSON-compatible maps
   */
  public PolicyContextInputMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Converts a {@link PolicyContext} into a {@link GenericJsonInputVO} for JSON payload evaluation
   * by the PAP.
   *
   * <p>The payload always includes the context's scope. For {@link ParticipantAgentPolicyContext}
   * subtypes (Layer 2), the mapper extracts the authenticated participant's identity and credential
   * claims into the subject. For {@link TransferProcessPolicyContext}, the contract agreement and
   * evaluation timestamp are added to the payload.
   *
   * @param context the policy context to convert
   * @return a {@link GenericJsonInputVO} representing the JSON input for PAP evaluation
   */
  public GenericJsonInputVO toJsonInput(PolicyContext context) {
    GenericJsonInputVO jsonInput = new GenericJsonInputVO();
    Map<String, Object> payload = new HashMap<>();
    payload.put(KEY_SCOPE, context.scope());

    if (context instanceof ParticipantAgentPolicyContext agentContext) {
      enrichFromParticipantAgent(jsonInput, agentContext.participantAgent());

      if (context instanceof TransferProcessPolicyContext transferContext) {
        enrichFromTransferProcessContext(payload, transferContext);
      }
    }

    jsonInput.payload(payload);
    return jsonInput;
  }

  /**
   * Extracts the participant's identity and credential claims into the subject.
   *
   * <p>The claims map may contain a {@code "vc"} key with a {@code List<VerifiableCredential>} when
   * using DCP. These Java objects are converted to JSON-compatible maps via the {@link
   * ObjectMapper}, so the PAP receives plain JSON it can evaluate with Rego/CEL.
   *
   * @param jsonInput the JSON input VO whose subject field will be populated
   * @param agent the authenticated participant agent
   */
  private void enrichFromParticipantAgent(GenericJsonInputVO jsonInput, ParticipantAgent agent) {
    Map<String, Object> subject = new HashMap<>();
    putIfNotNull(subject, KEY_IDENTITY, agent.getIdentity());

    Map<String, Object> claims = agent.getClaims();
    if (claims != null && !claims.isEmpty()) {
      subject.put(KEY_CLAIMS, serializeClaims(claims));
    }

    if (!subject.isEmpty()) {
      jsonInput.subject(subject);
    }
  }

  /**
   * Serializes the claims map to ensure all values are JSON-compatible. The {@code "vc"} claim
   * contains {@code List<VerifiableCredential>} Java objects that must be converted to maps for
   * JSON transport.
   *
   * @param claims the raw claims map from the {@link ParticipantAgent}
   * @return a new map with all values converted to JSON-serializable types
   */
  private Map<String, Object> serializeClaims(Map<String, Object> claims) {
    Map<String, Object> serialized = new HashMap<>();
    for (Map.Entry<String, Object> entry : claims.entrySet()) {
      if ("vc".equals(entry.getKey()) && entry.getValue() instanceof List<?>) {
        serialized.put(
            entry.getKey(), objectMapper.convertValue(entry.getValue(), VC_LIST_TYPE_REFERENCE));
      } else {
        serialized.put(entry.getKey(), entry.getValue());
      }
    }
    return serialized;
  }

  /**
   * Extracts transfer-specific fields from a {@link TransferProcessPolicyContext}: the contract
   * agreement and the evaluation timestamp.
   *
   * @param payload the payload map to enrich
   * @param context the transfer process policy context
   */
  private void enrichFromTransferProcessContext(
      Map<String, Object> payload, TransferProcessPolicyContext context) {
    ContractAgreement agreement = context.contractAgreement();
    if (agreement != null) {
      Map<String, Object> agreementMap = new HashMap<>();
      putIfNotNull(agreementMap, KEY_AGREEMENT_ID, agreement.getId());
      putIfNotNull(agreementMap, KEY_AGREEMENT_ASSET_ID, agreement.getAssetId());
      putIfNotNull(agreementMap, KEY_AGREEMENT_PROVIDER_ID, agreement.getProviderId());
      putIfNotNull(agreementMap, KEY_AGREEMENT_CONSUMER_ID, agreement.getConsumerId());
      agreementMap.put(KEY_AGREEMENT_SIGNING_DATE, agreement.getContractSigningDate());
      if (!agreementMap.isEmpty()) {
        payload.put(KEY_CONTRACT_AGREEMENT, agreementMap);
      }
    }

    if (context.now() != null) {
      payload.put(KEY_NOW, context.now().toString());
    }
  }

  /**
   * Puts a value into a map only if the value is not {@code null}.
   *
   * @param map the target map
   * @param key the key
   * @param value the value, or {@code null} to skip
   */
  private void putIfNotNull(Map<String, Object> map, String key, Object value) {
    if (value != null) {
      map.put(key, value);
    }
  }
}
