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
# Rehearses the cutover: stop writes, let replication drain, reconcile, advance sequences, and time every phase.
#
# The output is the number the business needs for Q-DB-4: how long the freeze window actually is on a
# production-sized copy. Run it against the rehearsal environment, never against production.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${1:-${SCRIPT_DIR}/migration.env}"

if [[ ! -f "${ENV_FILE}" ]]; then
    echo "error: no migration environment file at ${ENV_FILE} (copy migration.env.sample)" >&2
    exit 2
fi
# shellcheck disable=SC1090
source "${ENV_FILE}"

: "${WORK_DIR:?}"
: "${TARGET_PASSWORD:?export TARGET_PASSWORD before running}"
CATCHUP_TIMEOUT_SECONDS="${CATCHUP_TIMEOUT_SECONDS:-1800}"
CATCHUP_LAG_THRESHOLD_SECONDS="${CATCHUP_LAG_THRESHOLD_SECONDS:-0}"
TIMINGS="${WORK_DIR}/freeze-window-timings.txt"

mkdir -p "${WORK_DIR}"
: >"${TIMINGS}"

phase() {
    local name="$1"
    shift
    local started
    started="$(date +%s)"
    echo "== ${name}"
    "$@"
    local elapsed=$(($(date +%s) - started))
    printf '%-32s %6ds\n' "${name}" "${elapsed}" | tee -a "${TIMINGS}"
}

stop_writes() {
    # The application is the only writer. Scale the deployment to zero and confirm nothing holds a write connection;
    # a read-only flag on the database alone does not stop in-flight batch jobs (COB).
    echo "Stop the Fineract deployment and its scheduler now, then press enter."
    read -r
}

wait_for_catchup() {
    local deadline=$(($(date +%s) + CATCHUP_TIMEOUT_SECONDS))
    while true; do
        local lag
        if [[ "${TARGET_ENGINE}" == "postgresql" ]]; then
            lag="$(PGPASSWORD="${TARGET_PASSWORD}" psql -h "${TARGET_HOST}" -p "${TARGET_PORT}" -U "${TARGET_USER}" \
                -d "${TARGET_TENANTS_DB}" -At -c \
                "select coalesce(extract(epoch from now() - pg_last_xact_replay_timestamp())::bigint, 0)")"
        else
            lag="$(MYSQL_PWD="${TARGET_PASSWORD}" mariadb -h "${TARGET_HOST}" -P "${TARGET_PORT}" -u "${TARGET_USER}" \
                -N -B -e "show replica status\G" | awk -F': *' '/Seconds_Behind_Master/ {print $2}')"
            lag="${lag:-0}"
        fi
        echo "replication lag: ${lag}s"
        if [[ "${lag}" -le "${CATCHUP_LAG_THRESHOLD_SECONDS}" ]]; then
            return 0
        fi
        if [[ "$(date +%s)" -ge "${deadline}" ]]; then
            echo "error: replication did not catch up within ${CATCHUP_TIMEOUT_SECONDS}s" >&2
            return 1
        fi
        sleep 10
    done
}

reconcile() {
    "${SCRIPT_DIR}/reconcile.sh" "${ENV_FILE}"
}

advance_sequences() {
    "${SCRIPT_DIR}/reset-target-sequences.sh" "${ENV_FILE}"
}

phase "stop-writes" stop_writes
phase "replication-catch-up" wait_for_catchup
phase "advance-sequences" advance_sequences
phase "reconciliation" reconcile

total="$(awk '{ sum += $2 + 0 } END { print sum }' "${TIMINGS}")"
printf '%-32s %6ds\n' "TOTAL FREEZE WINDOW" "${total}" | tee -a "${TIMINGS}"
echo "timings: ${TIMINGS}"
echo "reconciliation evidence: ${WORK_DIR}/recon/reconciliation-report.txt"
