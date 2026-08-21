#!/usr/bin/env python3
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership. The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the
# specific language governing permissions and limitations
# under the License.
#
"""WS3 cross-engine equivalence harness.

Executes a fixed set of financially significant read paths against two
database engines from identical seed data and asserts that the results are
byte-identical after type normalisation, and executes a set of dialect
reproductions that assert which engine accepts which SQL construct.

Two engines are configured independently, so the same harness runs

  * locally, MariaDB vs PostgreSQL, seeding its own schema subset
    (``--schema subset``, the default), and
  * in WS9's parallel run, the incumbent database vs Aurora PostgreSQL against
    the real Liquibase-managed tenant schema (``--schema live --no-seed``),

with no code change - only configuration.

Requirements: the ``psql`` and ``mysql`` command line clients.

Configuration (flags override environment):

  SOURCE_ENGINE / TARGET_ENGINE      mysql | postgres
  SOURCE_HOST / TARGET_HOST          hostname
  SOURCE_PORT / TARGET_PORT          port
  SOURCE_USER / TARGET_USER          user
  SOURCE_PASSWORD / TARGET_PASSWORD  password
  SOURCE_DB / TARGET_DB              database (tenant database in live mode)

Defaults match the CI services in .github/workflows/build-mariadb.yml and
.github/workflows/build-postgresql.yml: MariaDB root/mysql on 127.0.0.1:3306
as source, PostgreSQL root/postgres on 127.0.0.1:5432 as target.

Exit code is 0 when every case matched its declared expectation.
"""

import argparse
import decimal
import os
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
SQL_DIR = HERE / "sql"

NULL_TOKEN = "<NULL>"


class SqlError(Exception):
    pass


class Engine:
    """A database endpoint the harness can send statements to."""

    def __init__(self, role, kind, host, port, user, password, database):
        if kind not in ("mysql", "postgres"):
            raise ValueError("unsupported engine kind: %s" % kind)
        self.role = role
        self.kind = kind
        self.host = host
        self.port = str(port)
        self.user = user
        self.password = password
        self.database = database

    def __str__(self):
        return "%s[%s %s@%s:%s/%s]" % (self.role, self.kind, self.user, self.host, self.port, self.database)

    @property
    def quote(self):
        return '"' if self.kind == "postgres" else "`"

    def version(self):
        sql = "SELECT version()"
        return self.query(sql)[0][0]

    def _command(self, sql):
        if self.kind == "postgres":
            return [
                "psql",
                "--host=%s" % self.host,
                "--port=%s" % self.port,
                "--username=%s" % self.user,
                "--dbname=%s" % self.database,
                "--no-psqlrc",
                "--quiet",
                "--tuples-only",
                "--no-align",
                "--field-separator=\t",
                "--pset=null=%s" % NULL_TOKEN,
                "--set=ON_ERROR_STOP=1",
                "--command=%s" % sql,
            ]
        return [
            "mysql",
            "--host=%s" % self.host,
            "--port=%s" % self.port,
            "--user=%s" % self.user,
            "--password=%s" % self.password,
            "--database=%s" % self.database,
            "--batch",
            "--skip-column-names",
            "--raw",
            "--execute=%s" % sql,
        ]

    def _env(self):
        env = dict(os.environ)
        if self.kind == "postgres":
            env["PGPASSWORD"] = self.password
        return env

    def execute(self, sql):
        """Run a statement, discarding any result set."""
        self.query(sql)

    def query(self, sql):
        """Run a statement and return the result set as a list of row tuples."""
        proc = subprocess.run(self._command(sql), env=self._env(), capture_output=True, text=True)
        if proc.returncode != 0:
            raise SqlError(_first_error_line(proc.stderr or proc.stdout))
        rows = []
        for line in proc.stdout.splitlines():
            if line == "":
                continue
            rows.append(tuple(normalise(cell) for cell in line.split("\t")))
        return rows

    def execute_script(self, path):
        """Run a whole .sql file, statement errors abort the run."""
        text = path.read_text()
        if self.kind == "postgres":
            cmd = [
                "psql",
                "--host=%s" % self.host,
                "--port=%s" % self.port,
                "--username=%s" % self.user,
                "--dbname=%s" % self.database,
                "--no-psqlrc",
                "--quiet",
                "--set=ON_ERROR_STOP=1",
                "--file=-",
            ]
        else:
            cmd = [
                "mysql",
                "--host=%s" % self.host,
                "--port=%s" % self.port,
                "--user=%s" % self.user,
                "--password=%s" % self.password,
                "--database=%s" % self.database,
                "--batch",
            ]
        proc = subprocess.run(cmd, env=self._env(), input=text, capture_output=True, text=True)
        if proc.returncode != 0:
            raise SqlError("%s: %s" % (path.name, _first_error_line(proc.stderr or proc.stdout)))


