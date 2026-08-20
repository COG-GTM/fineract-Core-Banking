#!/bin/bash

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

# WS3 cross-engine equivalence harness: local/CI entry point.
#
# Creates the harness database on both engines (dropping any previous copy),
# then runs cross_engine_equivalence.py against them. Defaults match the CI
# service containers: MariaDB 11.5.2 on 3306 (root/mysql) as source and
# PostgreSQL 18.3 on 5432 (root/postgres) as target.
#
# Usage: scripts/ws3/run-equivalence.sh [extra arguments for the harness]
#
# To point the harness at Aurora and the incumbent database for the WS9
# parallel run, skip this wrapper and call the harness directly:
#
#   TARGET_HOST=<aurora-endpoint> TARGET_DB=<tenant-db> \
#   SOURCE_HOST=<incumbent-host>  SOURCE_DB=<tenant-db> \
#   scripts/ws3/cross_engine_equivalence.py --schema live --no-seed

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

SOURCE_HOST="${SOURCE_HOST:-127.0.0.1}"
SOURCE_PORT="${SOURCE_PORT:-3306}"
SOURCE_USER="${SOURCE_USER:-root}"
SOURCE_PASSWORD="${SOURCE_PASSWORD:-mysql}"
TARGET_HOST="${TARGET_HOST:-127.0.0.1}"
TARGET_PORT="${TARGET_PORT:-5432}"
TARGET_USER="${TARGET_USER:-root}"
TARGET_PASSWORD="${TARGET_PASSWORD:-postgres}"
HARNESS_DB="${HARNESS_DB:-fineract_ws3_equivalence}"

echo "Creating ${HARNESS_DB} on MariaDB ${SOURCE_HOST}:${SOURCE_PORT} and PostgreSQL ${TARGET_HOST}:${TARGET_PORT}"

mysql --host="${SOURCE_HOST}" --port="${SOURCE_PORT}" --user="${SOURCE_USER}" --password="${SOURCE_PASSWORD}" \
    --execute="DROP DATABASE IF EXISTS ${HARNESS_DB}; CREATE DATABASE ${HARNESS_DB};"

PGPASSWORD="${TARGET_PASSWORD}" psql --host="${TARGET_HOST}" --port="${TARGET_PORT}" --username="${TARGET_USER}" \
    --dbname=postgres --no-psqlrc --quiet --set=ON_ERROR_STOP=1 \
    --command="DROP DATABASE IF EXISTS ${HARNESS_DB};" \
    --command="CREATE DATABASE ${HARNESS_DB};"

export SOURCE_HOST SOURCE_PORT SOURCE_USER SOURCE_PASSWORD TARGET_HOST TARGET_PORT TARGET_USER TARGET_PASSWORD
export SOURCE_DB="${HARNESS_DB}" TARGET_DB="${HARNESS_DB}"

exec python3 "${HERE}/cross_engine_equivalence.py" "$@"
