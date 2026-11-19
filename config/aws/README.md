# AWS environment profile

ECS/Fargate configuration for the target architecture in [docs/aws-migration/README.md](../../docs/aws-migration/README.md)
(work items A1/A2). It mirrors `config/docker/env/*`, but holds **no credentials**: every secret is an ECS `secrets`
reference to AWS Secrets Manager, and AWS access uses the task role (default credential chain), never static keys.

| Path | Purpose |
| --- | --- |
| `env/*.env` | Plain, non-secret settings per concern (common, Aurora, MSK, one file per service mode). |
| `secrets.env` | `NAME=valueFrom` pairs rendered into the task definition `secrets` block. |
| `ecs/services.json` | One entry per ECS service: size, port, declared modes and the env files it combines (later files win). |
| `ecs/task-definitions/*.json` | Generated templates; do not edit by hand. |

Regenerate the templates after changing any of the above and run the contract checks (CI runs both):

```bash
python3 scripts/aws/render_task_definitions.py
python3 scripts/aws/render_task_definitions.py --check
python3 scripts/aws/verify_aws_profile.py
```

`verify_aws_profile.py` fails on: `FINERACT_*` names that no `application*.properties` reads, credentials that
`application.properties` defaults to a literal but that are not sourced from Secrets Manager, literal credential
values in `environment`, `secrets` entries that are not `${SECRET_ARN_*}` references, JVM debug agents (JDWP),
service mode flags that do not match `services.json`, HTTPS with the bundled keystore, wildcard CORS origins, PostgreSQL connections without `sslmode=verify-full`,
and placeholders missing from the table below.

## Deploy-time placeholders

The deploy pipeline (Terraform outputs, A5) substitutes these, e.g. with `envsubst`, before `aws ecs register-task-definition`.

| Placeholder | Value |
| --- | --- |
| `ENVIRONMENT` | Environment name, e.g. `staging`, `prod`. |
| `AWS_REGION` | Region of the stack. |
| `IMAGE_URI` | ECR image URI pinned by digest. |
| `SERVER_SSL_KEY_STORE` | Location of the deployment TLS keystore (e.g. `file:/opt/fineract/keystore.p12` baked into the image by the deploy pipeline); its password is `SECRET_ARN_KEYSTORE`. Not the bundled `classpath:keystore.jks`. |
| `CORS_ALLOWED_ORIGIN_PATTERNS` | Comma-separated browser origins allowed to call the API (e.g. the web app URL); never `*`. |
| `CPU_ARCHITECTURE` | `ARM64` or `X86_64`, matching the image. |
| `ECS_EXECUTION_ROLE_ARN` | Execution role; needs `secretsmanager:GetSecretValue` on the secrets below and `kms:Decrypt` on their key. |
| `TASK_ROLE_ARN_FINERACT_READ` | Task role for `fineract-read`. |
| `TASK_ROLE_ARN_FINERACT_WRITE` | Task role for `fineract-write` (MSK write). |
| `TASK_ROLE_ARN_FINERACT_BATCH_MANAGER` | Task role for `fineract-batch-manager` (MSK read/write). |
| `TASK_ROLE_ARN_FINERACT_BATCH_WORKER` | Task role for `fineract-batch-worker` (MSK read/write). |
| `TASK_ROLE_ARN_FINERACT_LIQUIBASE` | Task role for the one-off `fineract-liquibase` migration task. |
| `AURORA_WRITER_ENDPOINT` | Aurora PostgreSQL cluster writer endpoint. |
| `AURORA_READER_ENDPOINT` | Aurora PostgreSQL cluster reader endpoint. |
| `RDS_CA_BUNDLE_PATH` | Path of the [RDS CA bundle](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/UsingWithRDS.SSL.html) in the image (e.g. `/opt/fineract/rds-global-bundle.pem`), used with `sslmode=verify-full`. |
| `MSK_BOOTSTRAP_BROKERS` | MSK IAM bootstrap brokers (port 9098). |
| `SECRET_ARN_TENANT_STORE_DB` | JSON secret `{username, password}` for the tenant store (`fineract_tenants`). |
| `SECRET_ARN_DEFAULT_TENANT_DB` | JSON secret `{username, password}` for the default tenant database. |
| `SECRET_ARN_DEFAULT_TENANT_DB_RO` | JSON secret `{username, password}` for the read-only tenant user. |
| `SECRET_ARN_TENANT_MASTER_PASSWORD` | Plain-string secret used to encrypt tenant connection passwords. |
| `SECRET_ARN_KEYSTORE` | JSON secret with the TLS keystore `password`. |

## Local development

The docker compose files keep using `config/docker/env/*`. They no longer contain passwords; run
`config/docker/generate-local-secrets.sh` once to write random local credentials to the gitignored `.env`
at the repository root (a local `gitleaks dir .` scan reports these generated values, as it should; the file is
never committed). Existing local database volumes created with the old shared passwords need `docker compose down -v`.
The JDWP debug agent is only enabled by `config/docker/env/debug.env`
(used by `docker-compose-development.yml`).
