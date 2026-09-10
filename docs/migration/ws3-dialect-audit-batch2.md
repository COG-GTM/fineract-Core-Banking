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

# WS3 dialect audit — batch 2: string functions and concatenation

This batch audits raw SQL string handling for the MariaDB/MySQL to PostgreSQL migration. It
continues [`ws3-dialect-audit.md`](ws3-dialect-audit.md), using the same main-source boundary:
tracked Java files under `*/src/main/java/*`, excluding `custom/`.

## Scope and method

The census at `/home/ubuntu/ws3-batch2-census.md` was treated as settled input. It was produced from
`scripts/ws3/dialect_scan.py`, Java SQL-literal extraction, source inspection, and Liquibase type and
nullability cross-references. The executable half of this batch adds D13–D17 cases under
`scripts/ws3/sql/dialect/`. Run them with `scripts/ws3/run-equivalence.sh` against the MariaDB and
PostgreSQL versions used by CI. Claims about harness outcomes are only made after that run; source
classifications below are otherwise source-based.

The MariaDB harness database is created with `utf8mb4_unicode_ci` so the D14 case mirrors
`config/docker/mysql/conf.d/server_collation.cnf`.

## Census summary

| Category | Result |
|---|---|
| Raw `CONCAT` | 19 Java SQL occurrences, including display projections, permission predicates, the staff hierarchy join, teller notes, and numeric-to-string projections. |
| Aggregation | No raw `GROUP_CONCAT` or `string_agg`; one `sqlGenerator.groupConcat` site at `LoanReadPlatformServiceImpl.java:1670-1671`. |
| `IFNULL`/`NVL` | Zero raw Java SQL uses. `COALESCE` is used and portable. Stored report SQL in Liquibase remains out of scope. |
| SQL `||` | No SQL-literal operator. The only scoped match was SpEL in `FlushModeAspect.java:52`. |
| Numeric/date coercion | Numeric IDs occur in display `CONCAT` expressions; `SearchReadPlatformServiceImpl.java:95` concatenates `deposit_type_enum`; `ProvisioningEntriesReadPlatformServiceImpl.java:248` applies `LIKE` to `entry.created_date`. |
| Case-sensitive search risk | The selected free-text paths used bare `LIKE` without `LOWER`/`UPPER`; the fixed paths now normalize both operands or the bound Java value. Hierarchy predicates remain unchanged. |
| Other functions | No `SUBSTRING_INDEX`, `LOCATE`, `INSTR`, `LPAD`, `RPAD`, `CHAR_LENGTH`, `CONVERT`, `DATE_FORMAT`, `STR_TO_DATE`, `REGEXP`, `LEFT`, `RIGHT`, or `FORMAT` SQL call sites. `LENGTH`/`REPLACE` indentation and permission-join uses are portable. |

### `CONCAT` sites and classifications

| File:line | SQL / arguments | Type/nullability and use |
|---|---|---|
| `fineract-accounting/.../GLAccountReadPlatformServiceImpl.java:47` | `concat(substring('....',1,((LENGTH(hierarchy)-LENGTH(REPLACE(hierarchy,'.',''))-1)*4)),name)` | `hierarchy` `VARCHAR(50)` nullability unspecified; `name` non-null `VARCHAR(200)`; display-only label. |
| `fineract-provider/.../creditbureau/service/CreditBureauLoanProductMappingReadPlatformServiceImpl.java:47`, `CreditBureauReadPlatformServiceImpl.java:47`, `OrganisationCreditBureauReadPlatformServiceImpl.java:47` | `concat(product,' - ',name,' - ',country)` | All three columns are non-null `VARCHAR(100)`; display/data projections. |
| `fineract-provider/.../dataqueries/service/DatatableReadServiceImpl.java:109`; `survey/service/ReadSurveyServiceImpl.java:81,94,178` | `p.code = concat('READ_', registered_table_name)` | Non-null PK `VARCHAR(50)`; permission `WHERE EXISTS` predicates. |
| `fineract-provider/.../dataqueries/service/ReadReportingServiceImpl.java:426` | `p.code = concat('READ_', r.report_name)` | Commented-out SQL construction; no runtime effect. `report_name` is non-null `VARCHAR(100)`. |
| `fineract-provider/.../organisation/office/service/OfficeReadPlatformServiceImpl.java:60` | Hierarchy indentation expression using `SUBSTRING`, `LENGTH`, and `REPLACE` | Display-only; hierarchy `VARCHAR(100)` nullability unspecified, name non-null `VARCHAR(50)`. |
| `fineract-provider/.../organisation/staff/service/StaffReadPlatformServiceImpl.java:89` | `o.hierarchy like concat(ohierarchy.hierarchy,'%')` | String `CONCAT` feeds a `JOIN` predicate; hierarchy nullability unspecified. |
| `fineract-provider/.../organisation/teller/service/TellerManagementReadPlatformServiceImpl.java:432,470,507,594,632,669` | Transaction notes concatenate enum text, numeric IDs, account numbers, and display names | Display-only; IDs are `BIGINT`; left joins can make nominally non-null joined columns null at runtime. |
| `fineract-provider/.../portfolio/search/service/SearchReadPlatformServiceImpl.java:95` | `concat(s.deposit_type_enum,'')` | Non-null `SMALLINT` to text in a display/search projection. |
| `fineract-savings/.../GSIMReadPlatformServiceImpl.java:243` | `CONCAT('(',clnt.id,') ',clnt.display_name)` | Numeric `BIGINT` ID in a display projection; joined-row nullability applies. |

The generator template at `fineract-core/.../SqlOperator.java:51,64` emits
`CONCAT('%', placeholder, '%')` for generated `LIKE`; it is not a raw service call site.

