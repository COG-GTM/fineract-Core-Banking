# Fineract build and delivery (WS2)

Build and supply chain for Fineract on AWS: images go to ECR, deployments resolve
images by digest, and every build produces an artefact record with its SBOM.

## Build and publish

`.github/workflows/publish-ecr.yml` runs on pushes to `develop` and `1.*` tags:

1. Assumes an AWS role via GitHub OIDC (`secrets.AWS_ECR_PUBLISH_ROLE_ARN`) and logs in to ECR.
2. Builds the multi-arch image with Jib and pushes it to `${ECR_REPOSITORY}` with:
   - the existing tag set — branch/tag name, plus short and long SHA on `develop`;
   - immutable tags `git-<long-sha>` and `build-<run-id>-<attempt>`. The ECR repository
     is created with `ImageTagMutability: IMMUTABLE`, so a tag can never be repointed.
3. Records the image digest that Jib writes to `fineract-provider/build/jib-image.digest`.
4. Runs `./gradlew cyclonedxBom` and folds the CycloneDX SBOM into the artefact record
   (`deploy/scripts/artefact-record.sh`).
5. Publishes the record and both SBOM formats to `s3://${ARTEFACT_BUCKET}/fineract/<digest>/`,
   plus a per-branch pointer at `s3://${ARTEFACT_BUCKET}/fineract/<ref>/artefact-record.json`.
6. Starts the CodePipeline deployment for `develop`.

Docker Hub is no longer in the deployment path; `publish-dockerhub.yml` has been removed.

## Deploy by digest

Nothing in the deployment path resolves a mutable tag. `kubernetes/fineract-server-deployment.yml`
carries the placeholder `__FINERACT_IMAGE__`, and `deploy/scripts/render-image-digest.sh`
substitutes the `image` field from the artefact record, refusing anything that is not
pinned with `@sha256:`.

`deploy/aws/codepipeline.yml` stands up the CD chain chosen in Q-DEL-2: an ECR repository
with immutable tags and scan-on-push, a CodeBuild deploy project running
`deploy/aws/buildspec-deploy.yml`, and a two-stage pipeline. Harness/Argo/Spinnaker can
replace the pipeline without touching the build, the artefact record, or the manifest —
they only need to read the record and apply the rendered manifest.

Deploy parameters: `ArtefactBucket`, `EksClusterName`, `ConnectionArn`, optionally
`EcrRepositoryName`, `TargetRef` and `KubectlVersion`.

## Repository configuration

Workflow variables: `AWS_REGION`, `ECR_REPOSITORY`, `ARTEFACT_BUCKET`, `CODEPIPELINE_NAME`.
Workflow secret: `AWS_ECR_PUBLISH_ROLE_ARN` (role trusted for GitHub OIDC with
`ecr:*` push permissions on the repository and write access to the artefact bucket).
