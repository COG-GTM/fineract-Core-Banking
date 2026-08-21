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

terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }
}

provider "aws" {
  region = var.region
}

# The WS1 network module owns the dev VPC. Until it exists, the stack accepts the network as input
# and falls back to the account default VPC so that WS3 is not blocked on WS1.
data "aws_vpc" "selected" {
  id      = var.vpc_id
  default = var.vpc_id == null ? true : null
}

data "aws_subnets" "selected" {
  count = length(var.subnet_ids) == 0 ? 1 : 0

  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.selected.id]
  }
}

module "aurora" {
  source = "../../modules/aurora-postgresql"

  name_prefix = var.name_prefix
  vpc_id      = data.aws_vpc.selected.id
  subnet_ids  = length(var.subnet_ids) > 0 ? var.subnet_ids : data.aws_subnets.selected[0].ids

  allowed_cidr_blocks        = var.allowed_cidr_blocks
  allowed_security_group_ids = var.allowed_security_group_ids

  instance_class  = var.instance_class
  instance_count  = 1
  max_connections = var.max_connections

  # Dev posture: single-AZ writer, recyclable, reachable from the CI runner that drives the suites.
  publicly_accessible            = var.publicly_accessible
  deletion_protection            = false
  skip_final_snapshot            = true
  apply_immediately              = true
  backup_retention_period        = 1
  performance_insights_enabled   = false
  secret_recovery_window_in_days = 0

  tags = {
    Environment = "dev"
    Application = "fineract"
    Workstream  = "WS3"
    ManagedBy   = "terraform"
  }
}
