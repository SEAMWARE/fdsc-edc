#!/usr/bin/env bash
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
# verify-tmf-audit.sh — Check that catalog changes made directly in TMForum
# reach the connector through the TMF620 hub and are emitted as OpenTelemetry
# audit logs.
#
# Requires the TCK stack up to the connector, with a TMForum version that sends
#   TMF_VERSION=1.14.1 docker compose -f docker-compose.tck.yml up --build -d edc
#   ./scripts/verify-tmf-audit.sh
###############################################################################

set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly COMPOSE_FILE="${SCRIPT_DIR}/../docker-compose.tck.yml"
readonly RUN_ID="$(date +%s)"
readonly ASSET_ID="AUDIT-ASSET-${RUN_ID}"
readonly OFFER_ID="AUDIT-CD-${RUN_ID}"
readonly POLICY_ID="AUDIT-POL-${RUN_ID}"
readonly EXPECTED_SUBSCRIPTIONS=6
# created, updated, and updated + state_changed when retired
readonly EXPECTED_RECORDS=4
readonly TIMEOUT=120
readonly POLL_INTERVAL=5

compose() {
  docker compose -f "${COMPOSE_FILE}" "$@"
}

log() {
  echo "[$(date '+%H:%M:%S')] $*"
}

subscriptions() {
  compose logs edc 2>/dev/null \
    | grep -c -E 'Otel Audit: (subscribed to TMForum|TMForum subscription already exists)' || true
}

records_for() {
  compose logs otel-collector 2>/dev/null | grep -c -- "-> $1: Str($2)" || true
}

wait_for() {
  local description="$1"
  shift
  local elapsed=0
  until "$@"; do
    if [ "${elapsed}" -ge "${TIMEOUT}" ]; then
      log "ERROR: timed out after ${TIMEOUT}s waiting for ${description}"
      return 1
    fi
    sleep "${POLL_INTERVAL}"
    elapsed=$((elapsed + POLL_INTERVAL))
  done
}

subscribed() {
  [ "$(subscriptions)" -ge "${EXPECTED_SUBSCRIPTIONS}" ]
}

audited() {
  [ "$(records_for audit.entity.id "${ASSET_ID}")" -ge "${EXPECTED_RECORDS}" ] \
    && [ "$(records_for audit.entity.id "${OFFER_ID}")" -ge "${EXPECTED_RECORDS}" ]
}

log "Waiting for the connector to subscribe to the TMForum product catalog hub..."
wait_for "the TMForum subscriptions" subscribed

log "Creating, updating and retiring ${ASSET_ID} and ${OFFER_ID} in TMForum..."
compose exec -T \
  -e ASSET_ID="${ASSET_ID}" -e OFFER_ID="${OFFER_ID}" -e POLICY_ID="${POLICY_ID}" \
  tmf-init sh -s <<'EOF'
set -eu
C=http://tmforum:8632/tmf-api/productCatalogManagement/v4
J='Content-Type: application/json'
SL=https://raw.githubusercontent.com/wistefan/edc-dsc/refs/heads/init/schemas/external-id.json
CDS=https://raw.githubusercontent.com/wistefan/edc-dsc/refs/heads/init/schemas/contract-definition.json

policy() {
  echo "{\"@type\":\"http://www.w3.org/ns/odrl/2/Set\",\"http://www.w3.org/ns/odrl/2/uid\":\"$1\",\"http://www.w3.org/ns/odrl/2/permission\":[{\"http://www.w3.org/ns/odrl/2/action\":[{\"@id\":\"http://www.w3.org/ns/odrl/2/use\"}]}]}"
}
terms() {
  echo "[{\"name\":\"edc:contractDefinition\",\"@schemaLocation\":\"$CDS\",\"contractPolicy\":$(policy "$1"),\"accessPolicy\":$(policy "$1")}]"
}
call() {
  curl -sS --fail-with-body -H "$J" "$@"
}

SPEC=$(call -X POST "$C/productSpecification" \
  -d "{\"name\":\"$ASSET_ID\",\"externalId\":\"$ASSET_ID\",\"@schemaLocation\":\"$SL\",\"lifecycleStatus\":\"Active\"}" \
  | grep -o '"id":"urn:ngsi-ld:product-specification:[^"]*"' | head -1 | cut -d'"' -f4)
sleep 2
call -o /dev/null -X PATCH "$C/productSpecification/$SPEC" -d "{\"name\":\"$ASSET_ID-renamed\"}"
sleep 2
OFFER=$(call -X POST "$C/productOffering" \
  -d "{\"name\":\"$OFFER_ID\",\"externalId\":\"$OFFER_ID\",\"@schemaLocation\":\"$SL\",\"lifecycleStatus\":\"Active\",\"productSpecification\":{\"id\":\"$SPEC\"},\"productOfferingTerm\":$(terms "$POLICY_ID-1")}" \
  | grep -o '"id":"urn:ngsi-ld:product-offering:[^"]*"' | head -1 | cut -d'"' -f4)
sleep 2
call -o /dev/null -X PATCH "$C/productOffering/$OFFER" -d "{\"productOfferingTerm\":$(terms "$POLICY_ID-2")}"
sleep 2
call -o /dev/null -X PATCH "$C/productOffering/$OFFER" -d '{"lifecycleStatus":"retired"}'
sleep 2
call -o /dev/null -X PATCH "$C/productSpecification/$SPEC" -d '{"lifecycleStatus":"retired"}'
echo "specification=$SPEC offering=$OFFER"
EOF

log "Waiting for the audit logs to reach the collector..."
if ! wait_for "the audit logs" audited; then
  log "asset records: $(records_for audit.entity.id "${ASSET_ID}"), offering records: $(records_for audit.entity.id "${OFFER_ID}")"
  exit 1
fi
# let late duplicates arrive before checking that none was audited twice
sleep "${POLL_INTERVAL}"

failures=0
check() {
  local description="$1" actual="$2" expected="$3"
  if [ "${actual}" -eq "${expected}" ]; then
    log "OK   ${description}: ${actual}"
  else
    log "FAIL ${description}: expected ${expected}, got ${actual}"
    failures=$((failures + 1))
  fi
}

check "asset audit records" "$(records_for audit.entity.id "${ASSET_ID}")" "${EXPECTED_RECORDS}"
check "contract definition audit records" "$(records_for audit.entity.id "${OFFER_ID}")" "${EXPECTED_RECORDS}"
check "records with the updated contract policy" \
  "$(records_for edc.contract_policy.id "${POLICY_ID}-2")" 2
check "records with the initial contract policy" \
  "$(records_for edc.contract_policy.id "${POLICY_ID}-1")" 1

if [ "${failures}" -gt 0 ]; then
  log "${failures} check(s) failed. Inspect: docker compose -f docker-compose.tck.yml logs otel-collector"
  exit 1
fi
log "Catalog audit verified."