### `LIKE` changes

Only the following free-text predicates were changed:

| File:line | Before | After |
|---|---|---|
| `ClientReadPlatformServiceImpl.java:164,171,181,186,548` | `c.external_id like ?`, `c.display_name like ?`, `c.firstname like ?`, `c.lastname like ?`, `ci.document_key like ?` | `lower(column) like lower(?)` |
| `SearchReadPlatformServiceImpl.java:86,91,97,103,109,113` | Search columns compared with `:search` using bare `LIKE` | All search columns use `lower(column) like lower(:search)`; `:hierarchy` remains unchanged |
| `GroupReadPlatformServiceImpl.java:211` | `g.display_name like ?` | `lower(g.display_name) like ?`, with the bound value lowercased using `Locale.ROOT` |
| `CenterReadPlatformServiceImpl.java:106` | `g.display_name like ?` | `lower(g.display_name) like ?`, with the nullable bound value lowercased using `Locale.ROOT` |

Hierarchy predicates, permission constants, `code_name`, `application_table_name`, and all other
bare `LIKE` sites were deliberately left alone. MariaDB `utf8mb4_unicode_ci` also folds accents,
which `LOWER()` does not; equality on varchar columns is likewise collation-dependent and is out of
scope for this batch.

### Cast and generator sites

`DatabaseSpecificSQLGenerator.castChar` now emits `%s::VARCHAR` for PostgreSQL instead of
`%s::CHAR`; MySQL remains `CAST(%s AS CHAR) COLLATE utf8mb4_unicode_ci`. The affected joins are:

- `DepositAccountInterestRateChartReadPlatformServiceImpl.java:201`
- `InterestRateChartReadPlatformServiceImpl.java:207,303`
- `InterestRateChartSlabReadPlatformServiceImpl.java:135`

These compare cast numeric `code.id` values with `iri.attribute_value`; PostgreSQL bare `CHAR` is
`char(1)`, truncating values such as `12` to `1`.

## Defect register

Severity uses batch 1's terminology: `financial-impact-adjacent` can silently select or label the
wrong business value; `functional-failure` rejects a query; `cosmetic` changes display output.

| # | Site | SQL | Why engines differ | Symptom | Severity | Evidence | Status |
|---:|---|---|---|---|---|---|---|
| 12 | `DatabaseSpecificSQLGenerator.castChar`; four interest-rate-chart joins above | PostgreSQL old output `code.id::CHAR` | PostgreSQL bare `CHAR` means `char(1)`; MariaDB `CAST(... AS CHAR)` preserves the number | IDs ≥ 10 truncate and match the wrong/no incentive code value | financial-impact-adjacent | `D13_cast_char_truncation.sql`, `D13_fixed_cast_varchar.sql`; generator regression test | **fixed** — PostgreSQL emits `::VARCHAR` |
| 13 | Listed client, search, group, and center free-text paths | Bare `column LIKE ?` / `column LIKE :search` | MariaDB's `utf8mb4_unicode_ci` is case-insensitive; PostgreSQL text comparison is case-sensitive | Mixed-case searches return fewer rows on PostgreSQL | functional | `D14_like_case_sensitivity.sql`, `D14_fixed_lower_like.sql` | **fixed** at listed sites; accent folding and varchar equality remain out of scope |
| 14 | `fineract-accounting/.../ProvisioningEntriesReadPlatformServiceImpl.java:248` | `entry.created_date like ?` with a String overload | MariaDB coerces text to DATE for `LIKE`; PostgreSQL rejects date/varchar comparison | PostgreSQL query failure if the dead overload is invoked | functional | `D17_date_like_varchar.sql` | **not fixed** — no callers found; recommend deletion in an application-code PR |
| 15 | `TellerManagementReadPlatformServiceImpl.java:432,470,507,594,632,669`; `GSIMReadPlatformServiceImpl.java:243` | `CONCAT(...)` with left-joined values | MariaDB returns NULL if any argument is NULL; PostgreSQL `concat()` ignores NULL arguments | Null display label can become a partial label | cosmetic | Carried from batch-1 D10; per-site census classification | **unfixed** — product decision required |
| 16 | `GSIMReadPlatformServiceImpl.java:243`; `SearchReadPlatformServiceImpl.java:95`; teller notes | Numeric values passed to `CONCAT` | Both engines render these numeric arguments compatibly in the guard case | Display labels retain numeric IDs/text | none | `D15_concat_numeric_argument.sql` | **no defect** — guard case |
| 17 | `GLAccountReadPlatformServiceImpl.java:152` | `name like %?% or gl_code like %?%` | The SQL is malformed on both engines; this is not a MariaDB/PostgreSQL dialect difference | Query fails when this path is executed | functional | Census/source inspection | **not fixed** — pre-existing, flagged for an application-code PR |

## Confirmed clean / follow-up

- `GROUP_CONCAT` is routed through `DatabaseSpecificSQLGenerator.groupConcat`; PostgreSQL emits
  `STRING_AGG(arg::varchar, ',')`.
- No raw Java SQL `IFNULL` or `NVL`.
- No SQL-literal `||`.
- No scoped MySQL-only string functions in the Java call sites listed by the census.
- Stored-report SQL embedded in Liquibase changelogs is out of scope and remains a follow-up; it
  contains legacy `ifnull`, `if`, and other MySQL-specific constructs.

The batch-2 harness cases are self-contained literal/CTE reproductions: D13 covers old and fixed
casts, D14 covers bare and normalized `LIKE`, D15 numeric `CONCAT`, D16 hierarchy indentation, and
D17 the dead date-`LIKE` overload.
