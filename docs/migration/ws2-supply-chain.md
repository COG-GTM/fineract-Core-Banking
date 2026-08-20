<!--
    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements. See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership. The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License. You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied. See the License for the
    specific language governing permissions and limitations
    under the License.
-->

# WS2 — Build and supply chain (ECR, immutable digests, SBOM)

This document describes the build and supply-chain contract introduced by
[`.github/workflows/publish-ecr.yml`](../../.github/workflows/publish-ecr.yml).

It closes current-state contradiction #2: the pipeline used to end at a Docker Hub
push (`.github/workflows/publish-dockerhub.yml:39-48`) while both deployment paths
resolved a floating `latest` tag (`kubernetes/fineract-server-deployment.yml:63`,
`config/docker/compose/fineract.yml:21`), so no deployed artefact could be traced
back to a commit.

## 1. What the pipeline now produces

For every push to `develop` and every `1.*` tag, one workflow run produces:

| Artefact | Where it lives |
| --- | --- |
| Multi-arch image (`linux/amd64`, `linux/arm64`) in ECR | `${AWS_ECR_REGISTRY}/${AWS_ECR_REPOSITORY}` |
| Image digest | job output `image_digest`, and `image.digest` in the release artefact record |
| CycloneDX SBOM | `fineract-sbom.cdx.json` in the `fineract-release-artifact-record` workflow artifact |
| Trivy image scan (HIGH/CRITICAL, fixed only) | `trivy-image-report.json` in the same artifact, summarised in the job summary |
| Release artefact record | `release-artifact-record.json` in the same artifact |

The image is still built by the existing Jib configuration
(`fineract-provider/build.gradle:264-296`); nothing about the image contents changes.

The Docker Hub workflow (`.github/workflows/publish-dockerhub.yml`) is deliberately
left in place — upstream publishing to `apache/fineract` continues unchanged. ECR is
the registry that *deployments* resolve from; Docker Hub is now a publishing side
effect with no deployment consumer in this repository.

## 2. Repository variables and the OIDC trust policy

No long-lived AWS keys exist in this repository or in GitHub secrets. The workflow
assumes an IAM role using the GitHub OIDC provider
(`aws-actions/configure-aws-credentials`, job permission `id-token: write`).

Configuration is supplied as **repository variables**, not secrets:

| Variable | Example | Meaning |
| --- | --- | --- |
| `AWS_REGION` | `eu-central-1` | Region of the ECR registry |
| `AWS_ECR_REGISTRY` | `123456789012.dkr.ecr.eu-central-1.amazonaws.com` | Registry host; **when unset, all AWS steps are skipped** and the workflow still builds, SBOMs and scans |
| `AWS_ECR_REPOSITORY` | `fineract` | Repository name inside the registry |
| `AWS_ECR_PUBLISH_ROLE_ARN` | `arn:aws:iam::123456789012:role/fineract-ecr-publish` | Role assumed via OIDC |

