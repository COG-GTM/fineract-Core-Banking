# Open Questions

Facts the repository cannot settle. Each question has an ID (referenced from `target-state.md` and
`migration-plan.md`), a named owning role, and the decision it blocks.

Priority: **P0** blocks design sign-off; **P1** blocks build; **P2** blocks cutover.

---

## Platform (owner: Platform Engineering)

| ID | Question | Why it matters | Blocks | Pri |
| --- | --- | --- | --- | --- |
| **PLT-1** | **Please confirm or replace the assumed AWS service allowlist in `target-state.md` §2.** No approved ("blessed") service list was supplied with this engagement, so §2 is an assumption made by us and is explicitly **not** your policy. Which of those services are approved, and which are prohibited? | Every row of the target-state table is invalid if the allowlist differs. Three services are already tagged `OFF-LIST?` pending your answer (MSK, OpenSearch, Managed Prometheus/Grafana) | Design sign-off | **P0** |
| **PLT-2** | **Please confirm or replace the assumed default guardrail set in `target-state.md` §1.** No customer guardrails were supplied; G1–G10 are the playbook defaults, labelled as assumptions. Do you have a published platform standard that supersedes them? | The guardrail-compliance table is measured against these ten statements | Design sign-off | **P0** |
| **PLT-3** | Is the approved managed container platform ECS Fargate, EKS, or both? The design assumes ECS Fargate | Changes the shape of rows 1, 4, 8 and 9 (batch manager singleton, migration task, worker scaling) | Design sign-off | **P0** |
| **PLT-4** | What is the account-vending process and the required account topology (dev/QA/prod, shared services, network account)? Who requests accounts and what is the lead time? | WS1 cannot start without accounts; lead time usually sets the critical path | WS1 | **P0** |
| **PLT-5** | What are the Terraform standards — module registry, state backend, provider version pinning, naming convention, and who reviews infrastructure changes? | WS1 and every later workstream produce Terraform | WS1 | **P1** |
| **PLT-6** | Which container registry and dependency proxy are approved? Specifically, may we mirror `azul/zulu-openjdk-alpine:21` (`fineract-provider/build.gradle:266`) into ECR, and what replaces Maven Central for Gradle resolution? | G9. The build currently pulls the base image from Docker Hub and dependencies from public repositories | WS4 | **P1** |
| **PLT-7** | Is there a landing-zone VPC/CIDR allocation, Transit Gateway attachment or shared-VPC model we must consume rather than create? | Determines whether WS6 builds a VPC or consumes one | WS6 | **P1** |

## Programme (owner: Application Owner)

| ID | Question | Why it matters | Blocks | Pri |
| --- | --- | --- | --- | --- |
| **PRG-1** | Which repository, branch and version is actually running in production today? This repository is a fork of `apache/fineract` and contains **no** deployment pipeline — nothing in `.github/workflows/` deploys anywhere | The current-state analysis describes *this* repository. If production runs a different fork or a modified build, parts of it do not apply | Design sign-off | **P0** |
| **PRG-2** | How many tenants are in production, what is the total and per-tenant data volume, and what is the peak concurrent API rate? | Sizing for Aurora and ECS, and the DMS full-load window in WS7 | WS3, WS7 | **P0** |
| **PRG-3** | What are the RTO and RPO commitments, and is multi-region prod a requirement or an aspiration? The design assumes multi-region prod / multi-AZ dev | Multi-region roughly doubles the infrastructure cost and adds the Aurora global-database constraint | Design sign-off | **P0** |
| **PRG-4** | What is the acceptable cutover downtime window, and are there regulatory or end-of-day constraints (COB must complete, statutory reporting dates)? | Fineract's Close-of-Business cycle makes cutover timing a business decision, not an ops one | WS10 | **P1** |
| **PRG-5** | Which optional Fineract subsystems are actually in use in production — external business events, remote job messaging, SMS campaigns, e-mail campaigns, report mailing, hooks? All are **disabled by default** in `application.properties` | Rows 9, 10, 16 and 23 of the target state exist only if these are switched on | Design sign-off | **P1** |
| **PRG-6** | Are there downstream consumers of the database (BI, reporting, ETL) reading the MariaDB schema directly? | A direct-SQL consumer turns the engine change into a cross-team migration | WS3 | **P1** |

