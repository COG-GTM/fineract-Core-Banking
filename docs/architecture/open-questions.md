# Open Questions for the Architecture Review

Everything below is something that **could not be determined from this repository**. Each item is
addressed to a role, states why it matters, and names the evidence that raised it. Answers should be
recorded inline so this file becomes the decision log.

---

## 0. Blocking input

**Q0 — Programme owner / cloud platform lead:** the approved AWS service list was supplied as the
literal placeholder `[PASTE BLESSED SERVICE LIST]`. `target-state.md` is written against an assumed
list and is therefore **not review-ready until you provide the real one**. Which services are
approved, and are Amazon MQ, Amazon Managed Prometheus/Grafana, AWS Glue Schema Registry and Amazon
OpenSearch in or out?

---

## 1. Production topology and release path — for the **platform / SRE team**

1. There is no Jenkinsfile, Helm chart, Kustomize overlay, Terraform or Harness pipeline in this
   repo — only GitHub Actions that push `apache/fineract` to Docker Hub
   (`.github/workflows/publish-dockerhub.yml:43-48`). **Where does the production deployment
   definition live today, and who owns it?**
2. **How does an image actually reach production right now** — manual `kubectl apply` using
   `kubernetes/fineract-server-deployment.yml`, a Compose file on a VM, or a WAR dropped into an
   external Tomcat? All three paths are documented in the repo and we cannot tell which is real.
3. **How many environments exist** (dev/test/UAT/pre-prod/prod), and what is the current promotion
   and change-approval process for each?
4. `kubernetes/fineract-server-deployment.yml:63` pins `apache/fineract:latest`. **Is production
   running a floating tag, or is a pinned digest used?** This determines whether we can reconstruct
   what is running today.
5. The manifests run MySQL as an in-cluster Deployment (`kubernetes/fineractmysql-deployment.yml`).
   **Is production really running its database inside the cluster, or is that dev-only?**
6. **What are the current production instance counts and sizes** per mode (API, batch manager,
   batch worker)? The k8s manifest says 1 replica, 1 vCPU / 2Gi
   (`kubernetes/fineract-server-deployment.yml:64-70`) — almost certainly not production truth.
7. **Is there any WAF, API gateway, or reverse proxy in front of Fineract today**, and what TLS
   policy/cert authority does it use?
8. **Are the custom modules under `custom/acme/**` deployed to production**, and are there other
   private plugin JARs dropped into the `/app/plugins/*` classpath
   (`fineract-provider/build.gradle:286`) that are not in this repo?

## 2. Database — for the **DBA**

9. **Which engine and exact version is production on** — MariaDB 11.4, MySQL 8, or PostgreSQL? The
   repo supports all three; `spring.datasource.hikari.driverClassName` defaults to MariaDB
   (`application.properties:405`).
10. **How many tenants are live, and what is the size of each tenant database** (total GB, largest
    tables, row counts for `m_loan`, `m_loan_transaction`, `m_journal_entry`, `m_savings_account_transaction`)?
    Nothing in the repo indicates data volume, and this drives the DMS sizing and cutover window.
11. **What is the acceptable cutover downtime and the required RPO/RTO?** Core banking usually has
    a hard end-of-day boundary; the COB job makes this worse.
12. **Are there MySQL/MariaDB-specific constructs in production that are not in the repo** —
    stored procedures, triggers, events, views, or scheduled `EVENT`s? We found **no** stored
    procedures in source, but report definitions and hooks are stored as **table rows**, so
    production may contain SQL we have never seen.
13. **Have all custom report definitions in `stretchy_report` been validated against PostgreSQL?**
    They are executed dynamically and are only guarded by regex filters
    (`application.properties:230-319`).
14. **Does any downstream system read the Fineract database directly** (BI, regulatory reporting,
    ETL)? If so, those consumers must be part of the Postgres cutover.
15. **Is there a read-only replica in use today** (`application.properties:56-61`), and which
    workloads depend on it?
16. **What are the current backup, PITR and retention settings, and what has been tested for
    restore?**
17. **Character set / collation and timezone:** the default tenant timezone in the repo is
    `Asia/Kolkata` (`application.properties:49`). **What does production use, and are dates stored
    in UTC?**

## 3. Integrations — for the **application owner**

18. **What is the real SMS/message-gateway endpoint?** The URI is assembled from database
    configuration at call time (`SmsMessageScheduledJobServiceImpl.java:69-74`), so we cannot see
    it. Who hosts it, is it internal, and does it need VPC connectivity or an allow-listed egress IP?
19. **Is the Twilio SMS bridge in use in production, and where does it run**
    (`TwilioHookProcessor.java:44,75,92`)?
20. **Which web hooks are configured per tenant, and what are their destination URLs?** These are
    rows in the hook tables, so migration means these URLs must keep working and the source IP will
    change to our NAT gateway addresses — **do any partners IP-allow-list us?**
21. **Is the Elasticsearch hook processor used** (`ElasticSearchHookProcessor.java:38-70`), and is
    the target cluster ours or a third party's?
22. **What SMTP relay is used in production** and from which sender domain? The code defaults to
    Gmail-style settings held in `m_report_mailing_job_configuration`
    (`ReportMailingJobEmailServiceImpl.java:60-66`). Moving to SES needs the domain, DKIM/SPF, daily
    volume, and production-access approval.
