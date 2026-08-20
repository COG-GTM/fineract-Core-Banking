# WS1 — Landing zone and network

Terraform for the AWS landing zone that the rest of the migration programme builds on: VPC and
subnets, security baseline, DNS/TLS and the internet edge. It creates **no compute and no data
stores** — WS3 (Aurora), WS4 (ECS service) and WS6 (MSK) attach to the outputs listed below.

The architecture package notes that infrastructure-as-code would normally live in its own
repository. For this programme the tree is kept here, under `infrastructure/terraform/`, so that
it is reviewable alongside the application it provisions. Splitting it out is a WS0/WS2 decision.

## Layout

```
infrastructure/terraform/
├── .tflint.hcl                 shared lint configuration
├── modules/
│   ├── network/                VPC, 3 AZs, 3 subnet tiers, NAT, VPC endpoints
│   ├── security/               KMS CMKs, security groups, flow logs, CloudTrail/Config stubs
│   ├── dns/                    Route 53 hosted zone, ACM certificate (DNS validation)
│   ├── edge/                   ALB, HTTPS listener, target group, WAFv2 web ACL, alias record
│   └── README.md               module contracts, including the WS4 target-group contract
├── envs/
│   ├── dev/                    single NAT, no deletion protection, 30-day log retention
│   └── prod/                   NAT per AZ, deletion protection, 365-day log retention
└── tests/offline-plan.sh       credential-free `terraform plan` against a mocked provider
```

`envs/dev` and `envs/prod` differ only in variable values and three inline switches
(`single_nat_gateway`, `flow_logs_retention_days`, `enable_deletion_protection`).

## Remote state

Each root declares an S3 backend with **no** inline configuration:

```hcl
terraform {
  backend "s3" {}
}
```

Values are supplied at init time from a partial-configuration file, so no real bucket name is
committed. Copy `backend.hcl.example` to `backend.hcl` (git-ignored) and run:

```bash
terraform init -backend-config=backend.hcl
```

The bucket, DynamoDB lock table and state CMK are provisioned out of band (a one-off bootstrap or
Control Tower), because they must exist before the first `init`.

## Variables each environment must supply

| Variable | Dev default | Prod default | Notes |
| --- | --- | --- | --- |
| `region` | `eu-west-1` | `eu-west-1` | Warm-standby second region is WS9 (Q-PRG-2) |
| `account_id` | `null` | `null` | Falls back to the caller's account; set explicitly for cross-account KMS policies |
| `vpc_cidr` | `10.60.0.0/16` | `10.70.0.0/16` | Must not overlap the on-premises range once hybrid connectivity is agreed (Q-PLT-9) |
| `availability_zones` | 3 AZs | 3 AZs | Exactly three, validated |
| `public_subnet_cidrs` | /24 × 3 | /24 × 3 | ALB only |
| `private_app_subnet_cidrs` | /20 × 3 | /20 × 3 | ECS tasks and interface endpoints |
| `private_data_subnet_cidrs` | /22 × 3 | /22 × 3 | Aurora and MSK; no default route |
| `alb_ingress_cidrs` | `0.0.0.0/0` | `0.0.0.0/0` | Narrow to the corporate range where the API is not public |
| `task_port` | `8443` | `8443` | Fineract HTTPS connector |
| `domain_name` / `api_record_name` | `*.example.com` placeholders | placeholders | Replace with the real zone before the first apply |
| `create_hosted_zone` / `hosted_zone_id` | create | create | Set `false` + an id to reuse a delegated zone |
| `validate_certificate` | `true` | `true` | `false` when the zone is delegated elsewhere and validation records are added manually |
| `enable_cloudtrail` / `enable_config` | `false` | `false` | Enable once the account/Organizations posture is settled (Q-SEC-7) |

## Outputs consumed by other workstreams

