#!/usr/bin/env bash
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership. The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the
# specific language governing permissions and limitations
# under the License.
#
# Runs the reconciliation harness against the source and target described by migration.env and writes the evidence
# the DBA signs. Exits non-zero on any variance.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
ENV_FILE="${1:-${SCRIPT_DIR}/migration.env}"

if [[ ! -f "${ENV_FILE}" ]]; then
    echo "error: no migration environment file at ${ENV_FILE} (copy migration.env.sample)" >&2
    exit 2
fi
# shellcheck disable=SC1090
source "${ENV_FILE}"

: "${WORK_DIR:?}"
: "${SOURCE_PASSWORD:?export SOURCE_PASSWORD before running}"
: "${TARGET_PASSWORD:?export TARGET_PASSWORD before running}"

CONFIG="$(mktemp "${TMPDIR:-/tmp}/recon.XXXXXX.properties")"
chmod 600 "${CONFIG}"
trap 'rm -f "${CONFIG}"' EXIT

cat >"${CONFIG}" <<EOF
source.engine=${SOURCE_ENGINE}
source.host=${SOURCE_HOST}
source.port=${SOURCE_PORT}
source.username=${SOURCE_USER}
source.password=${SOURCE_PASSWORD}
source.registryDatabase=${SOURCE_TENANTS_DB}
target.engine=${TARGET_ENGINE}
target.host=${TARGET_HOST}
target.port=${TARGET_PORT}
target.username=${TARGET_USER}
target.password=${TARGET_PASSWORD}
target.registryDatabase=${TARGET_TENANTS_DB}
tenants=${RECON_TENANTS:-}
output.directory=${WORK_DIR}/recon
EOF

"${REPO_ROOT}/gradlew" --quiet --console=plain :fineract-migration-recon:reconcile "-Precon.config=${CONFIG}"
