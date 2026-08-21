# Open Questions

Everything the repository cannot settle. Each question is addressed to the role that owns the answer
and carries an ID referenced by [target-state.md](target-state.md) and
[migration-plan.md](migration-plan.md). Questions marked **blocking** must be closed before design
sign-off.

## Programme

| ID | Question | Owner | Blocking |
| --- | --- | --- | --- |
| OQ-P1 | The approved ("blessed") AWS service list was not supplied, so §1 of the target-state document is an assumed list. Is it correct, and what is the real allowlist enforced by SCP? | Platform Engineering | **Yes** |
| OQ-P2 | The platform migration guardrails were not supplied, so the default set in §2 of the target-state document is assumed. Which of these are actually in force, and what have we missed? | Platform Engineering | **Yes** |
| OQ-P3 | Confirm the environment posture we assumed: dev and QA multi-AZ single region, production multi-region active/passive. Is active/active required, and which regions? | Platform Engineering | **Yes** |
| OQ-P4 | We selected ECS Fargate over EKS for guardrail G2. Does the organisation mandate Kubernetes as the managed container platform? If so the compute rows change but nothing else does. | Platform Engineering | **Yes** |
| OQ-P5 | What is the lead time and approval path for vending the dev, QA and prod accounts? This sets the start date of WS1 and everything after it. | Platform Engineering | Yes |
| OQ-P6 | Is `COG-GTM/fineract-Core-Banking` the artefact actually deployed today, or is production running a fork, a vendor build, or a different Fineract version? Nothing in this repository can answer that. | Application Owner | **Yes** |
| OQ-P7 | Is the Mifos web-app UI (`kubernetes/fineract-mifoscommunity-deployment.yml:102`) in scope for this migration, or is the customer's own front end used? | Application Owner | Yes |
| OQ-P8 | Are custom modules under `custom/` (e.g. `custom/acme/loan`, `custom/acme/note`) real customer extensions that must migrate, or sample scaffolding? | Application Owner | Yes |

## Database

| ID | Question | Owner | Blocking |
| --- | --- | --- | --- |
| OQ-D1 | Which engine is production on today — MariaDB or PostgreSQL — and at what version? The repository supports both (`README.md:33`) and defaults to MariaDB (`application.properties:405-406`). If it is already PostgreSQL, workstream WS3 collapses to a homogeneous data move. | DBA | **Yes** |
| OQ-D2 | Production data volumes: total size, largest tables, row counts on `m_loan_transaction` / journal entries, and daily growth. This sets the conversion window and whether DMS CDC is needed. | DBA | **Yes** |
| OQ-D3 | How many tenants exist, and does any tenant require database- or cluster-level isolation beyond database-per-tenant inside one Aurora cluster? | DBA / Application Owner | Yes |
| OQ-D4 | Are there any objects in the production schemas that are **not** managed by the 318 Liquibase changelogs — hand-created views, triggers, scheduled events, grants, or reporting-only tables? The repository has zero stored procedures, but reports are user-defined and stored in the database (`ReadReportingServiceImpl.java`). | DBA | **Yes** |
| OQ-D5 | The `m_report` / stretchy-reporting tables hold user-authored SQL executed at runtime. Has anyone inventoried that SQL for MariaDB-specific dialect (backticks, `IFNULL`, `LIMIT x,y`, `DATE_FORMAT`)? It is data, so it will not appear in any code diff. | DBA / Application Owner | **Yes** |
| OQ-D6 | Is there a read-only replica in use today? The configuration surface exists but is empty (`application.properties:56-62`). | DBA | No |
| OQ-D7 | What is the current backup, retention and point-in-time-recovery arrangement, and what must Aurora match? | DBA | Yes |

## Platform

| ID | Question | Owner | Blocking |
| --- | --- | --- | --- |
| OQ-A1 | What actually runs the workload today — the committed Kubernetes manifests, a Helm chart held elsewhere, or VMs running the WAR on Tomcat (`fineract-war/build.gradle:21`)? The manifests use a `hostPath` volume (C3), which is a single-node construct, so they are unlikely to be the production truth. | Platform Engineering / Application Owner | **Yes** |
| OQ-A2 | Real hostnames, DNS zones and certificate authority for the API and UI: what names must survive cutover, and who controls those DNS records? | Platform Engineering | Yes |
| OQ-A3 | Which clients call the API, from which networks, and can they be moved behind a private ALB, or is public internet exposure required (in which case WAF rules and rate limits need defining)? | Application Owner / Security | **Yes** |
| OQ-A4 | Is external eventing (Kafka/MSK) actually used in production? It is disabled by default (`application.properties:124`) but an MSK profile is committed (`config/docker/env/kafka-client-msk.env`). If unused, MSK drops out of the target entirely. | Application Owner | **Yes** |
| OQ-A5 | Does the `democluster1` MSK cluster in `eu-central-1` referenced at `config/docker/env/kafka-client-msk.env:23` exist, who owns it, and is it in scope? | Platform Engineering | Yes |
| OQ-A6 | How large is the document/image store today (currently container-local disk, C10), and is any of it business-critical data that must be preserved on migration to S3? | Application Owner | **Yes** |
| OQ-A7 | Peak and average API throughput, concurrent users, and the COB batch window — needed to size ECS task counts and the Aurora writer. | Application Owner | Yes |

