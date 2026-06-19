# Scope-Aware Policy Constraint Evaluation

## Problem

A single EDC contract policy may contain constraints that are relevant to different evaluation phases. For example:

- **Negotiation phase**: Only members of type "gold" may negotiate this contract.
- **Transfer phase**: Data access is restricted to workdays between 08:00 and 12:00.

Currently, the `OdrlPapPolicyValidator` sends the **entire policy** (all permissions with all constraints) to the ODRL-PAP service at every evaluation scope. The PAP has no way to distinguish which constraints should apply at which scope, so either all constraints are evaluated at every scope (which may cause incorrect denials) or the PAP must hardcode scope-awareness for specific constraint types (which is fragile and non-generic).

## Current Architecture

The validator already passes scope information to the PAP in two ways:

1. **Policy-level target** (`odrl:target`): Set by the validator to an `odrl:AssetCollection` with a `dcp:Scope` refinement matching the current evaluation scope (e.g., `contract.negotiation`, `transfer.process`).
2. **JSON input payload**: The `PolicyContextInputMapper` includes the scope as `payload.scope`.

The PAP receives both, but the policy's individual permissions/constraints carry no scope metadata, so the PAP cannot filter them.

EDC itself uses hardcoded scope bindings via `RuleBindingRegistry` (see `DCPExtension.java:134`):

```java
ruleBindingRegistry.bind("dayOfWeek", TransferProcessPolicyContext.TRANSFER_SCOPE);
```

This binds the `dayOfWeek` constraint left operand to the transfer scope, so EDC's built-in policy engine only evaluates it during transfer. The ODRL-PAP validator needs an equivalent mechanism — but configurable rather than hardcoded.

## Proposed Approach: Two-Layer Scope Resolution

Scope assignment for permissions uses a two-layer resolution strategy, applied **before** sending the policy to the PAP:

1. **Explicit scope target** (policy-driven): If a permission has an `odrl:target` with an `AssetCollection` + `dcp:Scope` refinement, that scope is authoritative.
2. **Configuration-based scope mapping** (deployment-driven fallback): If a permission has no explicit scope target, its constraint left operands are matched against a configurable scope mapping file. This is the configurable equivalent of EDC's `RuleBindingRegistry.bind()`.
3. **No match** (default): If neither layer assigns a scope, the permission is evaluated in **every** scope (backward-compatible behavior).

### Layer 1: Explicit Permission-Level Scope Targets

Use ODRL's built-in `odrl:target` on individual permissions with `odrl:AssetCollection` + `dcp:Scope` refinement to tag each permission with its evaluation scope.

#### Policy Structure

A scope-aware policy uses multiple `odrl:permission` entries, each targeting a scope via an `AssetCollection` refinement:

```json
{
  "@context": {
    "odrl": "http://www.w3.org/ns/odrl/2/",
    "dcp": "https://w3id.org/dspace/2024/1/"
  },
  "@type": "odrl:Offer",
  "odrl:permission": [
    {
      "@id": "perm-negotiation",
      "odrl:action": "odrl:use",
      "odrl:target": {
        "@type": "odrl:AssetCollection",
        "odrl:refinement": {
          "odrl:leftOperand": "dcp:scope",
          "odrl:operator": "odrl:eq",
          "odrl:rightOperand": "contract.negotiation"
        }
      },
      "odrl:constraint": [{
        "odrl:leftOperand": "Membership",
        "odrl:operator": "odrl:eq",
        "odrl:rightOperand": "gold"
      }]
    },
    {
      "@id": "perm-transfer",
      "odrl:action": "odrl:use",
      "odrl:target": {
        "@type": "odrl:AssetCollection",
        "odrl:refinement": {
          "odrl:leftOperand": "dcp:scope",
          "odrl:operator": "odrl:eq",
          "odrl:rightOperand": "transfer.process"
        }
      },
      "odrl:constraint": [
        {
          "odrl:leftOperand": "odrl:dateTime",
          "odrl:operator": "odrl:gteq",
          "odrl:rightOperand": { "@value": "08:00:00", "@type": "xsd:time" }
        },
        {
          "odrl:leftOperand": "odrl:dateTime",
          "odrl:operator": "odrl:lteq",
          "odrl:rightOperand": { "@value": "12:00:00", "@type": "xsd:time" }
        },
        {
          "odrl:leftOperand": "odrl:dayOfWeek",
          "odrl:operator": "odrl:isNoneOf",
          "odrl:rightOperand": ["odrl:Saturday", "odrl:Sunday"]
        }
      ]
    },
    {
      "@id": "perm-global",
      "odrl:action": "odrl:use",
      "odrl:constraint": [{
        "odrl:leftOperand": "odrl:count",
        "odrl:operator": "odrl:lteq",
        "odrl:rightOperand": { "@value": "1000", "@type": "xsd:integer" }
      }]
    }
  ]
}
```

