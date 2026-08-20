# Open Questions

Every fact this repository cannot settle. Each question is addressed to a named role and carries an ID so
the workstreams in `migration-plan.md` can reference it. Questions marked **BLOCKER** must be answered
before the workstream that depends on them can start.

## Programme

| ID | Question | Owner | Why it matters | Blocks |
| --- | --- | --- | --- | --- |
| OQ-P1 | **BLOCKER** The approved ("blessed") AWS service list was not supplied. Is the assumed allowlist in `target-state.md` correct, and which services must be removed or added? | Platform Engineering | Every target-state row is constrained by it; an SCP-enforced allowlist that excludes, say, Aurora or Amazon MQ changes the design, not just the wording. | WS0 |
| OQ-P2 | **BLOCKER** No customer platform guardrails were supplied, so the default set (G1–G10) is documented as an assumption. Do you have your own guardrail standard that replaces it? | Platform Engineering | The guardrail-compliance table is the review artefact; assessing against the wrong guardrails invalidates it. | WS0 |
| OQ-P3 | Is ECS Fargate approved and in use, or does the organisation standardise on EKS? If EKS, is there a shared platform cluster this workload joins, or a dedicated one? | Platform Engineering | Decides the compute mapping and roughly a session of Terraform work either way. | WS3 |
| OQ-P4 | What is the account-vending lead time and approval path for the dev, QA and prod accounts, and who requests them? | Platform Engineering | This is an external wait, not engineering effort; it usually sets the earliest possible start date. | WS2 |
| OQ-P5 | What is the policy for third-party base images? The build uses `azul/zulu-openjdk-alpine:21` (`fineract-provider/build.gradle:266`) — must it be mirrored into an approved registry, or replaced with an approved base? | Platform Engineering | G9 compliance and a build change. | WS5 |
| OQ-P6 | Is `COG-GTM/fineract-Core-Banking` the repository that is actually deployed, or is a fork/vendor distribution deployed and this repo used for development? | Application Owner | The whole current-state analysis is scoped to this repo; a different deployed artefact invalidates the release-flow findings. | WS0 |
| OQ-P7 | Is there an existing production Fineract deployment being migrated, or is this a greenfield AWS build of the platform? | Application Owner | Determines whether WS4 is a data migration with a cutover window or a schema bootstrap. | WS4 |

## Database

| ID | Question | Owner | Why it matters | Blocks |
| --- | --- | --- | --- | --- |
| OQ-DB1 | **BLOCKER** Is production on MariaDB or PostgreSQL today, at what exact version? The README requires MariaDB ≥ 11.5.2 (`README.md:33`) but every manifest in the repo pins `mariadb:11.4` (`kubernetes/fineractmysql-deployment.yml:89`). | DBA | MariaDB → PostgreSQL is the highest-risk workstream (WS4); if production is already PostgreSQL, that workstream shrinks to a managed-service move. | WS4 |
| OQ-DB2 | How many tenants exist, and what is the total and largest-tenant data volume? The platform creates one database per tenant (`TenantDatabaseUpgradeService.java:138`). | DBA | Sets the DMS sizing, the migration window and whether tenants can be cut over in waves. | WS4 |
| OQ-DB3 | What is the acceptable cutover downtime for the database, and is a wave-by-wave tenant cutover acceptable, or must all tenants move at once? | Application Owner | Determines whether DMS CDC (near-zero downtime) is required or a dump/restore window suffices. | WS4, WS9 |
| OQ-DB4 | Are there reporting tools, extracts, BI jobs or downstream consumers reading the Fineract database directly? | DBA | Direct readers are invisible in this repo and are the classic cutover surprise; they also constrain the engine change. | WS4 |
| OQ-DB5 | Are there customer-authored reports in `stretchy_report` or custom datatables containing engine-specific SQL? Fineract stores report SQL as data, so the repository cannot show it. | Application Owner | Engine-specific report SQL stored in the database will not be caught by the code-level "zero stored procedures" finding. | WS4 |
| OQ-DB6 | Is Aurora PostgreSQL acceptable, or must this be RDS PostgreSQL? | DBA | Aurora Global Database is the basis of the multi-region prod posture in G7; RDS changes the DR design. | WS3 |
| OQ-DB7 | Confirmed RPO and RTO for the banking data? | Application Owner | Drives backup frequency, PITR retention and whether a warm standby is sufficient. | WS8 |