def _first_error_line(text):
    for line in (text or "").splitlines():
        line = line.strip()
        if line and "Warning" not in line and "using a password" not in line:
            return line
    return (text or "").strip() or "unknown error"


def normalise(cell):
    """Reduce an engine's textual rendering of a value to a comparable form.

    psql prints booleans as t/f and NULL as the configured token; the mysql
    client prints 1/0 and NULL. Decimals differ in trailing zeros between the
    engines depending on the expression type, so they are compared by value.
    """
    if cell in (NULL_TOKEN, "NULL"):
        return NULL_TOKEN
    if cell == "t":
        return "1"
    if cell == "f":
        return "0"
    try:
        return format(decimal.Decimal(cell).normalize(), "f")
    except (decimal.InvalidOperation, ValueError):
        return cell


class Case:
    """One .sql file: header directives plus a single statement."""

    def __init__(self, path):
        self.path = path
        self.name = path.stem
        self.description = ""
        self.expect = None
        self.setup = []
        self.teardown = []
        self.known_divergence = False
        self.compare = "ordered"
        self.sql = ""
        self._parse()

    def _parse(self):
        body = []
        description = []
        in_description = False
        for line in self.path.read_text().splitlines():
            stripped = line.strip()
            if stripped.startswith("--"):
                directive = stripped[2:].strip()
                key, _, value = directive.partition(":")
                key = key.strip().lower()
                value = value.strip()
                if key == "name":
                    self.name = value
                    in_description = False
                elif key == "description":
                    description.append(value)
                    in_description = True
                elif key == "expect":
                    self.expect = value
                    in_description = False
                elif key == "setup":
                    self.setup.append(value)
                    in_description = False
                elif key == "teardown":
                    self.teardown.append(value)
                    in_description = False
                elif key == "compare":
                    self.compare = value.lower()
                    in_description = False
                elif key == "known-divergence":
                    self.known_divergence = value.lower() in ("yes", "true")
                    in_description = False
                elif key in ("source", "class"):
                    in_description = False
                elif in_description and directive:
                    description.append(directive)
                continue
            body.append(line)
        self.description = " ".join(description).strip()
        self.sql = "\n".join(body).strip().rstrip(";")

    def sql_for(self, engine):
        """Render the statement for one engine.

        {q}      identifier quote, mirroring DatabaseSpecificSQLGenerator.escape()
        {limit}  row limit, mirroring DatabaseSpecificSQLGenerator.limit(10000, 0)
        """
        limit = "LIMIT 0,10000" if engine.kind == "mysql" else "LIMIT 10000 OFFSET 0"
        return self.sql.replace("{q}", engine.quote).replace("{limit}", limit)

    def expectation_for(self, engine):
        """For dialect cases: does the engine accept the statement?"""
        if not self.expect:
            return "pass"
        for token in self.expect.replace(",", " ").split():
            key, _, value = token.partition("=")
            if key.strip() == engine.kind:
                return value.strip()
        return "pass"


