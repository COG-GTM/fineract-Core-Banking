# Infrastructure as code

Terraform for the AWS migration (WS1 — landing zone and network). Nothing here
touches the application build.

```
infrastructure/terraform
├── bootstrap/          # one-off: state bucket (versioned, KMS) + DynamoDB lock table
├── envs/dev/           # dev root module; S3 backend configured by backend.hcl
├── modules/network/    # VPC, private/public subnets across three AZs, flow logs
└── tests/fixture/      # provider-free module the pipeline dry-runs against
```

## Pipeline

`.github/workflows/iac-pipeline.yml` runs on any change under `infrastructure/`:

| Job | What it does |
|---|---|
| `fmt` | `terraform fmt -check -recursive` |
| `validate` | `terraform validate` per root/module |
| `dry-run` | `terraform test` against `tests/fixture` |
| `policy-scan` | tfsec + Checkov, SARIF uploaded to code scanning, fails the PR on any finding |
| `plan` | `terraform plan` for `envs/dev`, posted (and updated in place) as a PR comment |
| `apply` | `develop` only, gated on the `dev-apply` GitHub environment reviewer |

State locking is enforced by the DynamoDB table in `backend.hcl`; plan and apply
both run with `-lock-timeout=5m`.

## Repository configuration

Configure these as repository **variables** (empty values skip `plan`/`apply`):

- `AWS_IAC_PLAN_ROLE_ARN` — read-only OIDC role assumed by `plan`.
- `AWS_IAC_APPLY_ROLE_ARN` — OIDC role assumed by `apply`.
- `AWS_REGION`, `LOGS_KMS_KEY_ARN`.

An environment named `dev-apply` with at least one required reviewer must exist;
without it the apply job is not gated on human approval.

## Local use

```bash
terraform -chdir=infrastructure/terraform/envs/dev init -backend-config=backend.hcl
terraform -chdir=infrastructure/terraform/envs/dev plan
```

Suppressed policy findings carry an inline `checkov:skip` / `tfsec:ignore`
comment with the justification.