## Platform / application

| ID | Question | Owner | Why it matters | Blocks |
| --- | --- | --- | --- | --- |
| OQ-A1 | **BLOCKER** Is running exactly one write/manager instance acceptable, or must the write tier scale horizontally? Today Quartz uses the default in-memory job store (`JobRegisterServiceImpl.java:319-331`) and the cache manager throws `UnsupportedOperationException` for `MULTI_NODE` (`RuntimeDelegatingCacheManager.java:114`). | Application Owner + Platform Engineering | If horizontal write scaling is required, application work (clustered Quartz JDBC job store, shared cache) must be funded before prod, and it is not in scope of this migration. | WS3, WS9 |
| OQ-A2 | What are the peak and average API request rates, concurrent users, and the COB batch window and duration? | Application Owner | Nothing in the repo sizes the workload; Fargate task sizing, Aurora instance class and autoscaling thresholds all depend on it. | WS3 |
| OQ-A3 | Are external business events and remote COB partitioning actually enabled in production? Both default to off (`application.properties:97-145`). If enabled, is the broker ActiveMQ or Kafka, and who consumes the events? | Application Owner | Decides Amazon MQ vs. MSK, and whether a broker is needed at all. | WS3 |
| OQ-A4 | Where does document content live today, and how much of it is there? The default is local disk (`application.properties:186-187`), overridden to `/tmp` in containers (`config/docker/env/fineract-common.env:57`). | Application Owner | This is the only non-database state; if it is on a real volume it must be copied to S3 during cutover. | WS4, WS9 |
| OQ-A5 | Which SMS gateway and SMTP relay are used in production, and what are their egress endpoints? The code creates a `RestTemplate` with no timeouts (`SmsMessageScheduledJobServiceImpl.java:62`). | Application Owner | Needed for NAT egress allowlisting, and the missing timeout is a cutover risk on a batch job. | WS3, WS9 |
| OQ-A6 | Are there Pentaho report plugins or other jars loaded from `/app/plugins/*` (`fineract-provider/build.gradle:286`) in production? | Application Owner | Plugin jars are outside this repository and would need their own supply-chain path under G9. | WS5 |
| OQ-A7 | Is the Mifos community-app web UI (`kubernetes/kubectl-startup.sh:44-48`) part of the production estate, and therefore of this migration? | Application Owner | It is a separate deployable with its own ingress, TLS and CORS implications. | WS3 |

## Delivery

| ID | Question | Owner | Why it matters | Blocks |
| --- | --- | --- | --- | --- |
| OQ-D1 | **BLOCKER** Which CD tool is approved (Harness, Argo CD, GitHub Actions deploy, Spinnaker, other)? There is no deployment workflow in this repository at all. | Release Manager | WS6 cannot be designed, and G6 cannot be assessed, without it. | WS6 |
| OQ-D2 | How does a release currently reach production? The repo stops at a Docker Hub push (`.github/workflows/publish-dockerhub.yml:38-48`); the only deployment artefact is a manual `kubectl apply` script. | Release Manager | The as-is release path is a current-state fact the repo cannot supply, and it defines what "migrating off legacy pipelines" means. | WS6 |
| OQ-D3 | Are GitHub Actions self-hosted runners with VPC access available, or must the CD tool run deployments? Is GitHub OIDC federation to AWS IAM already established? | Platform Engineering | Determines how ECR pushes and ECS deployments authenticate without static keys (G8). | WS5, WS6 |
| OQ-D4 | Who approves promotion into QA and prod, and is there a change-management window? | Release Manager | Gates WS7 and WS9 and is a scheduling input, not an engineering one. | WS9 |
| OQ-D5 | Is the ~5-hour full CI matrix acceptable as a release gate, or must the deployment pipeline run a reduced suite? | Release Manager | Affects pipeline design and release cadence. | WS6 |

