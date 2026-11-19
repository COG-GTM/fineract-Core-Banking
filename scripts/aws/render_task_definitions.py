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

"""Renders ECS task definition templates for the AWS profile.

Sources: config/aws/ecs/services.json (one entry per ECS service), config/aws/env/*.env (plain settings,
later files win, as with docker compose env_file) and config/aws/secrets.env (Secrets Manager references).
Output: config/aws/ecs/task-definitions/<service>.json with ${...} placeholders that the deploy pipeline
substitutes (see config/aws/README.md).

  render_task_definitions.py           write the templates
  render_task_definitions.py --check   fail if the committed templates are out of date
"""

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
AWS = ROOT / "config" / "aws"
OUT = AWS / "ecs" / "task-definitions"


def read_env_file(path):
    values = {}
    for line_no, raw in enumerate(path.read_text().splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise SystemExit(f"{path.relative_to(ROOT)}:{line_no}: expected NAME=value")
        name, value = line.split("=", 1)
        values[name.strip()] = value
    return values


def placeholder(name):
    return "${" + name + "}"


def render(service, spec, secrets):
    environment = {}
    for env_file in spec["envFiles"]:
        environment.update(read_env_file(AWS / "env" / env_file))
    for name in secrets:
        environment.pop(name, None)

    container = {
        "name": service,
        "image": placeholder("IMAGE_URI"),
        "essential": True,
        "environment": [{"name": k, "value": v} for k, v in sorted(environment.items())],
        "secrets": [{"name": k, "valueFrom": v} for k, v in sorted(secrets.items())],
        "logConfiguration": {
            "logDriver": "awslogs",
            "options": {
                "awslogs-group": "/ecs/fineract/" + placeholder("ENVIRONMENT") + "/" + service,
                "awslogs-region": placeholder("AWS_REGION"),
                "awslogs-stream-prefix": service,
            },
        },
    }
    if spec["port"]:
        container["portMappings"] = [{"containerPort": spec["port"], "protocol": "tcp"}]

    role_suffix = service.upper().replace("-", "_")
    return {
        "family": service + "-" + placeholder("ENVIRONMENT"),
        "networkMode": "awsvpc",
        "requiresCompatibilities": ["FARGATE"],
        "cpu": str(spec["cpu"]),
        "memory": str(spec["memory"]),
        "runtimePlatform": {"operatingSystemFamily": "LINUX", "cpuArchitecture": placeholder("CPU_ARCHITECTURE")},
        "executionRoleArn": placeholder("ECS_EXECUTION_ROLE_ARN"),
        "taskRoleArn": placeholder("TASK_ROLE_ARN_" + role_suffix),
        "containerDefinitions": [container],
    }


def render_all():
    services = json.loads((AWS / "ecs" / "services.json").read_text())
    secrets = read_env_file(AWS / "secrets.env")
    return {
        OUT / f"{service}.json": json.dumps(render(service, spec, secrets), indent=2) + "\n"
        for service, spec in services.items()
    }


def main(argv):
    check = "--check" in argv
    rendered = render_all()
    stale = [p for p, text in rendered.items() if not p.exists() or p.read_text() != text]
    extra = [p for p in OUT.glob("*.json") if p not in rendered]
    if check:
        for path in stale + extra:
            print(f"out of date: {path.relative_to(ROOT)}")
        if stale or extra:
            print("Run scripts/aws/render_task_definitions.py and commit the result.")
            return 1
        print(f"{len(rendered)} task definitions up to date")
        return 0
    OUT.mkdir(parents=True, exist_ok=True)
    for path in extra:
        path.unlink()
    for path, text in rendered.items():
        path.write_text(text)
    print(f"wrote {len(rendered)} task definitions to {OUT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
