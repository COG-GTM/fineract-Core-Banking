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

variable "name_prefix" {
  description = "Prefix applied to the Name tag of every resource, e.g. fineract-dev."
  type        = string
}

variable "environment" {
  description = "Environment identifier (dev, prod). Used for tagging only."
  type        = string
}

variable "vpc_cidr" {
  description = "IPv4 CIDR block of the VPC."
  type        = string
}

variable "availability_zones" {
  description = "Exactly three availability zone names. Passed explicitly rather than discovered so that a plan is deterministic and reviewable without AWS credentials."
  type        = list(string)

  validation {
    condition     = length(var.availability_zones) == 3
    error_message = "Three availability zones are required (target-state.md assumed posture, Q-PRG-2)."
  }
}

variable "public_subnet_cidrs" {
  description = "One CIDR per availability zone for the public (ALB-only) tier."
  type        = list(string)
}

variable "private_app_subnet_cidrs" {
  description = "One CIDR per availability zone for the private application tier (ECS tasks, WS4)."
  type        = list(string)
}

variable "private_data_subnet_cidrs" {
  description = "One CIDR per availability zone for the private data tier (Aurora PostgreSQL, MSK)."
  type        = list(string)
}

variable "single_nat_gateway" {
  description = "true deploys one NAT Gateway shared by all AZs (dev), false deploys one per AZ (prod)."
  type        = bool
  default     = false
}

variable "enable_interface_endpoints" {
  description = "Create the interface (PrivateLink) endpoints. The S3 gateway endpoint is always created because it is free."
  type        = bool
  default     = true
}

variable "interface_endpoint_services" {
  description = "Service short names for the interface endpoints created in the application tier."
  type        = list(string)
  default = [
    "ecr.api",
    "ecr.dkr",
    "secretsmanager",
    "ssm",
    "ssmmessages",
    "logs",
    "kms",
  ]
}

variable "tags" {
  description = "Tags merged into every resource."
  type        = map(string)
  default     = {}
}