In this example:
- `perm-negotiation` is **only** evaluated during contract negotiation (membership check).
- `perm-transfer` is **only** evaluated during transfer (workday/time check).
- `perm-global` has **no scope target** and is evaluated in **every** scope (usage count limit).

### Layer 2: Configuration-Based Scope Mapping (Fallback)

For policies that were not authored with explicit scope targets — e.g., policies received from external connectors or created before scope-awareness was introduced — a deployment-level configuration file maps ODRL permission and constraint properties to scopes. This is the configurable equivalent of EDC's hardcoded `ruleBindingRegistry.bind("dayOfWeek", TRANSFER_SCOPE)` in `DCPExtension.java`, but generalized to match on **any** ODRL property — not just left operands.

#### Configuration File

A new EDC property `odrlPap.policy.scopeMappingsPath` points to a JSON file containing a list of mapping rules:

```json
{
  "@context": {
    "odrl": "http://www.w3.org/ns/odrl/2/"
  },
  "mappings": [
    {
      "match": { "odrl:leftOperand": "Membership" },
      "scopes": ["contract.negotiation"]
    },
    {
      "match": { "odrl:leftOperand": "odrl:dateTime" },
      "scopes": ["transfer.process"]
    },
    {
      "match": { "odrl:leftOperand": "odrl:dayOfWeek" },
      "scopes": ["transfer.process"]
    },
    {
      "match": { "odrl:action": "odrl:transfer" },
      "scopes": ["transfer.process"]
    },
    {
      "match": { "odrl:rightOperand": "gold" },
      "scopes": ["contract.negotiation"]
    },
    {
      "match": { "odrl:leftOperand": "odrl:dateTime", "odrl:operator": "odrl:gteq" },
      "scopes": ["transfer.process"]
    }
  ]
}
```

Each rule in `mappings` has:
- **`match`**: One or more property-value pairs. All conditions must be satisfied for the rule to match (AND semantics).
- **`scopes`**: The scope(s) where matching permissions are evaluated.

The optional `@context` block provides prefix definitions for resolving compact IRIs to full IRIs. At load time, both the property names and values in `match` are expanded using the context (e.g., `odrl:leftOperand` becomes `http://www.w3.org/ns/odrl/2/leftOperand`, `odrl:dateTime` becomes `http://www.w3.org/ns/odrl/2/dateTime`). Keys and values without a matching prefix are kept as-is and matched literally. This mirrors the `additionalContexts` loading pattern already established in `AdditionalContextsLoader`.

#### Matchable Properties

A rule's `match` conditions can reference any property that appears in an ODRL permission or its constraints:

| Property | Level | Description | Example value |
|---|---|---|---|
| `odrl:action` | Permission | The permitted action | `odrl:use`, `odrl:transfer` |
| `odrl:assignee` | Permission | Who is permitted | `did:web:consumer.example.com` |
| `odrl:assigner` | Permission | Who grants permission | `did:web:provider.example.com` |
| `odrl:leftOperand` | Constraint | What is constrained | `Membership`, `odrl:dateTime` |
| `odrl:operator` | Constraint | Comparison operator | `odrl:eq`, `odrl:gteq` |
| `odrl:rightOperand` | Constraint | Comparison value | `gold`, `08:00` |
| *(any other)* | Either | Custom or extended properties | *(any IRI or literal)* |

#### Matching Semantics

Properties are matched at the appropriate level of the expanded JSON-LD permission structure:

- **Permission-level properties** (`odrl:action`, `odrl:assignee`, `odrl:assigner`, etc.) are matched against the permission object itself.
- **Constraint-level properties** (`odrl:leftOperand`, `odrl:operator`, `odrl:rightOperand`, etc.) are matched against individual constraints within the permission.
- **Multi-condition rules**: When a rule has multiple conditions, all must be satisfied. If all conditions are constraint-level, they must be satisfied by the **same** constraint (not spread across different constraints). If conditions span permission-level and constraint-level, the permission-level conditions match the permission and all constraint-level conditions match the same constraint.

