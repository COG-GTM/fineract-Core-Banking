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
# Full load of the tenant registry and every tenant database from source to target.
#
# Same-engine (MariaDB -> MariaDB) runs here with mariadb-dump. Cross-engine (MariaDB -> PostgreSQL) is a DMS
# full-load task: this script prepares the target databases and prints the tenant inventory DMS must cover, because
# the DMS task definition depends on Q-DB-1/Q-DB-2 and does not belong in a shell script.
#
# Nothing here is a substitute for the reconciliation. Run scripts/migrate/reconcile.sh afterwards.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${1:-${SCRIPT_DIR}/migration.env}"

if [[ ! -f "${ENV_FILE}" ]]; then
    echo "error: no migration environment file at ${ENV_FILE} (copy migration.env.sample)" >&2
    exit 2
fi
# shellcheck disable=SC1090
source "${ENV_FILE}"

: "${SOURCE_ENGINE:?}" "${SOURCE_HOST:?}" "${SOURCE_PORT:?}" "${SOURCE_USER:?}" "${SOURCE_TENANTS_DB:?}"
: "${TARGET_ENGINE:?}" "${TARGET_HOST:?}" "${TARGET_PORT:?}" "${TARGET_USER:?}" "${TARGET_TENANTS_DB:?}"
: "${WORK_DIR:?}"
: "${SOURCE_PASSWORD:?export SOURCE_PASSWORD before running}"
: "${TARGET_PASSWORD:?export TARGET_PASSWORD before running}"

mkdir -p "${WORK_DIR}"

source_query() {
    if [[ "${SOURCE_ENGINE}" == "postgresql" ]]; then
        PGPASSWORD="${SOURCE_PASSWORD}" psql -h "${SOURCE_HOST}" -p "${SOURCE_PORT}" -U "${SOURCE_USER}" \
            -d "${SOURCE_TENANTS_DB}" -At -c "$1"
    else
        MYSQL_PWD="${SOURCE_PASSWORD}" mariadb -h "${SOURCE_HOST}" -P "${SOURCE_PORT}" -u "${SOURCE_USER}" \
            -D "${SOURCE_TENANTS_DB}" -N -B -e "$1"
    fi
}

tenant_databases() {
    source_query "select c.schema_name from tenants t join tenant_server_connections c on c.id = t.oltp_id order by t.identifier"
}

readarray -t DATABASES < <(tenant_databases)
if [[ ${#DATABASES[@]} -eq 0 ]]; then
    echo "error: tenant registry ${SOURCE_TENANTS_DB} lists no tenants" >&2
    exit 1
fi
echo "tenant databases to migrate: ${SOURCE_TENANTS_DB} ${DATABASES[*]}"

if [[ "${SOURCE_ENGINE}" != "${TARGET_ENGINE}" ]]; then
    cat <<EOF
Cross-engine migration (${SOURCE_ENGINE} -> ${TARGET_ENGINE}) is a DMS task, not a dump/restore.
Create the target schema with Liquibase (fineract-provider runs it on first boot against an empty database),
then point one DMS task per database at the list above and run this script's reconcile step when the full load
reports complete. Sequences are NOT carried by DMS: run scripts/migrate/reset-target-sequences.sh before cutover.
EOF
    exit 0
fi

for database in "${SOURCE_TENANTS_DB}" "${DATABASES[@]}"; do
    dump="${WORK_DIR}/${database}.sql"
    echo "dumping ${database} -> ${dump}"
    MYSQL_PWD="${SOURCE_PASSWORD}" mariadb-dump \
        -h "${SOURCE_HOST}" -P "${SOURCE_PORT}" -u "${SOURCE_USER}" \
        --single-transaction --quick --routines --events --triggers \
        --databases "${database}" >"${dump}"

    echo "restoring ${database} into ${TARGET_HOST}"
    MYSQL_PWD="${TARGET_PASSWORD}" mariadb \
        -h "${TARGET_HOST}" -P "${TARGET_PORT}" -u "${TARGET_USER}" <"${dump}"
done

echo "full load complete. Run scripts/migrate/reconcile.sh before anyone calls this migration done."
