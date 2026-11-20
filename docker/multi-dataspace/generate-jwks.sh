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

# nginx entrypoint hook: publishes the JWKS of the management API signing key.
# The key lives in the shared KEY_DIR volume, so the verifier can sign tokens
# with it; an existing key is reused across restarts.

set -eu

KEY_DIR="${KEY_DIR:-/keys}"
KEY_FILE="${KEY_DIR}/management-api.key"
KEY_ID="${KEY_ID:-multi-dataspace-verify}"
KEY_BITS=2048
JWKS_FILE=/usr/share/nginx/html/jwks.json

b64url() {
  openssl base64 -A | tr '+/' '-_' | tr -d '='
}

mkdir -p "${KEY_DIR}"
if [ ! -f "${KEY_FILE}" ]; then
  openssl genrsa -out "${KEY_FILE}" "${KEY_BITS}" 2>/dev/null
fi
# Test-only: world-readable so the verifier container can sign with it whatever
# user it runs as. This key is a throwaway, generated into a compose volume and
# never leaving this stack. Never grant 644 to a private key in a deployment.
chmod 644 "${KEY_FILE}"

modulus=$(openssl rsa -in "${KEY_FILE}" -noout -modulus | cut -d= -f2 | xxd -r -p | b64url)
exponent=$(printf '%06x' "$(openssl rsa -in "${KEY_FILE}" -noout -text | sed -n 's/^publicExponent: \([0-9]*\).*/\1/p')" | xxd -r -p | b64url)

printf '{"keys":[{"kty":"RSA","use":"sig","alg":"RS256","kid":"%s","n":"%s","e":"%s"}]}\n' \
  "${KEY_ID}" "${modulus}" "${exponent}" > "${JWKS_FILE}"
echo "Published JWKS for key ${KEY_ID} at ${JWKS_FILE}"
