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
# Builds the artefact record for one Fineract container build: the immutable
# image reference (by digest), the tags it was published under, the source
# commit and the CycloneDX SBOM produced by the Gradle build.

set -euo pipefail

image=""
digest=""
tags=""
commit=""
ref=""
run_url=""
sbom=""
out="build/artefact-record.json"

while [ $# -gt 0 ]; do
    case "$1" in
        --image) image="$2"; shift 2 ;;
        --digest) digest="$2"; shift 2 ;;
        --tags) tags="$2"; shift 2 ;;
        --commit) commit="$2"; shift 2 ;;
        --ref) ref="$2"; shift 2 ;;
        --run-url) run_url="$2"; shift 2 ;;
        --sbom) sbom="$2"; shift 2 ;;
        --out) out="$2"; shift 2 ;;
        *) echo "unknown argument: $1" >&2; exit 2 ;;
    esac
done

for required in image digest commit sbom; do
    if [ -z "${!required}" ]; then
        echo "missing required argument: --${required//_/-}" >&2
        exit 2
    fi
done

if [ ! -f "$sbom" ]; then
    echo "SBOM not found at $sbom - run './gradlew cyclonedxBom' first" >&2
    exit 1
fi

mkdir -p "$(dirname "$out")"

jq -n \
    --arg image "$image" \
    --arg digest "$digest" \
    --arg commit "$commit" \
    --arg ref "$ref" \
    --arg runUrl "$run_url" \
    --arg builtAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    --arg sbomSha256 "$(sha256sum "$sbom" | cut -d' ' -f1)" \
    --argjson tags "$(printf '%s' "$tags" | jq -R 'split(",") | map(select(length > 0))')" \
    --argjson sbomComponents "$(jq '.components | length' "$sbom")" \
    '{
        schemaVersion: 1,
        image: $image,
        digest: $digest,
        tags: $tags,
        commit: $commit,
        ref: $ref,
        buildRunUrl: $runUrl,
        builtAt: $builtAt,
        sbom: {
            format: "CycloneDX",
            mediaType: "application/vnd.cyclonedx+json",
            sha256: $sbomSha256,
            componentCount: $sbomComponents
        }
    }' > "$out"

echo "wrote artefact record to $out"
