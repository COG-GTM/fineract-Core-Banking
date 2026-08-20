# WS0 — Green baseline on unmodified `develop`

This is the reference measurement every other workstream cites when it claims a failure is pre-existing.
It was produced on unmodified `develop` — no application code, build file, workflow or manifest was changed
before or during the run.

- Repository: `COG-GTM/fineract-Core-Banking`
- Branch: `develop`
- Commit: `8c187f9d17fb839f26fc888f34a64f7c1f57a802` (`8c187f9d1`, "FINERACT-2471: Implement 'Force Debit'
  functionality for Savings Accounts with Configurable Limits") — the analysis commit named in the
  architecture package
- Working tree: clean except for the two new files under `docs/migration/`

---

## 1. Environment

| Item | Value | How it was obtained |
|------|-------|---------------------|
| OS | Ubuntu 22.04, x86_64, 8 vCPU, 31 GiB RAM | `nproc`, `free -g` |
| JDK | OpenJDK 21.0.11+10 (Ubuntu build) | `java -version`. CI uses Zulu 21 (`.github/workflows/build-postgresql.yml:52-56`); the toolchain requirement in `build.gradle:392-397` is `JavaLanguageVersion.of(21)`, which both satisfy. Recorded as the one deviation from CI. |
| Gradle | 8.14.3 (project wrapper) | `./gradlew --version` |
| Docker | 27.4.1 | `docker --version` |
| PostgreSQL | `postgres:18.3` container — `postgres (PostgreSQL) 18.3 (Debian 18.3-1.pgdg13+1)` | `docker exec postgres postgres --version`. Same image as CI (`.github/workflows/build-postgresql.yml:20`). |
| LocalStack | `localstack/localstack:2.1` | Same image as CI (`.github/workflows/build-postgresql.yml:84`) |
| Mock OAuth2 | `ghcr.io/navikt/mock-oauth2-server:3.0.1` on 9000 | Same image and `JSON_CONFIG` as CI (`.github/workflows/build-postgresql.yml:26-32`) |
| MariaDB | not used | The baseline follows the PostgreSQL workflow, which is the CI job the architecture package cites and the engine the target state assumes |

Environment variables for the unit run, taken verbatim from `.github/workflows/build-postgresql.yml:34-44`:

```
TZ=Asia/Kolkata
AWS_ENDPOINT_URL=http://localhost:4566
AWS_ACCESS_KEY_ID=localstack
AWS_SECRET_ACCESS_KEY=localstack
AWS_REGION=us-east-1
FINERACT_REPORT_EXPORT_S3_ENABLED=true
FINERACT_REPORT_EXPORT_S3_BUCKET_NAME=fineract-reports
```

### 1.1 One environment deviation, recorded because it is not in CI

Gradle plugin resolution on this machine is redirected by a host-level init script
(`~/.gradle/init.d/*.gradle`) that replaces the plugin repositories with Google and a Maven Central GCS
mirror. With that in place the build cannot resolve the `me.qoomon.git-versioning` plugin marker required by
`build.gradle:105`:

```
* What went wrong:
Plugin [id: 'me.qoomon.git-versioning', version: '6.4.4'] was not found in any of the following sources:
- Gradle Core Plugins (plugin is not in 'org.gradle' namespace)
- Included Builds (No included builds contain this plugin)
- Plugin Repositories (could not resolve plugin artifact
  'me.qoomon.git-versioning:me.qoomon.git-versioning.gradle.plugin:6.4.4')
  Searched in the following repositories:
    Google
    maven(https://maven-central.storage-download.googleapis.com/maven2)
```

This is a property of the machine, **not** of the repository — GitHub Actions resolves the same plugin from
`gradlePluginPortal()` without incident. It was fixed by adding `gradlePluginPortal()` back in a host-level
init script outside the repository. No repository file was touched.

---

## 2. Unit test baseline

### 2.1 Commands, verbatim

```bash
docker run -d --name postgres -e POSTGRES_USER=root -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:18.3
docker run -d --name localstack -p 4566:4566 -p 4510-4559:4510-4559 localstack/localstack:2.1
docker exec localstack awslocal s3api create-bucket --bucket fineract-reports
docker run -d --name mock-oauth2-server -p 9000:9000 -e SERVER_PORT=9000 -e JSON_CONFIG='{...as CI...}' \
  ghcr.io/navikt/mock-oauth2-server:3.0.1

./gradlew --no-daemon -q createPGDB -PdbName=fineract_tenants
./gradlew --no-daemon -q createPGDB -PdbName=fineract_default

# per shard 1..5, exactly as .github/workflows/build-postgresql.yml:88-124 does
./scripts/split-tests.sh 5 "$SHARD_INDEX"
# then, per module in shard-tests_$SHARD_INDEX.txt:
./gradlew "$module:test" --tests <class> ... -PdbType=postgresql \
  -x checkstyleJmh -x checkstyleMain -x checkstyleTest -x spotlessCheck -x spotlessApply \
  -x spotbugsMain -x spotbugsTest -x javadoc -x javadocJar -x modernizer
```

Both `createPGDB` invocations returned exit code 0.

### 2.2 Results

Wall clock: **57 min 18 s** total, 2026-08-21T03:01:29+05:30 → 2026-08-21T03:58:47+05:30.

| Shard | Tests | Passed | Failed | Skipped | Duration | Build |
|-------|-------|--------|--------|---------|----------|-------|
| 1 | 893 | 891 | 0 | 2 | 16m 49s | SUCCESS |
| 2 | 756 | 755 | 0 | 1 | 6m 02s | SUCCESS |
| 3 | 683 | 678 | **2** | 3 | 11m 30s | FAILED (`:integration-tests:test`) |
| 4 | 655 | 645 | **9** | 1 | 14m 34s | FAILED (`:integration-tests:test`) |
| 5 | 564 | 553 | **8** | 3 | 8m 23s | FAILED (`:integration-tests:test`) |
| **Total** | **3,551** | **3,522** | **19** | **10** | **57m 18s** | |

Every failure is in `:integration-tests` — the module that boots the application through
`cargoStartLocal` / `waitForFineract` and drives it over HTTPS. All pure unit modules
(`fineract-core`, `fineract-loan`, `fineract-progressive-loan`, `fineract-provider`, `fineract-savings`,
`fineract-client*`, …) are green in every shard.

### 2.3 The 19 baseline failures — this is the list other workstreams may cite as "pre-existing"

**A failure is only "pre-existing" if it appears below.** These were produced on unmodified `develop` at
`8c187f9d1` with no local changes; per WS0's remit they are recorded, not fixed.

| # | Shard | Test | Symptom |
|---|-------|------|---------|
| 1 | 3 | `org.apache.fineract.integrationtests.SchedulerJobsTest.testTriggeringManualExecutionOfAllSchedulerJobs()` | Batch job `Update Trial Balance Details` ends `status=failed` with `java.lang.ClassCastException: class java.time.OffsetDateTime cannot be cast to class java.time.LocalDate` at `UpdateTrialBalanceDetailsTasklet.lambda$insertTrialBalanceForDate$0(UpdateTrialBalanceDetailsTasklet.java:80)` |
| 2 | 3 | `bulkimport.populator.loan.LoanWorkbookPopulatorTest.testLoanWorkbookPopulate()` | `Expected status code <200> but was <500>` at `LoanWorkbookPopulatorTest.java:101` |
| 3 | 4 | `bulkimport.importhandler.loan.LoanImportHandlerTest.testLoanImport()` | `Expected status code <200> but was <500>` at `LoanImportHandlerTest.java:177` |
| 4–11 | 4 | `AccountTransferTest` — `testFromSavingsToSavingsAccountTransfer()`, `testFromSavingsToSavingsAccountTransferWithInvalidTransferDate()`, `testFromSavingsToLoanAccountTransfer()`, `testFromLoanToSavingsAccountTransfer()`, `testTransferWithInsufficientBalance()`, `testTransferWithNegativeAmount()`, `testTransferToNonExistentAccount()`, `testTransferToInvalidAccountTypes()` (8 tests) | All fail identically in the shared teardown: `Expected status code <200> but was <404>` at `AccountTransferTest.tearDown(AccountTransferTest.java:146)` |
| 12–18 | 5 | `SavingsAccountsExternalIdTest` — `submitSavingsAccountsApplication()`, `updateSavingsAccountWithExternalId()`, `approveSavingsAccount()`, `retrieveSavingsAccountWithExternalId()`, `undoApprovalSavingsAccountWithExternalId()`, `retrieveSavingsAccountWithExternalIdSecondTime()`, `deleteSavingsAccountWithExternalId()` (7 tests) | Ordered chain: the first `POST /savingsaccounts` fails, and every later step then 404s with `Savings account with identifier null does not exist` |
| 19 | 5 | `bulkimport.populator.savings.SavingsWorkbookPopulateTest.testSavingsWorkbookPopulate()` | `Expected status code <200> but was <500>` at `SavingsWorkbookPopulateTest.java:84` |

Three observations that matter to later workstreams, offered as observation not diagnosis:

- Failure 1 is a **PostgreSQL dialect defect**, not flakiness: a native/JDBC result column comes back as
  `OffsetDateTime` under PostgreSQL where the tasklet expects `LocalDate`. It is exactly the class of
  finding the architecture package assigns to the dialect workstream. WS0 records it and does not fix it.
- Failures 2, 3 and 19 are all bulk-import (Apache POI workbook) endpoints returning HTTP 500 — likely one
  shared root cause across three tests.
- Failures 4–11 fail in `tearDown`, so the assertion reported is the cleanup call, not the behaviour under
  test; the real cause is upstream and needs the server log to attribute.

Full per-shard logs, including stack traces, are at `~/ws0-baseline/shard-{1..5}.log` on the run machine;
the Gradle HTML reports are at `integration-tests/build/reports/tests/test/index.html` (not committed).

---

## 3. `current-state.md` verification — every asserted count re-run

Commands were run from the repository root at commit `8c187f9d1`. "Confirmed" means the number reproduces
exactly; "drifted" means it does not, and the real number is given.

Where a count is scoped, the scope is stated: `main` means the `*/src/main/java` trees only, excluding
`custom/`, `src/test`, and any `build/` output. That scoping is what reproduces the published numbers; a
naive repository-wide grep gives different figures, which are also shown so later workstreams do not
mistake a scope change for drift.

| # | Asserted in `current-state.md` | Command | Result | Verdict |
|---|-------------------------------|---------|--------|---------|
| 1 | 4,855 main-source Java files | `find . -path "*/src/main/java/*" -name "*.java" \| grep -v "/build/" \| wc -l` | `4855` | **Confirmed** |
| 2 | 34 `include` statements in `settings.gradle` | `grep -c "include" settings.gradle` | `34` | **Confirmed, but the wording is misleading.** Only 32 are static `include ':module'` lines (`settings.gradle:50-80`); one is `include ':custom:docker'` (`settings.gradle:82`) and one is the *dynamic* `include ":custom:${company}:${category}:${module}"` inside the `eachDir` loop (`settings.gradle:84-96`), which expands to one project per directory found at build time — 8 of them in this tree. The real Gradle project count is therefore 41, not 34. |
| 3 | 322 Liquibase changelog XML files | `grep -rl "databaseChangeLog" --include=*.xml . \| grep -v "/build/" \| wc -l` | `322` | **Confirmed** (237 of them under `fineract-provider/src/main/resources/db`, the rest in other modules and in `custom/`) |
| 4 | 590 `JdbcTemplate` occurrences across 204 files | `grep -ro "JdbcTemplate" --include=*.java */src/main/java \| wc -l` and `grep -rl ... \| wc -l` | `590` occurrences, `204` files | **Confirmed** (repository-wide, including tests: 603 occurrences over 220 files) |
| 5 | 19 `EntityManager` files | `grep -rl "EntityManager" --include=*.java */src/main/java \| wc -l` | `19` | **Confirmed** (22 including tests) |
| 6 | 1 `createNativeQuery` | `grep -rn "createNativeQuery" --include=*.java . \| grep -v "/build/"` | `1` | **Confirmed** |
| 7 | 8 `org.quartz` importers | `grep -rl "^import org.quartz" --include=*.java */src/main/java \| wc -l` | `8` | **Confirmed** (9 including tests) |
| 8 | Zero `@Scheduled` | `grep -rn "@Scheduled" --include=*.java . \| grep -v "/build/" \| wc -l` | `0` | **Confirmed** |
| 9 | Zero `WebClient` | `grep -rn "WebClient" --include=*.java . \| grep -v "/build/" \| wc -l` | `0` | **Confirmed** |
| 10 | Zero stored procedures / functions | `grep -rniE "CREATE (PROCEDURE\|FUNCTION)\|DELIMITER" fineract-provider/src/main/resources/db \| wc -l` | `0` | **Confirmed** — materially de-risks the Aurora PostgreSQL decision (Q-DB-1) |
| 11 | Zero `.prpt` files | `find . -name "*.prpt" \| wc -l` | `0` | **Confirmed** — no Pentaho report definitions to port |

### 3.1 Drifted rows

| Row | Old value | New value | Evidence |
|-----|-----------|-----------|----------|
| `custom/` extension tree is empty (`open-questions.md`, Q-PRG-3/Q-PRG-4) | empty | **8 Gradle modules under `custom/acme/**` plus `custom/docker`, and 4 custom Liquibase changelogs** | `find custom -mindepth 3 -maxdepth 3 -type d` → `custom/acme/{event/externalevent,event/starter,loan/cob,loan/job,loan/processor,loan/starter,note/service,note/starter}`; `find custom -name build.gradle` → 9; `find custom -path "*custom-changelog*" -name "*.xml"` → 4, loaded by `fineract-provider/src/main/resources/db/changelog/db.changelog-master.xml:38` |
| "34 modules" (used throughout the architecture package as the module count) | 34 | **41 Gradle projects** at this commit (32 static + `:custom:docker` + 8 dynamic `custom/acme` modules) | `settings.gradle:50-96` |

No other row in §§2–9 that this workstream could re-run has drifted; the remainder are configuration and
topology statements re-verified individually in `ws0-decision-register.md` with file:line citations.

---

## 4. End-to-end baseline

Repo: `/home/ubuntu/repos/fineract-Core-Banking`
Application code commit under test: `8c187f9d1` (unmodified `develop`).
Tree HEAD at run time: `1586b745d` — adds only `docs/migration/ws0-baseline.md` and
`docs/migration/ws0-decision-register.md`. `git diff --stat 8c187f9d1` = 2 Markdown files,
365 insertions, **zero** changes to application code, build files, workflows or manifests.

Procedure followed: `.github/workflows/build-e2e-tests.yml` (steps "Build the image",
"Start the Fineract stack", "Wait for Manager to be ready", "Execute tests for shard N").

### 4.0 Coverage
Full e2e suite: 48 feature files / 2,243 declared scenarios, split by
`scripts/split-features.sh` into 10 CI shards. This baseline executed **shard 1 of 10 only**:
5 feature files, 325 declared scenarios → **328 executed scenarios = 14.6 % of 2,243**.
Shards 2–10 were **not run** (serial execution ≈ 10+ h on this host).

### 4.1 Commands, exit codes, durations

| # | Command | Exit | Wall time |
|---|---|---|---|
| 1 | `./gradlew --no-daemon --console=plain :fineract-provider:jibDockerBuild -Djib.to.image=fineract -x test -x cucumber` | 0 | 33 s |
| 2 | `docker compose -f docker-compose-postgresql-test-activemq.yml up -d` | 0 | 3.4 s |
| 3 | health-wait loop (workflow lines 68–86) + `curl -f -k .../actuator/health` | 0 | 30 s (3 SSL-eof retries) |
| 4 | `scripts/split-features.sh 10 1` | 0 | <1 s |
| 5a | `./gradlew ... :fineract-e2e-tests-runner:cucumber -Pcucumber.features=src/test/resources/features/BusinessDate.feature -Dallure.results.directory=... allureReport` | 0 | 120 s |
| 5b | same, `Loan.feature` | 0 | 1 154 s |
| 5c | same, `LoanAccrualActivity.feature` | 0 | 797 s |
| 5d | same, `LoanInterestRateChange.feature` | 0 | 296 s |
| 5e | same, `LoanProduct.feature` | 0 | 117 s |
|  | **shard 1 total** | **0** (`SHARD_1_FAILED=0`) | **2 484 s ≈ 41 min** |

Cucumber env (exactly as workflow): `IMAGE_NAME=fineract`, `BASE_URL=https://localhost:8443`,
`TEST_USERNAME=mifos`, `TEST_PASSWORD=password`, `TEST_STRONG_PASSWORD=A1b2c3d4e5f$`,
`TEST_TENANT_ID=default`, `INITIALIZATION_ENABLED=true`, `EVENT_VERIFICATION_ENABLED=true`,
`ACTIVEMQ_BROKER_URL=tcp://localhost:61616`, `ACTIVEMQ_TOPIC_NAME=events`.

### 4.2 Cucumber counts — shard 1 of 10

| Feature | Scenarios | passed | failed | skipped | Steps | passed | failed | skipped | Gradle |
|---|---|---|---|---|---|---|---|---|---|
| BusinessDate.feature | 11 | 11 | 0 | 0 | 17 | 17 | 0 | 0 | BUILD SUCCESSFUL 1 m 59 s |
| Loan.feature | 185 | 185 | 0 | 0 | 3 230 | 3 230 | 0 | 0 | BUILD SUCCESSFUL 19 m 13 s |
| LoanAccrualActivity.feature | 81 | 81 | 0 | 0 | 2 322 | 2 322 | 0 | 0 | BUILD SUCCESSFUL 13 m 16 s |
| LoanInterestRateChange.feature | 34 | 34 | 0 | 0 | 751 | 751 | 0 | 0 | BUILD SUCCESSFUL 4 m 55 s |
| LoanProduct.feature | 17 | 17 | 0 | 0 | 189 | 189 | 0 | 0 | BUILD SUCCESSFUL 1 m 56 s |
| **TOTAL** | **328** | **328** | **0** | **0** | **6 509** | **6 509** | **0** | **0** | all 0 |

Independent cross-check: `allure-results-merged-1/*-result.json` = **328** result files,
status breakdown `{'passed': 328}` — matches the Cucumber summaries exactly, so the run was
not silently truncated.

Scenario-count notes: `Loan.feature` declares 186 scenarios but 185 executed — 1 scenario is
`@Skip`-tagged and excluded by the runner's `tags = 'not @Skip'`. `BusinessDate.feature`
declares 7 but executes 11 due to `Scenario Outline` example expansion. Net 325 declared → 328
executed.

### 4.3 Failing scenarios
**None.** 0 failed, 0 skipped-at-runtime across all 328 executed scenarios in shard 1.
(1 scenario excluded pre-execution by the `@Skip` tag in `Loan.feature`.)

### 4.4 Authenticated smoke calls (raw)

```
===== GET /fineract-provider/api/v1/offices (basic auth mifos/password, Fineract-Platform-TenantId: default) =====
[{"id":1,"name":"Head Office","nameDecorated":"Head Office","externalId":"1","openingDate":[2009,1,1],"hierarchy":"."}]
HTTP_STATUS=200 time=1.048324s

===== NEGATIVE CONTROL: same call, wrong password =====
{"timestamp":"2026-08-20T22:35:28.967Z","status":401,"error":"Unauthorized","path":"/fineract-provider/api/v1/offices"}
HTTP_STATUS=401

===== GET /fineract-provider/actuator/health =====
{"status":"UP","groups":["liveness","readiness"]}
HTTP_STATUS=200

===== GET /fineract-provider/actuator/health/liveness =====
{"status":"UP"}
HTTP_STATUS=200

===== GET /fineract-provider/actuator/health/readiness =====
{"status":"UP"}
HTTP_STATUS=200
```

Re-checked after the 41-minute shard run: `/actuator/health` still `{"status":"UP",...}` HTTP 200,
all three containers still up, fineract `healthy`.

### 4.5 Versions

| Component | Version / digest |
|---|---|
| JDK (Gradle launcher + daemon) | OpenJDK 21.0.11 (Ubuntu 21.0.11+10-1-22.04.2) |
| Gradle | 8.14.3 |
| Docker Engine | 27.4.1 (build b9d17ea) |
| Docker Compose | v2.32.1 |
| App image | `fineract:latest` = `fineract:1.15.0-SNAPSHOT`, sha256:1437f430533c…, 573 MB |
| Database | `postgres:18.3`, sha256:fbaa24359903… |
| Broker | `symptoma/activemq:5.18.3`, sha256:231cecd0fcbf… |

Note: CI uses Zulu JDK 21; this host used Ubuntu OpenJDK 21.0.11.

### 4.6 Observations (no fixes applied)
- **Health-gate false positive in the workflow (pre-existing).** Workflow lines 72–81 poll
  `docker ps --filter "name=fineract" --filter "health=healthy"`. Under compose the DB container
  is named `fineract-core-banking-db-1`, which also matches the substring `name=fineract`, so the
  loop can exit as soon as *Postgres* is healthy, before the app is. Here it was harmless (the
  subsequent `curl --retry` covered it — 3 `SSL routines::unexpected eof` retries ≈ 30 s while
  the app finished booting), but the gate does not actually gate on the app.
- `docker compose up` emits `FINERACT_USER/FINERACT_GROUP variable is not set` warnings and an
  obsolete `version:` attribute warning — same as CI, non-fatal.
- The e2e stack's Postgres binds host port 5432; the unit-baseline `postgres` container had to be
  stopped first. It is currently stopped.

---

## 5. Static analysis on unmodified `develop`

All four checks pass on unmodified `develop`, so any later failure is attributable to the change that
introduces it. Run with `--no-daemon --console=plain`.

| Command | Exit code | Result |
|---------|-----------|--------|
| `./gradlew spotlessCheck` | 0 | BUILD SUCCESSFUL — formatting is clean; nothing for `spotlessApply` to change |
| `./gradlew spotbugsMain` | 0 | BUILD SUCCESSFUL in 2m 8s, 144 tasks (includes the `custom:acme:*` modules) |
| `./gradlew rat` | 0 | BUILD SUCCESSFUL — Apache licence headers present on all files |
| `./gradlew licenseMain` | 0 | BUILD SUCCESSFUL |

The two files added by this workstream are Markdown under `docs/`, which `rat` accepts without a licence
header (`build.gradle:286` excludes `**/*.md` from RAT); the runs above were made with both files present
and still exit 0. Spotless does format Markdown (`build.gradle:230` targets `**/*.md`), and
`spotlessCheck` passes with them in the tree.
