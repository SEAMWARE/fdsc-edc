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
#   TCK_FILTER          Run only tests whose display name contains this
#                       substring (e.g., TCK_FILTER=TP:03).
#
# Container logs are written to ./tck-logs/ (all services on failure, the TCK
# runner only on success), mirroring the CI "tck-conformance" job artifacts.
###############################################################################

set -euo pipefail

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
readonly COMPOSE_FILE="${PROJECT_ROOT}/docker-compose.tck.yml"
# TCK-only assembly: controlplane-oid4vc minus data-plane-signaling, so the
# NoopDataFlowController wins and transfers complete without a data plane
# (docs/migration/EDC-0.18.1-UPGRADE.md 8.3.2).
readonly CONTROLPLANE_MODULE="controlplane-tck"
readonly SHADED_JAR="${PROJECT_ROOT}/${CONTROLPLANE_MODULE}/target/context/${CONTROLPLANE_MODULE}.jar"

# Maximum time (seconds) to wait for the TCK container to start
readonly TCK_START_TIMEOUT=600
# Poll interval (seconds) while waiting for TCK container
readonly POLL_INTERVAL=5
# Exit code returned by the kernel OOM-killer / Docker SIGKILL
readonly SIGKILL_EXIT_CODE=137

# Java version the project is built with (matches setup-java in
# .github/workflows/test.yml and maven.compiler.target in pom.xml).
readonly REQUIRED_JAVA_VERSION=21

# Directory the collected container logs are written to (mirrors the
# "tck-logs" artifact uploaded by the CI tck-conformance job).
readonly LOG_DIR="${PROJECT_ROOT}/tck-logs"
# Compose services whose logs are collected when the run fails.
readonly LOG_SERVICES=(postgis scorpio tmforum tmf-init pap-postgres opa odrl-pap edc tck)
# DSP TCK image tag. Exported so docker compose picks it up as a build arg;
# the default matches DSP_TCK_VERSION in .github/workflows/test.yml.
export DSP_TCK_VERSION="${DSP_TCK_VERSION:-1.0.1}"

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
  TCK_FILTER          Run only tests whose display name contains this value.

Container logs are written to ./tck-logs/ (all services on failure, the TCK
runner only on success), mirroring the CI "tck-conformance" job artifacts.

Examples:
  # Full build and test run
  ./scripts/run-tck.sh

  # Skip build, reuse existing JAR
  ./scripts/run-tck.sh --skip-build

  # Keep containers running for inspection
  ./scripts/run-tck.sh --keep-containers

  # Override TCK version
  DSP_TCK_VERSION=1.0.1 ./scripts/run-tck.sh
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

## Return the major version of the JDK that ${1} (a JAVA_HOME) points to,
## or nothing when that path holds no usable java binary.
java_major_version() {
  local java_home="$1"
  local java_bin="${java_home}/bin/java"

  [[ -x "${java_bin}" ]] || return 0
  "${java_bin}" -version 2>&1 \
    | sed -nE '1s/.*version "([0-9]+).*/\1/p'
}

