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

// Provider-free fixture used by the IaC pipeline dry-run: it exercises init,
// fmt, validate and plan without needing AWS credentials.
terraform {
  required_version = ">= 1.9.0"

  required_providers {
    null = {
      source  = "hashicorp/null"
      version = "~> 3.2"
    }
  }
}

variable "environment" {
  description = "Environment name under test."
  type        = string
  default     = "dry-run"
}

resource "null_resource" "fixture" {
  triggers = {
    environment = var.environment
  }
}

output "environment" {
  description = "Environment name echoed back by the fixture."
  value       = null_resource.fixture.triggers.environment
}