## Database (owner: DBA)

| ID | Question | Why it matters | Blocks | Pri |
| --- | --- | --- | --- | --- |
| **DB-1** | Is production on MariaDB or PostgreSQL today, and at which exact version? The repository disagrees with itself: `README.md:33` requires `MariaDB >= 11.5.2`, `kubernetes/fineractmysql-deployment.yml:89` and `config/docker/compose/mariadb.yml:21` pin `mariadb:11.4`, CI uses `mariadb:11.5.2` | If production is already PostgreSQL, WS3 collapses from ~10 weeks to a data copy | WS3 | **P0** |
| **DB-2** | Aurora PostgreSQL trails community PostgreSQL. `README.md:33` states `PostgreSQL >= 18.0` and `config/docker/compose/postgresql.yml:23` runs `postgres:18.3`. Is an Aurora PostgreSQL major version available that Fineract supports, and has the Liquibase changelog set (237 files) been validated against it? | If not, row 3 becomes an exception to guardrail G3 | WS3 | **P0** |
| **DB-3** | Who owns validating the 204 non-test files using `JdbcTemplate` against PostgreSQL? Dialect handling exists (310 `DatabaseTypeResolver`/`DatabaseSpecificSQLGenerator` references, 55 `isMySQL()`/`isPostgreSQL()` branches) but coverage is unproven for your tenant data | This single answer sets the risk level of the whole programme | WS3 | **P0** |
| **DB-4** | Are there customer-specific "stretchy" reports or ad-hoc datatable queries stored **as data** in the production database? The repository contains no stored procedures, but reports are SQL held in tables (`.../dataqueries/service/ReadReportingServiceImpl.java`) | Customer report SQL is invisible to this analysis and is the most likely source of post-cutover breakage | WS3, WS7 | **P0** |
| **DB-5** | Is one Aurora cluster hosting all tenant databases acceptable, or is tenant isolation at cluster level required? | Cost and blast radius; also changes the DMS task count in WS7 | WS3 | **P1** |
| **DB-6** | Are the read-only replica settings (`application.properties:56-61`) in use in production, and should reporting traffic be routed to Aurora readers? | Sizing and connection-string configuration | WS3 | **P2** |
| **DB-7** | What is the maximum acceptable Liquibase migration window? Tenant upgrades are deliberately single-threaded (`application.properties:157-160`), so migration time scales linearly with tenant count | Determines whether row 4's pre-deploy migration task fits the cutover window | WS10 | **P1** |

## Delivery (owner: Release Manager)

| ID | Question | Why it matters | Blocks | Pri |
| --- | --- | --- | --- | --- |
| **DEL-1** | Which pipeline tooling is approved for build and deploy, and who provisions runners with access to the target accounts? | WS5 has no target without this | WS5 | **P0** |
| **DEL-2** | May the 19 existing GitHub Actions test workflows stay as pre-merge validation, with the approved tool owning build-and-deploy only? | Keeping them avoids re-implementing a large, working test matrix | WS5 | **P1** |
| **DEL-3** | What are the change-approval requirements for prod deploys (CAB, ticket reference, approval gate in the pipeline)? | Shapes the WS10 cutover runbook | WS10 | **P1** |
| **DEL-4** | Is Kafka available as a managed platform service? If not, are the Avro external-event schemas (`fineract-avro-schemas/`) contractual for any consumer, i.e. can SNS/EventBridge replace them? | Decides rows 9 and 10 and whether `OFF-LIST?` MSK must be raised as an exception | Design sign-off | **P1** |
| **DEL-5** | Is publishing to Docker Hub (`.github/workflows/publish-dockerhub.yml`) a requirement of the upstream Apache project relationship, or can it be retired for this deployment? | Row 2; may need to keep both publish targets | WS4 | **P2** |

## Security (owner: Security)

