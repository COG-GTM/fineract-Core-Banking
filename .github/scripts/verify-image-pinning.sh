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
# Fails when a deployment path resolves a floating 'latest' tag, and when the Kubernetes deployment is not pinned to
# an immutable digest.

set -euo pipefail

cd "$(dirname "$0")/../.."

status=0

echo "Checking deployment manifests for ':latest' references..."
matches=$(grep -rn --include='*.yml' --include='*.yaml' -E '^\s*(-\s*)?image:.*:latest' \
  kubernetes config/docker/compose docker-compose*.yml || true)

if [ -n "$matches" ]; then
  echo "Deployment paths must not resolve 'latest':" >&2
  echo "$matches" >&2
  status=1
fi

echo "Checking that the Kubernetes deployment is pinned to a digest..."
if ! grep -Eq '^\s+digest: sha256:[0-9a-f]{64}$' kubernetes/kustomization.yml; then
  echo "kubernetes/kustomization.yml must pin fineract-server-image to a sha256 digest." >&2
  status=1
fi

if grep -Eq '^\s+image: .*fineract.*:' kubernetes/fineract-server-deployment.yml; then
  echo "kubernetes/fineract-server-deployment.yml must reference the kustomize placeholder, not a tagged image." >&2
  status=1
fi

if [ "$status" -eq 0 ]; then
  echo "OK: no deployment path resolves 'latest', and the Kubernetes image is digest-pinned."
fi

exit "$status"