def load_cases(directory, only):
    cases = [Case(p) for p in sorted(directory.glob("*.sql"))]
    if only:
        cases = [c for c in cases if any(token in c.name for token in only)]
    return cases


def run_equivalence(source, target, only, verbose):
    cases = load_cases(SQL_DIR / "equivalence", only)
    results = []
    for case in cases:
        expect_identical = (case.expect or "identical") == "identical"
        source_rows, source_error = _safe_query(source, case)
        target_rows, target_error = _safe_query(target, case)
        if source_error or target_error:
            outcome = "ERROR"
            detail = source_error or target_error
        else:
            ordering_note = ""
            if case.compare == "unordered":
                identical = sorted(source_rows) == sorted(target_rows)
                if identical and source_rows != target_rows:
                    ordering_note = ", row order differs between engines"
            else:
                identical = source_rows == target_rows
            if identical and expect_identical:
                outcome = "PASS"
                detail = "%d rows identical%s" % (len(source_rows), ordering_note)
            elif not identical and not expect_identical:
                outcome = "PASS"
                detail = "divergence reproduced as documented"
            elif identical and not expect_identical:
                outcome = "FAIL"
                detail = "expected a documented divergence but results were identical"
            else:
                outcome = "FAIL"
                detail = "results differ"
        results.append((case, outcome, detail, source_rows, target_rows, source_error, target_error))
        _print_equivalence(case, outcome, detail, source_rows, target_rows, source_error, target_error,
                           source, target, verbose or outcome != "PASS")
    return results


def _safe_query(engine, case):
    try:
        return engine.query(case.sql_for(engine)), None
    except SqlError as exc:
        return None, "%s: %s" % (engine.kind, exc)


def _print_equivalence(case, outcome, detail, source_rows, target_rows, source_error, target_error,
                       source, target, show_detail):
    print("  [%-4s] %-40s %s" % (outcome, case.name, detail))
    if not show_detail:
        return
    if source_error:
        print("         %s error: %s" % (source.kind, source_error))
    if target_error:
        print("         %s error: %s" % (target.kind, target_error))
    if source_rows is not None and target_rows is not None and source_rows != target_rows:
        for index in range(max(len(source_rows), len(target_rows))):
            left = source_rows[index] if index < len(source_rows) else None
            right = target_rows[index] if index < len(target_rows) else None
            if left != right:
                print("         row %d  %s: %s" % (index, source.kind, left))
                print("         row %d  %s: %s" % (index, target.kind, right))


def run_dialect(source, target, only, verbose):
    cases = load_cases(SQL_DIR / "dialect", only)
    results = []
    for case in cases:
        observed = {}
        rows = {}
        for engine in (source, target):
            for statement in case.setup:
                try:
                    engine.execute(statement)
                except SqlError as exc:
                    print("         %s setup failed: %s" % (engine.kind, exc))
            try:
                rows[engine.kind] = engine.query(case.sql_for(engine))
                observed[engine.kind] = ("pass", "")
            except SqlError as exc:
                rows[engine.kind] = None
                observed[engine.kind] = ("fail", str(exc))
            for statement in case.teardown:
                try:
                    engine.execute(statement)
                except SqlError:
                    pass
        matched = all(observed[e.kind][0] == case.expectation_for(e) for e in (source, target))
        divergent_rows = (rows.get(source.kind) is not None and rows.get(target.kind) is not None
                          and rows[source.kind] != rows[target.kind])
        if divergent_rows and not case.known_divergence:
            matched = False
        outcome = "PASS" if matched else "FAIL"
        summary = ", ".join("%s=%s" % (e.kind, observed[e.kind][0]) for e in (source, target))
        if divergent_rows:
            summary += ", results differ"
        results.append((case, outcome, summary, observed, rows))
        print("  [%-4s] %-40s %s" % (outcome, case.name, summary))
        if verbose or outcome == "FAIL":
            for engine in (source, target):
                state, message = observed[engine.kind]
                if state == "fail":
                    print("         %s rejected: %s" % (engine.kind, message))
                elif divergent_rows:
                    print("         %s returned: %s" % (engine.kind, rows[engine.kind]))
    return results


