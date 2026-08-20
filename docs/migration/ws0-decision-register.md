# WS0 — Decision register

Workstream WS0 (Discovery and decisions) of the Fineract → AWS migration programme.

Scope of this document: every open question from the architecture package `open-questions.md`, with the
answer the repository can prove, and the specific input the named owner must supply. All repository
evidence is against branch `develop` at commit `8c187f9d1` ("FINERACT-2471: Implement 'Force Debit'
functionality for Savings Accounts with Configurable Limits"), which is the analysis commit named in the
package, verified with `git rev-parse HEAD` →
`8c187f9d17fb839f26fc888f34a64f7c1f57a802`.

Status values:

- **Closed (repository)** — the repository settles the question; no external input needed.
- **Partially answered** — the repository narrows the answer but an owner must still confirm the
  production reality.
- **External** — nothing in the repository can answer it; the named owner must supply the fact.

Priority is carried unchanged from `open-questions.md`: B = blocks design sign-off, C = blocks cutover,
P = plan-shaping.

---

## 1. Programme

| ID | Owner | Pri | Status | What the repository proves | What the owner must supply |
|----|-------|-----|--------|---------------------------|----------------------------|
| Q-PRG-1 (approved AWS service list) | Cloud Architecture Lead | B | External | Nothing. No AWS service inventory, IaC or account reference exists in the repository (`find . \( -name "*.tf" -o -name "Chart.yaml" -o -name "cdk.json" \)` → 0 results). The only AWS surfaces the code itself uses are S3 (`fineract-provider/src/main/java/org/apache/fineract/infrastructure/s3/AmazonS3Config.java`) and MSK IAM auth (`config/docker/env/kafka-client-msk.env:22-35`). | The approved service list, and specifically an in/out ruling on Amazon SES for the SMTP path at `fineract-provider/src/main/java/org/apache/fineract/infrastructure/reportmailingjob/service/ReportMailingJobEmailServiceImpl.java:94-96`. |
| Q-PRG-2 (resilience posture per environment) | Cloud Architecture Lead | B | External | Nothing. The repository has one deployment topology per artefact (single compose stack, single Kubernetes Deployment with `strategy: Recreate`, `kubernetes/fineract-server-deployment.yml:49-56`) and no environment matrix. | Whether prod is single-region multi-AZ or warm-standby multi-region. Decides Aurora Global Database and cross-region MSK replication. |
| Q-PRG-3 (is this repo what runs in production) | Application Owner | B | **Partially answered** | The `custom/` extension tree is **not empty**, contradicting `open-questions.md`. It contains a working sample company tree `custom/acme/**` with eight Gradle modules (`custom/acme/event/externalevent`, `custom/acme/event/starter`, `custom/acme/loan/cob`, `custom/acme/loan/job`, `custom/acme/loan/processor`, `custom/acme/loan/starter`, `custom/acme/note/service`, `custom/acme/note/starter`), each with its own `build.gradle` / `dependencies.gradle`, plus `custom/docker`. `settings.gradle:84-96` loads them dynamically with the pattern `custom -> company -> category -> module`, and `settings.gradle:82` includes `:custom:docker`. So the extension mechanism is live and demonstrably used here — by a sample, not by customer code. | Whether production builds from this branch, from a release tag, or from a fork; and if a fork, the diff. |
| Q-PRG-4 (customisations in `custom/` in the production build) | Application Owner | B | **Partially answered** | The extension points are enumerable from the repository: (a) Spring auto-configuration starters — `custom/acme/note/starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`; (b) service overrides — `custom/acme/note/service/src/main/java/com/acme/fineract/portfolio/note/service/AcmeNoteWritePlatformService.java`; (c) COB business steps — `custom/acme/loan/cob/src/main/java/com/acme/fineract/loan/cob/AcmeNoopBusinessStep.java`; (d) scheduled jobs — `custom/acme/loan/job/src/main/java/com/acme/fineract/loan/job/AcmeNoopJobConfiguration.java`; (e) loan transaction processors — `custom/acme/loan/processor/src/main/java/com/acme/fineract/loan/processor/AcmeLoanRepaymentScheduleTransactionProcessor.java`; (f) per-module Liquibase changelogs picked up by `includeAll path="db/custom-changelog"` in `fineract-provider/src/main/resources/db/changelog/db.changelog-master.xml:38`. Only the `acme` sample occupies them here. | The production `custom/` tree (module list plus changelogs). Any custom changelog is schema that WS3 must migrate and that is invisible to this analysis. |
| Q-PRG-5 (migration driver and deadline) | Programme Sponsor | P | External | Nothing. | The driver (datacentre exit / licence renewal / modernisation) and the date. Determines whether WS3 is in the initial cutover. |

## 2. Database

| ID | Owner | Pri | Status | What the repository proves | What the owner must supply |
|----|-------|-----|--------|---------------------------|----------------------------|
| Q-DB-1 (PostgreSQL vs RDS for MariaDB) | DBA + Application Owner | B | External (repository de-risks it) | PostgreSQL is a first-class, CI-verified target: `DatabaseType` has exactly two values (`fineract-core/src/main/java/org/apache/fineract/infrastructure/core/service/database/DatabaseType.java:21-25`), CI runs the full unit suite against `postgres:18.3` (`.github/workflows/build-postgresql.yml:20`) and separately validates Liquibase-only (`.github/workflows/liquibase-only-postgresql.yml`), and this workstream re-ran that suite locally on PostgreSQL 18.3 with 3,522 of 3,551 tests passing — the 19 failures are all in `:integration-tests` and one of them, `SchedulerJobsTest`, is itself a PostgreSQL type-mapping defect (`ws0-baseline.md` §2.3). There are **no** stored procedures or functions (`grep -rniE "CREATE (PROCEDURE\|FUNCTION)\|DELIMITER" fineract-provider/src/main/resources/db` → 0). | The engine decision. It is a scope decision, not a technical blocker. |
| Q-DB-2 (tenant count, largest tenant size) | DBA | B | External | Hikari defaults are 3 idle / 10 max **per tenant datasource** (`config/docker/env/fineract-common.env:23-24`), and tenants are resolved per request over one datasource each (`fineract-core/src/main/java/org/apache/fineract/infrastructure/core/service/database/RoutingDataSource.java`). Aurora `max_connections` therefore scales with tenant count; the repository cannot supply the multiplier. | Live tenant count, row count and on-disk size of the largest tenant DB. See "sizing inputs still missing". |
| Q-DB-3 (production MariaDB/MySQL version) | DBA | B | **Contradiction confirmed, answer still external** | Verified at HEAD: `README.md:33` requires `MariaDB >= 11.5.2 or PostgreSQL >= 18.0`; `config/docker/compose/mariadb.yml:21` pins `image: mariadb:11.4`; `kubernetes/fineractmysql-deployment.yml:89` pins `image: mariadb:11.4`. Nothing in the repository runs the documented minimum. CI corroborates the split: `.github/workflows/build-postgresql.yml:20` uses `postgres:18.3`. The contradiction is real and unchanged. | The engine and exact version running in production. If it is < 11.5.2, the repository's own stated minimum is already violated in production and that is a finding for WS3, not a documentation nit. |
| Q-DB-4 (RTO/RPO, cutover freeze window) | Application Owner + Business Continuity | C | External | Nothing. No backup, restore or DR configuration exists in the repository. | RTO, RPO and the maximum acceptable freeze window. |
| Q-DB-5 (direct database readers) | DBA + Data Platform | B | External | The repository can only prove Fineract's own access paths: 590 `JdbcTemplate` occurrences across 204 main-source files, 19 files using `EntityManager`, 1 `createNativeQuery`. Any reader outside this process is invisible here. | The list of systems reading the Fineract database directly (reporting, warehouse, regulatory extract). |
| Q-DB-6 (is the tenant master password still the default) | DBA + Security | C | **Partially answered** | The default is `fineract` in two places: `fineract-provider/src/main/resources/application.properties:53` (`fineract.tenant.master-password`) and `:206` (`fineract.database.defaultMasterPassword`), and the dev compose does **not** override it — `config/docker/env/fineract-common.env:52` sets `FINERACT_DEFAULT_MASTER_PASSWORD=fineract`. So every environment that inherits the shipped env files uses the default. | Whether production overrides `FINERACT_DEFAULT_MASTER_PASSWORD` / `FINERACT_DEFAULT_TENANTDB_MASTER_PASSWORD`. |
| Q-DB-7 (MariaDB-specific operational tooling) | DBA | P | External | The repository contains no operational runbooks and no `mariadb-dump` / `mariabackup` / MaxScale reference outside container images. | Runbook inventory. |

## 3. Platform

| ID | Owner | Pri | Status | What the repository proves | What the owner must supply |
|----|-------|-----|--------|---------------------------|----------------------------|
| Q-PLT-1 (what production runs on today) | Platform Engineering | B | External | The repository ships manifests for *both* container options and no evidence of which is used: 12 root compose files (`docker-compose.yml` plus 11 variants) and a Kubernetes set under `kubernetes/`. No cluster reference, no environment inventory. | The production platform (Kubernetes / Compose / VMs / other). |
| Q-PLT-2 (confirm ECS Fargate) | Platform Engineering | B | External | The image is compute-agnostic: rootless (`user = 'nobody:nogroup'`, `fineract-provider/build.gradle:295`), container-aware JVM flags (`config/docker/env/fineract-common.env:60`), ports 8080/8443 (`fineract-provider/build.gradle:293`). Nothing in it prefers ECS over EKS. | The org compute standard. |
| Q-PLT-3 (IaC standard) | Platform Engineering | B | External | No IaC of any kind exists (0 `*.tf`, `Chart.yaml`, `cdk.json`, CloudFormation templates). | Terraform / CDK / CloudFormation. |
| Q-PLT-4 (where TLS terminates) | Platform Engineering + Security | B | **Partially answered** | Today TLS terminates in-process on 8443 with a keystore committed to the repository (`fineract-provider/src/main/resources/keystore.jks`, enabled at `application.properties:386`, password default `openmf` in plain text at `:391`), and Kubernetes exposes that port directly via `type: LoadBalancer` (`kubernetes/fineract-server-deployment.yml:26-34`) — so the self-signed certificate is what clients see today. The application can also serve plain 8080 (`fineract-provider/build.gradle:293`). | The regulatory position on in-VPC encryption: ALB+ACM only, or end-to-end TLS to the task. |
| Q-PLT-5 (document-store filesystem contents) | Application Owner + Platform Engineering | B | External | Defaults only: filesystem store enabled (`application.properties:186`), root `${user.home}/.fineract` (`:187`), overridden to `/tmp` in dev compose (`config/docker/env/fineract-common.env:57`); S3 store present but disabled (`:188`, implementation at `fineract-document/src/main/java/org/apache/fineract/infrastructure/contentstore/service/S3ContentStoreService.java`). | Size of the store, and whether anything other than Fineract reads it (S3 vs EFS decision). |
| Q-PLT-6 (are external events consumed today) | Application Owner + Integration | B | **Partially answered** | Both producers are off by default: JMS `application.properties:129`, Kafka `:140`. The MSK env file however enables them (`config/docker/env/kafka-client-msk.env:29-31`: `FINERACT_EXTERNAL_EVENTS_ENABLED=true`, `FINERACT_EXTERNAL_EVENTS_KAFKA_ENABLED=true`) against three named broker endpoints, which is evidence that *someone* ran this path against a real cluster. | Whether a production consumer exists. If yes, the committed configuration does not describe production. |
| Q-PLT-7 (request rate, COB duration) | Application Owner + SRE | P | External | The repository fixes the COB shape but not its cost: `LOAN_COB` chunk 100, partition 100, 5 threads, retry limit 5, poll 500 ms (`application.properties:88-95`). | Peak/average API rate and current COB window duration. See "sizing inputs still missing". |
| Q-PLT-8 (egress path for SMS/SMTP) | Network Engineering | C | External | Outbound HTTP is blocking `RestTemplate` in 4 main-source files (`.../sms/scheduler/SmsMessageScheduledJobServiceImpl.java`, `.../campaigns/sms/service/SmsCampaignDropdownReadPlatformServiceImpl.java`, `.../campaigns/jobs/sendmessagetosmsgateway/SendMessageToSmsGatewayTasklet.java`, `.../campaigns/jobs/getdeliveryreportsfromsmsgateway/GetDeliveryReportsFromSmsGatewayTasklet.java`); zero `WebClient` occurrences repository-wide. Endpoint hosts are database-configured, not committed. | Whether NAT Gateway egress is permitted or traffic must traverse an on-prem proxy / Direct Connect. |
| Q-PLT-9 (hybrid period required) | Network Engineering | P | External | Nothing. | Whether AWS must reach on-prem systems (or vice versa) during migration. |

## 4. Delivery

| ID | Owner | Pri | Status | What the repository proves | What the owner must supply |
|----|-------|-----|--------|---------------------------|----------------------------|
| Q-DEL-1 (how is it deployed today) | Release Manager | B | **Closed (repository) as far as this repository goes** | The pipeline provably ends at a registry push: `.github/workflows/publish-dockerhub.yml:43-48` runs `:fineract-provider:jib -Djib.to.image=apache/fineract` with `DOCKERHUB_USER` / `DOCKERHUB_TOKEN`, and there is no deployment job in any of the 19 workflow files, no IaC and no CD configuration. Whatever deploys is outside this repository. | The deployment mechanism itself. |
| Q-DEL-2 (target CD tool) | Release Manager + Platform Engineering | B | External | Nothing. | CodePipeline / Harness / Argo / other. |
| Q-DEL-3 (which image is authoritative) | Release Manager | B | **Contradiction confirmed, answer still external** | Three mutually inconsistent references verified at HEAD: (1) compose runs a locally built image — `config/docker/compose/fineract.yml:21` `image: fineract:latest`, produced by `fineract-provider/build.gradle:275-281` whose Jib `to.image` is `fineract` with tags `${project.version}` and `latest`; (2) Kubernetes runs the upstream public image — `kubernetes/fineract-server-deployment.yml:63` `image: apache/fineract:latest`; (3) CI publishes to `apache/fineract` tagged with the branch name plus, on `develop`, the short and long commit hashes — `.github/workflows/publish-dockerhub.yml:38-48`. Only path (3) produces a commit-traceable artefact, and neither deployment path references it. If production really runs `apache/fineract:latest`, production content is not reproducible from any commit. **This is the entry criterion for WS2 and it is now evidenced, but the choice remains the owner's.** | Which of the three is authoritative in production, and the digest currently running. |
| Q-DEL-4 (release cadence, change approval) | Release Manager | P | External | Nothing. | CAB process and freeze windows. |
| Q-DEL-5 (ownership of Docker Hub secrets) | Release Manager + Security | P | External | The secrets are referenced at `.github/workflows/publish-dockerhub.yml:44-45`; ownership is not in the repository. | Owner, and whether they survive the move to ECR. |
| Q-DEL-6 (must schema changes be reversible) | Release Manager + DBA | C | **Partially answered — repository says rollback is not currently possible** | `grep -ro "<rollback" --include="*.xml" .` → **0 occurrences repository-wide**. Not one of the 322 changelog files declares a rollback, so a Liquibase-based schema rollback does not exist today in any path, not merely "not in the master path". | Whether rollback must be schema-reversible. If yes, that is new work and it is not in any current workstream. |

## 5. Security

Recorded as current-state facts, not as defects to fix in this workstream (see `migration-plan.md` WS8).

| ID | Owner | Pri | Status | What the repository proves | What the owner must supply |
|----|-------|-----|--------|---------------------------|----------------------------|
| Q-SEC-1 (committed database password) | Security | C | **Partially answered** | Confirmed at HEAD: the same literal appears twice in `config/docker/env/fineract-common.env` — line 31 (`FINERACT_HIKARI_PASSWORD`) and line 51 (`FINERACT_DEFAULT_TENANTDB_PWD`). Value deliberately not reproduced here. It is in git history, so rotation alone is insufficient. | Whether the value is used in any real environment, the rotation date, and whether history is to be scrubbed. |
| Q-SEC-2 (Basic vs OAuth2 in production) | Security + Application Owner | B | **Partially answered** | Defaults: Basic on (`application.properties:24`), OAuth2 off (`:25`), sample `frontend-client` registration committed (`:37-42`), dedicated `:oauth2-tests` module (`settings.gradle:71`). CI proves the OAuth2 path is exercised — `build-postgresql.yml:26-32` runs `ghcr.io/navikt/mock-oauth2-server:3.0.1`. | Which is enabled in production, and whether Cognito is in scope for this programme. |
| Q-SEC-3 (production CORS allow-list) | Security | C | **Partially answered** | Confirmed: CORS on by default (`:30`) with `*` for origin patterns, methods, headers and exposed headers (`:31-34`) and `allow-credentials=true` (`:35`); actuator CORS is `*` and **not** env-overridable (`:341-343` are literal values, unlike every neighbouring property). | The production allowed-origin list. |
| Q-SEC-4 (`fineract.insecure-http-client`) | Security | C | **Partially answered** | Default `true` (`application.properties:221`) and the dev compose reaffirms it (`config/docker/env/fineract-common.env:56` `FINERACT_INSECURE_HTTP_CLIENT=true`). Every shipped environment disables outbound TLS verification. | Confirmation it is `false` in production and will be `false` in AWS. |
| Q-SEC-5 (committed MSK broker endpoints) | Security + Platform Engineering | C | **Partially answered** | Confirmed: three broker hostnames on port 9198 appear at `config/docker/env/kafka-client-msk.env:23` and again at `:31`, with `security.protocol=SASL_SSL` and `AWS_MSK_IAM`. Endpoints not reproduced here. | Whether that cluster is live and customer-owned, and why it is publicly resolvable. |
| Q-SEC-6 (committed keystore in real use) | Security | C | **Partially answered** | `fineract-provider/src/main/resources/keystore.jks` exists in the source tree, TLS is on by default (`application.properties:386`) and the password default is plain text at `:391`. Kubernetes exposes 8443 straight to a `LoadBalancer` (`kubernetes/fineract-server-deployment.yml:26-34`), so on that path clients *would* see this certificate. | Whether any real client is served by it today. |
| Q-SEC-7 (compliance regimes) | Security + Compliance | B | External | Nothing. | PCI DSS / SOC 2 / local banking regulation, and any dedicated-account, KMS-policy or residency mandate. |
| Q-SEC-8 (is 2FA required) | Security | P | **Partially answered** | Off by default (`application.properties:26`); a dedicated `:twofactor-tests` module exists (`settings.gradle:70`), so the feature is real and tested. | Whether any user population requires it. |
| Q-SEC-9 (break-glass access to Aurora) | Security + Platform Engineering | C | External | Nothing. | Who, and through what mechanism. |

## 6. Operations

| ID | Owner | Pri | Status | What the repository proves | What the owner must supply |
|----|-------|-----|--------|---------------------------|----------------------------|
| Q-OPS-1 (backup regime and retention) | SRE + DBA | C | External | Nothing; the Kubernetes database is a `Deployment` over a `hostPath` PV (`kubernetes/fineractmysql-deployment.yml:20-33`) with no backup object at all. | Current regime and retention that AWS Backup must reproduce. |
| Q-OPS-2 (which alerts exist, who receives them) | SRE | C | **Partially answered** | Confirmed: exporters ship (`config/docker/env/prometheus.env:20`, `cloudwatch.env:20-23`, `oltp.env:20-22`; Prometheus/Loki/Tempo/Grafana in `config/docker/compose/observability.yml:33-63`) but **no alert rule files exist anywhere in the repository** — a filename search for `*alert*` / `*rules*.yml` returns nothing. Alerting is therefore entirely external today. | The current alert inventory and recipients. |
| Q-OPS-3 (log retention, immutability) | SRE + Compliance | P | External | JSON logging is available but off (`application.properties:209`); logs go to a bind mount in compose (`config/docker/compose/fineract.yml:23-26`). | Mandated retention and audit-export requirements. |
| Q-OPS-4 (who runs COB, failure detection, recovery) | SRE + Application Owner | C | **Partially answered** | `fineract.job.stuck-retry-threshold=5` (`application.properties:79`) confirms stuck jobs are an anticipated operational event; the job registry is database-driven Quartz (8 main-source files import `org.quartz`) with no `@Scheduled` anywhere. | Who operates it, how failure is detected, and the manual recovery procedure. |
| Q-OPS-5 (maintenance windows) | SRE | P | External | Nothing. | Acceptable window for Aurora minor upgrades and ECS platform updates. |
| Q-OPS-6 (tenant onboarding runbook) | SRE + Application Owner | P | External | The repository documents developer-side database creation only (`README.md:58-59`, `./gradlew createDB -PdbName=...`). | The production runbook, and whether it assumes shell access to the database host. |
| Q-OPS-7 (budget, cost model) | Programme Sponsor + FinOps | P | External | Nothing. | Target monthly budget and whether a cost model is a sign-off artefact. |

---

## 7. Sizing inputs still missing

Four numbers block concrete sizing. Each one blocks a specific design choice, not the design in general.

### Q-DB-2 — live tenant count

Blocks: **Aurora instance class and `max_connections`; whether one cluster or several.**

Each tenant gets its own datasource through `RoutingDataSource`
(`fineract-core/src/main/java/org/apache/fineract/infrastructure/core/service/database/RoutingDataSource.java`),
and the shipped pool is 3 idle / 10 max per datasource
(`config/docker/env/fineract-common.env:23-24`). Worst-case connections are therefore
`10 × tenants × ECS tasks`, and the three ECS services in the target (api, cob-manager, cob-worker) each
carry their own pools. Without the tenant count the instance class is a guess, and the guess is the
difference between a `db.r6g.large` and a cluster that needs RDS Proxy in front of it.

### Q-DB-2 — largest tenant database size (rows and bytes)

Blocks: **migration mechanism and cutover freeze window.**

Under ~100 GB, `pg_dump`/`pg_restore` or AWS DMS full-load is a single freeze window; above that, DMS with
CDC and a rehearsed cutover is the only safe option, and that changes WS3's shape and effort, not just its
duration.

### Q-PLT-7 — peak and average API request rate

Blocks: **ECS task count, ALB target-group settings, autoscaling policy.**

The application exposes no capacity hints; `fineract-provider/build.gradle:293` says only that it listens on
8080/8443. `README.md:32` documents a *minimum host* of 16 GB / 8 vCPU, which is a floor for a whole node,
not a per-task sizing input, and it is far above the 1 vCPU / 2 GiB limit the Kubernetes manifest actually
sets (`kubernetes/fineract-server-deployment.yml:64-70`) — that gap alone shows the repository cannot size
the fleet.

### Q-PLT-7 — current COB window duration

Blocks: **cob-worker task count and the scale-out schedule.**

`LOAN_COB` runs chunk 100 / partition 100 with a 5-thread pool (`application.properties:88-95`). Mapping
that onto Fargate tasks requires knowing how long the window is today and what the deadline is; the
partition transport must also move off in-JVM Spring events (`application.properties:97`) at the moment the
worker becomes a separate task, so this number gates WS4 and WS5 together.

---

## 8. Defects and drift found while verifying (recorded, not fixed)

WS0 is verification only. Each item below is evidenced in `ws0-baseline.md` or in the table above and is
handed to the workstream that owns it.

1. **`current-state.md` §9 claims the `custom/` tree is empty here; it is not.** Eight sample Gradle modules
   exist under `custom/acme/**`, dynamically included by `settings.gradle:84-96`, with four Liquibase
   changelogs under `custom/*/*/*/src/main/resources/db/custom-changelog/`. Owner: WS0 (this document
   corrects it); consumers: WS3 (custom changelogs are schema).
2. **No Liquibase changelog in the repository declares a rollback** (0 `<rollback>` elements in 322
   changelog files). Owner: WS3/WS9 — rollback strategy.
3. **Actuator CORS is not environment-overridable** (`application.properties:341-343` are literals while
   every neighbouring property uses `${ENV:default}`). Owner: WS8 — it cannot be closed by configuration
   alone in AWS.
4. **`java.time.OffsetDateTime cannot be cast to java.time.LocalDate` in the trial-balance batch job under
   PostgreSQL** (`UpdateTrialBalanceDetailsTasklet.java:80`), which fails
   `SchedulerJobsTest.testTriggeringManualExecutionOfAllSchedulerJobs()` on unmodified `develop`
   (`ws0-baseline.md` §2.3, failure 1). This is a live PostgreSQL type-mapping defect on the engine the
   target state assumes. Whether it also fails in GitHub Actions was not checked from this session, so it
   is recorded as reproduced locally on the CI image and configuration. Owner: the dialect workstream.
5. **Three bulk-import endpoints return HTTP 500 on unmodified `develop`** (`ws0-baseline.md` §2.3,
   failures 2, 3 and 19). Recorded, not diagnosed.
6. All eleven counts asserted in `current-state.md` §§2–9 reproduce exactly (`ws0-baseline.md` §3). Two
   *statements* around them do not: the "34 modules" figure is really 41 Gradle projects, and the `custom/`
   tree is not empty. Neither changes a design decision; both change the credibility of the document if
   left uncorrected.
