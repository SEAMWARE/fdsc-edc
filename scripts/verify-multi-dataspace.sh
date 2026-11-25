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
# verify-multi-dataspace.sh — Assert that controlplane-dcp serves two
#                             dataspaces from one runtime and one TMForum.
#
# Runs against the stack of docker-compose.multi-dataspace.yml after
# init-tmforum-multi-dataspace.sh has seeded it. Checks:
#   - management API v5: both participant contexts are listed, both list the
#     shared product, and a non-admin token of B may read the shared product
#     in B but not in A;
#   - DSP routing: a profile associated with a context reaches the token check
#     (401 without a DCP token), an unassociated profile or unknown context
#     gives 404 (ADR-11 of docs/multi-dataspace-design.md);
#   - DSP version metadata at <protocol>/{ctx}/.well-known/dspace-version,
#     and none at the root <protocol>/.well-known/dspace-version (guards
#     against dsp-version-http-api returning to the virtual assembly);
#   - TMForum seed: offerings per context with participantContextIds=X,*;
#   - EDR retrieval (management API v5 edrs resource): scopes, token and
#     context isolation of query, dataaddress and delete (ADR-14).
# A full DSP catalog exchange needs a DCP wallet and is out of scope, so no
# transfer reaches STARTED here and the EDR checks only see empty listings and
# unknown ids. The STARTED path is covered by EdrTwoContextIsolationTest and by
# the FIWARE Data Space Connector ITs (dsp_dcp_flow, dsp_advanced_policy
# Scenario C). EDR token isolation at the gateway is out of scope too: this
# stack carries no APISIX and runs with fdscTransfer.enabled=false.
# EdrIsRejectedByForeignRouteTest covers it instead (ADR-13 of
# docs/multi-dataspace-design.md).
#
# Management tokens are RS256 JWTs signed with SIGNING_KEY_FILE, whose JWKS the
# controlplane trusts. Requires curl, jq and openssl. Exits non-zero if any
# check fails.
#
# Environment variables:
#   MANAGEMENT_URL   Management API base (default: http://edc:8181/management)
#   PROTOCOL_URL     DSP protocol base (default: http://edc:8282/protocol)
#   TMF_BASE_URL     TMForum API base URL (default: http://tmforum:8632)
#   SIGNING_KEY_FILE PEM RSA key for management tokens (default: /keys/management-api.key)
#   TOKEN_ISSUER     iss claim of management tokens (default: http://static-files)
#   KEY_ID           kid header of management tokens (default: multi-dataspace-verify)
#   CONTEXT_A        Participant context id of dataspace A (default: dataspace-a)
#   CONTEXT_B        Participant context id of dataspace B (default: dataspace-b)
#   PROFILE_A        DSP profile of dataspace A (default: dsp-dataspace-a)
#   PROFILE_B        DSP profile of dataspace B (default: dsp-dataspace-b)
###############################################################################

# jq programs reference jq variables ($ids, $id, ...) inside single quotes.
# shellcheck disable=SC2016

set -eu

MANAGEMENT_URL="${MANAGEMENT_URL:-http://edc:8181/management}"
PROTOCOL_URL="${PROTOCOL_URL:-http://edc:8282/protocol}"
TMF_BASE_URL="${TMF_BASE_URL:-http://tmforum:8632}"
SIGNING_KEY_FILE="${SIGNING_KEY_FILE:-/keys/management-api.key}"
TOKEN_ISSUER="${TOKEN_ISSUER:-http://static-files}"
KEY_ID="${KEY_ID:-multi-dataspace-verify}"
CONTEXT_A="${CONTEXT_A:-dataspace-a}"
CONTEXT_B="${CONTEXT_B:-dataspace-b}"
PROFILE_A="${PROFILE_A:-dsp-dataspace-a}"
PROFILE_B="${PROFILE_B:-dsp-dataspace-b}"

PARTICIPANTS_API="${MANAGEMENT_URL}/v5beta/participants"
CATALOG_API="${TMF_BASE_URL}/tmf-api/productCatalogManagement/v4"
TOKEN_TTL_SECONDS=300
UNKNOWN_CONTEXT="unknown-dataspace"
ALL_CONTEXTS='*'
TMF_PAGE_LIMIT=1000

ADMIN_SUBJECT="admin"
ADMIN_SCOPE="management-api:admin management-api:read management-api:write"
READ_SCOPE="management-api:read"