WS1 owns the Terraform that creates the OIDC provider, the role and the repository.
The role's trust policy must be scoped to this repository and to the refs that
publish — a wildcard on `repo:COG-GTM/fineract-Core-Banking:*` would let any branch
in the repository push a production image:

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": { "Federated": "arn:aws:iam::<account>:oidc-provider/token.actions.githubusercontent.com" },
    "Action": "sts:AssumeRoleWithWebIdentity",
    "Condition": {
      "StringEquals": { "token.actions.githubusercontent.com:aud": "sts.amazonaws.com" },
      "StringLike": {
        "token.actions.githubusercontent.com:sub": [
          "repo:COG-GTM/fineract-Core-Banking:ref:refs/heads/develop",
          "repo:COG-GTM/fineract-Core-Banking:ref:refs/tags/1.*"
        ]
      }
    }
  }]
}
```

The permission policy needs only ECR push on the one repository plus
`ecr:GetAuthorizationToken` (which cannot be resource-scoped):

`ecr:GetAuthorizationToken` (`*`), and on `arn:aws:ecr:<region>:<account>:repository/fineract`:
`ecr:BatchCheckLayerAvailability`, `ecr:CompleteLayerUpload`, `ecr:InitiateLayerUpload`,
`ecr:PutImage`, `ecr:UploadLayerPart`, `ecr:BatchGetImage`, `ecr:DescribeImages`.

## 3. ECR repository and lifecycle policy expected from WS1

WS1's Terraform must create the repository with:

- **Tag immutability**: `IMMUTABLE_WITH_EXCLUSION`, with an exclusion filter for the
  mutable branch tag (`develop`). Every other tag this workflow pushes —
  `sha-<long-hash>`, the short hash, the long hash and release tags `1.*` — is then
  physically un-overwritable. A plain `IMMUTABLE` setting **breaks the workflow**: the
  second push to `develop` would fail on re-tagging `develop`.
- **Enhanced scanning** (Inspector) enabled at registry level with a scan-on-push
  rule for this repository. The Trivy step in CI is the pre-push gate and is what
  makes the workflow verifiable without an AWS account; ECR enhanced scanning is the
  continuous post-push control that keeps finding new CVEs in an image that was
  already published.
- **Encryption**: KMS with a customer-managed key (same key family as the S3 document
  store in WS5).
- **Lifecycle policy**: expire untagged images after 1 day; keep the last 30 images
  tagged `sha-`; keep release-tagged (`1.*`) images indefinitely. Note that a
  lifecycle rule can delete an image a running task still references, so the retention
  of `sha-` images must exceed the longest expected rollback window (Q-DEL-6).
- **Cross-account/region**: if prod is a separate account (WS1) or has a warm standby
  in a second region, add an ECR replication configuration rather than a second build;
  a rebuild would produce a different digest and break the contract in §5.

## 4. Tagging scheme

| Tag | Mutable? | Purpose |
| --- | --- | --- |
| `develop` | yes (needs the immutability exclusion filter) | "what is on the branch head" — humans only |
| `<short-hash>` | no | convenience reference, kept from `publish-dockerhub.yml:39-48` |
| `<long-hash>` | no | kept from `publish-dockerhub.yml:39-48` |
| `sha-<long-hash>` | no | the immutable tag; one tag per commit, never re-pushed |
| `1.*` (release tags) | no | release identity, kept from `publish-dockerhub.yml:33` |

Tags exist for humans. **No deployment resolves a tag**, including the immutable ones:
a tag is a pointer, and the only reference that is the artefact is the digest.

## 5. The digest hand-off contract (consumed by WS4)

The workflow emits the digest three ways:

1. **Job output** — `needs.publish.outputs.image_digest` and `image_ref`
   (`<registry>/<repository>@sha256:...`), for a downstream job or a `workflow_call`
   from the CD pipeline chosen in Q-DEL-2.
2. **Workflow artifact** — `fineract-release-artifact-record/release-artifact-record.json`.
3. **Job summary** — the published reference, for humans.

The record is written by
[`scripts/release-artifact-record.sh`](../../scripts/release-artifact-record.sh) and is
schema-versioned (`fineract.release-artifact-record/v1`):

```json
{
  "schema": "fineract.release-artifact-record/v1",
  "image": {
    "published": true,
    "registry": "123456789012.dkr.ecr.eu-central-1.amazonaws.com",
    "repository": "fineract",
    "digest": "sha256:...",
    "ref": "123456789012.dkr.ecr.eu-central-1.amazonaws.com/fineract@sha256:...",
    "tags": ["develop", "8c187f9d1", "8c187f9d1...", "sha-8c187f9d1..."],
    "immutable_tag": "sha-8c187f9d1..."
  },
  "sbom": { "format": "CycloneDX", "path": "...", "sha256": "..." },
  "source": { "commit": "...", "commit_time": "...", "ref_name": "develop", "build_url": "..." },
  "vulnerability_scan": { "scanner": "trivy", "report": "...", "severity_counts": { "HIGH": 0 } }
}
```

Rules for consumers (WS4's ECS task definition, and any CD tool):

- Read `image.ref`. Never construct a reference from `image.tags`.
- Treat `image.published == false` as "this run published nothing" (a pull request
  run, or a run in a repository with no `AWS_ECR_REGISTRY` variable) and do not deploy
  from it.
- A rollback is a redeploy of an older record's `image.ref`; it never re-runs a build.
- The record is the join key between a running ECS task and its SBOM: the digest in a
  task definition must match `image.digest` in exactly one archived record.

`kubernetes/fineract-server-deployment.yml:63` now carries the placeholder
`__FINERACT_IMAGE_REF__` instead of `apache/fineract:latest`, and
`config/docker/compose/fineract.yml:21` reads `${FINERACT_IMAGE:-fineract:latest}`,
defaulting to the locally built image so the e2e stack
(`.github/workflows/build-e2e-tests.yml`) is unaffected. Neither path can now silently
resolve a floating public tag: the Kubernetes manifest fails closed if the substitution
is not performed.

## 5a. SBOM scope

The workflow runs `:fineract-provider:cyclonedxDirectBom`, not the root aggregate
`cyclonedxBom`, for two reasons: the image ships exactly one module's runtime
classpath (`fineract-provider`, 529 components), and the root aggregate task currently
fails on this commit with

```
Execution failed for task ':cyclonedxBom'.
> Class com.fasterxml.jackson.core.JsonParser$Feature does not have member field
  'com.fasterxml.jackson.core.JsonParser$Feature CLEAR_CURRENT_TOKEN_ON_CLOSE'