## Delivery

| ID | Question | Owner | Blocking |
| --- | --- | --- | --- |
| OQ-DL1 | Which CD tool is approved? There is no deployment automation of any kind in this repository, so this is net-new work, and the choice shapes workstream WS6. | Release Manager | **Yes** |
| OQ-DL2 | Which registry must the Jib base image `azul/zulu-openjdk-alpine:21` (`fineract-provider/build.gradle:266`) and all Gradle dependencies come from, per guardrail G9? Is there an internal mirror/proxy? | Release Manager / Platform Engineering | **Yes** |
| OQ-DL3 | How is a release approved today, and who authorises a production deployment? The current path is a manual `kubectl apply` (`kubernetes/kubectl-startup.sh:25-45`). | Release Manager | Yes |
| OQ-DL4 | Should GitHub Actions remain the CI system, publishing to ECR instead of Docker Hub (`.github/workflows/publish-dockerhub.yml:43-48`), or must CI also move? | Release Manager | Yes |
| OQ-DL5 | What is the acceptable release outage? Today every release is a full outage because the deployment strategy is `Recreate` (`kubernetes/fineract-server-deployment.yml:49-50`); ECS rolling deployments remove that, but only if the Liquibase changes are backward-compatible. | Release Manager / Application Owner | Yes |

## Security

| ID | Question | Owner | Blocking |
| --- | --- | --- | --- |
| OQ-S1 | Authentication target: keep HTTP Basic (`application.properties:24`), or enable the OAuth2 support against the corporate IdP (`:25,37-42`)? Is 2FA (`:26`) required for any user population? | Security / Application Owner | **Yes** |
| OQ-S2 | SMTP, SMS and S3 credentials are stored in tenant database tables and read at runtime (`ExternalServicesPropertiesReadPlatformServiceImpl.java:54-94`). Is that acceptable under the secrets guardrail (G8) given Aurora KMS encryption at rest, or must they move to Secrets Manager — which would require an application change? | Security | **Yes** |
| OQ-S3 | The default CORS configuration is `*` with `allow-credentials=true` (`application.properties:31,35`) and the actuator CORS origin is `*` (`:341`). What origin list should the deployed configuration use? | Security | **Yes** |
| OQ-S4 | `fineract.insecure-http-client` defaults to `true` (`application.properties:221`), disabling outbound TLS verification. Confirm it is set to `false` in all AWS environments, and identify any integration endpoint with a private-CA certificate that then needs a trust store. | Security | **Yes** |
| OQ-S5 | Data classification of the tenant data (PII, financial records) — this drives the mandatory tag set (G10), KMS key policy, and whether cross-region replication for the DR posture is permitted. | Security / Data Governance | **Yes** |
| OQ-S6 | Is a WAF ruleset mandated in front of internet-facing services, and does the customer have a standard rule group? | Security | Yes |
| OQ-S7 | Who rotates the database credentials today? The Kubernetes secret is generated once by a shell script with no rotation path (`kubernetes/kubectl-startup.sh:24`), and a working DB password is committed to the local-development env file (`config/docker/env/fineract-common.env:31`). | Security | Yes |

## Operations

| ID | Question | Owner | Blocking |
| --- | --- | --- | --- |
| OQ-O1 | Target RTO and RPO for the platform. Nothing in the repository implies either, and the multi-region prod posture (G7) is only meaningful against a number. | Application Owner / Business | **Yes** |
| OQ-O2 | The mandatory tag keys (owner, cost centre, environment, data classification) and the ingest endpoint of the central observability platform. | Platform Engineering | Yes |
| OQ-O3 | Which alerts and dashboards exist today, and what constitutes a page? The application exposes `health`, `info` and `prometheus` only (`application.properties:347`). | Operations | Yes |
| OQ-O4 | The COB batch window and its business deadline. The COB job is partitioned and node-bound (`application.properties:88-95`; `JobRegisterServiceImpl.java:206-212`), so a missed window is a business incident, and the Quartz in-memory job store (C7) means an in-flight run is lost on task replacement. | Operations / Application Owner | **Yes** |
| OQ-O5 | Who operates the platform after cutover, and does that team already run ECS and Aurora, or is enablement part of the programme? | Operations | Yes |
| OQ-O6 | Is there a compliance or audit obligation on log retention and the immutable command audit trail that constrains CloudWatch retention settings? | Operations / Compliance | Yes |
