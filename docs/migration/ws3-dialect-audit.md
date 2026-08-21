# WS3 — SQL dialect audit (MariaDB/MySQL → PostgreSQL)

Scope: every raw-SQL call site in the main sources of this repository, classified by whether it
routes through `DatabaseSpecificSQLGenerator`, and audited for the failure classes that separate
MySQL/MariaDB from PostgreSQL.

Analysis base: `develop` @ `8c187f9d1`. Engines used for every reproduction below: `mariadb:11.5.2`
and `postgres:18.3` (the images CI uses in `.github/workflows/build-mariadb.yml` and
`.github/workflows/build-postgresql.yml`).

Every "differs" claim in this document was executed against both engines by the harness in
`scripts/ws3/`; the SQL for each case is the file named in the *Evidence* column. Nothing here is
asserted from reading alone — source observations that were **not** executed are listed separately
in §5 and are explicitly marked as unverified.

## 1. Census

Counted with `git ls-files '*.java'` restricted to `*/src/main/java/*` and excluding `custom/`
(scanner: `scripts/ws3/dialect_scan.py`, see §7):

| Item | Count |
|---|---:|
| Main-source Java files | 4,842 |
| Files referencing `JdbcTemplate` | 204 |
| `JdbcTemplate` occurrences | 590 |
| Files using `EntityManager` directly | 19 |
| `createNativeQuery` call sites | 1 |

These match the counts in the architecture package (590 occurrences / 204 files / 19
`EntityManager` users / 1 native query).

Classification of the 204 JDBC files:

| Class | Files |
|---|---:|
| Build SQL through `DatabaseSpecificSQLGenerator` (`sqlGenerator`) somewhere in the file | 75 |
| Never reference the generator | 129 |

The per-file table (all 204 rows, with `JdbcTemplate` reference count, number of SQL statement
literals, generator usage and detected hazard classes) is in
[`ws3-dialect-audit-appendix.md`](ws3-dialect-audit-appendix.md).

The single `createNativeQuery`
(`fineract-cob/src/main/java/org/apache/fineract/cob/domain/CustomLoanAccountLockRepositoryImpl.java:39-52`)
already routes its only dialect-sensitive fragment through the generator
(`databaseSpecificSQLGenerator.subDate("lck.lock_placed_on_cob_business_date", "1", "DAY")`); the
rest of the statement is portable ANSI SQL. No defect.

The 19 `EntityManager` users are listed in §6. None of them build engine-specific SQL text: they use
JPQL, the Criteria API, or EclipseLink/JPA lifecycle APIs. The dialect risk in those files is
EclipseLink platform selection, not hand-written SQL.

## 2. Method

The audit is a scan plus execution, not a reading exercise:

1. Extract every double-quoted string literal from each of the 204 JDBC files and match it against
   pattern classes for the failure modes named in the plan (date/interval arithmetic, `CONCAT` vs
   `||`, `LIMIT`/`OFFSET`, boolean vs tinyint, identifier quoting/case folding, `GROUP BY`
   strictness, MySQL-only DDL and MySQL-only functions).
2. Cross-reference *typed* boolean columns: parse the Liquibase changelogs for columns declared
   `boolean`/`tinyint(1)`/`bit(1)` (138 columns) and flag any raw SQL comparing one of them to the
   integer literals `0` or `1`. This is what distinguishes a genuine boolean/integer defect from the
   many `x_enum = 1` comparisons against integer columns, which are portable.
3. Reproduce each candidate against both live engines from identical seeded data with
   `scripts/ws3/run-equivalence.sh`, and keep the case in the harness so the finding stays proven.

Hazard hits over the 204 files (`bypassing` = hits in files that never use the generator):

| Hazard class | Hits | of which bypassing |
|---|---:|---:|
| String concatenation (`CONCAT`/`||`) | 34 | 20 |
| Boolean-vs-integer shaped comparisons (pattern only) | 15 | 8 |
| `LIMIT`/`OFFSET` literals | 6 | 3 |
| MySQL-only DDL (`DROP FOREIGN KEY`, `CHANGE COLUMN`, `AUTO_INCREMENT`, `SHOW …`, `ENGINE=`) | 8 | 1 |
| `GROUP BY … ASC/DESC` shaped | 3 | 0 |
| Backtick identifier quoting | 13 | 5 |
| MySQL-only date/interval functions (`DATE_ADD`, `DATEDIFF`, `NOW()`, …) | 0 | 0 |
| MySQL-only functions (`GROUP_CONCAT`, `IFNULL`, `FIND_IN_SET`, …) | 0 | 0 |