```

i.e. a Jackson version conflict between the CycloneDX plugin (`build.gradle:128`) and
the build classpath, pre-existing and independent of WS2. Repairing the aggregate task
means moving shared plugin versions and belongs with whoever owns the build classpath.

## 6. Reproducibility

The workflow pins:

- every action by commit SHA (matching the convention already used across
  `.github/workflows/`);
- the image creation time and file modification times to the **commit** timestamp
  (`-Djib.container.creationTime`, `-Djib.container.filesModificationTime`), overriding
  `creationTime = 'USE_CURRENT_TIMESTAMP'` at `fineract-provider/build.gradle:265`
  without changing the default for local developer builds.

Known obstacles to a byte-identical rebuild of the same commit, in order of impact:

1. **`git.properties`** — the `com.gorylenko.gradle-git-properties` plugin
   (`build.gradle:116`) writes `git.build.host`, `git.build.user.*`, `git.branch` and
   `git.dirty` into the packaged `git.properties`, so the classes layer differs between
   two machines building the same commit (it does not emit `git.build.time`, so a
   rebuild on the same runner for the same commit is stable). Restricting the property
   set touches shared build configuration and is deliberately out of scope for WS2.
2. **Base image by tag** — `from.image = 'azul/zulu-openjdk-alpine:21'`
   (`fineract-provider/build.gradle:266`) is a moving tag, so a rebuild months later
   picks up a different JRE layer. Pinning it to a digest is a one-line change that
   should be made when WS1 decides whether the base image is mirrored into ECR (it
   also removes a Docker Hub pull from the build path).
3. **Dependency resolution** — the build has no dependency lock files, so a
   non-reproducible transitive resolution is possible in principle.

None of these affect the *correctness* of the digest contract: the digest is recorded
from the push that actually happened, so a deployed artefact is always traceable to a
build even where that build is not bit-for-bit repeatable.

## 7. Verifying the workflow without AWS

With `AWS_ECR_REGISTRY` unset, every AWS step is skipped and the remainder of the
workflow runs end to end — SBOM generation, the local `jibDockerBuild`, the Trivy
scan, and a record with `image.published == false`. The `pull_request` trigger on this
workflow's own path exists for exactly that reason: a change to the publish path is
exercised on the pull request that makes it.

## 8. Open questions this workstream is still carrying

| ID | Question | Effect if the assumption is wrong |
| --- | --- | --- |
| Q-DEL-1 | How is the application deployed today? | The digest hand-off in §5 assumes a consumer that can read a workflow artifact or job output. A pull-based deployer (Argo) would need the record published to a git repository or an S3 bucket instead. |
| Q-DEL-2 | Which CD tool? | `target-state.md` assumes CodePipeline. This workflow deliberately stops at "publish + record" and does not stand up a CD tool, so the choice stays open; only the consumer of §5 changes. |
| Q-DEL-3 | Which image is authoritative in production — upstream `apache/fineract:latest` or an internally built one? | This is WS2's formal entry criterion and it is **not answered**. The workflow assumes production should run an internally built image. If production genuinely runs upstream `latest`, the first ECR image will not be byte-equivalent to what runs today and WS4's cutover needs an explicit content comparison. |
| Q-DEL-5 | Who owns `DOCKERHUB_USER` / `DOCKERHUB_TOKEN`? | The Docker Hub workflow is untouched, so those secrets are still required. They can be retired only once someone confirms no consumer pulls `apache/fineract` from this fork's publications. |

Assumed in the absence of WS0 answers: ECR in the same account and region as the
Fargate workload (Q-PRG-2 posture), Terraform as the IaC tool (Q-PLT-3), and
CodePipeline as the CD tool (Q-DEL-2) — none of which this workflow hard-codes.