23. **What is the expected daily e-mail and SMS volume?** Needed for SES/SNS quota requests.
24. **Are external business events enabled in production** (`fineract.events.external.enabled`
    defaults to `false`, `application.properties:124`), and if so on Kafka or JMS, with which
    consumers? Consumer teams must be in the cutover plan.
25. **Are there any file-based integrations** — SFTP drops, NFS/SMB shares, nightly CSV exchanges
    with a core ledger, payment scheme or bureau? **We found no FTP or file-share client anywhere in
    `src/main`**, which is surprising for a core banking platform, so we assume this happens in a
    system outside this repo. Please confirm or list them.
26. **Is the Interoperability (Mojaloop) API in use** (`interoperation/api/InteropApiResource.java`)?
27. **Is Pentaho reporting live?** The loader is commented out
    (`dataqueries/service/ReadReportingServiceImpl.java:516`) but the file path
    `${FINERACT_BASE_DIR}/pentahoReports` implies report files on disk somewhere.
28. **Which clients call the API** (Mifos web-app, community-app, mobile, partner systems), and are
    any of them hardcoded to the current hostname or IP?

## 4. Scheduling and operations — for the **operations / batch team**

29. **Which of the 38 Quartz jobs are actually enabled per tenant, on what cron, and in which
    timezone** (`JobName.java:21-59`)? Job schedules live in the database.
30. **Is there any external cron, Control-M, Autosys or Lambda that calls Fineract APIs or runs
    scripts against its database?** Nothing in the repo triggers Fineract externally, so external
    orchestration would be invisible to us.
31. **Is Quartz running in clustered mode in production?** If not, more than one batch-manager
    instance would double-fire jobs — this constrains the ECS service to exactly one task
    (target-state #3).
32. **What is the COB batch window, its current runtime, and the SLA for completing it?** Loan COB
    is partitioned (`application.properties:88-95`) and its runtime is the main cutover constraint.
33. **How many batch workers run today, and what are the current `LOAN_COB_CHUNK_SIZE` /
    `PARTITION_SIZE` values in production?** The repo's dev values are 10/10
    (`config/docker/env/fineract-manager.env`).
34. **Who is paged when a job fails today, and what monitoring exists?** The repo only shows a
    local Prometheus/Grafana/Loki/Tempo stack (`config/docker/compose/observability.yml`).

## 5. Content, storage and state — for the **application owner + platform**

35. **How much document/image content exists and where does it live today?** The filesystem content
    store is the default (`application.properties:186-187`) and writes under `${user.home}/.fineract`
    — in the container that resolves to `/tmp`
    (`fineract-provider/build.gradle:288`, `config/docker/env/fineract-common.env:57`). **Is
    production writing documents to an ephemeral or a mounted volume, and is anything already on S3?**
36. **Is the command file dead-letter queue enabled in production**
    (`application.properties:617-618`), and if so has anything ever landed in
    `/tmp/fineract-command-audit`?
37. **What retention and legal-hold rules apply to stored documents?** Drives S3 lifecycle,
    versioning and Object Lock decisions.

## 6. Security and identity — for the **security team / IAM owner**

38. **Which authentication mode is production using — Basic auth or OAuth2 JWT?** OAuth2 defaults to
    disabled (`application.properties:25`) and Basic auth is the enabled default
    (`SecurityConfig.java:81`).
39. **If OAuth2 is on, who is the issuer?** The repo ships a self-hosted authorization server
    (`AuthorizationServerConfig.java:90`) with a dev redirect URI of `http://localhost:3000/callback`
    (`application.properties:41`).
40. **Is two-factor authentication enabled** (`application.properties:26`), and over SMS or e-mail?
41. **The credentials committed to this repo must be treated as compromised** — the DB password and
    tenant master password in `config/docker/env/fineract-common.env:31,51,52`, the keystore
    password default `openmf` (`application.properties:391`), and the LocalStack keys in
    `config/docker/aws/etc/credentials`. **Are any of these values reused in a real environment?**
    If yes, rotate before migration, not during.
42. **What is the current tenant master password / encryption key custody process**
    (`application.properties:53,206`), and who can rotate it? Rotation re-encrypts stored tenant DB
    credentials, so it needs a runbook.
43. **What is production's CORS policy?** The default is permissive origin patterns with
    `allow-credentials=true` (`application.properties:30-35`).
44. **What compliance regimes apply** (PCI-DSS, local banking regulator, data residency)? Data
    residency directly constrains the multi-region prod design.
45. **Is there an existing IdP** (Okta, Entra, Ping) that Cognito would have to federate with, or is
    Cognito acceptable as the primary user store?

## 7. Networking — for the **network team**

46. **Which AWS accounts, VPCs and CIDR ranges are we landing in**, and is there an existing
    Transit Gateway / Direct Connect to on-prem systems the SMS gateway or core ledger sit behind?
47. **Are there fixed egress IP requirements** from any partner (see Q20)? That decides NAT Gateway
    EIP allocation per region.
48. **What DNS names do clients use today**, and can we take over those names in Route 53 or must we
    CNAME to an existing corporate zone?
49. `kubernetes/fineract-server-deployment.yml:60` hardcodes the hostname `fineractmysql:3306` in an
    init-container. **Are there other hardcoded hostnames or IPs in the deployment tooling outside
    this repo?**
