# Policy Context Input for ODRL-PAP Validation

The EDC policy-extension sends a `ValidationRequest` to the ODRL-PAP's `POST /validate`
endpoint whenever the EDC `PolicyEngine` evaluates a policy. This document describes the
structure of the `jsonInput` field within that request, which carries the context
information a policy interpreter needs to make access-control decisions.

## EDC Two-Layer Policy Evaluation Architecture

EDC evaluates policies at two distinct layers during request processing. Understanding
this architecture is essential for policy authors, because the information available to
the policy interpreter differs fundamentally between the two layers.

### Layer 1 — Pre-Authentication (`request.*` scopes)

**When:** Before the counter-party's token and VerifiableCredentials are verified.

**Flow:** `ProtocolTokenValidatorImpl.verify()` creates a `RequestPolicyContext` and
calls `policyEngine.evaluate()`. At this point, no `ClaimToken` or `ParticipantAgent`
exists yet. The only context available is the raw DSP protocol message (counter-party
address, message type, process IDs, etc.).

**Purpose:** Determines which VerifiableCredential scopes to request from the
counter-party's credential service. Pre-validators (like the ODRL-PAP validator) and
scope extractors (like `CredentialScopeExtractorRegistry`) run here.

**Context classes:**

| EDC Scope | Context Class |
|-----------|---------------|
| `request.catalog` | `RequestCatalogPolicyContext` |
| `request.contract.negotiation` | `RequestContractNegotiationPolicyContext` |
| `request.transfer.process` | `RequestTransferProcessPolicyContext` |

**Available data:** DSP message metadata only — no credentials, no claims.

### Layer 2 — Post-Authentication (`catalog`, `contract.negotiation`, `transfer.process` scopes)

**When:** After the counter-party's token has been verified, VerifiableCredentials have
been validated, and a `ParticipantAgent` has been created from the `ClaimToken`.

**Flow:** The service layer (e.g., `ContractDefinitionResolverImpl` for catalog,
`ContractNegotiationProtocolServiceImpl` for negotiation) creates a context that carries
the `ParticipantAgent` and calls `policyEngine.evaluate()`.

**Purpose:** Evaluates the actual access policy and contract policy against the
authenticated participant's identity and credential claims. This is where credential-based
constraints (e.g., "participant must hold a MembershipCredential with active status") are
enforced.

**Context classes:**

| EDC Scope | Context Class | Implements | Additional Data |
|-----------|---------------|------------|-----------------|
| `catalog` | `CatalogPolicyContext` | `ParticipantAgentPolicyContext` | `participantAgent()` |
| `contract.negotiation` | `ContractNegotiationPolicyContext` | `ParticipantAgentPolicyContext` | `participantAgent()` |
| `transfer.process` | `TransferProcessPolicyContext` | `ParticipantAgentPolicyContext`, `AgreementPolicyContext` | `participantAgent()`, `contractAgreement()`, `now()` |

**Available data:** Full participant identity, all verified VerifiableCredential claims,
and (for transfers) the contract agreement.

### Evaluation Sequence

```
Incoming DSP Request (e.g., catalog request with Bearer token)
│
├─ Layer 1: policyEngine.evaluate(policy, RequestCatalogPolicyContext)
│   ├─ Pre-validators run (scope extractors — PAP is NOT registered here)
│   ├─ Constraint functions run (for request.* scope bindings)
│   └─ Post-validators run
│   Context: DSP message metadata only — NO credentials
│
├─ IdentityService.verifyJwtToken(token, scopes)
│   ├─ For DCP: requests VerifiablePresentation, validates VCs
│   ├─ Creates ClaimToken with claims: {"vc": [<VerifiableCredential>, ...]}
│   └─ Creates ParticipantAgent(identity, claims, attributes)
│
└─ Layer 2: policyEngine.evaluate(accessPolicy, CatalogPolicyContext(agent))
    ├─ Pre-validators run (OdrlPapPolicyValidator ← PAP is called HERE)
    ├─ Constraint functions run (CEL expressions, custom evaluators)
    │   └─ Can access: ctx.agent.claims.vc → List<VerifiableCredential>
    └─ Post-validators run
    Context: Full participant identity + verified credential claims
```

