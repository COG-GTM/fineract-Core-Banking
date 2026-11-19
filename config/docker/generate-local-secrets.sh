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

# Generates random credentials for the local docker compose stacks into the repository root .env file
# (gitignored), which docker compose reads to interpolate ${LOCAL_*} references in config/docker/env/*.env.
# Existing values are kept, so the script is safe to run repeatedly.
set -euo pipefail

ENV_FILE="${1:-$(cd "$(dirname "$0")/../.." && pwd)/.env}"
touch "$ENV_FILE"
chmod 600 "$ENV_FILE"

for name in LOCAL_DB_PASSWORD LOCAL_DB_ROOT_PASSWORD LOCAL_TENANT_MASTER_PASSWORD; do
  if ! grep -q "^${name}=" "$ENV_FILE"; then
    echo "${name}=$(openssl rand -hex 24)" >> "$ENV_FILE"
    echo "Generated ${name} in ${ENV_FILE}"
  fi
done