#### Scope Assignment Logic for Unscoped Permissions

When a permission has **no** explicit scope target (Layer 1 did not match), the validator checks all mapping rules:

1. Evaluate each mapping rule against the permission and its constraints.
2. Collect the `scopes` from all matching rules.
3. Compute the **intersection** of scope sets across all matching rules.
4. Result:
   - **All matching rules agree on the same scope(s)**: Assign those scope(s) to the permission.
   - **No rules match**: Permission is evaluated in **all** scopes (default).
   - **Matching rules disagree** (empty intersection): Permission is evaluated in **all** scopes, with a `WARNING` log — the configuration is likely misconfigured or the permission mixes concerns that should be in separate permissions.

The intersection logic ensures that a permission is only scoped when **all** matching rules agree. This prevents a permission with mixed-scope constraints from being silently dropped in one scope while its constraints are only partially relevant.

#### Example: Matching on Left Operand (Most Common)

Given a policy without scope targets:

```json
{
  "@type": "odrl:Offer",
  "odrl:permission": [
    {
      "odrl:action": "odrl:use",
      "odrl:constraint": [{
        "odrl:leftOperand": "Membership",
        "odrl:operator": "odrl:eq",
        "odrl:rightOperand": "gold"
      }]
    },
    {
      "odrl:action": "odrl:use",
      "odrl:constraint": [{
        "odrl:leftOperand": "odrl:dayOfWeek",
        "odrl:operator": "odrl:isNoneOf",
        "odrl:rightOperand": ["odrl:Saturday", "odrl:Sunday"]
      }]
    }
  ]
}
```

And this scope mapping:

```json
{ "match": { "odrl:leftOperand": "Membership" }, "scopes": ["contract.negotiation"] }
{ "match": { "odrl:leftOperand": "odrl:dayOfWeek" }, "scopes": ["transfer.process"] }
```

- **Permission 1**: Rule 1 matches (leftOperand=Membership). Scopes: `[contract.negotiation]`. Assigned to `contract.negotiation`.
- **Permission 2**: Rule 2 matches (leftOperand=dayOfWeek). Scopes: `[transfer.process]`. Assigned to `transfer.process`.

**Evaluation in negotiation scope**: Permission 1 included, Permission 2 excluded.
**Evaluation in transfer scope**: Permission 1 excluded, Permission 2 included.

#### Example: Matching on Action

```json
{
  "@type": "odrl:Offer",
  "odrl:permission": [
    {
      "odrl:action": "odrl:use",
      "odrl:constraint": [{ "odrl:leftOperand": "Membership", "odrl:operator": "odrl:eq", "odrl:rightOperand": "gold" }]
    },
    {
      "odrl:action": "odrl:transfer",
      "odrl:constraint": [{ "odrl:leftOperand": "odrl:dateTime", "odrl:operator": "odrl:gteq", "odrl:rightOperand": "08:00" }]
    }
  ]
}
```

With a rule: `{ "match": { "odrl:action": "odrl:transfer" }, "scopes": ["transfer.process"] }`

- **Permission 1** (action=use): Rule does not match. No mapping → evaluated in **all** scopes.
- **Permission 2** (action=transfer): Rule matches. Assigned to `transfer.process`.

#### Example: Matching on Right Operand

A rule matching a specific right operand value:

```json
{ "match": { "odrl:rightOperand": "gold" }, "scopes": ["contract.negotiation"] }
```

This scopes any permission containing a constraint with `rightOperand: "gold"` to the negotiation phase — useful when the distinguishing factor is the value, not the operand type.

#### Example: Multi-Condition Rule

A rule requiring both leftOperand and operator to match the same constraint:

```json
{ "match": { "odrl:leftOperand": "odrl:dateTime", "odrl:operator": "odrl:gteq" }, "scopes": ["transfer.process"] }
```

This only matches constraints where leftOperand is `dateTime` **and** operator is `gteq` — in the same constraint. A constraint with leftOperand=dateTime and operator=lteq would **not** match this rule.

#### Example: Multi-Constraint Permission with Agreeing Rules

