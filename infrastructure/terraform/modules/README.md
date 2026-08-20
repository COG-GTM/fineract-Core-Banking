# Fineract landing zone modules (WS1)

Four reusable modules compose the landing zone. The per-environment roots under
`infrastructure/terraform/envs/<env>` only supply variables; all resource logic lives here.

```
envs/dev, envs/prod
  └── network  → VPC, subnets, NAT, endpoints
  └── security → KMS CMKs, security groups, flow logs, CloudTrail/Config stubs
  └── dns      → Route 53 hosted zone, ACM certificate
  └── edge     → ALB, HTTPS listener, target group, WAF web ACL, alias record
```

Nothing in these modules creates compute. The ECS cluster, task definition and service
belong to WS4; the Aurora cluster to WS3; MSK to WS6.

## network

Three-AZ VPC with three subnet tiers.

| Tier | Route to internet | Purpose |
| --- | --- | --- |
| `public` | IGW | ALB only |
| `private_app` | NAT | ECS Fargate tasks, interface endpoints |
| `private_data` | none (no default route) | Aurora, MSK |

`single_nat_gateway = true` gives dev one NAT Gateway; prod sets `false` for one per AZ.
NAT egress exists because Fineract calls an external SMS gateway
(`fineract-provider/.../SmsMessageScheduledJobServiceImpl.java`) and an SMTP relay
(`ReportMailingJobEmailServiceImpl.java:94-96`). Whether that egress stays on NAT or moves to an
on-premises proxy/Direct Connect is **Q-PLT-8**.

Endpoints: S3 gateway endpoint on every private route table, plus interface endpoints for
`ecr.api`, `ecr.dkr`, `secretsmanager`, `ssm`, `logs` and `kms` in the app subnets, so image pulls,
secret fetches and log writes do not traverse NAT.

The VPC's default security group is emptied (`aws_default_security_group`) so an ENI that
accidentally lands on it has no connectivity.

## security

- Three CMKs with key policies, keyed by data domain: `database` (Aurora, WS3), `s3` (report
  export and artefact buckets, WS2/WS5), `logs` (CloudWatch log groups, WS7).
- Security groups: `alb`, `tasks`, `data`. The only rule open to `0.0.0.0/0` is ALB ingress on 443
  (`var.alb_ingress_cidrs`). Tasks accept traffic only from the ALB security group; the data group
  accepts Postgres and MSK ports only from the tasks group.
- VPC flow logs to a CloudWatch log group encrypted with the `logs` CMK.
- `enable_cloudtrail` and `enable_config` default to `false` so a dev account without
  Organizations access can still `plan`; set them per environment once WS0 confirms the account
  structure (**Q-SEC-7**).

## dns

Hosted zone (or an existing `hosted_zone_id`) plus a DNS-validated ACM certificate.
`validate_certificate = false` skips the `aws_acm_certificate_validation` wait when the zone is
delegated outside this account.

## edge

Internet-facing ALB, HTTPS listener (TLS 1.3 policy), IP-target-type target group and a WAFv2 web
ACL with the AWS managed rule groups plus a rate-based rule (`var.waf_rate_limit`). WAF logs go to
an `aws-waf-logs-*` CloudWatch group with the `authorization` header redacted.

The target group health check is `/fineract-provider/actuator/health/readiness`, matching the
existing Kubernetes readiness probe in `kubernetes/fineract-server-deployment.yml:71-86`.

### Contract consumed by WS4 (ECS service)

WS4 must not create an ALB, listener or target group. It consumes these root outputs:

| Root output | WS4 usage |
| --- | --- |
| `target_group_arn` | `load_balancer { target_group_arn = ... , container_name = "fineract", container_port = 8443 }` on the ECS service |
| `tasks_security_group_id` | `network_configuration.security_groups` of the ECS service |
| `private_app_subnet_ids` | `network_configuration.subnets` |
| `vpc_id` | any additional WS4-owned security group |
| `kms_key_arns["logs"]` | CMK for the task log group |
| `api_endpoint_url` | smoke tests and deployment verification |

Target type is `ip` (awsvpc networking) and the container must listen on `var.task_port`
(8443 by default, the port Fineract's HTTPS connector uses). Deregistration delay is 60s;
WS4 should keep the ECS deployment's `health_check_grace_period_seconds` above the readiness
probe's warm-up time.

### Contract consumed by WS3 (Aurora) and WS6 (MSK)

| Root output | Usage |
| --- | --- |
| `private_data_subnet_ids` | DB subnet group / MSK client subnets |
| `data_security_group_id` | cluster security group |
| `kms_key_arns["database"]` | storage encryption CMK |

WS2 (CI/CD) consumes `kms_key_arns["s3"]` for artefact buckets and the VPC/subnet ids for
CodeBuild projects that need VPC access.
