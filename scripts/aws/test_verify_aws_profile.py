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

"""Proves verify_aws_profile.py and render_task_definitions.py --check fail on bad AWS profile changes."""

import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVICE = "fineract-read"


class VerifyAwsProfileTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.tmp)
        shutil.copytree(ROOT / "config" / "aws", self.tmp / "config" / "aws")
        shutil.copytree(ROOT / "scripts" / "aws", self.tmp / "scripts" / "aws")
        for props in ROOT.glob("*/src/main/resources/application*.properties"):
            target = self.tmp / props.relative_to(ROOT)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy(props, target)
        self.task_def = self.tmp / "config" / "aws" / "ecs" / "task-definitions" / f"{SERVICE}.json"

    def run_script(self, *args):
        return subprocess.run([sys.executable, *args], cwd=self.tmp, capture_output=True, text=True)

    def verify(self):
        return self.run_script("scripts/aws/verify_aws_profile.py")

    def edit_container(self, change):
        task_def = json.loads(self.task_def.read_text())
        change(task_def["containerDefinitions"][0])
        self.task_def.write_text(json.dumps(task_def))

    def set_env(self, name, value):
        def change(container):
            container["environment"] = [e for e in container["environment"] if e["name"] != name]
            container["environment"].append({"name": name, "value": value})

        self.edit_container(change)

    def assert_fails_with(self, message):
        result = self.verify()
        self.assertEqual(1, result.returncode, result.stdout)
        self.assertIn(message, result.stdout)

    def test_committed_profile_passes(self):
        result = self.verify()
        self.assertEqual(0, result.returncode, result.stdout)
        self.assertEqual(0, self.run_script("scripts/aws/render_task_definitions.py", "--check").returncode)

    def test_literal_credential_fails(self):
        self.set_env("FINERACT_CONTENT_S3_SECRET_KEY", "not-a-real-key")
        self.assert_fails_with("credential FINERACT_CONTENT_S3_SECRET_KEY has a literal value")

    def test_missing_secret_fails(self):
        self.edit_container(lambda c: c.update(secrets=[s for s in c["secrets"] if s["name"] != "FINERACT_HIKARI_PASSWORD"]))
        self.assert_fails_with("FINERACT_HIKARI_PASSWORD defaults to a literal")

    def test_secret_without_secrets_manager_reference_fails(self):
        self.edit_container(lambda c: c["secrets"].append({"name": "FINERACT_DEFAULT_TENANTDB_RO_PWD", "valueFrom": "plain"}))
        self.assert_fails_with("must reference a ${SECRET_ARN_*} placeholder")

    def test_jdwp_agent_fails(self):
        self.set_env("JAVA_TOOL_OPTIONS", "-agentlib:jdwp=transport=dt_socket,server=y,address=*:5000")
        self.assert_fails_with("enables a JVM debug agent")

    def test_unknown_fineract_variable_fails(self):
        self.set_env("FINERACT_HIKARI_PASWORD_TYPO", "x")
        self.assert_fails_with("FINERACT_HIKARI_PASWORD_TYPO is not read by any application*.properties")

    def test_wrong_mode_fails(self):
        self.set_env("FINERACT_MODE_WRITE_ENABLED", "true")
        self.assert_fails_with("FINERACT_MODE_WRITE_ENABLED must be false")

    def test_undocumented_placeholder_fails(self):
        self.set_env("FINERACT_DEFAULT_TENANTDB_RO_NAME", "${NOT_DOCUMENTED}")
        self.assert_fails_with("placeholder ${NOT_DOCUMENTED} is not documented")

    def test_stale_template_fails(self):
        env_file = self.tmp / "config" / "aws" / "env" / "fineract-read.env"
        env_file.write_text(env_file.read_text() + "FINERACT_NODE_ID=7\n")
        result = self.run_script("scripts/aws/render_task_definitions.py", "--check")
        self.assertEqual(1, result.returncode)
        self.assertIn(f"out of date: config/aws/ecs/task-definitions/{SERVICE}.json", result.stdout)


if __name__ == "__main__":
    unittest.main(verbosity=2)
