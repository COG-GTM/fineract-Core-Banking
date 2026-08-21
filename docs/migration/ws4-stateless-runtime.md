# WS4 — Stateless runtime on ECS Fargate

Tracking issue: [COG-932](https://linear.app/cog-gtm/issue/COG-932/ws4-stateless-runtime-on-ecs-fargate)

One task definition, one `fineract-api` service, on Fargate behind the WS1 ALB. The image is
already rootless and container-aware, so nothing in the application needed to change; all of
the work is runtime shape, identity and rollout mechanics.

## Contents

| Path | What it is |
| --- | --- |
| `infrastructure/terraform/modules/runtime` | Cluster, task definitions, service, IAM roles, log groups, autoscaling. |
| `infrastructure/terraform/examples/dev-runtime` | Dev wiring, showing which WS1 outputs feed which module inputs. |
| `.github/workflows/deploy-ecs-dev.yml` | Gated migration, then a digest-pinned service roll. |
| `scripts/ecs-run-liquibase.sh` | One-off Liquibase ECS task with exit-code propagation. |

## Inputs from other workstreams

| Input | Source |
| --- | --- |
| `private_app_subnet_ids`, `tasks_security_group_id`, `kms_key_arn` | WS1 network / security modules |
| `target_group_arn`, `target_group_arn_suffix`, `alb_arn_suffix` | WS1 edge module |
| `image_uri` (digest-pinned) | WS2 ECR publish pipeline |
| `tenants_jdbc_url`, DB secrets | WS3 Aurora PostgreSQL |

## Health checking

The ALB target group health-checks `/fineract-provider/actuator/health/readiness` over HTTPS
on 8443 — the same endpoints the Kubernetes manifest already uses
(`kubernetes/fineract-server-deployment.yml`). The compose health check does *not* use them
(contradiction #3 in the discovery register); the readiness endpoint is the correct signal
because it flips only once the tenant context is loaded, whereas a socket or `/health` probe
reports ready while the service would still 500.

`health_check_grace_period_seconds` defaults to 180 to cover JVM start plus tenant context
load; `stop_timeout_seconds` gives in-flight requests 60 seconds to drain after SIGTERM.
Combined with `deployment_minimum_healthy_percent = 100`, `deployment_maximum_percent = 200`
and target group deregistration delay, task replacement drops no requests.

## Sizing (Q-PLT-7 open)

`README.md` documents a 16 GB / 8 vCPU minimum; the Kubernetes manifest caps a pod at
2 GiB / 1 vCPU. Those contradict, and the manifest value is a laptop-scale number, not a
production starting point. The module therefore defaults to the documented minimum
(`task_cpu = 8192`, `task_memory = 16384`) and dev overrides down to 2 vCPU / 4 GB for cost.
Q-PLT-7 replaces both with load-test numbers before prod; until then the documented minimum
is the safe default.

The JVM is sized by `-XX:MaxRAMPercentage` (default 75) against the task memory rather than a
fixed `-Xmx`, so a sizing change is a single Terraform variable.

## Identity

Two roles, no long-lived keys anywhere:

* **Execution role** — pulls the image, writes to the two log groups, resolves exactly the
  secret ARNs listed in `secret_arns`, and decrypts with the platform CMK.
* **Task role** — the application identity. It gets S3 access only when
  `content_bucket_arn` is set, and ECS Exec permissions only when `enable_execute_command`
  is turned on (off by default). When neither applies, no inline policy is attached at all.

Database credentials arrive through the container `secrets` block from Secrets Manager, so
they never appear in the task definition, in `environment_variables`, or in Terraform state
as plaintext values.

## Autoscaling

Two target-tracking policies on the same scalable target: `ALBRequestCountPerTarget`
(default 600) and `ECSServiceAverageCPUUtilization` (default 60%). ECS applies the larger of
the two desired counts. Scale-in cooldown is deliberately long (300s) because task startup is
expensive. `max_capacity` is bounded by the Aurora connection budget:
`max_capacity × FINERACT_HIKARI_MAXIMUM_POOL_SIZE` connections.

`desired_count` is in `ignore_changes` — autoscaling owns it after the first apply — and is
validated to be at least 2.

## Liquibase runs as a gated one-off task

The service task definition sets `FINERACT_LIQUIBASE_ENABLED=false`. Schema changes run
through a separate `fineract-<env>-migrate` task definition built from the *same image
digest*, so the changelog applied is exactly the one shipped in the image being deployed.

The deploy workflow runs the migration job first, in a protected `dev-database` GitHub
environment, and only rolls the service if the task exits 0. Running Liquibase on service
start instead would serialise every task launch behind a single-threaded tenant upgrade whose
duration grows with tenant count — including during a scale-out under load.

## Exit criteria

* Dev serving authenticated API traffic through the ALB — apply
  `infrastructure/terraform/examples/dev-runtime` with the WS1 outputs, then
  `POST /fineract-provider/api/v1/authentication` through the ALB DNS name.
* Tasks replaceable with no request loss — force a new deployment while a load generator
  runs; the circuit breaker rolls back automatically if the new tasks never report ready.