SHARED_PRODUCT="MDS-SHARED"
A_ONLY_PRODUCT="MDS-A-ONLY"
OFFER_SHARED_A="MDS-OFFER-SHARED-A"
OFFER_SHARED_B="MDS-OFFER-SHARED-B"
OFFER_A_ONLY="MDS-OFFER-A-ONLY"
OFFER_ALL="MDS-OFFER-ALL"
OFFER_LEGACY="MDS-OFFER-LEGACY"
UNKNOWN_TRANSFER="mds-unknown-transfer"

HTTP_OK=200
HTTP_UNAUTHORIZED=401
HTTP_FORBIDDEN=403
HTTP_NOT_FOUND=404

QUERY_SPEC='{"@context":["https://w3id.org/edc/connector/management/v2"],"@type":"QuerySpec"}'
CATALOG_REQUEST='{"@context":["https://w3id.org/dspace/2025/1/context.jsonld"],"@type":"CatalogRequestMessage","filter":[]}'

RESPONSE_FILE=$(mktemp)
trap 'rm -f "${RESPONSE_FILE}"' EXIT
failures=0
checks=0

log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

pass() {
  checks=$((checks + 1))
  log "PASS: $*"
}

fail() {
  checks=$((checks + 1))
  failures=$((failures + 1))
  log "FAIL: $*"
}

b64url() {
  openssl base64 -A | tr '+/' '-_' | tr -d '='
}

## Prints an RS256 management API token.
## $1 - sub (admin or a participant context id), $2 - space-separated scopes
token() {
  now=$(date +%s)
  header=$(jq -cn --arg kid "$KEY_ID" '{alg:"RS256",typ:"JWT",kid:$kid}' | b64url)
  payload=$(jq -cn --arg iss "$TOKEN_ISSUER" --arg sub "$1" --arg scope "$2" \
    --argjson now "$now" --argjson ttl "$TOKEN_TTL_SECONDS" \
    '{iss:$iss,sub:$sub,scope:$scope,iat:$now,nbf:$now,exp:($now+$ttl)}' | b64url)
  signature=$(printf '%s.%s' "$header" "$payload" \
    | openssl dgst -sha256 -sign "$SIGNING_KEY_FILE" -binary | b64url)
  echo "${header}.${payload}.${signature}"
}

## Sends a request, stores the body in RESPONSE_FILE and prints the status.
## $1 - method, $2 - URL, $3 - bearer token or "", $4 - JSON body or ""
request() {
  if [ -n "$3" ] && [ -n "$4" ]; then
    curl -s -o "$RESPONSE_FILE" -w '%{http_code}' -X "$1" -H "Authorization: Bearer $3" \
      -H 'Content-Type: application/json' -d "$4" "$2"
  elif [ -n "$3" ]; then
    curl -s -o "$RESPONSE_FILE" -w '%{http_code}' -X "$1" -H "Authorization: Bearer $3" "$2"
  elif [ -n "$4" ]; then
    curl -s -o "$RESPONSE_FILE" -w '%{http_code}' -X "$1" \
      -H 'Content-Type: application/json' -d "$4" "$2"
  else
    curl -s -o "$RESPONSE_FILE" -w '%{http_code}' -X "$1" "$2"
  fi
}

## Checks the status of a request. $1 - description, $2 - expected, $3.. - request args
expect_status() {
  description="$1"
  expected="$2"
  shift 2
  status=$(request "$@") || true
  if [ "$status" = "$expected" ]; then
    pass "${description} (${status})"
    return 0
  fi
  fail "${description}: expected ${expected}, got ${status}: $(head -c 300 "$RESPONSE_FILE")"
  return 1
}

## Checks the last response body with a jq expression that must yield true.
## $1 - description, $2 - jq expression, further jq args follow
expect_body() {
  description="$1"
  expression="$2"
  shift 2
  if jq -e "$@" "$expression" "$RESPONSE_FILE" >/dev/null 2>&1; then
    pass "$description"
  else
    fail "${description}: $(head -c 300 "$RESPONSE_FILE")"
  fi
}

verify_participants() {
  log "--- Management API: participant contexts"
  if expect_status "admin lists participant contexts" "$HTTP_OK" GET "$PARTICIPANTS_API" "$ADMIN_TOKEN" ""; then
    expect_body "participant contexts include ${CONTEXT_A} and ${CONTEXT_B}" \
      '[.[]."@id"] as $ids | ($ids | index($a)) != null and ($ids | index($b)) != null' \
      --arg a "$CONTEXT_A" --arg b "$CONTEXT_B"
  fi
  expect_status "request without token is rejected" "$HTTP_UNAUTHORIZED" GET "$PARTICIPANTS_API" "" ""
}

