# Open Questions — Fineract AWS Migration

Each question is a fact the repository cannot settle, phrased for one named owner. IDs are referenced from `target-state.md` and `migration-plan.md`. Nothing in the design is safe to sign off while a **Blocker** remains open.

Priority: **B** = blocks design sign-off, **C** = blocks cutover, **P** = plan-shaping.

---

## Programme

| ID | Question | Owner | Pri |
|---|---|---|---|
| Q-PRG-1 | The approved AWS service list was not supplied. Is the assumed list in `target-state.md` §0 the real one, and specifically: is **Amazon SES** in or out (the SMTP path at `ReportMailingJobEmailServiceImpl.java:94-96` has no in-list equivalent other than keeping an external relay)? | Cloud Architecture Lead | B |
| Q-PRG-2 | Confirm the resilience posture per environment: is prod single-region multi-AZ, or warm-standby multi-region? The answer changes whether Aurora Global Database and cross-region MSK replication are in scope. | Cloud Architecture Lead | B |
| Q-PRG-3 | Is this repository (`COG-GTM/fineract-Core-Banking`, branch `develop`, commit `8c187f9d1`) actually what runs in production, or is production a fork/release tag with local customisation? The `custom/` module tree (`settings.gradle:81-90`) exists but is empty here. | Application Owner | B |
| Q-PRG-4 | Are there customisations in the `custom/` extension points in the production build that are not in this repository? | Application Owner | B |
| Q-PRG-5 | What is the migration driver and the deadline — datacentre exit, licence renewal, or modernisation? This determines whether WS3 (engine change) is in the initial cutover or deferred. | Programme Sponsor | P |

## Database

| ID | Question | Owner | Pri |
|---|---|---|---|
| Q-DB-1 | Do we change engine to **PostgreSQL** (upstream-preferred, CI-tested at `.github/workflows/build-postgresql.yml:20`) or lift-and-shift onto **RDS for MariaDB**? This is the single largest scope decision in the programme. | DBA + Application Owner | B |
| Q-DB-2 | How many tenants are live, and what is the row count and on-disk size of the largest tenant database? Hikari is configured at 3–10 connections per tenant datasource (`config/docker/env/fineract-common.env:23-24`), so tenant count sets Aurora instance sizing directly. | DBA | B |
| Q-DB-3 | What is the production MariaDB/MySQL version actually running? The repository contradicts itself: `README.md:33` requires ≥ 11.5.2, `config/docker/compose/mariadb.yml:21` and `kubernetes/fineractmysql-deployment.yml:89` pin 11.4. | DBA | B |
| Q-DB-4 | What are the RTO and RPO for the core banking database, and what is the maximum acceptable cutover freeze window? | Application Owner + Business Continuity | C |
| Q-DB-5 | Is any downstream system (reporting, warehouse, regulatory extract) reading the Fineract database directly rather than through the API? Direct readers break silently on an engine change. | DBA + Data Platform | B |
| Q-DB-6 | Is the tenant master password (`application.properties:206`, default `fineract`) currently the default in production? Per-tenant credentials are decrypted with it. | DBA + Security | C |
| Q-DB-7 | Do any operational runbooks depend on MariaDB-specific tooling (`mariadb-dump`, `mariabackup`, MaxScale)? | DBA | P |

## Platform

| ID | Question | Owner | Pri |
|---|---|---|---|
| Q-PLT-1 | Is production on Kubernetes today (matching `kubernetes/`), on Docker Compose, on VMs, or on something else entirely? The repository contains manifests for all of the container options and no evidence of which is used. | Platform Engineering | B |
| Q-PLT-2 | Confirm **ECS Fargate** as the compute target. If EKS is the org standard, the manifests in `kubernetes/` become the starting point instead and the compute rows of the target-state table change. | Platform Engineering | B |
| Q-PLT-3 | Which IaC tool is the standard — Terraform, CDK or CloudFormation? No IaC exists in this repository. | Platform Engineering | B |
| Q-PLT-4 | Where does TLS terminate in the target: at the ALB with an ACM certificate, or end-to-end to the task using the current keystore (`fineract-provider/src/main/resources/keystore.jks`)? Regulatory guidance on in-VPC encryption decides this. | Platform Engineering + Security | B |
| Q-PLT-5 | What is on the document-store filesystem today (`application.properties:187`), how large is it, and does anything other than Fineract read it? This sets the S3-vs-EFS decision. | Application Owner + Platform Engineering | B |
| Q-PLT-6 | Are external events currently consumed by anyone? Both the JMS and Kafka producers default to disabled (`application.properties:129,140`); if a downstream consumer exists in production, the config in this repository does not describe production. | Application Owner + Integration | B |
| Q-PLT-7 | Peak and average API request rate, and the duration of the current COB window. Autoscaling policies and worker task counts are unsizeable without them. | Application Owner + SRE | P |
| Q-PLT-8 | Is egress to the SMS gateway and the SMTP relay allowed from a NAT Gateway, or must it traverse an on-premises proxy / Direct Connect? | Network Engineering | C |
| Q-PLT-9 | Is a hybrid period required — will the AWS deployment need to reach on-premises systems (or vice versa) during migration? | Network Engineering | P |