```json
{
  "odrl:action": "odrl:use",
  "odrl:constraint": [
    { "odrl:leftOperand": "odrl:dateTime", "odrl:operator": "odrl:gteq", "odrl:rightOperand": "08:00" },
    { "odrl:leftOperand": "odrl:dayOfWeek", "odrl:operator": "odrl:isNoneOf", "odrl:rightOperand": ["odrl:Saturday"] }
  ]
}
```

Rule 1 matches constraint 1 (leftOperand=dateTime → `[transfer.process]`). Rule 2 matches constraint 2 (leftOperand=dayOfWeek → `[transfer.process]`). Intersection: `[transfer.process]`. Permission is assigned to `transfer.process`.

#### Example: Multi-Constraint Permission with Disagreeing Rules

```json
{
  "odrl:action": "odrl:use",
  "odrl:constraint": [
    { "odrl:leftOperand": "Membership", "odrl:operator": "odrl:eq", "odrl:rightOperand": "gold" },
    { "odrl:leftOperand": "odrl:dayOfWeek", "odrl:operator": "odrl:isNoneOf", "odrl:rightOperand": ["odrl:Saturday"] }
  ]
}
```

Rule for Membership matches → `[contract.negotiation]`. Rule for dayOfWeek matches → `[transfer.process]`. Intersection is **empty**. The validator logs a warning and evaluates this permission in **all** scopes.

### Combined Resolution Flow

```
For each permission in the policy:

  1. Does it have an odrl:target with an AssetCollection + dcp:Scope refinement?
     ├── YES → Use that scope (Layer 1). Done.
     └── NO  → Continue to Layer 2.

  2. Does the scope mapping configuration exist?
     ├── NO  → Evaluate in all scopes. Done.
     └── YES → Evaluate all mapping rules against the permission and its constraints.
               ├── No rules match → Evaluate in all scopes. Done.
               ├── All matching rules agree → Use the intersection scope(s). Done.
               └── Matching rules disagree → Log WARNING. Evaluate in all scopes. Done.
```

### Evaluation Rules Summary

| Condition | Evaluation behavior |
|---|---|
| Permission has `AssetCollection` target with `dcp:scope` refinement matching current scope | **Included** |
| Permission has `AssetCollection` target with `dcp:scope` refinement **not** matching current scope | **Excluded** |
| No scope target, matching rules agree on current scope | **Included** |
| No scope target, matching rules agree on a **different** scope | **Excluded** |
| No scope target, matching rules disagree or no rules match | **Included** (all scopes) |
| No `odrl:target`, no scope mapping config loaded | **Included** (all scopes, backward-compatible) |

## Implementation

### New Configuration

| Property | Type | Default | Description |
|---|---|---|---|
| `odrlPap.policy.scopeMappingsPath` | String | `null` | Path to a JSON file containing mapping rules that assign ODRL permissions to evaluation scopes |

Added to `OdrlPapConfig` alongside the existing `additionalContextsPath`.

### New Record: `ScopeMappingRule`

Represents a single mapping rule from the configuration file:

```java
/**
 * A rule that maps ODRL permission/constraint properties to evaluation scopes.
 *
 * <p>A rule matches a permission if all conditions in {@code match} are satisfied:
 * permission-level conditions match the permission object, and constraint-level
 * conditions are satisfied by the same constraint.
 *
 * @param match  property-value conditions (expanded IRIs) that must all be satisfied
 * @param scopes the evaluation scopes assigned when this rule matches
 */
public record ScopeMappingRule(Map<String, String> match, List<String> scopes) {}
```

### New Class: `ScopeMappingsLoader`

Loads and resolves the scope mapping configuration file. Follows the same pattern as `AdditionalContextsLoader`:

```java
/**
 * Loads scope mapping rules from a JSON configuration file.
 *
 * <p>Each rule maps a set of ODRL property-value conditions to evaluation scopes.
 * An optional {@code @context} block provides prefix definitions for resolving
 * compact IRIs to full IRIs in both property names and values.
 */
public class ScopeMappingsLoader {

    /**
     * Loads scope mapping rules from the given file path.
     *
     * @param path          the path to the JSON scope mappings file
     * @param objectMapper  the Jackson ObjectMapper for parsing
     * @return an unmodifiable list of scope mapping rules with expanded IRIs
     * @throws IllegalArgumentException if the file cannot be read or parsed
     */
    public static List<ScopeMappingRule> load(String path, ObjectMapper objectMapper) {
        // 1. Read and parse JSON file
        // 2. Extract @context (if present) for prefix resolution
        // 3. For each entry in "mappings" array:
        //    a. Expand compact IRIs in match keys and values using @context
        //    b. Create ScopeMappingRule(expandedMatch, scopes)
        // 4. Return unmodifiable list
    }
}
```

