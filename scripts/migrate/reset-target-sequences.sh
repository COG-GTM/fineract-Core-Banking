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
# Advances every PostgreSQL identity sequence in a migrated tenant database past the highest migrated id.
#
# A logical load and a DMS full load both insert explicit primary keys and leave the sequences at their start value.
# Row counts and balances reconcile perfectly and the first insert after cutover then fails on a duplicate key.
# The reconciliation harness reports this as SEQUENCE_BEHIND_MAX_ID; this script is the fix.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${1:-${SCRIPT_DIR}/migration.env}"

if [[ ! -f "${ENV_FILE}" ]]; then
    echo "error: no migration environment file at ${ENV_FILE} (copy migration.env.sample)" >&2
    exit 2
fi
# shellcheck disable=SC1090
source "${ENV_FILE}"

: "${TARGET_HOST:?}" "${TARGET_PORT:?}" "${TARGET_USER:?}" "${TARGET_TENANTS_DB:?}"
: "${TARGET_PASSWORD:?export TARGET_PASSWORD before running}"

if [[ "${TARGET_ENGINE:-}" != "postgresql" ]]; then
    echo "target engine is ${TARGET_ENGINE:-unset}; MariaDB carries AUTO_INCREMENT in the dump, nothing to do" >&2
    exit 0
fi

target_psql() {
    PGPASSWORD="${TARGET_PASSWORD}" psql -h "${TARGET_HOST}" -p "${TARGET_PORT}" -U "${TARGET_USER}" -d "$1" -At -v ON_ERROR_STOP=1 "${@:2}"
}

readarray -t DATABASES < <(target_psql "${TARGET_TENANTS_DB}" \
    -c "select c.schema_name from tenants t join tenant_server_connections c on c.id = t.oltp_id order by t.identifier")

for database in "${DATABASES[@]}"; do
    echo "resetting sequences in ${database}"
    target_psql "${database}" -c "
        do \$\$
        declare
            record_row record;
        begin
            for record_row in
                -- information_schema.sequences omits sequences owned by identity and serial columns,
                -- which is every sequence Fineract has; pg_get_serial_sequence resolves both.
                select table_name, column_name, sequence_name
                from (
                    select table_name, column_name,
                        pg_get_serial_sequence(format('%I.%I', table_schema, table_name), column_name) as sequence_name
                    from information_schema.columns
                    where table_schema = 'public'
                ) owned
                where sequence_name is not null
            loop
                execute format('select setval(%L, coalesce((select max(%I) from %I), 0) + 1, false)',
                    record_row.sequence_name, record_row.column_name, record_row.table_name);
            end loop;
        end
        \$\$;"
done

echo "sequences advanced. Re-run the reconciliation to prove no SEQUENCE_BEHIND_MAX_ID variance remains."
