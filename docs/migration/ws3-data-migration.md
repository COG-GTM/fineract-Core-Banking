# WS3 — data migration runbook (MariaDB → Aurora PostgreSQL)

This runbook is executable as written **except** for the steps that need a provisioned Aurora
cluster and a production data copy; those are marked **[needs WS1 + customer data]** and state their
inputs so they can be run the day the data lands.

Assumptions, pending WS0 (target-state §0): target RDBMS is Aurora PostgreSQL; compute is ECS Fargate
behind an ALB; dev is single-region multi-AZ, prod is 3 AZs with a warm standby in a second region.

## 0. What is being moved

Fineract is **database-per-tenant**:

- one registry database (`fineract_tenants`), schema managed by
  `fineract-provider/src/main/resources/db/changelog/tenant-store/`, holding one row per tenant in
  `tenant_server_connections` with that tenant's host, port, schema name, credentials and pool
  sizing;
- one database per tenant (`fineract_default` for the demo tenant,
  `config/docker/env/fineract-common.env:48`), schema managed by
  `.../db/changelog/tenant/`.

Consequence for the migration: **the unit of migration is a tenant database, and the registry has to
be rewritten to point at Aurora**. A tenant whose row still points at the incumbent host keeps
serving traffic from it, silently, after cutover. That row is the single most important artefact of
the cutover.

## 1. Schema creation on Aurora: Liquibase, not a schema dump

Do **not** translate the MySQL schema with a dump/convert tool. The repository's Liquibase
changelogs are engine-aware and already produce a correct PostgreSQL schema; CI proves it on every
run (`.github/workflows/liquibase-only-postgresql.yml`). Procedure per environment:

1. Create the empty databases on Aurora (`fineract_tenants` and one per tenant).
2. Start the application (or run the Liquibase task) against Aurora with an empty registry; it
   applies `tenant-store` to the registry and `tenant` to each tenant database.
3. Compare the resulting object inventory with the expectation in §3.1 before loading any data.

This keeps the target schema identical to what the application's own tests run against, and removes
the whole class of "the converter chose `numeric(19,6)` where the entity expects `numeric(19,6)`"
defects.

## 2. Data movement

Two supported routes. Choose per environment, not per tenant.

### 2.1 AWS DMS (required for prod: minimises the freeze window) **[needs WS1 + customer data]**

- **Full load + CDC.** Source endpoint: the incumbent MariaDB (binlog enabled, `binlog_format=ROW`,
  `binlog_row_image=FULL`). Target endpoint: the Aurora PostgreSQL writer. One DMS task **per tenant
  database**, plus one for the registry.
- **Target preparation mode: `DO_NOTHING`.** The schema comes from Liquibase (§1); DMS must not
  create tables, or it will invent its own types.
- **Type mapping to override explicitly** (DMS's MySQL→PostgreSQL defaults are not safe for a
  ledger):
  - every monetary column must land as `numeric`, never `double precision`. The entities use
    `BigDecimal` with scale 6 for balances; a float round-trip is exactly the "silently wrong
    financial result" failure mode this workstream exists to prevent.
  - `tinyint(1)` → `boolean` (the Liquibase schema declares 138 such columns as `boolean`; see the
    dialect audit §2).
  - `datetime`/`timestamp` → `timestamp without time zone`, with the application's `TZ` unchanged
    (`Asia/Kolkata` in the CI and docker envs). Do not let DMS localise timestamps.
  - `bigint unsigned` → `bigint` only after confirming no value exceeds `2^63-1`.
- **LOB handling:** full LOB mode for `stretchy_report.report_sql` and the document/note text
  columns; limited LOB mode truncates silently.
- **Sequences.** PostgreSQL identity/sequence values are **not** carried by DMS. After the final
  load, set every sequence to `max(id)+1` per table, and verify: a missed sequence means the first
  post-cutover insert fails on a duplicate key, per table, at random times.
- Enable DMS validation on every task and treat any `MISMATCHED_RECORDS` as a stop-the-cutover
  condition.

### 2.2 Dump/restore (dev, and acceptable for small tenants)

```bash
# per tenant, from the incumbent
mariadb-dump --single-transaction --quick --no-create-info --compatible=postgresql \
             --complete-insert --skip-extended-insert -h "$SRC_HOST" -u "$SRC_USER" -p "$SRC_DB" \
  > "$SRC_DB.sql"
```