## Security

| ID | Question | Owner | Why it matters | Blocks |
| --- | --- | --- | --- | --- |
| OQ-S1 | **BLOCKER** Were any of the credentials committed in this repository ever used in a real environment (`config/docker/env/fineract-common.env:31`, `:51-52`, `config/docker/env/mariadb.env:20`, `mysql.env:20`, `postgresql.env:21-23`, `application.properties:391`)? | Security | Determines whether WS1 is a rotation exercise or a security incident with a forensic scope. Values are public in git history regardless. | WS1 |
| OQ-S2 | SMTP credentials are read from the tenant database at send time (`GmailBackedPlatformEmailService.java:57-64`). Is storing outbound credentials in application tables acceptable, or must they move to Secrets Manager? | Security | Changing it is application work outside this migration; accepting it is a documented risk. | WS1 |
| OQ-S3 | Is the API allowed to be internet-facing at all? G8 assumes private by default; the current manifests expose a `LoadBalancer` service directly (`kubernetes/fineract-server-deployment.yml:26-34`). | Security | Decides internal vs. internet-facing ALB, and whether WAF and Shield Advanced are required. | WS3 |
| OQ-S4 | Which authentication mode is used in production — HTTP Basic (default, `application.properties:24`), OAuth2, or both — and which identity provider backs it? | Security | OAuth2 changes the edge design and the CI mock-server assumptions. | WS3 |
| OQ-S5 | What are the acceptable CORS origins per environment? The default is `*` with credentials allowed (`application.properties:30-35`). | Security | Wildcard CORS with credentials on a banking API is not defensible in prod. | WS7 |
| OQ-S6 | What is the data classification of Fineract data, and are there residency constraints that limit the DR region? | Security | Constrains the multi-region posture required by G7. | WS8 |
| OQ-S7 | Are the MSK broker endpoints committed at `config/docker/env/kafka-client-msk.env:23` real infrastructure? They are configured on the public listener port 9198. | Security | If real, a publicly reachable broker is an immediate finding independent of this migration. | WS1 |
| OQ-S8 | Which KMS key strategy applies — AWS-managed, account CMK, or per-workload CMK with a defined key policy? | Security | Terraform module design for every encrypted resource. | WS3 |

## Operations

| ID | Question | Owner | Why it matters | Blocks |
| --- | --- | --- | --- | --- |
| OQ-O1 | **BLOCKER** What is the required resilience posture for prod — multi-AZ single region, active/passive multi-region, or active/active? G7 assumes multi-region. | Platform Engineering | Determines whether WS8 builds a warm standby or a second live stack, and roughly doubles or halves that workstream. | WS8, WS9 |
| OQ-O2 | What is the mandatory tag taxonomy and the cost-centre code for this workload? | Platform Engineering | G10 compliance; Terraform default tags cannot be written without it. | WS2 |
| OQ-O3 | Which central observability platform receives logs and metrics — CloudWatch alone, Managed Grafana/Prometheus, or a third-party SIEM? The app currently supports Prometheus scrape, CloudWatch push and OTLP (`application.properties:349-367`). | Platform Engineering | Picks the exporter configuration and the alerting home. | WS3 |
| OQ-O4 | What are the SLOs and the alerting/on-call arrangement for the migrated service? | Application Owner | Nothing in the repo defines availability targets; they gate the prod entry review. | WS9 |
| OQ-O5 | What timezone must the platform run in? The image forces UTC (`fineract-provider/build.gradle:290`) but the CI matrix runs `TZ: Asia/Kolkata` (`.github/workflows/build-postgresql.yml:37`) and the README warns about timezone handling (`README.md:291-327`). | Application Owner | Banking date boundaries and COB correctness depend on it. | WS4 |
| OQ-O6 | Who operates the platform after cutover, and does that team already run ECS/Aurora workloads? | Platform Engineering | Determines runbook depth and whether managed-service choices match existing operational skills. | WS9 |
