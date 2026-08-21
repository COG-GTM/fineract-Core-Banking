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
# Resolves the image for a deployment from an artefact record and substitutes it
# into a Kubernetes manifest. Deployments always resolve an image by digest, so a
# mutable tag such as 'latest' can never change what is already running.
#
# Usage: render-image-digest.sh <artefact-record.json> <manifest.yml> [output.yml]

set -euo pipefail

record="${1:?usage: render-image-digest.sh <artefact-record.json> <manifest.yml> [output.yml]}"
manifest="${2:?usage: render-image-digest.sh <artefact-record.json> <manifest.yml> [output.yml]}"
output="${3:--}"

image="$(jq -r '.image' "$record")"

if [[ "$image" != *"@sha256:"* ]]; then
    echo "artefact record does not pin an image by digest: $image" >&2
    exit 1
fi

rendered="$(sed "s|__FINERACT_IMAGE__|$image|g" "$manifest")"

if grep -q '__FINERACT_IMAGE__' <<< "$rendered"; then
    echo "image placeholder was not substituted in $manifest" >&2
    exit 1
fi

if [ "$output" = "-" ]; then
    printf '%s\n' "$rendered"
else
    printf '%s\n' "$rendered" > "$output"
fi
