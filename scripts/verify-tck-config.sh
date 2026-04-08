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
# verify-tck-config.sh — Static verification of TCK configuration consistency
#
# Checks that IDs in tck.properties match those in DataAssembly.java,
# and that port/endpoint configuration is consistent across all TCK config
# files and Docker Compose.
#
# Usage:
#   ./scripts/verify-tck-config.sh
#
# Exit code 0 = all checks pass, non-zero = mismatches found.
###############################################################################

set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
readonly TCK_PROPS="${PROJECT_ROOT}/config/tck/tck.properties"
readonly EDC_PROPS="${PROJECT_ROOT}/config/tck/edc.properties"
readonly COMPOSE_FILE="${PROJECT_ROOT}/docker-compose.tck.yml"
readonly DATA_ASSEMBLY="${PROJECT_ROOT}/test-extension/src/main/java/org/seamware/edc/edc/DataAssembly.java"

ERRORS=0

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

## Print a check result.
pass() { echo "  ✓ $*"; }
fail() { echo "  ✗ $*" >&2; ERRORS=$((ERRORS + 1)); }

## Check that a file exists.
require_file() {
  if [[ ! -f "$1" ]]; then
    fail "Required file not found: $1"
    return 1
  fi
  return 0
}

# ---------------------------------------------------------------------------
# 1. Verify required files exist
# ---------------------------------------------------------------------------
echo "=== Checking required files ==="
require_file "${TCK_PROPS}" || exit 1
require_file "${EDC_PROPS}" || exit 1
require_file "${COMPOSE_FILE}" || exit 1
require_file "${DATA_ASSEMBLY}" || exit 1
pass "All required files exist"

# ---------------------------------------------------------------------------
# 2. Extract IDs from DataAssembly.java
# ---------------------------------------------------------------------------
echo ""
echo "=== Checking asset ID consistency ==="

# Extract ASSET_IDS from DataAssembly.java (e.g., "ACN0101", "CAT0101")
ASSET_IDS=$(grep -oP '"[A-Z]+\d+"' "${DATA_ASSEMBLY}" | grep -P '"(ACN|CAT)\d+"' | tr -d '"' | sort -u)

# Extract asset IDs referenced in tck.properties (DATASETID values and the
# asset-id portion of OFFERID values, which use the format CD123:<assetId>).
# Only from CN_ and CAT_ lines (not CN_C_ which use ACNC IDs).
TCK_ASSET_IDS=$(grep -P '^(CN_\d|CAT_\d).*=' "${TCK_PROPS}" | grep -oP '(ACN|CAT)\d+' | sort -u)

for id in ${TCK_ASSET_IDS}; do
  if echo "${ASSET_IDS}" | grep -qx "${id}"; then
    pass "Asset ID ${id} found in DataAssembly.java"
  else
    fail "Asset ID ${id} referenced in tck.properties but NOT in DataAssembly.java"
  fi
done

# ---------------------------------------------------------------------------
# 3. Check consumer negotiation IDs (ACNC*)
# ---------------------------------------------------------------------------
echo ""
echo "=== Checking consumer negotiation ID consistency ==="

# Extract ACNC IDs from DataAssembly.java triggers
ACNC_IDS=$(grep -oP '"ACNC\d+"' "${DATA_ASSEMBLY}" | tr -d '"' | sort -u)

# Extract ACNC IDs from tck.properties CN_C_ lines
TCK_ACNC_IDS=$(grep -P '^CN_C_' "${TCK_PROPS}" | grep -oP '=ACNC\d+' | tr -d '=' | sort -u)

for id in ${TCK_ACNC_IDS}; do
  if echo "${ACNC_IDS}" | grep -qx "${id}"; then
    pass "Consumer negotiation ID ${id} found in DataAssembly.java"
  else
    fail "Consumer negotiation ID ${id} referenced in tck.properties but NOT in DataAssembly.java"
  fi
done

# ---------------------------------------------------------------------------
# 4. Check agreement IDs (ATP* and ATPC*)
# ---------------------------------------------------------------------------
echo ""
echo "=== Checking agreement ID consistency ==="

# Extract AGREEMENT_IDS from DataAssembly.java
AGREEMENT_IDS=$(grep -oP '"ATP[C]?\d+"' "${DATA_ASSEMBLY}" | tr -d '"' | sort -u)

# Extract agreement IDs from tck.properties TP_ and TP_C_ lines
TCK_AGREEMENT_IDS=$(grep -P '^TP_' "${TCK_PROPS}" | grep -P 'AGREEMENTID=' | grep -oP '=ATP[C]?\d+' | tr -d '=' | sort -u)

for id in ${TCK_AGREEMENT_IDS}; do
  if echo "${AGREEMENT_IDS}" | grep -qx "${id}"; then
    pass "Agreement ID ${id} found in DataAssembly.java"
  else
    fail "Agreement ID ${id} referenced in tck.properties but NOT in DataAssembly.java"
  fi
done

# ---------------------------------------------------------------------------
# 5. Verify port consistency
# ---------------------------------------------------------------------------
echo ""
echo "=== Checking port consistency ==="

# DSP protocol port (should be 8282 across all files)
DSP_PORT_EDC=$(grep -P '^web\.http\.protocol\.port=' "${EDC_PROPS}" | grep -oP '\d+')
DSP_PORT_TCK=$(grep -P 'connector\.http\.url=' "${TCK_PROPS}" | grep -oP ':\d+' | tr -d ':')