| ID | Question | Why it matters | Blocks | Pri |
| --- | --- | --- | --- | --- |
| **SEC-1** | Credential material is committed to this repository: an AWS credentials file (`config/docker/aws/etc/credentials`), a TLS keystore (`fineract-provider/src/main/resources/keystore.jks`) and literal database passwords in `config/docker/env/*.env`. Who owns rotation and revocation, and is a git-history purge required as well as rotation? | WS0. Values are not reproduced anywhere in this package; treat them as compromised | WS0 | **P0** |
| **SEC-2** | Is AWS Secrets Manager the approved secrets store, or is there an existing enterprise vault we must integrate with? | Row 13, and the ECS task-definition shape | WS6 | **P0** |
| **SEC-3** | Who owns TLS certificates and DNS for the production hostnames? The app currently terminates TLS itself with the committed keystore and its default password (`application.properties:390-391`) | Row 12; ACM issuance needs DNS validation | WS6 | **P1** |
| **SEC-4** | `fineract.insecure-http-client=true` (`application.properties:221`) disables outbound TLS verification by default. Is it overridden in production, and who owns fixing it before cutover? | Cutover blocker under guardrail G8; the fix is application configuration, not infrastructure | WS10 | **P0** |
| **SEC-5** | CORS allows any origin with credentials (`application.properties:30-35`) and the actuator CORS allows any origin (`application.properties:341-343`). What is the approved origin list for production? | Cutover blocker under G8 | WS10 | **P1** |
| **SEC-6** | Is HTTP Basic auth (`application.properties:24`, OAuth2 and 2FA both off by default) the intended production auth model, or is there an IdP to integrate? | Affects whether an OAuth2 authorisation server is needed in the target design | Design sign-off | **P1** |
| **SEC-7** | What is the data classification of tenant data and the mandatory encryption/key-management standard (customer-managed KMS keys, rotation period, cross-region key strategy for the DR region)? | Row 14 and the multi-region design | WS6 | **P1** |
| **SEC-8** | Is a WAF ruleset mandated, and does the ALB need to be private-only behind an existing ingress? | Row 11 | WS6 | **P2** |

## Operations (owner: Platform Engineering / Operations)

| ID | Question | Why it matters | Blocks | Pri |
| --- | --- | --- | --- | --- |
| **OPS-1** | What is the central observability platform — CloudWatch, or an existing Prometheus/Grafana/Datadog estate? Fineract can export OTLP, Prometheus or CloudWatch (`application.properties:347,354-367`), all disabled by default | Row 17 and whether `OFF-LIST?` Managed Prometheus is needed | WS8 | **P1** |
| **OPS-2** | What are the mandatory tag keys and their allowed values (owner, cost centre, environment, data classification)? | G10; applied via Terraform `default_tags` | WS1 | **P1** |
| **OPS-3** | What are the current document-store volume and growth rate, and where do the existing documents live? The default content store is the container's local disk (`application.properties:186-187`, `config/docker/env/fineract-common.env:57`) | Row 6 and the WS7 document copy | WS7 | **P1** |
| **OPS-4** | Which hooks are configured in production — Twilio (`.../hooks/processor/TwilioHookProcessor.java:44`), Message Gateway, Elasticsearch, generic web hooks — and what are their destination endpoints? | The NAT egress allowlist in row 15 must enumerate every destination | WS6 | **P1** |
| **OPS-5** | What SMTP relay does production use today, and is Amazon SES acceptable (sender domains, DKIM, sending limits)? | Row 16 | WS6 | **P2** |
| **OPS-6** | How is Close-of-Business operated today — schedule, duration, who monitors it, and what happens on failure? | The batch-manager singleton and worker autoscaling in rows 8 and 9, and the WS10 first-COB gate | WS8, WS10 | **P1** |
| **OPS-7** | What is the current production instance count and size? The Kubernetes manifest allows 1 vCPU / 2 GiB with `-Xmx1G` (`kubernetes/fineract-server-deployment.yml:64-70,122-123`) while `README.md:32` asks for 16 GB / 8 cores — neither is evidence of production sizing | Fargate task sizing | WS2 | **P1** |
| **OPS-8** | Who is the on-call owner after cutover, and what are the alert routing and escalation paths? | WS8 exit criteria | WS8 | **P2** |
