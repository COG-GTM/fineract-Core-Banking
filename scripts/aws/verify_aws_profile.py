#!/usr/bin/env python3

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

"""Contract checks for the AWS profile (config/aws) and its rendered ECS task definitions.

Fails when:
  * a FINERACT_* variable set in the profile is not read by any application*.properties (typo / dead setting)
  * a credential that application.properties defaults to a literal (e.g. FINERACT_HIKARI_PASSWORD=mysql)
    is not supplied from Secrets Manager
  * a credential-like variable has a literal value in a task definition "environment" block
  * a "secrets" entry does not reference a ${SECRET_ARN_*} placeholder
  * a JVM debug agent (JDWP) is configured
  * a service's FINERACT_MODE_* flags differ from its declared modes in services.json
  * a task definition uses a placeholder that is not documented in config/aws/README.md
"""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
AWS = ROOT / "config" / "aws"
TASK_DEFS = AWS / "ecs" / "task-definitions"

CREDENTIAL = re.compile(r"(PASSWORD|_PWD$|SECRET|ACCESS_KEY|_TOKEN$)")
DEBUG_AGENT = re.compile(r"(-agentlib:jdwp|-Xrunjdwp|-Xdebug)")
PROPERTY_REF = re.compile(r"\$\{(FINERACT_[A-Z0-9_]+)(?::([^}]*))?\}")
PLACEHOLDER = re.compile(r"\$\{([A-Z0-9_]+)\}")
MODES = {
    "read": "FINERACT_MODE_READ_ENABLED",
    "write": "FINERACT_MODE_WRITE_ENABLED",
    "batch-manager": "FINERACT_MODE_BATCH_MANAGER_ENABLED",
    "batch-worker": "FINERACT_MODE_BATCH_WORKER_ENABLED",
}


def application_properties():
    """FINERACT_* variable -> default value (None when there is no default) across all application*.properties."""
    refs = {}
    for path in ROOT.glob("*/src/main/resources/application*.properties"):
        for name, default in PROPERTY_REF.findall(path.read_text()):
            refs.setdefault(name, default)
    return refs


def main():
    errors = []
    refs = application_properties()
    if not refs:
        return fail(["no application*.properties found"])
    services = json.loads((AWS / "ecs" / "services.json").read_text())
    documented = set(re.findall(r"`([A-Z][A-Z0-9_]+)`", (AWS / "README.md").read_text()))
    literal_credentials = {n for n, d in refs.items() if CREDENTIAL.search(n) and d}

    for service, spec in services.items():
        path = TASK_DEFS / f"{service}.json"
        if not path.exists():
            errors.append(f"{service}: missing {path.relative_to(ROOT)}")
            continue
        task_def = json.loads(path.read_text())
        container = task_def["containerDefinitions"][0]
        env = {e["name"]: e["value"] for e in container.get("environment", [])}
        secrets = {s["name"]: s["valueFrom"] for s in container.get("secrets", [])}

        for name, value in env.items():
            if name.startswith("FINERACT_") and name not in refs:
                errors.append(f"{service}: {name} is not read by any application*.properties")
            if CREDENTIAL.search(name) and value:
                errors.append(f"{service}: credential {name} has a literal value; move it to config/aws/secrets.env")
            if DEBUG_AGENT.search(value):
                errors.append(f"{service}: {name} enables a JVM debug agent")
        for name, value_from in secrets.items():
            if name in env:
                errors.append(f"{service}: {name} is set both as plain environment and as a secret")
            if not re.fullmatch(r"\$\{SECRET_ARN_[A-Z0-9_]+\}(:[^:]*:[^:]*:[^:]*)?", value_from):
                errors.append(f"{service}: secret {name} must reference a ${{SECRET_ARN_*}} placeholder")
        for name in sorted(literal_credentials - secrets.keys()):
            if name in env or name.startswith(("FINERACT_HIKARI_", "FINERACT_DEFAULT_", "FINERACT_SERVER_SSL_")):
                errors.append(f"{service}: {name} defaults to a literal in application.properties; supply it from Secrets Manager")

        if spec["modes"]:
            for mode, var in MODES.items():
                expected = "true" if mode in spec["modes"] else "false"
                if env.get(var) != expected:
                    errors.append(f"{service}: {var} must be {expected}")
            if env.get("FINERACT_LIQUIBASE_ENABLED") != "false":
                errors.append(f"{service}: app services must run with FINERACT_LIQUIBASE_ENABLED=false")
        for name in sorted(set(PLACEHOLDER.findall(path.read_text())) - documented):
            errors.append(f"{service}: placeholder ${{{name}}} is not documented in config/aws/README.md")

    if errors:
        return fail(errors)
    print(f"AWS profile OK: {len(services)} task definitions checked")
    return 0


def fail(errors):
    for error in errors:
        print(f"ERROR: {error}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
