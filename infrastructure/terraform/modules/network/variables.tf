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

variable "project" {
  description = "Project name used as a prefix for all resource names."
  type        = string
  default     = "fineract"
}

variable "environment" {
  description = "Environment name (dev, staging, prod)."
  type        = string
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC."
  type        = string
}

variable "availability_zones" {
  description = "Availability zones to spread the private and public subnets across."
  type        = list(string)
}

variable "log_retention_days" {
  description = "Retention in days for the VPC flow log group."
  type        = number
  default     = 365
}

variable "logs_kms_key_arn" {
  description = "KMS key ARN used to encrypt the VPC flow log group."
  type        = string
}

variable "tags" {
  description = "Additional tags applied to every resource."
  type        = map(string)
  default     = {}
}