def seed(engine, verbose):
    for script in sorted((SQL_DIR / "schema").glob("*.sql")):
        if verbose:
            print("  %s <- %s" % (engine.kind, script.name))
        engine.execute_script(script)


def engine_from(role, args, default_kind, default_port, default_user, default_password, default_db):
    prefix = role.upper()
    kind = getattr(args, "%s_engine" % role) or os.environ.get("%s_ENGINE" % prefix) or default_kind
    return Engine(
        role=role,
        kind=kind,
        host=getattr(args, "%s_host" % role) or os.environ.get("%s_HOST" % prefix) or "127.0.0.1",
        port=getattr(args, "%s_port" % role) or os.environ.get("%s_PORT" % prefix) or default_port,
        user=getattr(args, "%s_user" % role) or os.environ.get("%s_USER" % prefix) or default_user,
        password=(getattr(args, "%s_password" % role) if getattr(args, "%s_password" % role) is not None
                  else os.environ.get("%s_PASSWORD" % prefix, default_password)),
        database=getattr(args, "%s_db" % role) or os.environ.get("%s_DB" % prefix) or default_db,
    )


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    for role in ("source", "target"):
        parser.add_argument("--%s-engine" % role, choices=("mysql", "postgres"))
        parser.add_argument("--%s-host" % role)
        parser.add_argument("--%s-port" % role)
        parser.add_argument("--%s-user" % role)
        parser.add_argument("--%s-password" % role)
        parser.add_argument("--%s-db" % role)
    parser.add_argument("--schema", choices=("subset", "live"), default="subset",
                        help="subset: harness creates and seeds its own schema subset (default). "
                             "live: run read-only against an existing Fineract tenant schema.")
    parser.add_argument("--no-seed", action="store_true", help="skip schema creation and seeding")
    parser.add_argument("--suite", choices=("all", "equivalence", "dialect"), default="all")
    parser.add_argument("--only", action="append", default=[], help="substring filter on case name, repeatable")
    parser.add_argument("--verbose", action="store_true")
    args = parser.parse_args(argv)

    source = engine_from("source", args, "mysql", "3306", "root", "mysql", "fineract_ws3_equivalence")
    target = engine_from("target", args, "postgres", "5432", "root", "postgres", "fineract_ws3_equivalence")

    print("WS3 cross-engine equivalence harness")
    print("  source: %s" % source)
    print("  target: %s" % target)
    print("  schema: %s" % args.schema)
    try:
        print("  source version: %s" % source.version())
        print("  target version: %s" % target.version())
    except SqlError as exc:
        print("Cannot reach an engine: %s" % exc, file=sys.stderr)
        return 2

    if args.schema == "live" and not args.no_seed:
        print("  refusing to seed in live mode; re-run with --no-seed", file=sys.stderr)
        return 2

    if not args.no_seed:
        print("\nSeeding identical data into both engines")
        try:
            seed(source, args.verbose)
            seed(target, args.verbose)
        except SqlError as exc:
            print("Seeding failed: %s" % exc, file=sys.stderr)
            return 2

    failures = 0
    if args.suite in ("all", "equivalence"):
        print("\nEquivalence cases (identical results required)")
        for _case, outcome, _detail, _s, _t, _se, _te in run_equivalence(source, target, args.only, args.verbose):
            failures += outcome != "PASS"
    if args.suite in ("all", "dialect"):
        if args.schema == "live":
            print("\nDialect cases skipped in live mode")
        else:
            print("\nDialect cases (declared engine acceptance required)")
            for _case, outcome, _summary, _observed, _rows in run_dialect(source, target, args.only, args.verbose):
                failures += outcome != "PASS"

    print("\n%s" % ("FAILED: %d case(s) did not match expectation" % failures if failures else "OK: all cases matched expectation"))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
