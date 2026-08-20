# Open Questions

Facts the repository cannot settle. Each question names the role that owns the answer and is
referenced by the target state and the migration plan. Nothing in the plan past WS1 should be
scheduled before the programme and platform questions are closed.

## Programme

| ID | Question | Owner |
| --- | --- | --- |
| Q-PROG-01 | Is this fork intended to track Apache Fineract `develop` after migration, or to diverge? The answer decides whether target-state changes go in this repo or in an overlay (`custom/` already exists as an extension point). | Application Owner |
| Q-PROG-02 | Which artefact is actually deployed in production today — the Jib image published to Docker Hub (`.github/workflows/publish-dockerhub.yml:47`), a locally built JAR, or the WAR on Tomcat? The repository supports all three. | Release Manager |
| Q-PROG-03 | Which optional modules are live? `fineract.module.self-service` defaults off, `investor` and `loan-origination` default on (`fineract-provider/src/main/resources/application.properties:217-219`); each changes the API surface that must be regression-tested. | Application Owner |
| Q-PROG-04 | How many tenants exist and what is the largest tenant's data volume? Tenant count drives the single-threaded Liquibase upgrade window (`application.properties:157-160`) and the Aurora connection budget. | Application Owner |

## Platform

| ID | Question | Owner |
| --- | --- | --- |
| Q-PLAT-01 | The approved AWS service list used in target-state.md is an assumption. Please confirm it or supply the real allowlist, including whether CloudFront and Amazon OpenSearch Service are permitted — both were rejected as `OFF-LIST?` and replaced. | Platform Engineering |
| Q-PLAT-02 | Confirm or replace the assumed default guardrail set (G1–G10) and the assumed resilience posture: multi-AZ in dev/QA, multi-region in prod. | Platform Engineering |
| Q-PLAT-03 | What is the account-vending lead time through Control Tower, and can dev, QA and prod accounts be requested in parallel? Everything after WS1 waits on the dev account. | Platform Engineering |
| Q-PLAT-04 | What is the mirroring policy for third-party base images? The build pulls `azul/zulu-openjdk-alpine:21` directly (`fineract-provider/build.gradle:266`) and `busybox:1.28` is used as an init container (`kubernetes/fineract-server-deployment.yml:59`). | Platform Engineering |
| Q-PLAT-05 | What are the mandatory tag keys and their allowed values (owner, cost centre, environment, data classification)? | Platform Engineering |
| Q-PLAT-06 | Is EKS the standard container platform, or is ECS Fargate preferred for a workload with one singleton pod and one horizontally scaled pod? | Platform Engineering |

## Database

| ID | Question | Owner |
| --- | --- | --- |
| Q-DB-01 | Confirm Aurora PostgreSQL as the target engine. The application supports PostgreSQL, but the current manifests are MariaDB (`kubernetes/fineractmysql-deployment.yml:89`) and the engine change is the single largest risk in the programme. | DBA |
| Q-DB-02 | Is there any object outside Liquibase's control in the production databases — views, triggers, custom reports registered in `stretchy_report`, tenant-specific datatables? Liquibase owns 237 changelog files, but datatables are created at runtime through the API. | DBA |
| Q-DB-03 | What is the acceptable cutover downtime, and is AWS DMS with CDC required, or is a maintenance-window dump/restore acceptable? | DBA |
| Q-DB-04 | Is a read-only replica in use? The application supports one per tenant (`application.properties:56-61`) but no manifest configures it. | DBA |
| Q-DB-05 | Who owns the tenant master password used to encrypt tenant DB credentials (`application.properties:53`), and how is it rotated? Its default value is committed. | DBA / Security |

## Delivery

| ID | Question | Owner |
| --- | --- | --- |
| Q-DEL-01 | Is Harness the delivery tool, and are there existing pipeline templates for EKS deployments to reuse? There is no CD in the repository, so this is greenfield. | Release Manager |
| Q-DEL-02 | Does the organisation require signed images and an SBOM gate? The build already produces a CycloneDX SBOM (`build.gradle:128`) but nothing consumes it. | Release Manager |
| Q-DEL-03 | Can GitHub Actions reach AWS through OIDC, or must builds move to an internal runner fleet? | Platform Engineering |
| Q-DEL-04 | Which environments need the full CI matrix? Today MariaDB, MySQL and PostgreSQL are each built and tested separately; after the engine decision, two of the three become dead cost. | Release Manager |

## Security

| ID | Question | Owner |
| --- | --- | --- |
| Q-SEC-01 | The repository contains default credentials in `config/docker/env/*.env` and a keystore password in `application.properties:391`. Were any of these values ever used outside local development? If so they need revocation, not just replacement. | Security |
| Q-SEC-02 | Is HTTP Basic authentication acceptable in the target, or must OAuth2 (already implemented, disabled by default at `application.properties:25`) be enabled at cutover? | Security |
| Q-SEC-03 | Is two-factor authentication required for platform users (`application.properties:26`, disabled today)? It requires a working SMS/email path in AWS. | Security |
| Q-SEC-04 | What is the data classification of documents in the content store, and does it permit S3 with SSE-KMS in the same account as the application? | Security |
| Q-SEC-05 | Are there network egress restrictions for the SMS gateway, Twilio and generic web hooks (`infrastructure/hooks/processor/`), or will a NAT gateway with an allowlist suffice? | Security |

## Operations

| ID | Question | Owner |
| --- | --- | --- |
| Q-OPS-01 | What are the RTO and RPO targets? They determine whether prod needs Aurora Global Database with a warm standby region or a backup-restore posture. | Application Owner |
| Q-OPS-02 | What is the close-of-business batch window, and what is the current COB runtime for the largest tenant? Worker sizing and the promotion freeze window depend on it. | Application Owner |
| Q-OPS-03 | Who owns the SMS gateway integration and is that provider staying? Four `RestTemplate` call sites depend on it. | Application Owner |
| Q-OPS-04 | Which observability platform is central — CloudWatch alone, Amazon Managed Grafana, or an existing third-party tool? The repo's Grafana/Loki/Tempo stack is development-only. | Platform Engineering |
| Q-OPS-05 | Are the real production hostnames, DNS zones and certificate issuance process available for Route 53 and ACM planning? The only hostnames in the repo are the demo MSK brokers (`config/docker/env/kafka-client-msk.env:23`). | Platform Engineering |
