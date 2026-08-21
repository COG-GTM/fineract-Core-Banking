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
"""WS3 raw-SQL dialect scan (MariaDB/MySQL -> PostgreSQL).

Produces the census and hazard tables in docs/migration/ws3-dialect-audit.md:

  * counts JdbcTemplate call sites, EntityManager users and createNativeQuery sites
    in the main sources (custom/ excluded, matching the architecture package scope),
  * classifies each JdbcTemplate file by whether it builds SQL through
    DatabaseSpecificSQLGenerator,
  * flags the dialect failure classes named in the migration plan,
  * cross-references columns declared boolean in the Liquibase changelogs against raw
    SQL comparing them to the integer literals 0/1 (the PostgreSQL
    "operator does not exist: boolean = integer" class).

Usage:
    python3 scripts/ws3/dialect_scan.py [--json out.json] [--appendix out.md]
"""
import argparse
import json
import re
import subprocess
from collections import Counter, defaultdict
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
CHANGELOG = REPO / "fineract-provider/src/main/resources/db/changelog"

HAZARDS = [
    ("backtick_quoting", re.compile(r"`")),
    ("if_function", re.compile(r"\bIF\s*\(", re.I)),
    ("group_by_direction", re.compile(r"group\s+by[^\"]*\b(asc|desc)\b", re.I)),
    ("date_arith", re.compile(r"\b(DATE_ADD|DATE_SUB|ADDDATE|SUBDATE|DATEDIFF|INTERVAL\s+\d|CURDATE|NOW\s*\(|UNIX_TIMESTAMP|STR_TO_DATE|DATE_FORMAT)\b", re.I)),
    ("string_concat", re.compile(r"\bCONCAT\s*\(|\|\|", re.I)),
    ("limit_offset", re.compile(r"\bLIMIT\s+\d|\bLIMIT\s+\?|\bOFFSET\b", re.I)),
    ("bool_int_shaped", re.compile(r"\b\w+\s*(=|<>|!=)\s*[01]\b")),
    ("mysql_ddl", re.compile(r"\bDROP\s+FOREIGN\s+KEY\b|\bDROP\s+KEY\b|\bADD\s+KEY\b|\bCHANGE\s+COLUMN\b|AUTO_INCREMENT|\bSHOW\s+(TABLES|COLUMNS|INDEX)\b|ENGINE\s*=", re.I)),
    ("mysql_functions", re.compile(r"\b(GROUP_CONCAT|IFNULL|SUBSTRING_INDEX|FIND_IN_SET|LAST_INSERT_ID|RAND\s*\()\b", re.I)),
]

SQL_KEYWORD = re.compile(r"\b(select|insert into|update |delete from|alter table|create table|drop table)\b", re.I)
STRING_LITERAL = re.compile(r"\"((?:[^\"\\]|\\.)*)\"")


def main_sources():
    out = subprocess.run(["git", "ls-files", "*.java"], cwd=REPO, capture_output=True, text=True, check=True).stdout
    return [f for f in out.split() if "/src/main/java/" in f and not f.startswith("custom/")]


def boolean_columns():
    """Column names declared boolean/tinyint(1)/bit(1) anywhere in the Liquibase changelogs."""
    cols = set()
    named = re.compile(r"<column[^>]*name=\"([A-Za-z0-9_]+)\"[^>]*type=\"(?i:boolean|bit\(1\)|tinyint\(1\))\"")
    typed = re.compile(r"<column[^>]*type=\"(?i:boolean|bit\(1\)|tinyint\(1\))\"[^>]*name=\"([A-Za-z0-9_]+)\"")
    for xml in CHANGELOG.rglob("*.xml"):
        text = xml.read_text(errors="ignore")
        cols.update(m.group(1).lower() for m in named.finditer(text))
        cols.update(m.group(1).lower() for m in typed.finditer(text))
    return cols


def scan():
    bool_cols = boolean_columns()
    bool_cmp = re.compile(r"([A-Za-z0-9_]+)\.?([A-Za-z0-9_]*)\s*(=|<>|!=)\s*([01])\b")

    jdbc_files = jdbc_occurrences = 0
    em_files, native_files = [], []
    per_file, findings = {}, defaultdict(list)
    typed_bool_defects = []

    for rel in main_sources():
        text = (REPO / rel).read_text(errors="ignore")
        if "EntityManager" in text:
            em_files.append(rel)
        if "createNativeQuery" in text:
            native_files.append(rel)
        occurrences = text.count("JdbcTemplate")
        if occurrences == 0:
            continue
        jdbc_files += 1
        jdbc_occurrences += occurrences
        uses_generator = "DatabaseSpecificSQLGenerator" in text or "sqlGenerator" in text
        statements = 0
        hazards = Counter()
        for lineno, line in enumerate(text.splitlines(), 1):
            for literal in STRING_LITERAL.findall(line):
                is_sql = bool(SQL_KEYWORD.search(literal))
                if is_sql:
                    statements += 1
                elif len(literal) < 12:
                    continue
                for name, pattern in HAZARDS:
                    if pattern.search(literal):
                        hazards[name] += 1
                        findings[name].append([rel, lineno, literal.strip()[:200], uses_generator])
                for match in bool_cmp.finditer(literal):
                    column = (match.group(2) or match.group(1)).lower()
                    if column in bool_cols:
                        typed_bool_defects.append([rel, lineno, column, literal.strip()[:200]])
        per_file[rel] = {"jdbc_refs": occurrences, "statements": statements,
                         "generator": uses_generator, "hazards": dict(hazards)}

    return {
        "summary": {
            "main_java_files": len(main_sources()),
            "jdbc_files": jdbc_files,
            "jdbc_occurrences": jdbc_occurrences,
            "entitymanager_files": len(em_files),
            "createnativequery_files": native_files,
            "generator_yes": sum(1 for v in per_file.values() if v["generator"]),
            "generator_no": sum(1 for v in per_file.values() if not v["generator"]),
            "boolean_columns_in_schema": len(bool_cols),
            "hazard_counts": {k: len(v) for k, v in sorted(findings.items())},
            "hazard_counts_bypassing_generator": {k: len([x for x in v if not x[3]]) for k, v in sorted(findings.items())},
            "typed_boolean_integer_comparisons": len(typed_bool_defects),
        },
        "per_file": per_file,
        "findings": {k: v for k, v in sorted(findings.items())},
        "typed_boolean_integer_comparisons": typed_bool_defects,
        "entitymanager_files": sorted(em_files),
    }


def appendix(report):
    lines = ["| File | `JdbcTemplate` refs | SQL statements | Routes via `DatabaseSpecificSQLGenerator` | Hazard classes detected |",
             "|---|---:|---:|---|---|"]
    for rel, v in sorted(report["per_file"].items()):
        hazards = ", ".join(f"{k} ({n})" for k, n in sorted(v["hazards"].items())) or "—"
        lines.append(f"| `{rel}` | {v['jdbc_refs']} | {v['statements']} | {'yes' if v['generator'] else 'no'} | {hazards} |")
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--json", help="write the full report as JSON to this path")
    parser.add_argument("--appendix", help="write the per-file markdown table to this path")
    args = parser.parse_args()

    report = scan()
    print(json.dumps(report["summary"], indent=2))
    for rel, lineno, column, literal in report["typed_boolean_integer_comparisons"]:
        print(f"boolean-vs-integer: {rel}:{lineno} [{column}] {literal}")
    if args.json:
        Path(args.json).write_text(json.dumps(report, indent=2))
    if args.appendix:
        Path(args.appendix).write_text(appendix(report))