| Output | Consumer | Use |
| --- | --- | --- |
| `target_group_arn` | WS4 | ECS service `load_balancer` block — WS4 must not create its own target group |
| `tasks_security_group_id` | WS4 | ECS service network configuration |
| `private_app_subnet_ids` | WS4 | ECS service subnets |
| `private_data_subnet_ids` | WS3, WS6 | DB subnet group, MSK client subnets |
| `data_security_group_id` | WS3, WS6 | Aurora / MSK security group |
| `kms_key_arns["database"]` | WS3 | Aurora storage encryption |
| `kms_key_arns["s3"]` | WS2, WS5 | Artefact and report-export buckets |
| `kms_key_arns["logs"]` | WS4, WS7 | Task and platform log groups |
| `vpc_id`, `public_subnet_ids` | WS2, WS4 | VPC-attached CodeBuild, extra security groups |
| `api_endpoint_url`, `alb_dns_name` | WS4, WS8 | Smoke tests and cutover checks |
| `nat_gateway_public_ips` | WS0/WS8 | Egress addresses to register with the SMS and SMTP providers |
| `hosted_zone_name_servers` | WS0 | Delegation from the parent domain |
| `web_acl_arn` | WS7 | WAF metrics and alarms |

## Design decisions

- **Three subnet tiers rather than two.** The data tier has route tables with no default route, so
  Aurora and MSK cannot reach the internet even if a security group is later widened.
- **NAT sized per environment.** Dev uses one NAT Gateway (cost); prod one per AZ (AZ-failure
  isolation). Egress is required by the SMS job in `SmsMessageScheduledJobServiceImpl.java` and the
  SMTP relay in `ReportMailingJobEmailServiceImpl.java:94-96`.
- **Interface endpoints for ECR, Secrets Manager, SSM, CloudWatch Logs and KMS**, so task startup
  and logging do not depend on NAT.
- **Readiness, not liveness, as the target-group health check.**
  `/fineract-provider/actuator/health/readiness` matches
  `kubernetes/fineract-server-deployment.yml:71-86`, so ALB registration follows the same signal
  the current Kubernetes deployment uses.
- **CMKs per data domain** rather than one key, so database, object storage and log access can be
  separated in key policies later.
- **CloudTrail and Config behind variables**, default off, so a sandbox account without
  Organizations access can still run `terraform plan`.

## Open WS0 questions this workstream depends on

| Question | Impact if answered differently |
| --- | --- |
| **Q-PRG-2** — production resilience posture (warm standby in a second region?) | A second region needs a peer landing zone and cross-region CMK/replication decisions; only the primary region is built here |
| **Q-PLT-3** — approved IaC tool | The whole tree is Terraform by assumption; a CDK/CloudFormation mandate would replace it |
| **Q-SEC-7** — compliance regimes, account structure, data residency | Drives whether CloudTrail/Config live here or in a management account, key policy scoping, and region choice |
| **Q-PLT-8** — SMS/SMTP egress via NAT vs on-premises proxy or Direct Connect | Would replace NAT egress with routes to a transit gateway and change the prod NAT count |
| **Q-PLT-9** — hybrid connectivity during migration | Determines VPC CIDR allocation and whether a Transit Gateway/VPN is added to the network module |

## Validation

No AWS credentials are used anywhere. CI (`.github/workflows/terraform-validate.yml`) runs
`terraform fmt -check`, `terraform init -backend=false`, `terraform validate` and `tflint` for each
module and each environment root. Locally, `tests/offline-plan.sh dev|prod` drops a temporary
provider override with mocked credentials and a local backend and runs a refresh-free plan, which
proves the graph resolves end to end without touching an account.

`checkov` is run as a static policy check. Findings that remain are deliberate and listed with
their rationale in the pull request: dev-only relaxations (log retention, ALB deletion
protection), stub-scoped CloudTrail/Config buckets, security groups whose consumers are created by
WS3/WS4/WS6, and cross-module or dynamic-expression false positives (flow logs and the Log4j
managed rule group are both present but declared in a different module or via a variable).
