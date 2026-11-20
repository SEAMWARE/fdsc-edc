#!/bin/sh
#
# Copyright 2025 Seamless Middleware Technologies S.L and/or its affiliates
# and other contributors as indicated by the @author tags.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

###############################################################################
# init-tmforum-multi-dataspace.sh — Seed a TMForum instance with the data of
#                                   the two-dataspace verification stack.
#
# Products (ProductSpecifications) are shared; each ProductOffering names the
# dataspaces it is made in via participantContextIds (ADR-4):
#   MDS-SHARED  offered as MDS-OFFER-SHARED-A in ["A"] and MDS-OFFER-SHARED-B
#               in ["B"], each with a different policy
#   MDS-A-ONLY  offered as MDS-OFFER-A-ONLY in ["A"]
#   MDS-ALL     offered as MDS-OFFER-ALL in ["*"] (every dataspace)
#   MDS-LEGACY  offered as MDS-OFFER-LEGACY without a context (default context)
#
# Existing entities with the same externalId are reused, so re-running against
# the same TMForum adds nothing. Requires curl and jq.
#
# Environment variables:
#   TMF_BASE_URL        TMForum API base URL (default: http://tmforum:8632)
#   SCHEMA_BASE_URI     Base of the @schemaLocation URLs, with trailing slash;
#                       must be reachable by TMForum
#                       (default: http://static-files/schemas/)
#   CONTEXT_A           Participant context id of dataspace A (default: dataspace-a)
#   CONTEXT_B           Participant context id of dataspace B (default: dataspace-b)
#   MAX_RETRIES         Max retries waiting for TMForum (default: 60)
#   RETRY_INTERVAL      Seconds between retries (default: 2)
###############################################################################

set -eu

TMF_BASE_URL="${TMF_BASE_URL:-http://tmforum:8632}"
SCHEMA_BASE_URI="${SCHEMA_BASE_URI:-http://static-files/schemas/}"
CONTEXT_A="${CONTEXT_A:-dataspace-a}"
CONTEXT_B="${CONTEXT_B:-dataspace-b}"
MAX_RETRIES="${MAX_RETRIES:-60}"
RETRY_INTERVAL="${RETRY_INTERVAL:-2}"

CATALOG_API="${TMF_BASE_URL}/tmf-api/productCatalogManagement/v4"
EXTERNAL_ID_SCHEMA="${SCHEMA_BASE_URI}external-id.json"
CONTRACT_DEFINITION_SCHEMA="${SCHEMA_BASE_URI}contract-definition.json"
ALL_CONTEXTS='*'

ODRL_NS="http://www.w3.org/ns/odrl/2/"
ACTION_USE="${ODRL_NS}use"
ACTION_READ="${ODRL_NS}read"

log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

die() {
  echo "[ERROR] $*" >&2
  exit 1
}

wait_for_tmforum() {
  log "Waiting for TMForum API at ${TMF_BASE_URL}..."
  attempt=0
  while [ "$attempt" -lt "$MAX_RETRIES" ]; do
    if curl -sf "${CATALOG_API}/productSpecification?limit=1" >/dev/null 2>&1; then
      log "TMForum API is ready."
      return 0
    fi
    attempt=$((attempt + 1))
    sleep "$RETRY_INTERVAL"
  done
  die "TMForum API did not become ready within $((MAX_RETRIES * RETRY_INTERVAL))s"
}

## Prints the TMForum id of the entity with the given externalId, if any.
## Dies when the lookup fails, so an outage is not mistaken for "absent".
## $1 - collection (productSpecification, productOffering), $2 - externalId
find_id() {
  lookup=$(curl -sf "${CATALOG_API}/$1?externalId=$2") ||
    die "Lookup of $1 $2 failed"
  echo "$lookup" | jq -r '.[0].id // empty'
}

