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

# Runs terraform plan for an environment root against a mocked provider, with
# no AWS account and no credentials. It proves the resource graph resolves and
# that every reference between the four modules is satisfiable; it does not and
# cannot prove that AWS would accept the API calls.
#
# Usage: ./tests/offline-plan.sh dev|prod

set -euo pipefail

ENV_NAME="${1:-dev}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_DIR="${SCRIPT_DIR}/../envs/${ENV_NAME}"
OVERRIDE="${ENV_DIR}/zz_offline_override.tf"

if [ ! -d "$ENV_DIR" ]; then
  echo "ERROR: no environment root at ${ENV_DIR}" >&2
  exit 1
fi

cleanup() {
  rm -f "$OVERRIDE"
}
trap cleanup EXIT

# An override file merges into the provider block of the root it sits next to,
# so the committed configuration stays free of test-only settings.
cat > "$OVERRIDE" <<'EOF'
terraform {
  backend "local" {}
}

provider "aws" {
  access_key                  = "mock-access-key"
  secret_key                  = "mock-secret-key"
  skip_credentials_validation = true
  skip_metadata_api_check     = true
  skip_region_validation      = true
  skip_requesting_account_id  = true
}
EOF

cd "$ENV_DIR"
terraform init -input=false -no-color -reconfigure
terraform plan \
  -input=false \
  -no-color \
  -refresh=false \
  -var 'account_id=111122223333' \
  "${@:2}"
