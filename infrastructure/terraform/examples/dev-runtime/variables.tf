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

variable "region" {
  description = "AWS region."
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name."
  type        = string
  default     = "dev"
}

variable "image_uri" {
  description = "ECR image pinned by digest, promoted by the WS2 pipeline."
  type        = string
}

variable "private_app_subnet_ids" {
  description = "WS1 output: private_app_subnet_ids."
  type        = list(string)
}

variable "tasks_security_group_id" {
  description = "WS1 output: tasks_security_group_id."
  type        = string
}

variable "target_group_arn" {
  description = "WS1 output: target_group_arn."
  type        = string
}

variable "target_group_arn_suffix" {
  description = "Suffix portion of the WS1 target group ARN (targetgroup/<name>/<id>)."
  type        = string
}

variable "alb_arn_suffix" {
  description = "WS1 output: alb_arn_suffix."
  type        = string
}

variable "kms_key_arn" {
  description = "WS1 output: kms_key_arns[\"logs\"]."
  type        = string
}

variable "tenants_jdbc_url" {
  description = "JDBC URL of the Aurora tenants database provisioned by WS3."
  type        = string
}

variable "tenant_db_hostname" {
  description = "Aurora writer endpoint used when creating tenant databases."
  type        = string
}

variable "secret_arns" {
  description = "Secrets Manager ARNs keyed by the environment variable they populate."
  type        = map(string)
  default     = {}
}
