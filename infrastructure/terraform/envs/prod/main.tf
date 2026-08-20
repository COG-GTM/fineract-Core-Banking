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

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Application = "fineract"
      Environment = var.environment
      ManagedBy   = "terraform"
      Repository  = "COG-GTM/fineract-Core-Banking"
      Workstream  = "WS1"
    }
  }
}

locals {
  name_prefix = "fineract-${var.environment}"
}

module "network" {
  source = "../../modules/network"

  name_prefix                = local.name_prefix
  environment                = var.environment
  vpc_cidr                   = var.vpc_cidr
  availability_zones         = var.availability_zones
  public_subnet_cidrs        = var.public_subnet_cidrs
  private_app_subnet_cidrs   = var.private_app_subnet_cidrs
  private_data_subnet_cidrs  = var.private_data_subnet_cidrs
  single_nat_gateway         = false
  enable_interface_endpoints = true
}

module "security" {
  source = "../../modules/security"

  name_prefix                 = local.name_prefix
  environment                 = var.environment
  vpc_id                      = module.network.vpc_id
  account_id                  = var.account_id
  alb_ingress_cidrs           = var.alb_ingress_cidrs
  task_port                   = var.task_port
  enable_flow_logs            = true
  flow_logs_retention_days    = 365
  enable_cloudtrail           = var.enable_cloudtrail
  enable_config               = var.enable_config
  kms_deletion_window_in_days = 30
}

module "dns" {
  source = "../../modules/dns"

  name_prefix               = local.name_prefix
  environment               = var.environment
  domain_name               = var.domain_name
  subject_alternative_names = var.subject_alternative_names
  create_hosted_zone        = var.create_hosted_zone
  hosted_zone_id            = var.hosted_zone_id
  validate_certificate      = var.validate_certificate
}

module "edge" {
  source = "../../modules/edge"

  name_prefix                = local.name_prefix
  environment                = var.environment
  vpc_id                     = module.network.vpc_id
  public_subnet_ids          = module.network.public_subnet_ids
  alb_security_group_id      = module.security.alb_security_group_id
  certificate_arn            = module.dns.certificate_arn
  hosted_zone_id             = module.dns.hosted_zone_id
  record_name                = var.api_record_name
  target_port                = var.task_port
  waf_log_kms_key_arn        = module.security.kms_key_arns["logs"]
  waf_rate_limit             = 10000
  enable_deletion_protection = true
}