if [[ "${DSP_PORT_EDC}" == "${DSP_PORT_TCK}" ]]; then
  pass "DSP protocol port consistent: ${DSP_PORT_EDC}"
else
  fail "DSP protocol port mismatch: edc.properties=${DSP_PORT_EDC}, tck.properties=${DSP_PORT_TCK}"
fi

# TCK webhook port (should be 8687 across all files)
TCK_PORT_EDC=$(grep -P '^web\.http\.tck\.port=' "${EDC_PROPS}" | grep -oP '\d+')
TCK_PORT_NEGOTIATE=$(grep -P 'negotiation\.initiate\.url=' "${TCK_PROPS}" | grep -oP ':\d+' | tr -d ':')
TCK_PORT_TRANSFER=$(grep -P 'transfer\.initiate\.url=' "${TCK_PROPS}" | grep -oP ':\d+' | tr -d ':')

if [[ "${TCK_PORT_EDC}" == "${TCK_PORT_NEGOTIATE}" && "${TCK_PORT_EDC}" == "${TCK_PORT_TRANSFER}" ]]; then
  pass "TCK webhook port consistent: ${TCK_PORT_EDC}"
else
  fail "TCK webhook port mismatch: edc=${TCK_PORT_EDC}, negotiate=${TCK_PORT_NEGOTIATE}, transfer=${TCK_PORT_TRANSFER}"
fi

# ---------------------------------------------------------------------------
# 6. Verify participant ID consistency
# ---------------------------------------------------------------------------
echo ""
echo "=== Checking participant ID consistency ==="

PARTICIPANT_EDC=$(grep -P '^edc\.participant\.id=' "${EDC_PROPS}" | cut -d= -f2)
PARTICIPANT_TCK=$(grep -P '^dataspacetck\.dsp\.connector\.agent\.id=' "${TCK_PROPS}" | cut -d= -f2)

if [[ "${PARTICIPANT_EDC}" == "${PARTICIPANT_TCK}" ]]; then
  pass "Participant ID consistent: ${PARTICIPANT_EDC}"
else
  fail "Participant ID mismatch: edc.properties=${PARTICIPANT_EDC}, tck.properties=${PARTICIPANT_TCK}"
fi

# ---------------------------------------------------------------------------
# 7. Verify webhook path consistency
# ---------------------------------------------------------------------------
echo ""
echo "=== Checking webhook path consistency ==="

TCK_PATH_EDC=$(grep -P '^web\.http\.tck\.path=' "${EDC_PROPS}" | cut -d= -f2)
NEGOTIATE_PATH=$(grep -P 'negotiation\.initiate\.url=' "${TCK_PROPS}" | grep -oP ':\d+\K/[^ ]*')
TRANSFER_PATH=$(grep -P 'transfer\.initiate\.url=' "${TCK_PROPS}" | grep -oP ':\d+\K/[^ ]*')

# The webhook paths should start with the TCK context path
if [[ "${NEGOTIATE_PATH}" == "${TCK_PATH_EDC}/negotiations/requests" ]]; then
  pass "Negotiation webhook path consistent: ${NEGOTIATE_PATH}"
else
  fail "Negotiation webhook path mismatch: expected ${TCK_PATH_EDC}/negotiations/requests, got ${NEGOTIATE_PATH}"
fi

if [[ "${TRANSFER_PATH}" == "${TCK_PATH_EDC}/transfers/requests" ]]; then
  pass "Transfer webhook path consistent: ${TRANSFER_PATH}"
else
  fail "Transfer webhook path mismatch: expected ${TCK_PATH_EDC}/transfers/requests, got ${TRANSFER_PATH}"
fi

# ---------------------------------------------------------------------------
# 8. Verify test extension settings are enabled
# ---------------------------------------------------------------------------
echo ""
echo "=== Checking test extension enablement ==="

for prop in "testExtension.enabled" "testExtension.controller.enabled" "testExtension.identity.enabled"; do
  val=$(grep -P "^${prop}=" "${EDC_PROPS}" | cut -d= -f2)
  if [[ "${val}" == "true" ]]; then
    pass "${prop}=true"
  else
    fail "${prop} should be true but is '${val}'"
  fi
done

# ---------------------------------------------------------------------------
# 9. Verify DSP callback address
# ---------------------------------------------------------------------------
echo ""
echo "=== Checking DSP callback address ==="

CALLBACK_EDC=$(grep -P '^edc\.dsp\.callback\.address=' "${EDC_PROPS}" | cut -d= -f2)
DSP_URL_TCK=$(grep -P '^dataspacetck\.dsp\.connector\.http\.url=' "${TCK_PROPS}" | cut -d= -f2)

if [[ "${CALLBACK_EDC}" == "${DSP_URL_TCK}" ]]; then
  pass "DSP callback address matches TCK connector URL: ${CALLBACK_EDC}"
else
  fail "DSP callback address mismatch: edc=${CALLBACK_EDC}, tck=${DSP_URL_TCK}"
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo ""
echo "==========================================="
if [[ "${ERRORS}" -eq 0 ]]; then
  echo "All verification checks PASSED"
  echo "==========================================="
  exit 0
else
  echo "${ERRORS} verification check(s) FAILED"
  echo "==========================================="
  exit 1
fi