### Changes to `OdrlPapPolicyValidator`

The validator receives the loaded scope mapping rules (may be empty) and uses them in the filtering logic:

```java
public class OdrlPapPolicyValidator implements PolicyValidatorRule<PolicyContext> {

    // ... existing fields ...
    private final List<ScopeMappingRule> scopeMappingRules;

    public OdrlPapPolicyValidator(
            OdrlPapClient odrlPapClient,
            TypeTransformerRegistry transformerRegistry,
            JsonLd jsonLd,
            PolicyContextInputMapper inputMapper,
            Monitor monitor,
            ObjectMapper objectMapper,
            boolean denyOnError,
            List<Map<String, Object>> additionalContexts,
            List<ScopeMappingRule> scopeMappingRules) {
        // ... existing assignments ...
        this.scopeMappingRules = List.copyOf(scopeMappingRules);
    }

    @Override
    public Boolean apply(Policy policy, PolicyContext context) {
        // ... existing early return for empty permissions ...

        Map<String, Object> policyMap = new HashMap<>(convertPolicyToMap(policy));
        filterPermissionsByScope(policyMap, context.scope());
        ensureOdrlDefaults(policyMap, policy, context);

        // ... rest of existing flow ...
    }

    /**
     * Filters the policy's permissions to include only those relevant to the
     * current evaluation scope, using the two-layer resolution strategy.
     */
    private void filterPermissionsByScope(Map<String, Object> policyMap, String currentScope) {
        String permissionKey = ODRL_NAMESPACE + "permission";
        Object permissions = policyMap.get(permissionKey);
        if (!(permissions instanceof List<?> permList)) {
            return;
        }

        int originalCount = permList.size();
        List<Object> filtered = permList.stream()
            .filter(p -> isPermissionRelevantForScope(p, currentScope))
            .toList();

        if (filtered.size() < originalCount) {
            monitor.debug(() -> String.format(
                "%s: Filtered %d permission(s) not relevant for scope '%s'. "
                    + "Evaluating %d of %d total permissions.",
                VALIDATOR_NAME, originalCount - filtered.size(),
                currentScope, filtered.size(), originalCount));
        }

        policyMap.put(permissionKey, filtered);
    }

    /**
     * Determines whether a permission is relevant for the current scope using
     * two-layer resolution: (1) explicit scope target, (2) config-based rules.
     */
    private boolean isPermissionRelevantForScope(Object permission, String currentScope) {
        if (!(permission instanceof Map<?, ?> permMap)) {
            return true;
        }

        // Layer 1: Check explicit scope target on the permission
        Object target = permMap.get(ODRL_TARGET_KEY);
        String scopeFromTarget = extractScopeFromTarget(target);
        if (scopeFromTarget != null) {
            return currentScope.equals(scopeFromTarget);
        }

        // Layer 2: Check permission/constraint properties against mapping rules
        if (!scopeMappingRules.isEmpty()) {
            Set<String> resolvedScopes = resolveScopesFromRules(permMap);
            if (resolvedScopes != null) {
                return resolvedScopes.contains(currentScope);
            }
        }

        // No match from either layer → evaluate in all scopes
        return true;
    }

    /**
     * Resolves scopes for a permission by evaluating all mapping rules and
     * intersecting the scopes of all matching rules. Returns {@code null} if
     * no rules match (meaning: evaluate in all scopes).
     */
    private Set<String> resolveScopesFromRules(Map<?, ?> permMap) {
        Set<String> intersection = null;

        for (ScopeMappingRule rule : scopeMappingRules) {
            if (!ruleMatchesPermission(rule, permMap)) {
                continue;
            }
            if (intersection == null) {
                intersection = new HashSet<>(rule.scopes());
            } else {
                intersection.retainAll(rule.scopes());
            }
        }

        if (intersection != null && intersection.isEmpty()) {
            monitor.warning(String.format(
                "%s: Permission matched scope mapping rules with conflicting scopes. "
                    + "Evaluating in all scopes.",
                VALIDATOR_NAME));
            return null;
        }

        return intersection;
    }

    /**
     * Checks whether a mapping rule matches a permission. Permission-level
     * conditions (action, assignee, assigner) are matched against the permission
     * object. Constraint-level conditions (leftOperand, operator, rightOperand)
     * must all be satisfied by the same constraint.
     */
    private boolean ruleMatchesPermission(ScopeMappingRule rule, Map<?, ?> permMap) {
        Map<String, String> permissionLevelConditions = new HashMap<>();
        Map<String, String> constraintLevelConditions = new HashMap<>();

        for (Map.Entry<String, String> condition : rule.match().entrySet()) {
            if (isConstraintLevelProperty(condition.getKey())) {
                constraintLevelConditions.put(condition.getKey(), condition.getValue());
            } else {
                permissionLevelConditions.put(condition.getKey(), condition.getValue());
            }
        }

        // All permission-level conditions must match the permission
        for (Map.Entry<String, String> cond : permissionLevelConditions.entrySet()) {
            if (!propertyMatches(permMap, cond.getKey(), cond.getValue())) {
                return false;
            }
        }

        // All constraint-level conditions must be satisfied by the same constraint
        if (!constraintLevelConditions.isEmpty()) {
            List<Map<?, ?>> constraints = extractConstraints(permMap);
            return constraints.stream().anyMatch(constraint ->
                constraintLevelConditions.entrySet().stream()
                    .allMatch(cond -> propertyMatches(constraint, cond.getKey(), cond.getValue()))
            );
        }

        return true;
    }

    /**
     * Checks whether a property in the expanded JSON-LD map matches the
     * expected value. Handles the nested @id and @value structures of
     * expanded JSON-LD.
     */
    private boolean propertyMatches(Map<?, ?> map, String propertyIri, String expectedValue) {
        // Navigate expanded JSON-LD: property → List<Map> → @id or @value
    }

    /**
     * Returns true if the given property IRI is a constraint-level property
     * (leftOperand, operator, rightOperand) rather than a permission-level one.
     */
    private boolean isConstraintLevelProperty(String propertyIri) {
        // Check against known constraint-level property IRIs
    }
}
```