## Delivery

| ID | Question | Owner | Pri |
|---|---|---|---|
| Q-DEL-1 | **How is this application deployed today?** The pipeline in this repository ends at a Docker Hub push (`.github/workflows/publish-dockerhub.yml:43-48`); there is no deployment stage, IaC or CD configuration anywhere. Whatever performs the deploy is invisible to this analysis. | Release Manager | B |
| Q-DEL-2 | Which CD tool should the target use — CodePipeline, Harness, Argo CD, or something else? `target-state.md` assumes CodePipeline only so the diagram is complete. | Release Manager + Platform Engineering | B |
| Q-DEL-3 | Which image is authoritative in production: the upstream `apache/fineract:latest` referenced at `kubernetes/fineract-server-deployment.yml:63`, or an internally built one? If it is upstream `latest`, production content is not reproducible from any commit. | Release Manager | B |
| Q-DEL-4 | What is the release cadence and the change-approval process for a production deployment (CAB, freeze windows)? | Release Manager | P |
| Q-DEL-5 | Who owns the `DOCKERHUB_USER` / `DOCKERHUB_TOKEN` secrets (`.github/workflows/publish-dockerhub.yml:44-45`), and do they need to survive the move to ECR? | Release Manager + Security | P |
| Q-DEL-6 | Is a rollback expected to be an image rollback only, or must schema changes be reversible too? Liquibase changelogs here have no declared rollbacks in the master path. | Release Manager + DBA | C |

## Security

| ID | Question | Owner | Pri |
|---|---|---|---|
| Q-SEC-1 | A database password literal is committed at `config/docker/env/fineract-common.env:31,51`. Is that value used in any real environment, and if so, when will it be rotated and the history scrubbed? | Security | C |
| Q-SEC-2 | Is production authenticating with HTTP Basic (`application.properties:24`, default on) or OAuth2 (`:25`, default off)? If Basic, is moving to Cognito in scope for this programme or a follow-on? | Security + Application Owner | B |
| Q-SEC-3 | CORS is wide open by default — `*` origins with `allow-credentials=true` (`application.properties:30-35`) and `*` on the actuator (`:341-343`). What is the actual allowed-origin list for production? | Security | C |
| Q-SEC-4 | `fineract.insecure-http-client` defaults to `true` (`application.properties:221`), disabling outbound TLS verification. Confirm this is `false` in production, and that it will be `false` in AWS. | Security | C |
| Q-SEC-5 | Three public MSK broker endpoints in `eu-central-1` are committed at `config/docker/env/kafka-client-msk.env:23,31`. Is that a live cluster owned by the customer, and if so, why is it publicly reachable? | Security + Platform Engineering | C |
| Q-SEC-6 | Is the committed self-signed keystore (`fineract-provider/src/main/resources/keystore.jks`, password in plain text at `application.properties:391`) served to any real client today? | Security | C |
| Q-SEC-7 | Which compliance regimes apply (PCI DSS, SOC 2, local banking regulation), and do they mandate a dedicated account, specific KMS key policies, or data residency? | Security + Compliance | B |
| Q-SEC-8 | Is two-factor authentication (`application.properties:26`, default off) required for any user population? | Security | P |
| Q-SEC-9 | Who may hold break-glass access to the production Aurora cluster, and through what mechanism (SSM Session Manager, bastion, none)? | Security + Platform Engineering | C |

## Operations

| ID | Question | Owner | Pri |
|---|---|---|---|
| Q-OPS-1 | What is the current backup regime and retention for the core banking database, and what must AWS Backup reproduce? | SRE + DBA | C |
| Q-OPS-2 | Which alerts exist today and who receives them? The repository ships Prometheus, CloudWatch and OTLP exporters (`config/docker/env/prometheus.env:20`, `cloudwatch.env:20-23`, `oltp.env:20-22`) but no alert rules. | SRE | C |
| Q-OPS-3 | Is a log-retention period mandated, and must logs be immutable or exportable for audit? | SRE + Compliance | P |
| Q-OPS-4 | Who runs the COB job today, how is failure detected, and what is the manual recovery procedure? `fineract.job.stuck-retry-threshold` is 5 (`application.properties:79`), which implies stuck jobs are a known operational event. | SRE + Application Owner | C |
| Q-OPS-5 | What is the acceptable maintenance window for Aurora minor-version upgrades and ECS platform updates? | SRE | P |
| Q-OPS-6 | Is there an existing runbook for tenant onboarding, and does it assume shell access to the database host? | SRE + Application Owner | P |
| Q-OPS-7 | What is the target monthly infrastructure budget, and is a cost model expected as part of design sign-off? | Programme Sponsor + FinOps | P |