The same scan run on `develop` (in a clean `git worktree`) and on the head of this branch shows what
the fixes in §3 removed:

| Hazard class | `develop` | this branch |
|---|---:|---:|
| Typed boolean column compared to `0`/`1` | 4 | 0 |
| `IF(...)` function | 1 | 0 |
| `GROUP BY … DESC` in files bypassing the generator | 2 | 0 |
| Backtick hits (SQL identifiers + error-message text) | 14 | 13 (all error-message text) |

Reading the hits, not just the counts, matters:

- All 13 backtick hits after the fixes in this PR are backticks inside **error message text**, not
  SQL (e.g. `"Charge with name `"`). Zero backtick-quoted SQL identifiers remain.
- Of the 15 boolean-shaped comparisons, 13 compare *integer* columns (`type_enum`, `loan_type_enum`,
  `account_usage`, `sub_status_enum`, `success`, `account_type`, `level_id`) and are portable. Only
  the sites in step 2's typed cross-reference are real defects (rows 4 and 5 below).
- All three `GROUP BY … DESC`-shaped hits in
  `JournalEntryRunningBalanceUpdateServiceImpl.java:113,138,197` are `group by je.id order by
  je.entry_date DESC` — the `DESC` belongs to `ORDER BY`. Portable.
- The date/interval and MySQL-function classes are clean because those constructs are centralised in
  `DatabaseSpecificSQLGenerator` (`subDate`, `dateDiff`, `currentBusinessDate`, `groupConcat`,
  `castChar`, `castInteger`, `castJson`, `limit`, `incrementDateByOneDay`).

## 3. Defect register

Severity definitions used here:

- **financial-impact** — can change a monetary result or silently return different rows/values.
- **functional-failure** — the statement is rejected by PostgreSQL; the feature errors out loudly.
- **cosmetic** — display-only difference in a rendered string.

| # | Site | SQL (abridged) | Why the engines differ | Symptom | Severity | Evidence | Status |
|---|---|---|---|---|---|---|---|
| 1 | `fineract-provider/.../portfolio/account/service/AccountTransfersReadPlatformServiceImpl.java:421` | `and IF(1=?, det.from_loan_account_id = ?, det.from_savings_account_id = ?)` | `IF(cond,a,b)` is a MySQL function; PostgreSQL has only `CASE`. | PostgreSQL: `ERROR: function if(boolean, boolean, boolean) does not exist`. The statement sums transfer amounts, so on PostgreSQL the transfer total cannot be computed at all. | functional-failure (financial path) | `scripts/ws3/sql/dialect/D01_if_function_mysql_only.sql` (mysql pass / postgres fail) and `D01_fixed_case_portable.sql` (both pass) | **fixed in this PR** (`CASE WHEN ? = 1 THEN … ELSE … END = ?`) |
| 1b | `fineract-provider/.../portfolio/account/service/AccountTransfersReadPlatformServiceImpl.java:423` | `… where trans.transaction_date = ?` bound with `DATE_TIME_FORMATTER.format(transactionDate)` | The parameter is sent as a `varchar`; `m_account_transfer_transaction.transaction_date` is `DATE`. MariaDB coerces the string to a date, PostgreSQL has no implicit varchar→date cast for `=`. | PostgreSQL: `ERROR: operator does not exist: date = character varying`, surfaced as `BadSqlGrammarException` / HTTP 500 on `POST /self/accounttransfers?type=tpt` (the only caller, behind the self-service module and the `daily-tpt-limit` global configuration, which is why CI never reaches it). Observed live on the e2e stack built from this branch. | functional-failure (financial path) | `scripts/ws3/sql/dialect/D11_date_varchar_bind.sql` (mysql pass / postgres fail) / `D11_fixed_date_typed_bind.sql`; live container log during e2e verification | **fixed in this PR** (bind the `LocalDate` itself) |
| 2 | `fineract-accounting/.../glaccount/service/GLAccountReadPlatformServiceImpl.java:126-129` | `group by account_id desc, id` … `group by t2.account_id desc` | MySQL/MariaDB accept a sort direction in `GROUP BY` (a MySQL extension, deprecated since 8.0); PostgreSQL's grammar has no such production. | PostgreSQL: `ERROR: syntax error at or near "DESC"`. This is the running-balance filter on GL journal entries. | functional-failure (financial path) | `scripts/ws3/sql/dialect/D02_group_by_desc_mysql_only.sql` / `D02_fixed_group_by_portable.sql` | **fixed in this PR** (direction removed; grouping semantics unchanged) |
| 3 | `fineract-provider/.../interoperation/service/InteropServiceImpl.java:158-159` | `a.`address_line_1`, a.`address_line_2`` | Backticks are MySQL identifier quotes; PostgreSQL uses double quotes. | PostgreSQL: `ERROR: syntax error at or near "`"`. | functional-failure | `scripts/ws3/sql/dialect/D03_backtick_quoting_mysql_only.sql` / `D03_fixed_escaped_identifiers.sql` | **fixed in this PR** (routed through `sqlGenerator.escape(...)`) |
| 4 | `fineract-provider/.../portfolio/collectionsheet/service/CollectionSheetReadPlatformServiceImpl.java:244,746,830` | `ls.completed_derived = 0`, `mss.completed_derived = 0` | `completed_derived` is declared `boolean` (`db/changelog/tenant/parts/0001_initial_schema.xml:2533`). MySQL/MariaDB implement `BOOLEAN` as `TINYINT(1)` and compare it to integers; PostgreSQL has no implicit boolean↔integer cast. | PostgreSQL: `ERROR: operator does not exist: boolean = integer`. Collection-sheet generation (individual, group and mandatory-savings variants) fails. | functional-failure (financial path) | `scripts/ws3/sql/dialect/D09_boolean_integer_comparison.sql` / `D09_fixed_boolean_literal.sql` | **fixed in this PR** (`= false`) |
| 5 | `fineract-provider/.../portfolio/account/service/StandingInstructionReadPlatformServiceImpl.java:361` | `and ls.completed_derived <> 1` | Same as row 4. | PostgreSQL: `ERROR: operator does not exist: boolean = integer` when computing standing-instruction loan dues. | functional-failure (financial path) | as row 4 | **fixed in this PR** (`<> true`; matches the pre-existing portable form at `LoanReadPlatformServiceImpl.java:1575`) |
| 5b | `fineract-provider/.../portfolio/collectionsheet/service/CollectionSheetReadPlatformServiceImpl.java:324,459,675` | `addValue("dueDate", DateUtils.DEFAULT_DATE_FORMATTER.format(transactionDate))` against `ls.duedate <= :dueDate` | Same class as row 1b: the named parameter is sent as `varchar` against a `date` column. | PostgreSQL: `ERROR: operator does not exist: date <= character varying`; `POST /collectionsheet?command=generateCollectionSheet` returns HTTP 500. Observed live on the e2e stack after the row-4 boolean fix landed (the boolean fix is necessary but not sufficient). | functional-failure (financial path) | `scripts/ws3/sql/dialect/D11_date_varchar_bind.sql` / `D11_fixed_date_typed_bind.sql`; live container stack trace at `CollectionSheetReadPlatformServiceImpl.java:700` | **fixed in this PR** (bind the `LocalDate`; all three collection-sheet variants) |
| 5c | `fineract-provider/.../portfolio/collectionsheet/service/CollectionSheetReadPlatformServiceImpl.java:257,260,749,752,524,835` | `SELECT … pl.short_name, rc.name … GROUP BY cl.id, ln.id` and the outer `GROUP BY loandata.clientId, loandata.loanId` over `loandata.*` | MySQL/MariaDB run with `ONLY_FULL_GROUP_BY` disabled here and accept any non-aggregated column. PostgreSQL applies the SQL functional-dependency rule: it only accepts non-aggregated columns of a table whose **primary key** is in the `GROUP BY`, so `m_product_loan` / `m_currency` columns (and every column of a derived table) must be grouped explicitly. | PostgreSQL: `ERROR: column "pl.short_name" must appear in the GROUP BY clause or be used in an aggregate function`; `POST /collectionsheet?command=generateCollectionSheet` returns HTTP 500. Observed live after rows 4 and 5b were fixed — this query needed all three fixes to run. | functional-failure (financial path) | `scripts/ws3/sql/dialect/D12_group_by_joined_table_column.sql` / `D12_fixed_group_by_joined_pk.sql`; live container stack trace at `CollectionSheetReadPlatformServiceImpl.java:693` | **fixed in this PR** (joined-table PKs added to the inner grouping; the outer groupings list every projected column of the derived table) |
| 6 | `fineract-provider/.../infrastructure/dataqueries/service/DatatableWriteServiceImpl.java:357-372` and `:893` | `ALTER TABLE … DROP KEY …, DROP FOREIGN KEY …, CHANGE COLUMN …, ADD KEY …` | `DROP FOREIGN KEY`, `DROP KEY`, `ADD KEY` and `CHANGE COLUMN` are MySQL-only; PostgreSQL uses `DROP CONSTRAINT`, `DROP INDEX`, `CREATE INDEX` and `ALTER COLUMN … TYPE` / `RENAME COLUMN`. These branches are **not** guarded by `databaseTypeResolver.isMySQL()`, unlike the adjacent identity-column branch at `:234-241`. | PostgreSQL: `ERROR: syntax error at or near "FOREIGN"`. Changing a datatable's application entity, or dropping a datatable column that has an FK, fails. | functional-failure | `scripts/ws3/sql/dialect/D04_drop_foreign_key_mysql_only.sql` / `D04_fixed_drop_constraint.sql` | **not fixed** — see §4 |
| 7 | `fineract-savings/.../portfolio/savings/service/GSIMReadPlatformServiceImpl.java:243`, `fineract-provider/.../organisation/teller/service/TellerManagementReadPlatformServiceImpl.java:432,470,507,594,632,669`, plus 27 further `concat(...)` sites (appendix) | `CONCAT('(', clnt.id, ') ', clnt.display_name)` | `CONCAT()` exists in both engines, but MySQL/MariaDB return `NULL` if **any** argument is `NULL`, while PostgreSQL's `concat()` ignores `NULL` arguments. | Display strings that were `NULL` on MariaDB become partially-rendered text on PostgreSQL (e.g. `"() suffix"` instead of `NULL`). No monetary value is affected at these sites; all of them build labels. | cosmetic | `scripts/ws3/sql/dialect/D10_concat_null_semantics.sql` (both engines accept it; results differ: `NULL` vs `() suffix`) | **not fixed** — behaviour change, needs product sign-off |
| 8 | Repository-wide (no current call site) | `'a' || 'b'` | With MariaDB's default `sql_mode`, `||` is logical OR (returns `0`); in PostgreSQL it is string concatenation (returns `ab`). | Any future use of `||` silently produces a number on MariaDB and a string on PostgreSQL. | financial-impact if introduced | `scripts/ws3/sql/dialect/D06_concat_operator.sql` (both accept, results differ) | no defect today; guard-rail case kept in the harness |
| 9 | Repository-wide pattern | `GREATEST(NULL, some_date)` | MySQL/MariaDB propagate `NULL`; PostgreSQL's `GREATEST` ignores `NULL` arguments. | An aggregate over a partially-`NULL` set returns `NULL` on MariaDB and a real value on PostgreSQL — a silent difference, no error. | financial-impact if used over nullable money/date columns | `scripts/ws3/sql/dialect/D05_greatest_null_semantics.sql` (both accept, results differ) | no current call site found; guard-rail case kept in the harness |
| 10 | Repository-wide pattern | `SELECT je.account_id, je.entry_date, SUM(...) … GROUP BY je.account_id` | MariaDB's default `sql_mode` in 11.5.2 permits selecting non-aggregated columns absent from `GROUP BY`; PostgreSQL always enforces functional dependency. | PostgreSQL: `ERROR: column "je.entry_date" must appear in the GROUP BY clause…`. | functional-failure if present | `scripts/ws3/sql/dialect/D07_only_full_group_by.sql` | no current call site found by the scan; guard-rail case kept in the harness |
| 11 | Repository-wide pattern | `SELECT "some_token"` | MySQL/MariaDB (without `ANSI_QUOTES`) treat double quotes as a **string literal**; PostgreSQL treats them as a case-folded **identifier**. | A double-quoted token that "worked" as a literal on MariaDB becomes `ERROR: column "not_a_column" does not exist` on PostgreSQL. | functional-failure if present | `scripts/ws3/sql/dialect/D08_double_quoted_string_literal.sql` | no current call site found; guard-rail case kept in the harness |

Rows 8–11 have no current call site: they are kept in the harness so a regression is caught by
`scripts/ws3/run-equivalence.sh` rather than by Aurora in production.

## 4. Findings deliberately left unfixed

The architecture package assigns dialect defects to a separate application-code PR. This PR fixes
only defects that the harness demonstrates failing before and passing after, where the portable form
is semantically identical on both engines (rows 1–5). Two findings are recorded but not changed:

- **Row 6 (datatable DDL).** A correct fix is a dialect branch producing `DROP CONSTRAINT` /
  `ALTER COLUMN … TYPE` / `RENAME COLUMN` for PostgreSQL, and the PostgreSQL path also has to decide
  what to do about the implicit index MySQL drops with `DROP KEY`. That is a behavioural change to
  DDL with no test coverage in this repository (`grep -ril collectionsheet`/datatable-FK rename
  returns no test or `.feature` file exercising it), so it belongs in the application-code PR with
  its own tests.
- **Row 7 (`CONCAT` NULL semantics).** Making the two engines agree means choosing which behaviour
  is correct for the product (`NULL` label vs partial label). That is a product decision, not a
  migration decision.

## 5. Source observations not reproduced (follow-up)

Listed separately because they were read, not executed:

- `fineract-core/.../database/MySQLQueryService.java:46` uses `SHOW TABLES LIKE ?` and MySQL
  `INFORMATION_SCHEMA` spellings. A sibling `PostgreSQLQueryService` exists and is selected by
  `DatabaseTypeResolver`, so this is intentional engine-specific code, not a defect. It is listed so
  the next reviewer does not re-flag it.
- `DatatableWriteServiceImpl.java:236,280` (`AUTO_INCREMENT`, `ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4`)
  are inside `databaseTypeResolver.isMySQL()` branches with PostgreSQL equivalents alongside
  (`GENERATED BY DEFAULT AS IDENTITY`). Correct as written.
- EclipseLink platform selection and the `@Convert`/`tinyint` mappings used by the JPA layer were not
  audited here; they are exercised by the full test suite on both engines rather than by raw-SQL
  inspection.
- Stored reports (`stretchy_report` rows shipped in the Liquibase changelogs) contain their own SQL
  and are executed by `ReadReportingServiceImpl`. They are data, not source, and are out of scope of
  this call-site audit; they need the same treatment before cutover and are a WS3 follow-up.

## 6. `EntityManager` users (19)

`fineract-cob/.../cob/domain/CustomLoanAccountLockRepositoryImpl.java` ·
`fineract-core/.../batch/service/BatchApiServiceImpl.java` ·
`fineract-core/.../core/jpa/CriteriaQueryFactory.java` ·
`fineract-core/.../core/persistence/ExtendedJpaTransactionManager.java` ·
`fineract-core/.../core/persistence/FlushModeHandler.java` ·
`fineract-core/.../event/external/repository/CustomExternalEventConfigurationRepositoryImpl.java` ·
`fineract-core/.../event/external/service/ExternalEventService.java` ·
`fineract-core/.../portfolio/client/domain/search/SearchingClientRepositoryImpl.java` ·
`fineract-investor/.../investor/domain/search/SearchingExternalAssetOwnerRepositoryImpl.java` ·
`fineract-loan/.../loanaccount/domain/LoanSummaryBalancesRepository.java` ·
`fineract-loan/.../loanaccount/service/LoanChargePaidByReadService.java` ·
`fineract-loan/.../loanaccount/service/LoanTransactionReadService.java` ·
`fineract-loan/.../loanaccount/service/LoanTransactionRelationReadService.java` ·
`fineract-progressive-loan/.../repository/CustomizedLoanCapitalizedIncomeBalanceRepositoryImpl.java` ·
`fineract-provider/.../core/config/jpa/EntityManagerFactoryCustomizer.java` ·
`fineract-provider/.../core/config/jpa/JPAConfig.java` ·
`fineract-provider/.../core/diagnostics/jpa/DiagnosticsEntityManager.java` ·
`fineract-provider/.../core/diagnostics/jpa/StatementLoggingCustomizer.java` ·
`fineract-provider/.../loanaccount/service/LoanPointInTimeServiceImpl.java`

## 7. Reproducing this audit

```bash
# census + hazard scan (prints the tables in §1 and §2); --appendix regenerates the per-file table
python3 scripts/ws3/dialect_scan.py --appendix docs/migration/ws3-dialect-audit-appendix.md

# execute every case in §3 against both engines
scripts/ws3/run-equivalence.sh
```

`scripts/ws3/dialect_scan.py` needs only a checkout; `run-equivalence.sh` needs a MariaDB and a
PostgreSQL endpoint (defaults match the CI service containers) and is described in
`scripts/ws3/README.md`.