### Changes to `OdrlPapConfig`

Add the new property:

```java
static final String SETTING_SCOPE_MAPPINGS_PATH = "odrlPap.policy.scopeMappingsPath";

private final String scopeMappingsPath;

// ... in fromConfig():
String scopeMappingsPath =
    getNullSafeFromConfig(() -> policyConfig.getString("scopeMappingsPath")).orElse(null);

public String scopeMappingsPath() {
    return scopeMappingsPath;
}
```

### Changes to `OdrlPapPolicyExtension`

Load scope mapping rules and pass to the validator:

```java
List<ScopeMappingRule> scopeMappingRules = loadScopeMappingRules(config);

OdrlPapPolicyValidator validator = new OdrlPapPolicyValidator(
    client, typeTransformerRegistry, jsonLd, mapper, monitor,
    objectMapper, config.denyOnError(), additionalContexts, scopeMappingRules);

// ...

private List<ScopeMappingRule> loadScopeMappingRules(OdrlPapConfig config) {
    String path = config.scopeMappingsPath();
    if (path == null || path.isBlank()) {
        monitor.info(LOG_PREFIX + "No scope mappings file configured.");
        return List.of();
    }

    List<ScopeMappingRule> rules = ScopeMappingsLoader.load(path, objectMapper);
    monitor.info(LOG_PREFIX + "Loaded " + rules.size()
        + " scope mapping rule(s) from '" + path + "'.");
    return rules;
}
```

## Walkthrough: Full Example

### Setup

**Scope mappings file** (`scope-mappings.json`):
```json
{
  "@context": {
    "odrl": "http://www.w3.org/ns/odrl/2/"
  },
  "mappings": [
    {
      "match": { "odrl:leftOperand": "Membership" },
      "scopes": ["contract.negotiation"]
    },
    {
      "match": { "odrl:leftOperand": "odrl:dateTime" },
      "scopes": ["transfer.process"]
    },
    {
      "match": { "odrl:leftOperand": "odrl:dayOfWeek" },
      "scopes": ["transfer.process"]
    },
    {
      "match": { "odrl:action": "odrl:transfer" },
      "scopes": ["transfer.process"]
    }
  ]
}
```

