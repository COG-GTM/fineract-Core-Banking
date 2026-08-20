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

# Writes the release artefact record: the single JSON document that identifies a
# Fineract container image by digest and links it to its SBOM and vulnerability
# scan. Downstream deployment automation resolves the image from this record and
# must never resolve it from a tag. See docs/migration/ws2-supply-chain.md.
#
# Usage:
#   ./scripts/release-artifact-record.sh --output <file> --commit <sha> [options]

set -euo pipefail

OUTPUT=""
COMMIT=""
COMMIT_TIME=""
REF_NAME=""
REGISTRY=""
REPOSITORY=""
DIGEST=""
TAGS=""
IMMUTABLE_TAG=""
SBOM=""
SBOM_SHA256=""
SCAN_REPORT=""
BUILD_URL=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --output) OUTPUT="$2"; shift 2 ;;
    --commit) COMMIT="$2"; shift 2 ;;
    --commit-time) COMMIT_TIME="$2"; shift 2 ;;
    --ref-name) REF_NAME="$2"; shift 2 ;;
    --registry) REGISTRY="$2"; shift 2 ;;
    --repository) REPOSITORY="$2"; shift 2 ;;
    --digest) DIGEST="$2"; shift 2 ;;
    --tags) TAGS="$2"; shift 2 ;;
    --immutable-tag) IMMUTABLE_TAG="$2"; shift 2 ;;
    --sbom) SBOM="$2"; shift 2 ;;
    --sbom-sha256) SBOM_SHA256="$2"; shift 2 ;;
    --scan-report) SCAN_REPORT="$2"; shift 2 ;;
    --build-url) BUILD_URL="$2"; shift 2 ;;
    *) echo "ERROR: unknown argument '$1'" >&2; exit 1 ;;
  esac
done

if [[ -z "$OUTPUT" || -z "$COMMIT" ]]; then
  echo "ERROR: --output and --commit are required." >&2
  exit 1
fi

mkdir -p "$(dirname "$OUTPUT")"

OUTPUT="$OUTPUT" COMMIT="$COMMIT" COMMIT_TIME="$COMMIT_TIME" REF_NAME="$REF_NAME" \
REGISTRY="$REGISTRY" REPOSITORY="$REPOSITORY" DIGEST="$DIGEST" TAGS="$TAGS" \
IMMUTABLE_TAG="$IMMUTABLE_TAG" SBOM="$SBOM" SBOM_SHA256="$SBOM_SHA256" \
SCAN_REPORT="$SCAN_REPORT" BUILD_URL="$BUILD_URL" python3 - <<'PY'
import json
import os

def env(name):
    value = os.environ.get(name, "")
    return value if value else None

digest = env("DIGEST")
registry = env("REGISTRY")
repository = env("REPOSITORY")
image_ref = f"{registry}/{repository}@{digest}" if digest and registry and repository else None

scan_report = env("SCAN_REPORT")
scan = {"scanner": "trivy", "report": scan_report, "severity_counts": {}}
if scan_report and os.path.exists(scan_report):
    with open(scan_report) as fh:
        results = json.load(fh).get("Results") or []
    counts = {}
    for result in results:
        for vulnerability in result.get("Vulnerabilities") or []:
            severity = vulnerability["Severity"]
            counts[severity] = counts.get(severity, 0) + 1
    scan["severity_counts"] = counts
else:
    scan["report"] = None

record = {
    "schema": "fineract.release-artifact-record/v1",
    "source": {
        "commit": env("COMMIT"),
        "commit_time": env("COMMIT_TIME"),
        "ref_name": env("REF_NAME"),
        "build_url": env("BUILD_URL"),
    },
    "image": {
        # Deployments MUST consume image.ref (digest). Tags are informational only.
        "published": bool(image_ref),
        "registry": registry,
        "repository": repository,
        "digest": digest,
        "ref": image_ref,
        "tags": [tag for tag in (os.environ.get("TAGS") or "").split(",") if tag],
        "immutable_tag": env("IMMUTABLE_TAG"),
    },
    "sbom": {
        "format": "CycloneDX",
        "path": env("SBOM"),
        "sha256": env("SBOM_SHA256"),
    },
    "vulnerability_scan": scan,
}

with open(os.environ["OUTPUT"], "w") as fh:
    json.dump(record, fh, indent=2, sort_keys=True)
    fh.write("\n")
PY

echo "Wrote release artefact record to ${OUTPUT}"