verify_assets() {
  log "--- Management API: shared products"
  for context in "$CONTEXT_A" "$CONTEXT_B"; do
    if expect_status "admin queries assets of ${context}" "$HTTP_OK" \
      POST "${PARTICIPANTS_API}/${context}/assets/request" "$ADMIN_TOKEN" "$QUERY_SPEC"; then
      expect_body "assets of ${context} include shared ${SHARED_PRODUCT} and ${A_ONLY_PRODUCT}" \
        '[.[]."@id"] as $ids | ($ids | index($shared)) != null and ($ids | index($aonly)) != null' \
        --arg shared "$SHARED_PRODUCT" --arg aonly "$A_ONLY_PRODUCT"
    fi
  done

  if expect_status "non-admin ${CONTEXT_B} reads shared product in its own context" "$HTTP_OK" \
    GET "${PARTICIPANTS_API}/${CONTEXT_B}/assets/${SHARED_PRODUCT}" "$CONTEXT_B_TOKEN" ""; then
    expect_body "shared product is returned" '."@id" == $id' --arg id "$SHARED_PRODUCT"
  fi
  expect_status "non-admin ${CONTEXT_B} cannot read through ${CONTEXT_A}" "$HTTP_FORBIDDEN" \
    GET "${PARTICIPANTS_API}/${CONTEXT_A}/assets/${SHARED_PRODUCT}" "$CONTEXT_B_TOKEN" "" || true
  expect_status "non-admin ${CONTEXT_B} cannot list participant contexts" "$HTTP_FORBIDDEN" \
    GET "$PARTICIPANTS_API" "$CONTEXT_B_TOKEN" "" || true
}

verify_dsp_routing() {
  log "--- DSP: profile routing (no DCP token)"
  # Each line: context profile expected-status
  while read -r context profile expected; do
    expect_status "catalog request at /${context}/${profile}" "$expected" \
      POST "${PROTOCOL_URL}/${context}/${profile}/catalog/request" "" "$CATALOG_REQUEST" || true
  done <<EOF
${CONTEXT_A} ${PROFILE_A} ${HTTP_UNAUTHORIZED}
${CONTEXT_B} ${PROFILE_B} ${HTTP_UNAUTHORIZED}
${CONTEXT_A} ${PROFILE_B} ${HTTP_NOT_FOUND}
${CONTEXT_B} ${PROFILE_A} ${HTTP_NOT_FOUND}
${UNKNOWN_CONTEXT} ${PROFILE_A} ${HTTP_NOT_FOUND}
EOF
}

verify_dsp_versions() {
  log "--- DSP: version metadata"
  # Each line: context own-profile foreign-profile
  while read -r context own foreign; do
    if expect_status "version metadata of ${context}" "$HTTP_OK" \
      GET "${PROTOCOL_URL}/${context}/.well-known/dspace-version" "" ""; then
      expect_body "${context} advertises ${own} but not ${foreign}" \
        '[.protocolVersions[].profile] as $p | ($p | index($own)) != null and ($p | index($foreign)) == null' \
        --arg own "$own" --arg foreign "$foreign"
    fi
  done <<EOF
${CONTEXT_A} ${PROFILE_A} ${PROFILE_B}
${CONTEXT_B} ${PROFILE_B} ${PROFILE_A}
EOF
  expect_status "version metadata of an unknown context" "$HTTP_NOT_FOUND" \
    GET "${PROTOCOL_URL}/${UNKNOWN_CONTEXT}/.well-known/dspace-version" "" "" || true
  expect_status "no root version metadata in virtual mode" "$HTTP_NOT_FOUND" \
    GET "${PROTOCOL_URL}/.well-known/dspace-version" "" "" || true
}

