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
# run-tck.sh — Run Eclipse DSP TCK conformance tests against the fdsc-edc
#               controlplane in a single command.
#
# Usage:
#   ./scripts/run-tck.sh [OPTIONS]
#
# Options:
#   --skip-build        Skip the Maven build step (use an existing JAR).
#   --keep-containers   Leave containers running after tests for debugging.
#   --help              Show this help message and exit.
#
# Environment variables:
#   DSP_TCK_VERSION     Override the DSP TCK Docker image tag
#                       (default: defined in docker-compose.tck.yml).
#   MVN_ARGS            Additional arguments to pass to the Maven build
#                       (e.g., MVN_ARGS="-T 2C" for parallel builds).
###############################################################################

set -euo pipefail

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
readonly COMPOSE_FILE="${PROJECT_ROOT}/docker-compose.tck.yml"
readonly CONTROLPLANE_MODULE="controlplane-oid4vc"
readonly SHADED_JAR="${PROJECT_ROOT}/${CONTROLPLANE_MODULE}/target/context/${CONTROLPLANE_MODULE}.jar"

# ---------------------------------------------------------------------------
# Defaults
# ---------------------------------------------------------------------------
SKIP_BUILD=false
KEEP_CONTAINERS=false

# ---------------------------------------------------------------------------
# Functions
# ---------------------------------------------------------------------------

## Print usage information.
usage() {
  cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Run Eclipse DSP TCK conformance tests against the fdsc-edc controlplane.

Options:
  --skip-build        Skip the Maven build step (requires an existing shaded JAR).
  --keep-containers   Leave containers running after tests (useful for debugging).
  --help              Show this help message and exit.

Environment variables:
  DSP_TCK_VERSION     Override the DSP TCK Docker image tag.
  MVN_ARGS            Additional arguments for the Maven build.

Examples:
  # Full build and test run
  ./scripts/run-tck.sh

  # Skip build, reuse existing JAR
  ./scripts/run-tck.sh --skip-build

  # Keep containers running for inspection
  ./scripts/run-tck.sh --keep-containers

  # Override TCK version
  DSP_TCK_VERSION=1.0.0-RC6 ./scripts/run-tck.sh
EOF
}

## Print a timestamped log message.
log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

## Print an error message to stderr and exit.
die() {
  echo "[ERROR] $*" >&2
  exit 1
}

## Build the controlplane shaded JAR with Maven.
build_controlplane() {
  log "Building controlplane shaded JAR (${CONTROLPLANE_MODULE})..."
  # shellcheck disable=SC2086
  mvn -f "${PROJECT_ROOT}/pom.xml" \
    clean package \
    -pl "${CONTROLPLANE_MODULE}" -am \
    -DskipTests \
    ${MVN_ARGS:-}
  log "Build completed successfully."
}

## Verify that the shaded JAR exists.
verify_jar() {
  if [[ ! -f "${SHADED_JAR}" ]]; then
    die "Shaded JAR not found at ${SHADED_JAR}. Run without --skip-build or build manually first."
  fi
  log "Using shaded JAR: ${SHADED_JAR}"
}

## Run Docker Compose to start the EDC controlplane and the TCK runner.
run_tck() {
  log "Starting DSP TCK conformance tests via Docker Compose..."
  log "Compose file: ${COMPOSE_FILE}"

  local tck_exit_code=0
  docker compose -f "${COMPOSE_FILE}" up \
    --build \
    --abort-on-container-exit \
    --exit-code-from tck || tck_exit_code=$?

  return "${tck_exit_code}"
}

## Tear down Docker Compose containers and networks.
cleanup() {
  if [[ "${KEEP_CONTAINERS}" == "true" ]]; then
    log "Keeping containers running (--keep-containers). To stop them run:"
    log "  docker compose -f ${COMPOSE_FILE} down"
  else
    log "Tearing down containers..."
    docker compose -f "${COMPOSE_FILE}" down --volumes --remove-orphans 2>/dev/null || true
  fi
}

# ---------------------------------------------------------------------------
# Parse arguments
# ---------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-build)
      SKIP_BUILD=true
      shift
      ;;
    --keep-containers)
      KEEP_CONTAINERS=true
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      die "Unknown option: $1 (use --help for usage)"
      ;;
  esac
done

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
log "=== DSP TCK Conformance Test Runner ==="
log "Project root: ${PROJECT_ROOT}"

# Step 1: Build (unless skipped)
if [[ "${SKIP_BUILD}" == "true" ]]; then
  log "Skipping Maven build (--skip-build)."
else
  build_controlplane
fi

# Step 2: Verify JAR exists
verify_jar

# Step 3: Run TCK tests
tck_exit_code=0
run_tck || tck_exit_code=$?

# Step 4: Cleanup
cleanup

# Step 5: Report results
echo ""
echo "==========================================="
if [[ "${tck_exit_code}" -eq 0 ]]; then
  log "DSP TCK conformance tests PASSED"
  echo "==========================================="
else
  log "DSP TCK conformance tests FAILED (exit code: ${tck_exit_code})"
  echo "==========================================="
  log "Hint: Re-run with --keep-containers to inspect the running EDC controlplane."
fi

exit "${tck_exit_code}"