`--no-create-info` is deliberate: the schema comes from Liquibase. The dump is data only. Boolean and
backslash-escape handling still differ, so the dump is loaded through a normalisation pass rather
than piped straight into `psql`; validate the load with §3 rather than trusting the exit code. Take
the corresponding `pg_dump -Fc` of the loaded Aurora database as the rollback artefact before
opening traffic.

## 3. Reconciliation

Nothing is signed off on a "DMS task succeeded" status. Three layers, in order.

### 3.1 Object inventory (schema)

Per tenant database, compare table count, column count per table, and index/constraint count against
the Liquibase-built reference database created in §1 on a scratch Aurora schema. Any delta is a
schema defect, not a data defect, and stops the migration.

### 3.2 Row counts, per table, per tenant

```sql
-- run on both engines, per tenant database
SELECT table_name, (xpath('/row/c/text()',
       query_to_xml(format('SELECT count(*) AS c FROM %I', table_name), false, true, '')))[1]::text::bigint AS rows
  FROM information_schema.tables
 WHERE table_schema = current_schema() AND table_type = 'BASE TABLE'
 ORDER BY table_name;
```

The MariaDB side must use `SELECT COUNT(*)` per table as well — `information_schema.TABLES.TABLE_ROWS`
is an estimate and will disagree by design. Expected result: exact equality on every table for a
frozen source; during CDC, equality only after the freeze in §4.

### 3.3 Financial reconciliation

Row counts do not catch a wrong `numeric` scale or a truncated LOB. The financial layer is the
harness in this PR, run in live mode against both databases:

```bash
SOURCE_HOST=<incumbent> SOURCE_DB=<tenant_db> SOURCE_USER=… SOURCE_PASSWORD=… \
TARGET_HOST=<aurora>    TARGET_DB=<tenant_db> TARGET_USER=… TARGET_PASSWORD=… \
python3 scripts/ws3/cross_engine_equivalence.py --schema live --no-seed --suite equivalence
```

It asserts identical results for loan outstanding balances per office and currency, loan installment
dues, loan transaction totals, journal entry totals and running balances, savings balances, savings
charge balances, and account transfer totals. Add per-engagement cases for any report the customer
reconciles on; the case format is in `scripts/ws3/README.md`.

Three control totals are additionally checked by hand and recorded in the sign-off:

| Control | Statement | Tolerance |
|---|---|---|
| Trial balance | `SELECT account_id, SUM(CASE WHEN type_enum = 2 THEN amount ELSE -amount END) FROM acc_gl_journal_entry WHERE reversed = false GROUP BY account_id` | exact, to the last minor unit |
| Loan portfolio | `SELECT currency_code, SUM(principal_outstanding_derived + interest_outstanding_derived + fee_charges_outstanding_derived + penalty_charges_outstanding_derived) FROM m_loan WHERE loan_status_id = 300 GROUP BY currency_code` | exact |
| Savings balances | `SELECT currency_code, SUM(account_balance_derived) FROM m_savings_account WHERE status_enum = 300 GROUP BY currency_code` | exact |

"Exact" means exact. A rounding difference here is a type-mapping defect (§2.1), not noise.

### 3.4 Sign-off

The cutover proceeds only with all four recorded in the change record: §3.1 clean, §3.2 clean for
every tenant, §3.3 clean with the harness output attached, and a named owner from the customer's
finance function signing the three control totals. Any single failure returns to §2 for that tenant;
tenants are independent and can be cut over in waves.

## 4. Freeze window and replication (Q-DB-4)

Open question Q-DB-4 (acceptable downtime) is unanswered; the runbook is written for both answers.

**Sequence (DMS route):**

1. `T-days`: DMS full load + CDC running, validation enabled, replication lag monitored and stable.
2. `T-0`: stop write traffic. Concretely: drain the ECS service to zero desired tasks (or scale the
   incumbent deployment to zero) — not a read-only flag, because Fineract has no application-level
   write freeze. Batch/COB jobs must be disabled first, or a scheduled job will write after the
   freeze.
3. Wait for DMS `CDCLatencySource` and `CDCLatencyTarget` to reach 0 and validation to report no
   pending rows.
4. Run §3.2 and §3.3. Fix sequences (§2.1).
5. Repoint `tenant_server_connections` in the Aurora registry at the Aurora endpoints, and verify no
   row still references the incumbent host.
6. Start the application against Aurora, run the smoke set (authenticated `GET /offices`, actuator
   `health`/`liveness`/`readiness`), then open traffic.