**EDC configuration** (`edc.properties`):
```properties
odrlPap.policy.enabled=true
odrlPap.host=http://odrl-pap:8080
odrlPap.policy.scopeMappingsPath=/config/scope-mappings.json
```

### Case A: Policy with Explicit Scope Targets (Layer 1 resolves)

Policy as in the [Layer 1 example](#policy-structure) above. The scope mapping rules are **not consulted** because all permissions have explicit targets.

| Scope | Permissions sent to PAP |
|---|---|
| `catalog` | `perm-global` |
| `contract.negotiation` | `perm-negotiation` + `perm-global` |
| `transfer.process` | `perm-transfer` + `perm-global` |

### Case B: Policy without Scope Targets — Left Operand Rules (Layer 2)

```json
{
  "@type": "odrl:Offer",
  "odrl:permission": [
    {
      "odrl:action": "odrl:use",
      "odrl:constraint": [{
        "odrl:leftOperand": "Membership",
        "odrl:operator": "odrl:eq",
        "odrl:rightOperand": "gold"
      }]
    },
    {
      "odrl:action": "odrl:use",
      "odrl:constraint": [
        { "odrl:leftOperand": "odrl:dayOfWeek", "odrl:operator": "odrl:isNoneOf", "odrl:rightOperand": ["odrl:Saturday", "odrl:Sunday"] },
        { "odrl:leftOperand": "odrl:dateTime", "odrl:operator": "odrl:gteq", "odrl:rightOperand": "08:00" }
      ]
    }
  ]
}
```

- **Permission 1**: Membership rule matches → `[contract.negotiation]`. Assigned to `contract.negotiation`.
- **Permission 2**: dayOfWeek rule matches → `[transfer.process]`, dateTime rule matches → `[transfer.process]`. Intersection: `[transfer.process]`. Assigned to `transfer.process`.

| Scope | Permissions sent to PAP |
|---|---|
| `catalog` | *(none — both permissions are scoped)* |
| `contract.negotiation` | Permission 1 (Membership) |
| `transfer.process` | Permission 2 (dayOfWeek + dateTime) |

### Case C: Policy without Scope Targets — Action Rule (Layer 2)

```json
{
  "@type": "odrl:Offer",
  "odrl:permission": [
    {
      "odrl:action": "odrl:use",
      "odrl:constraint": [{ "odrl:leftOperand": "Membership", "odrl:operator": "odrl:eq", "odrl:rightOperand": "gold" }]
    },
    {
      "odrl:action": "odrl:transfer",
      "odrl:constraint": [{ "odrl:leftOperand": "odrl:dateTime", "odrl:operator": "odrl:gteq", "odrl:rightOperand": "08:00" }]
    }
  ]
}
```

- **Permission 1** (action=use): Membership rule matches → `[contract.negotiation]`. No action rule matches (only `odrl:transfer` is configured). Assigned to `contract.negotiation`.
- **Permission 2** (action=transfer): Action rule matches → `[transfer.process]`. dateTime rule also matches → `[transfer.process]`. Intersection: `[transfer.process]`. Assigned to `transfer.process`.

| Scope | Permissions sent to PAP |
|---|---|
| `catalog` | *(none)* |
| `contract.negotiation` | Permission 1 |
| `transfer.process` | Permission 2 |

### Case D: Mixed — Some Permissions Scoped, Some Not

```json
{
  "@type": "odrl:Offer",
  "odrl:permission": [
    {
      "odrl:action": "odrl:use",
      "odrl:target": {
        "@type": "odrl:AssetCollection",
        "odrl:refinement": {
          "odrl:leftOperand": "dcp:scope",
          "odrl:operator": "odrl:eq",
          "odrl:rightOperand": "contract.negotiation"
        }
      },
      "odrl:constraint": [{ "odrl:leftOperand": "Membership", "odrl:operator": "odrl:eq", "odrl:rightOperand": "gold" }]
    },
    {
      "odrl:action": "odrl:use",
      "odrl:constraint": [{ "odrl:leftOperand": "odrl:dayOfWeek", "odrl:operator": "odrl:isNoneOf", "odrl:rightOperand": ["odrl:Saturday"] }]
    },
    {
      "odrl:action": "odrl:use",
      "odrl:constraint": [{ "odrl:leftOperand": "SomeCustom", "odrl:operator": "odrl:eq", "odrl:rightOperand": "value" }]
    }
  ]
}
```

- **Permission 1**: Explicit target `contract.negotiation` (Layer 1). Assigned to `contract.negotiation`.
- **Permission 2**: No target. dayOfWeek rule matches → `[transfer.process]` (Layer 2). Assigned to `transfer.process`.
- **Permission 3**: No target. No rules match `SomeCustom`. No match → evaluated in **all** scopes.

| Scope | Permissions sent to PAP |
|---|---|
| `catalog` | Permission 3 |
| `contract.negotiation` | Permission 1 + Permission 3 |
| `transfer.process` | Permission 2 + Permission 3 |

### Case E: No Scope Mappings File Configured

When `odrlPap.policy.scopeMappingsPath` is not set, Layer 2 is disabled. Only Layer 1 (explicit scope targets) is active. Permissions without scope targets are always evaluated in all scopes — identical to the current behavior.

## Backward Compatibility

- **No scope mappings file**: Layer 2 is disabled. Behavior is identical to the current implementation (all permissions evaluated in all scopes), unless permissions have explicit scope targets (Layer 1).
- **Existing policies** (single permission, no scope target, no matching config): Evaluated in all scopes — no change.
- **Existing `odrlPap.policy.scopes.*` settings**: These control **whether the validator runs at all** for a scope. The new filtering controls **which permissions** are sent to the PAP within a scope where the validator does run.

## Logging

| Level | When |
|---|---|
| `DEBUG` | Permissions filtered out for current scope (includes count and scope name) |
| `WARNING` | Permission has constraints with conflicting scope mappings (intersection is empty) |
| `INFO` | Scope mappings file loaded (count of mappings) |
| `INFO` | No scope mappings file configured |

## Alternative Approaches Considered

### A. PAP-Side Scope Filtering

Send the full policy and let the PAP filter based on the `scope` field in the input payload.

**Pros**: No validator code changes.
**Cons**: Couples the PAP to EDC's scope model; every PAP deployment needs scope-aware Rego/CEL rules; harder to debug since the PAP becomes a black box for scope logic.

**Verdict**: Rejected. The PAP should remain a generic ODRL evaluator. Scope filtering is an EDC concern.

### B. Custom Constraint Property (`fdsc:evaluationScope`)

Add a custom property on each constraint indicating its scope.

**Pros**: Fine-grained (per-constraint, not per-permission).
**Cons**: Non-standard ODRL extension; adds a custom vocabulary; constraints within the same permission could target different scopes, creating ambiguity.

**Verdict**: Rejected. Permission-level scoping via `odrl:target` (Layer 1) or configuration mapping (Layer 2) covers the use cases without custom ODRL extensions.

### C. Separate Policies Per Scope

Create distinct policies for each scope and assign them to different contract definitions.

**Pros**: Clean separation; no filtering needed.
**Cons**: EDC uses a single policy per contract agreement. Splitting requires multiple contract definitions, complicating negotiation. Also duplicates shared constraints.

**Verdict**: Rejected. A single policy per agreement is the EDC convention.

### D. Configuration Only (No Policy-Level Scope Targets)

Rely solely on the configuration file for all scope assignment.

**Pros**: No policy structure changes needed.
**Cons**: Configuration must be kept in sync with policy content; no way for a policy author to declare intent; the policy loses self-describing scope information.

**Verdict**: Rejected as sole mechanism. Configuration is valuable as a fallback (Layer 2) but explicit policy-level targets (Layer 1) should take precedence. The two-layer approach gives policy authors control when they can express it, and deployment operators a safety net when they cannot.

## Recommendation

The **two-layer resolution** approach is recommended:

1. **Layer 1** (explicit scope targets) uses standard ODRL vocabulary and makes policies self-describing.
2. **Layer 2** (configuration-based rules) provides a deployment-level fallback analogous to EDC's `RuleBindingRegistry.bind()`, handling policies from external sources or legacy systems that lack scope annotations. Rules can match on **any** ODRL property (action, leftOperand, rightOperand, operator, or custom properties), with multi-condition rules for precise matching.
3. **Both layers are optional**: without either, the system behaves exactly as it does today.
4. The PAP remains **generic and scope-unaware** — all filtering happens in the validator.
5. Implementation is **localized**: changes to `OdrlPapPolicyValidator`, `OdrlPapConfig`, `OdrlPapPolicyExtension`, plus new `ScopeMappingRule` and `ScopeMappingsLoader` classes.
