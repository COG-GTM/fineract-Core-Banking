#!/bin/bash
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
# Pins the Kubernetes deployment to an ECR repository and an immutable digest.
#
# Usage: ./set-image-digest.sh <ecr-repository> <sha256:...>

set -euo pipefail

REPOSITORY="${1:-}"
DIGEST="${2:-}"
KUSTOMIZATION="$(dirname "$0")/kustomization.yml"

if [ -z "$REPOSITORY" ] || [ -z "$DIGEST" ]; then
  echo "Usage: $0 <ecr-repository> <sha256:...>" >&2
  exit 1
fi

if ! echo "$DIGEST" | grep -Eq '^sha256:[0-9a-f]{64}$'; then
  echo "Refusing to deploy '$DIGEST': deployments must resolve an immutable sha256 digest, not a tag." >&2
  exit 1
fi

python3 - "$KUSTOMIZATION" "$REPOSITORY" "$DIGEST" <<'PY'
import re
import sys

path, repository, digest = sys.argv[1:4]
with open(path) as manifest:
    content = manifest.read()

content = re.sub(r'(\n    newName: ).*', r'\g<1>' + repository, content, count=1)
content = re.sub(r'(\n    digest: ).*', r'\g<1>' + digest, content, count=1)

with open(path, 'w') as manifest:
    manifest.write(content)
PY

echo "Pinned fineract-server-image to ${REPOSITORY}@${DIGEST}"