## Point JAVA_HOME at a JDK ${REQUIRED_JAVA_VERSION} installation when the
## current default JDK is a different major version, so local builds match CI.
## Warns and continues when no matching JDK is installed.
select_java() {
  local current
  current="$(java_major_version "${JAVA_HOME:-/usr}")"
  if [[ -z "${current}" && -x "$(command -v java 2>/dev/null)" ]]; then
    current="$(java -version 2>&1 | sed -nE '1s/.*version "([0-9]+).*/\1/p')"
  fi

  if [[ "${current}" == "${REQUIRED_JAVA_VERSION}" ]]; then
    log "Using Java ${current} (JAVA_HOME=${JAVA_HOME:-<default>})."
    return 0
  fi

  local candidate
  for candidate in /usr/lib/jvm/java-${REQUIRED_JAVA_VERSION}-openjdk-* \
                   /usr/lib/jvm/temurin-${REQUIRED_JAVA_VERSION}* \
                   /usr/lib/jvm/*-${REQUIRED_JAVA_VERSION}-*; do
    if [[ -d "${candidate}" ]] \
      && [[ "$(java_major_version "${candidate}")" == "${REQUIRED_JAVA_VERSION}" ]]; then
      export JAVA_HOME="${candidate}"
      export PATH="${JAVA_HOME}/bin:${PATH}"
      log "Switched to Java ${REQUIRED_JAVA_VERSION} (JAVA_HOME=${JAVA_HOME})."
      return 0
    fi
  done

  log "WARNING: Java ${REQUIRED_JAVA_VERSION} not found; building with Java ${current:-unknown}."
  log "         CI builds with Java ${REQUIRED_JAVA_VERSION}, so results may differ."
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

## Start all services in detached mode, wait for the TCK container to exit,
## and return a non-zero code when the EDC crashes or any TCK test fails.
##
## Uses detached mode (`docker compose up -d`) instead of
## `--abort-on-container-exit` so that the tmf-init sidecar (which stays
## alive via `tail -f /dev/null`) does not inadvertently stop the stack.
run_tck() {
  log "Starting DSP TCK conformance tests via Docker Compose..."
  log "Compose file: ${COMPOSE_FILE}"

  # Remove leftovers from a previous run first. CI always starts from a clean
  # slate; locally a stale network or container makes `up` fail.
  docker compose -f "${COMPOSE_FILE}" down --volumes --remove-orphans >/dev/null 2>&1 || true

  # Build images and start all services in the background.
  # The exit code must be checked explicitly: run_tck is called in a `||`
  # context, which disables `set -e` for the whole function body.
  if ! docker compose -f "${COMPOSE_FILE}" up --build -d; then
    log "ERROR: 'docker compose up' failed - the stack did not start."
    docker compose -f "${COMPOSE_FILE}" ps -a 2>/dev/null || true
    return 1
  fi

  # -----------------------------------------------------------------------
  # Wait for the TCK container to appear and then finish
  # -----------------------------------------------------------------------
  log "Waiting for TCK container to start (timeout: ${TCK_START_TIMEOUT}s)..."
  local waited=0
  local tck_id=""

  while true; do
    tck_id="$(docker compose -f "${COMPOSE_FILE}" ps -q tck 2>/dev/null)" || true
    if [[ -n "${tck_id}" ]]; then
      break
    fi
    if (( waited >= TCK_START_TIMEOUT )); then
      log "ERROR: TCK container did not start within ${TCK_START_TIMEOUT}s."
      docker compose -f "${COMPOSE_FILE}" logs 2>/dev/null | tail -40
      return 1
    fi
    sleep "${POLL_INTERVAL}"
    (( waited += POLL_INTERVAL ))
  done

  log "TCK container started (id: ${tck_id:0:12}). Waiting for test run to complete..."

  # Stream the TCK logs in the background so test progress is visible while
  # the suite runs, instead of only after it finishes.
  docker compose -f "${COMPOSE_FILE}" logs -f tck &
  local log_pid=$!

  # `docker wait` blocks until the container exits, then prints the exit code
  docker wait "${tck_id}" >/dev/null 2>&1 || true

  # Stop the log streamer
  kill "${log_pid}" 2>/dev/null || true
  wait "${log_pid}" 2>/dev/null || true

  local tck_exit
  tck_exit="$(docker inspect --format='{{.State.ExitCode}}' "${tck_id}" 2>/dev/null)" || tck_exit="1"

  # -----------------------------------------------------------------------
  # Check EDC health — only flag non-137 exits as errors.
  # Exit code 137 (SIGKILL) is expected when the stack is torn down after
  # the TCK finishes; it does NOT indicate an EDC failure.
  # -----------------------------------------------------------------------
  local edc_id
  edc_id="$(docker compose -f "${COMPOSE_FILE}" ps -q edc 2>/dev/null | head -1)" || edc_id=""
  if [[ -n "${edc_id}" ]]; then
    local edc_running
    edc_running="$(docker inspect --format='{{.State.Running}}' "${edc_id}" 2>/dev/null)" || edc_running="false"
    local edc_exit
    edc_exit="$(docker inspect --format='{{.State.ExitCode}}' "${edc_id}" 2>/dev/null)" || edc_exit=""

    if [[ "${edc_running}" == "false" && -n "${edc_exit}" && "${edc_exit}" != "0" && "${edc_exit}" != "${SIGKILL_EXIT_CODE}" ]]; then
      log "ERROR: EDC container exited unexpectedly with code ${edc_exit}. Dumping EDC logs:"
      docker compose -f "${COMPOSE_FILE}" logs edc 2>/dev/null | tail -80
      return 1
    fi
  fi

  return "${tck_exit}"
}

## Extract and display individual test results from the TCK container logs.
## Parses the TCK output for PASSED/FAILED lines and the summary, then
## prints them. Also determines pass/fail from the log content when the
## TCK exit code is unreliable (TCK may exit 0 even on failures).
## Sets the global TCK_TESTS_FAILED flag.
TCK_TESTS_FAILED=false

print_test_results() {
  log "Collecting individual test results from TCK container logs..."
  echo ""

  local tck_logs
  tck_logs="$(docker compose -f "${COMPOSE_FILE}" logs tck 2>/dev/null)" || true

  if [[ -z "${tck_logs}" ]]; then
    log "WARNING: No TCK container logs available."
    TCK_TESTS_FAILED=true
    return 1
  fi

  # Print lines that look like test results (JUnit/TCK output patterns)
  local result_lines
  result_lines="$(echo "${tck_logs}" \
    | grep -iE '(PASS(ED)?|FAIL(ED)?|SKIP|ERROR|Tests run:|test.*result|Suite |── |✓|✗|✔|✘|SUCCESSFUL|ABORTED|Passed tests:|Failed tests:|Failures:)' \
    || true)"

  if [[ -n "${result_lines}" ]]; then
    echo "-------------------------------------------"
    echo "  Individual Test Results"
    echo "-------------------------------------------"
    echo "${result_lines}"
    echo "-------------------------------------------"
  else
    # If no structured results found, dump the last portion of TCK logs
    log "No structured test results found. Showing last 60 lines of TCK logs:"
    echo "-------------------------------------------"
    echo "${tck_logs}" | tail -60
    echo "-------------------------------------------"
  fi

  # Detect failures from TCK logs — the TCK runtime may exit 0 even when
  # tests fail, so we must parse the log output to determine the true result.
  local failed_count
  failed_count="$(echo "${tck_logs}" | grep -oP 'Failed tests:\s*\K\d+' | tail -1)" || failed_count=""
  local passed_count
  passed_count="$(echo "${tck_logs}" | grep -oP 'Passed tests:\s*\K\d+' | tail -1)" || passed_count=""

  if [[ -n "${failed_count}" && "${failed_count}" != "0" ]]; then
    log "TCK reported ${failed_count} failed test(s) and ${passed_count:-0} passed."
    TCK_TESTS_FAILED=true
  elif echo "${tck_logs}" | grep -qi "there were failing tests"; then
    log "TCK reported failing tests."
    TCK_TESTS_FAILED=true
  elif [[ -n "${passed_count}" && "${passed_count}" != "0" ]]; then
    log "TCK reported ${passed_count} passed test(s), 0 failures."
  fi

  # Print failure details (connection errors, exceptions, etc.)
  local failure_details
  failure_details="$(echo "${tck_logs}" | grep -E '(ConnectException|IOException|Exception|Error):' | head -20 || true)"
  if [[ -n "${failure_details}" ]]; then
    echo ""
    echo "-------------------------------------------"
    echo "  Failure Details (first 20 unique errors)"
    echo "-------------------------------------------"
    echo "${failure_details}" | sort -u
    echo "-------------------------------------------"
  fi

  return 0
}

## Write container logs to ${LOG_DIR}, mirroring the log artifacts the CI
## tck-conformance job uploads: every service on failure, only the TCK
## runner on success.
collect_logs() {
  local failed="$1"

  mkdir -p "${LOG_DIR}"

  if [[ "${failed}" == "true" ]]; then
    local service
    for service in "${LOG_SERVICES[@]}"; do
      docker compose -f "${COMPOSE_FILE}" logs "${service}" \
        > "${LOG_DIR}/${service}.log" 2>&1 || true
    done
  else
    docker compose -f "${COMPOSE_FILE}" logs tck \
      > "${LOG_DIR}/tck.log" 2>&1 || true
  fi

  log "Container logs written to ${LOG_DIR}/"
}

## Tear down Docker Compose containers and networks. Idempotent: the EXIT
## trap and the main flow may both reach it.
CLEANUP_DONE=false

cleanup() {
  if [[ "${CLEANUP_DONE}" == "true" ]]; then
    return 0
  fi
  CLEANUP_DONE=true

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
# Guarantee teardown even when a later step fails unexpectedly, so a broken
# run never leaves the stack behind.
trap cleanup EXIT

log "=== DSP TCK Conformance Test Runner ==="
log "Project root: ${PROJECT_ROOT}"

# Step 1: Build (unless skipped)
if [[ "${SKIP_BUILD}" == "true" ]]; then
  log "Skipping Maven build (--skip-build)."
else
  select_java
  build_controlplane
fi

# Step 2: Verify JAR exists
verify_jar

# Step 3: Run TCK tests
tck_exit_code=0
run_tck || tck_exit_code=$?

# Step 4: Display individual test results (before tearing down containers).
# Must not abort the run: it returns non-zero when no TCK logs exist.
print_test_results || true

# Step 5: Collect container logs while the containers still exist
if [[ "${TCK_TESTS_FAILED}" == "true" || "${tck_exit_code}" -ne 0 ]]; then
  collect_logs "true"
else
  collect_logs "false"
fi

# Step 6: Cleanup
cleanup

# Step 7: Report overall result
# Use the TCK log-based failure flag as the primary indicator, because the
# TCK runtime may exit 0 even when tests fail.
echo ""
echo "==========================================="
if [[ "${TCK_TESTS_FAILED}" == "true" || "${tck_exit_code}" -ne 0 ]]; then
  log "DSP TCK conformance tests FAILED (exit code: ${tck_exit_code})"
  echo "==========================================="
  log "Hint: Re-run with --keep-containers to inspect the running EDC controlplane."
  exit 1
else
  log "DSP TCK conformance tests PASSED"
  echo "==========================================="
fi

exit 0
