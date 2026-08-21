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

# Dev wiring for the WS4 runtime. Every input marked "WS1 contract" is an output of the
# WS1 landing zone stack; once WS1 lands in envs/dev this file collapses into a module
# block that reads module.network / module.security / module.edge directly.

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Application = "fineract"
      Environment = var.environment
      ManagedBy   = "terraform"
      Repository  = "COG-GTM/fineract-Core-Banking"
      Workstream  = "WS4"
    }
  }
}

module "runtime" {
  source = "../../modules/runtime"

  name_prefix = "fineract-${var.environment}"
  environment = var.environment
  region      = var.region

  # WS2 publishes immutable digests; the pipeline passes the promoted digest here.
  image_uri = var.image_uri

  private_app_subnet_ids  = var.private_app_subnet_ids
  tasks_security_group_id = var.tasks_security_group_id
  target_group_arn        = var.target_group_arn
  target_group_arn_suffix = var.target_group_arn_suffix
  alb_arn_suffix          = var.alb_arn_suffix
  kms_key_arn             = var.kms_key_arn

  # Dev runs at a quarter of the README sizing; prod keeps the module defaults
  # (8 vCPU / 16 GB) until Q-PLT-7 replaces them with load-test numbers.
  task_cpu    = 2048
  task_memory = 4096

  desired_count = 2
  min_capacity  = 2
  max_capacity  = 4

  environment_variables = {
    FINERACT_NODE_ID                                  = "1"
    FINERACT_HIKARI_DRIVER_CLASS_NAME                 = "org.postgresql.Driver"
    FINERACT_HIKARI_JDBC_URL                          = var.tenants_jdbc_url
    FINERACT_HIKARI_MAXIMUM_POOL_SIZE                 = "10"
    FINERACT_DEFAULT_TENANTDB_HOSTNAME                = var.tenant_db_hostname
    FINERACT_DEFAULT_TENANTDB_PORT                    = "5432"
    FINERACT_MANAGEMENT_ENDPOINT_WEB_EXPOSURE_INCLUDE = "health,info,prometheus"
    SPRING_PROFILES_ACTIVE                            = "prod"
  }

  secret_arns = var.secret_arns
}