## Creates a ProductSpecification unless it exists; prints its TMForum id.
## $1 - externalId (the EDC asset id)
ensure_product_spec() {
  # Explicit: busybox sh drops set -e inside the caller's $(...) subshell.
  existing=$(find_id productSpecification "$1") || exit 1
  if [ -n "$existing" ]; then
    echo "$existing"
    return 0
  fi
  body=$(jq -n --arg id "$1" --arg schema "$EXTERNAL_ID_SCHEMA" '{
    name: $id,
    externalId: $id,
    "@schemaLocation": $schema,
    productSpecCharacteristic: [
      {id: "upstreamAddress", name: "upstreamAddress", valueType: "upstreamAddress",
       productSpecCharacteristicValue: [{value: {value: "http://test-upstream.local"}, isDefault: true}]},
      {id: "endpointUrl", name: "endpointUrl", valueType: "endpointUrl",
       productSpecCharacteristicValue: [{value: {value: "http://test-upstream.local/data"}, isDefault: true}]}
    ]}')
  response=$(curl -s --fail-with-body -X POST "${CATALOG_API}/productSpecification" \
    -H "Content-Type: application/json" -d "$body") ||
    die "Failed to create ProductSpecification $1: ${response}"
  echo "$response" | jq -r '.id'
}

## Creates a ProductOffering unless it exists.
## $1 - externalId (the EDC contract definition id), $2 - TMForum spec id,
## $3 - ODRL policy uid, $4 - ODRL action, $5 - participantContextIds as a
## JSON array, or "" to omit the property (legacy, default context)
ensure_product_offering() {
  # Assigned first: a failing find_id would be ignored inside the test.
  existing=$(find_id productOffering "$1") || exit 1
  if [ -n "$existing" ]; then
    log "ProductOffering $1 exists, skipping."
    return 0
  fi
  body=$(jq -n --arg id "$1" --arg spec "$2" --arg uid "$3" --arg action "$4" \
    --arg ns "$ODRL_NS" --arg schema "$EXTERNAL_ID_SCHEMA" \
    --arg termSchema "$CONTRACT_DEFINITION_SCHEMA" --arg contexts "$5" '
    {"@type": ($ns + "Set"), ($ns + "uid"): $uid,
     ($ns + "permission"): [{($ns + "action"): [{"@id": $action}]}]} as $policy
    | {name: $id,
       externalId: $id,
       "@schemaLocation": $schema,
       productSpecification: {id: $spec},
       productOfferingTerm: [{name: "edc:contractDefinition", "@schemaLocation": $termSchema,
                              contractPolicy: $policy, accessPolicy: $policy}]}
    + (if $contexts == "" then {} else {participantContextIds: ($contexts | fromjson)} end)')
  response=$(curl -s --fail-with-body -X POST "${CATALOG_API}/productOffering" \
    -H "Content-Type: application/json" -d "$body") ||
    die "Failed to create ProductOffering $1: ${response}"
  log "Created ProductOffering $1 (participantContextIds: ${5:-absent})."
}

log "=== Multi-dataspace TMForum seed ==="
log "TMForum API: ${TMF_BASE_URL}, schemas: ${SCHEMA_BASE_URI}"
wait_for_tmforum

shared_spec=$(ensure_product_spec MDS-SHARED)
a_only_spec=$(ensure_product_spec MDS-A-ONLY)
all_spec=$(ensure_product_spec MDS-ALL)
legacy_spec=$(ensure_product_spec MDS-LEGACY)
log "ProductSpecifications ready."

ensure_product_offering MDS-OFFER-SHARED-A "$shared_spec" P-MDS-SHARED-A "$ACTION_USE" "[\"${CONTEXT_A}\"]"
ensure_product_offering MDS-OFFER-SHARED-B "$shared_spec" P-MDS-SHARED-B "$ACTION_READ" "[\"${CONTEXT_B}\"]"
ensure_product_offering MDS-OFFER-A-ONLY "$a_only_spec" P-MDS-A-ONLY "$ACTION_USE" "[\"${CONTEXT_A}\"]"
ensure_product_offering MDS-OFFER-ALL "$all_spec" P-MDS-ALL "$ACTION_USE" "[\"${ALL_CONTEXTS}\"]"
ensure_product_offering MDS-OFFER-LEGACY "$legacy_spec" P-MDS-LEGACY "$ACTION_USE" ""

log "=== Multi-dataspace TMForum seed complete ==="