**Window estimate inputs** (fill from the customer's data; the arithmetic is what matters):
freeze ≈ CDC drain + reconciliation (§3.2 scales with table count × tenants; §3.3 is minutes) +
sequence fix + registry repoint + smoke. With CDC caught up in advance, the dominant term is
reconciliation, not data movement. Without DMS (§2.2), the dump/restore time itself lands inside the
freeze and the window is governed by the largest tenant database.

**Rollback:** keep the incumbent writable-but-idle until the sign-off in §3.4 plus one business day.
Rollback is repointing the registry back and restarting; it is only safe while no writes have landed
on Aurora, so the decision point is the moment traffic opens.

## 5. Aurora sizing from the connection-pool arithmetic (Q-DB-2)

The pool sizing in the architecture package (3–10) is the **registry** datasource, not the tenant
datasources. Precisely:

| Pool | Where it is configured | Default |
|---|---|---|
| Registry (`fineract_tenants`) | `fineract-provider/src/main/resources/application.properties:409-410`, `config/docker/env/fineract-common.env:23-24` (`FINERACT_HIKARI_MINIMUM_IDLE=3`, `FINERACT_HIKARI_MAXIMUM_POOL_SIZE=10`) | min-idle 3 / max 10 |
| Per tenant | `DataSourcePerTenantServiceFactory.java:86-133` builds one `HikariConfig` per tenant from `tenant_server_connections.pool_initial_size` / `pool_max_active` (`db/changelog/tenant-store/parts/0001_initial_schema.xml:48,55`), overridable globally by `fineract.tenant.config.min-pool-size` / `max-pool-size` (`application.properties:63-64`) | initial 5 / **max 40** per tenant |

Worst-case connections from the application tier:

```
connections = tasks × (10 + Σ_tenants max_active)
            = tasks × (10 + tenants × 40)          with the shipped defaults
```

For 4 Fargate tasks and 10 tenants that is 4 × (10 + 400) = **1,640** connections — far above the
~1,000-connection practical ceiling of a `db.r6g.large` Aurora PostgreSQL writer. Three levers, in
the order they should be pulled:

1. Set `FINERACT_CONFIG_MAX_POOL_SIZE` (and `MIN_POOL_SIZE`) to override every tenant pool centrally
   — the code path is `getMaxPoolSize()`/`getMinPoolSize()` above. A value of 5–10 makes the
   arithmetic tractable: 4 × (10 + 10 × 10) = 440.
2. Put RDS Proxy in front of the writer so idle-but-open tenant pools do not consume backend
   connections. **[needs WS1]**
3. Size the instance class from the resulting number with 30% headroom, not from CPU.

Record the chosen `tasks`, `tenants` and `max_active` in the change record; the Aurora instance class
follows from them arithmetically. **Residual dependency:** the real tenant count and the production
task count are customer inputs (Q-DB-2). Until those are known this section yields a formula, not an
instance type.

## 6. Direct database readers (Q-DB-5)

Anything that connects to the tenant databases outside the application — BI extracts, ETL,
operational scripts, reporting tools pointed at `stretchy_report` output, replicas feeding a
warehouse — breaks on cutover in two distinct ways:

1. **Connection string and driver.** MariaDB JDBC/ODBC clients cannot connect to Aurora PostgreSQL.
   Every such consumer needs a new driver, endpoint and credential.
2. **Their SQL is MySQL SQL.** They will hit exactly the dialect classes catalogued in
   `ws3-dialect-audit.md` — backtick quoting, `IF()`, boolean-vs-integer comparisons, `GROUP BY`
   strictness, `CONCAT` NULL semantics. The last one is silent: a report that produced `NULL` starts
   producing a partial string.

Action, before the cutover date: inventory every direct reader (source: incumbent database's
`processlist`/audit log over a full month, plus the customer's own list), and run each reader's SQL
through the harness's dialect suite. The stored `stretchy_report` SQL shipped in the Liquibase
changelogs, and any customer-added report rows, need the same treatment — they are data, so they are
not covered by the source-code audit. **Residual risk:** unknown-unknown readers. The mitigation is
keeping the incumbent up and readable (but not written to) for an agreed period after cutover, which
is a customer decision.

## 7. What remains blocked

| Step | Blocked on |
|---|---|
| DMS task definitions and their validation output | AWS account + WS1 network/security groups |
| §3.2/§3.3 executed against real data | a production-sized data copy |
| Freeze window duration | Q-DB-4 + measured CDC lag on real volumes |
| Aurora instance class | Q-DB-2 (tenant count, task count) |
| Direct-reader inventory | Q-DB-5 (customer input) |
| Cutover rehearsal | all of the above |
