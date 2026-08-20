<!--
    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements. See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership. The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License. You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied. See the License for the
    specific language governing permissions and limitations
    under the License.
-->

# WS3 — cross-engine SQL equivalence harness

Executes financially significant read paths against **two database engines from identical seeded
data** and asserts the results are identical. It is the mechanism the WS9 parallel run reuses for
reconciliation, so it is configured entirely by environment variables: the same cases can be pointed
at Aurora PostgreSQL and the incumbent MariaDB without editing anything.

Two suites:

| Suite | Question it answers | Pass condition |
|---|---|---|
| `equivalence` (`sql/equivalence/*.sql`) | Do the read paths return the same rows on both engines? | rows identical, unless the case declares `known-divergence` |
| `dialect` (`sql/dialect/*.sql`) | Does each known dialect construct behave as documented? | each engine's accept/reject outcome matches the case's declaration |

## Running it locally

Needs the `mysql` and `psql` clients and the two CI service containers:

```bash
docker run -d --name mariadb -e MARIADB_ROOT_PASSWORD=mysql -p 3306:3306 mariadb:11.5.2
docker run -d --name postgres -e POSTGRES_USER=root -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:18.3

scripts/ws3/run-equivalence.sh                                   # everything
scripts/ws3/run-equivalence.sh --suite dialect --only D09 --verbose     # one case, verbose
```

The wrapper drops and recreates `fineract_ws3_equivalence` on both engines, applies
`sql/schema/000_schema.sql` + `sql/schema/010_seed.sql`, and runs every case. Exit code is non-zero
if any case deviates from its declaration.

## Pointing it at Aurora and the incumbent database (WS9)

`--schema live --no-seed` skips schema creation and seeding and runs the cases against existing,
Liquibase-managed tenant databases. Only the `equivalence` suite is meaningful there.

```bash
SOURCE_HOST=incumbent.internal  SOURCE_PORT=3306 SOURCE_USER=… SOURCE_PASSWORD=… SOURCE_DB=fineract_default \
TARGET_HOST=aurora.cluster-xxx.rds.amazonaws.com TARGET_PORT=5432 TARGET_USER=… TARGET_PASSWORD=… TARGET_DB=fineract_default \
python3 scripts/ws3/cross_engine_equivalence.py --schema live --no-seed --suite equivalence
```

The harness refuses to seed in live mode: seeding a production copy would destroy the very data
being reconciled.

## Writing a case

A case is a `.sql` file with a metadata header:

```sql
-- name: loan_outstanding_by_office
-- source: fineract-provider/src/main/java/.../LoanReadPlatformServiceImpl.java   (free-form, for the reader)
-- description: what this proves
-- expect: mysql=pass postgres=fail        -- dialect suite only; omit in the equivalence suite
-- known-divergence: yes                   -- optional; results are expected to differ
-- compare: unordered                      -- optional; compare as a set when the caller ignores order
-- setup: / -- teardown:                   -- optional statements run before/after the case
SELECT …
```

Two placeholders keep a case faithful to the application's generated SQL:

- `{q}` — identifier quote, mirroring `DatabaseSpecificSQLGenerator.escape()` (`` ` `` on MariaDB,
  `"` on PostgreSQL).
- `{limit}` — row limit, mirroring `DatabaseSpecificSQLGenerator.limit(count, offset)`
  (`LIMIT 0,10000` on MariaDB, `LIMIT 10000 OFFSET 0` on PostgreSQL).

Before comparing, results are normalised for representation-only differences that no caller can
observe through JDBC: boolean rendering (`t`/`f` vs `1`/`0`), `NULL` rendering, and trailing zeros on
decimals. Anything else is a real difference and fails the case.

`compare: unordered` exists for statements whose caller folds the rows into a map or a scalar
(`JournalEntryRunningBalanceUpdateServiceImpl:113` is the worked example): the row *set* must match,
and the harness reports when the order differed so the reviewer can confirm order is genuinely
unobservable.

`scripts/ws3/dialect_scan.py` is the static half of the audit: it produces the census and hazard
tables in `docs/migration/ws3-dialect-audit.md`.
