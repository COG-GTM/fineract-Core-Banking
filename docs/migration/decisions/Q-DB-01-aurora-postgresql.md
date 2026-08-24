# Q-DB-01: Use Aurora PostgreSQL as the target database engine

- **Status:** Accepted
- **Date:** 2026-08-24
- **Owner:** DBA
- **Decision:** Amazon Aurora PostgreSQL is the target engine for the tenant registry and all tenant databases.

## Context

The current deployment manifests use MariaDB, while Fineract also supports PostgreSQL through its database
type resolver, PostgreSQL-specific query service, Liquibase PostgreSQL extension, Docker Compose profiles,
and dedicated PostgreSQL CI workflows.

The AWS migration requires a single target engine before database migration work begins. Aurora PostgreSQL
is the programme's standard managed relational database, so no G3 exception is required.

## Consequences

- WS1 provisions Aurora PostgreSQL rather than RDS for MariaDB.
- WS3 treats the move from MariaDB to PostgreSQL as a data and SQL-dialect migration.
- The PostgreSQL CI and Liquibase workflows are the correctness baseline for WS3.
- The exact Aurora PostgreSQL major version and parameter groups must be validated against Fineract's
  supported PostgreSQL version before deployment.
- Existing MariaDB manifests remain for legacy/local compatibility only and are not the AWS target
  deployment model.

If no Aurora PostgreSQL version compatible with Fineract is available, work must stop and a documented G3
exception must be approved before WS3 continues.