### ParticipantAgent Claims Structure

When using DCP, the `ParticipantAgent.getClaims()` map contains:

| Key | Type | Description |
|-----|------|-------------|
| `"vc"` | `List<VerifiableCredential>` | All verified VerifiableCredentials from the counter-party's VerifiablePresentation. |

Each `VerifiableCredential` contains:

| Field | Type | Description |
|-------|------|-------------|
| `type` | `List<String>` | Credential types (e.g., `["VerifiableCredential", "MembershipCredential"]`). |
| `credentialSubject` | `List<CredentialSubject>` | Subjects with claim key-value pairs (e.g., `{"membershipStartDate": "2025-01-01"}`). |
| `issuer` | `Issuer` | The credential issuer's identity. |
| `issuanceDate` | `Instant` | When the credential was issued. |
| `expirationDate` | `Instant` | When the credential expires (may be null). |
| `credentialStatus` | `CredentialStatus` | Revocation/suspension status. |

### Reference: MVD CEL Expression Example

The [eclipse-dataspace-hub/MinimumViableDataspace](https://github.com/eclipse-dataspace-hub/MinimumViableDataspace)
demonstrates credential-based access control using CEL expressions registered for
Layer 2 scopes. These expressions evaluate claims within VerifiableCredentials:

```cel
# Check that a MembershipCredential exists with a valid start date
ctx.agent.claims.vc
  .filter(c, c.type.exists(t, t.contains('MembershipCredential')))
  .exists(c, c.credentialSubject.exists(cs, timestamp(cs.membershipStartDate) < now))

# Check that a ManufacturerCredential has a specific part_types value
ctx.agent.claims.vc
  .filter(c, c.type.exists(t, t.contains('ManufacturerCredential')))
  .exists(c, c.credentialSubject.exists(cs, cs.part_types == this.rightOperand))
```

These CEL expressions are registered via the management API
(`POST /api/mgmt/v5beta/celexpressions`) for scopes `catalog`, `contract.negotiation`,
and `transfer.process`, and are bound to ODRL `leftOperand` values
(e.g., `MembershipCredential`, `ManufacturerCredential.part_types`).

---

## PAP Integration (Layer 2)

The `OdrlPapPolicyValidator` is registered as a **pre-validator** for the Layer 2
scopes: `catalog`, `contract.negotiation`, and `transfer.process`. At this layer, the
counter-party's token and VerifiableCredentials have already been verified, so the PAP
receives the authenticated participant's identity and all verified credential claims.
This enables credential-based access control (e.g., "participant must hold a
MembershipCredential with status=active").

The PAP is **not** registered at Layer 1 (`request.*` scopes). Layer 1 evaluation runs
before authentication, so only unverified DSP message metadata is available — not
suitable for access-control decisions.

## Validation Request Structure

Every request to the PAP has this top-level shape:

```json
{
  "policy": { ... },
  "jsonInput": {
    "payload": { ... },
    "subject": { ... }
  },
  "additionalContexts": [ ... ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `policy` | Object | The ODRL policy in expanded JSON-LD form. Contains `odrl:permission`, `odrl:prohibition`, `odrl:obligation`, etc. |
| `jsonInput` | Object | Context information about the request being evaluated. Contains `payload` and optionally `subject`. |
| `jsonInput.payload` | Object | Always present. Carries the policy scope and context-specific fields. |
| `jsonInput.subject` | Object | Identifies the authenticated counter-party. Populated from the `ParticipantAgent` with verified identity and credential claims. |
| `additionalContexts` | Array | Optional. Additional JSON-LD context objects for controlling term compaction (e.g., remapping `odrl:use` to `dcp:use`). See [additional-contexts.md](additional-contexts.md). |

## Context Types Sent to the PAP

The PAP receives context from the Layer 2 scopes, evaluated **after** token verification.
The `ParticipantAgent` is available, carrying the verified identity and all
VerifiableCredential claims.

### 1. Catalog Access (`catalog`)

Triggered per-asset when the `DatasetResolver` evaluates the access policy of each
contract definition to determine catalog visibility for the authenticated participant.

**Payload fields:**

| Key | Type | Presence | Description |
|-----|------|----------|-------------|
| `scope` | String | Always | `"catalog"` |

**Subject fields:**

| Key | Type | Presence | Description |
|-----|------|----------|-------------|
| `identity` | String | Always | The authenticated participant's identity (e.g., a DID). Verified via DCP/OID4VP. |
| `claims` | Object | When available | The participant's claims from the `ClaimToken`. |
| `claims.vc` | Array of Object | When using DCP | The verified VerifiableCredentials. Each entry contains `type`, `credentialSubject`, `issuer`, `issuanceDate`, etc. |

**Example:**

```json
{
  "payload": {
    "scope": "catalog"
  },
  "subject": {
    "identity": "did:web:consumer.example.com",
    "claims": {
      "vc": [
        {
          "type": ["VerifiableCredential", "MembershipCredential"],
          "credentialSubject": [
            {
              "id": "did:web:consumer.example.com",
              "membershipStartDate": "2025-01-01T00:00:00Z",
              "status": "active"
            }
          ],
          "issuer": { "id": "did:web:issuer.dataspace.org" },
          "issuanceDate": "2025-01-01T00:00:00Z",
          "expirationDate": "2026-01-01T00:00:00Z"
        },
        {
          "type": ["VerifiableCredential", "ManufacturerCredential"],
          "credentialSubject": [
            {
              "id": "did:web:consumer.example.com",
              "part_types": "non_critical"
            }
          ],
          "issuer": { "id": "did:web:issuer.dataspace.org" },
          "issuanceDate": "2025-03-15T00:00:00Z"
        }
      ]
    }
  }
}
```

**Policy evaluation use cases:**

- Grant catalog visibility only to participants holding a valid `MembershipCredential`.
- Filter assets based on credential claim values (e.g., `part_types == "all"`).
- Check credential issuer trustworthiness.
- Enforce temporal constraints on credential validity.

### 2. Contract Negotiation (`contract.negotiation`)

Triggered when a contract negotiation is being processed. The context carries the
`ParticipantAgent` of the counter-party.

**Payload fields:**

| Key | Type | Presence | Description |
|-----|------|----------|-------------|
| `scope` | String | Always | `"contract.negotiation"` |

**Subject fields:** Same as [Catalog Access](#1-catalog-access-catalog).

**Policy evaluation use cases:**

- Restrict which credential holders may negotiate for specific assets.
- Require specific credential claim values for contract formation (e.g., manufacturer
  certification level).

### 3. Transfer Process (`transfer.process`)

Triggered when a transfer process is being processed. The context carries both the
`ParticipantAgent` and the `ContractAgreement`.

**Payload fields:**

| Key | Type | Presence | Description |
|-----|------|----------|-------------|
| `scope` | String | Always | `"transfer.process"` |
| `contractAgreement` | Object | Always | The contract agreement authorizing this transfer. |
| `contractAgreement.id` | String | Always | The agreement identifier. |
| `contractAgreement.assetId` | String | Always | The asset being transferred. |
| `contractAgreement.providerId` | String | Always | The provider's participant ID. |
| `contractAgreement.consumerId` | String | Always | The consumer's participant ID. |
| `contractAgreement.contractSigningDate` | Number (epoch seconds) | Always | When the agreement was signed. |
| `now` | String (ISO-8601) | Always | The current timestamp at evaluation time, for temporal policy constraints. |

**Subject fields:** Same as [Catalog Access](#1-catalog-access-catalog).

**Example:**

```json
{
  "payload": {
    "scope": "transfer.process",
    "contractAgreement": {
      "id": "agreement-abc-123",
      "assetId": "urn:asset:weather-data-2025",
      "providerId": "did:web:provider.example.com",
      "consumerId": "did:web:consumer.example.com",
      "contractSigningDate": 1700000000
    },
    "now": "2025-06-15T10:30:00Z"
  },
  "subject": {
    "identity": "did:web:consumer.example.com",
    "claims": {
      "vc": [
        {
          "type": ["VerifiableCredential", "MembershipCredential"],
          "credentialSubject": [
            {
              "id": "did:web:consumer.example.com",
              "membershipStartDate": "2025-01-01T00:00:00Z",
              "status": "active"
            }
          ],
          "issuer": { "id": "did:web:issuer.dataspace.org" },
          "issuanceDate": "2025-01-01T00:00:00Z",
          "expirationDate": "2026-01-01T00:00:00Z"
        }
      ]
    }
  }
}
```

**Policy evaluation use cases:**

- Enforce time-based transfer restrictions (e.g., "only during business hours").
- Validate credential claims at transfer time (credentials may have been revoked since
  negotiation).
- Apply agreement-specific constraints.

## Field Reference

### Payload fields

| Key | Type | Scope(s) | Description |
|-----|------|----------|-------------|
| `scope` | String | All | The EDC policy engine scope. Always present. |
| `contractAgreement` | Object | `transfer.process` | The contract agreement authorizing this transfer. |
| `contractAgreement.id` | String | `transfer.process` | The agreement identifier. |
| `contractAgreement.assetId` | String | `transfer.process` | The asset being transferred. |
| `contractAgreement.providerId` | String | `transfer.process` | The provider's participant ID. |
| `contractAgreement.consumerId` | String | `transfer.process` | The consumer's participant ID. |
| `contractAgreement.contractSigningDate` | Number | `transfer.process` | When the agreement was signed (epoch seconds). |
| `now` | String | `transfer.process` | Current timestamp (ISO-8601). |

### Subject fields

| Key | Type | Description |
|-----|------|-------------|
| `identity` | String | The authenticated participant's identity (e.g., a DID). **Verified via DCP/OID4VP.** |
| `claims` | Object | The participant's claims from the verified `ClaimToken`. |
| `claims.vc` | Array of Object | Verified VerifiableCredentials (DCP). Each contains `type`, `credentialSubject`, `issuer`, `issuanceDate`, `expirationDate`, `credentialStatus`. |

## VerifiableCredential Serialization

The `ParticipantAgent.getClaims()` map contains `"vc"` → `List<VerifiableCredential>`,
where each entry is a Java object. The `PolicyContextInputMapper` converts these to
JSON-compatible maps via `ObjectMapper.convertValue()` before placing them in
`subject.claims.vc`. This means the PAP receives plain JSON maps — not Java object
references — and can evaluate them with Rego, CEL, or any other policy language.

Other claims in the map (non-`"vc"` keys) are passed through as-is.

## PAP-Side Policy Examples

The PAP receives `subject.claims.vc` as a JSON array. Example Rego rules that evaluate
VerifiableCredential claims:

```rego
# Check that the participant holds a MembershipCredential with active status
has_active_membership {
    some vc in input.subject.claims.vc
    some t in vc.type
    t == "MembershipCredential"
    some cs in vc.credentialSubject
    cs.status == "active"
}

# Check that a ManufacturerCredential has an allowed part_types value
allowed_manufacturer {
    some vc in input.subject.claims.vc
    some t in vc.type
    t == "ManufacturerCredential"
    some cs in vc.credentialSubject
    cs.part_types == "all"
}

# Check credential issuer is trusted
trusted_issuer {
    some vc in input.subject.claims.vc
    vc.issuer.id == "did:web:issuer.dataspace.org"
}

# Combine with scope check
default allow = false
allow {
    input.payload.scope == "catalog"
    has_active_membership
}
```

## Null Handling

All fields except `scope` are optional. A field is omitted entirely from the JSON when
its source value is `null` or, for collections, empty. Policy rules must not assume any
field besides `scope` is present.