verify_tmforum_seed() {
  log "--- TMForum: offerings per context"
  # Each line: context offerings-expected offerings-not-expected (comma-separated)
  while read -r context present absent; do
    if expect_status "offerings of ${context}" "$HTTP_OK" \
      GET "${CATALOG_API}/productOffering?participantContextIds=${context},${ALL_CONTEXTS}&limit=${TMF_PAGE_LIMIT}" "" ""; then
      expect_body "${context} offerings include [${present}] and exclude [${absent}]" \
        '[.[].externalId] as $ids
         | all(($present | split(","))[]; . as $o | $ids | index($o) != null)
           and all(($absent | split(","))[]; . as $o | $ids | index($o) == null)' \
        --arg present "$present" --arg absent "$absent"
    fi
  done <<EOF
${CONTEXT_A} ${OFFER_SHARED_A},${OFFER_A_ONLY},${OFFER_ALL} ${OFFER_SHARED_B},${OFFER_LEGACY}
${CONTEXT_B} ${OFFER_SHARED_B},${OFFER_ALL} ${OFFER_SHARED_A},${OFFER_A_ONLY},${OFFER_LEGACY}
EOF
  if expect_status "legacy offering is seeded" "$HTTP_OK" \
    GET "${CATALOG_API}/productOffering?externalId=${OFFER_LEGACY}" "" ""; then
    expect_body "legacy offering has no participantContextIds" \
      'length == 1 and (.[0].participantContextIds == null)'
  fi
}

verify_edrs() {
  log "--- Management API: EDRs"
  for context in "$CONTEXT_A" "$CONTEXT_B"; do
    if expect_status "admin queries EDRs of ${context}" "$HTTP_OK" \
      POST "${PARTICIPANTS_API}/${context}/edrs/request" "$ADMIN_TOKEN" "$QUERY_SPEC"; then
      expect_body "EDRs of ${context} are a JSON array" 'type == "array"'
    fi
  done
  expect_status "non-admin ${CONTEXT_B} queries its own EDRs" "$HTTP_OK" \
    POST "${PARTICIPANTS_API}/${CONTEXT_B}/edrs/request" "$CONTEXT_B_TOKEN" "$QUERY_SPEC" || true
  expect_status "non-admin ${CONTEXT_B} cannot query EDRs of ${CONTEXT_A}" "$HTTP_FORBIDDEN" \
    POST "${PARTICIPANTS_API}/${CONTEXT_A}/edrs/request" "$CONTEXT_B_TOKEN" "$QUERY_SPEC" || true

  # An unknown id in a foreign context is denied either way: 403 or 404, whichever check fails first.
  status=$(request GET "${PARTICIPANTS_API}/${CONTEXT_A}/edrs/${UNKNOWN_TRANSFER}/dataaddress" \
    "$CONTEXT_B_TOKEN" "") || true
  if [ "$status" = "$HTTP_FORBIDDEN" ] || [ "$status" = "$HTTP_NOT_FOUND" ]; then
    pass "non-admin ${CONTEXT_B} cannot read an EDR through ${CONTEXT_A} (${status})"
  else
    fail "non-admin ${CONTEXT_B} reads an EDR through ${CONTEXT_A}: expected ${HTTP_FORBIDDEN} or ${HTTP_NOT_FOUND}, got ${status}: $(head -c 300 "$RESPONSE_FILE")"
  fi
  expect_status "unknown EDR in ${CONTEXT_B} is not found" "$HTTP_NOT_FOUND" \
    GET "${PARTICIPANTS_API}/${CONTEXT_B}/edrs/${UNKNOWN_TRANSFER}/dataaddress" "$CONTEXT_B_TOKEN" "" || true
  expect_status "read-only ${CONTEXT_B} token cannot delete an EDR" "$HTTP_FORBIDDEN" \
    DELETE "${PARTICIPANTS_API}/${CONTEXT_B}/edrs/${UNKNOWN_TRANSFER}" "$CONTEXT_B_TOKEN" "" || true
  expect_status "EDR query without token is rejected" "$HTTP_UNAUTHORIZED" \
    POST "${PARTICIPANTS_API}/${CONTEXT_B}/edrs/request" "" "$QUERY_SPEC" || true
}

log "=== Multi-dataspace verification ==="
ADMIN_TOKEN=$(token "$ADMIN_SUBJECT" "$ADMIN_SCOPE")
CONTEXT_B_TOKEN=$(token "$CONTEXT_B" "$READ_SCOPE")

verify_participants || true
verify_assets || true
verify_dsp_routing
verify_dsp_versions
verify_tmforum_seed || true
verify_edrs

if [ "$failures" -gt 0 ]; then
  log "=== ${failures} of ${checks} checks FAILED ==="
  exit 1
fi
log "=== All ${checks} checks passed ==="
