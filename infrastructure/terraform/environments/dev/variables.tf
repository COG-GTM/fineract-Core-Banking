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

variable "region" {
  description = "AWS region for the dev environment."
  type        = string
  default     = "us-east-1"
}

variable "name_prefix" {
  description = "Prefix for every resource name."
  type        = string
  default     = "fineract-dev"
}

variable "vpc_id" {
  description = "VPC from the WS1 network module. Null selects the account default VPC."
  type        = string
  default     = null
}

variable "subnet_ids" {
  description = "Subnets for the DB subnet group. Empty selects every subnet in the VPC."
  type        = list(string)
  default     = []
}

variable "allowed_cidr_blocks" {
  description = "CIDR blocks allowed to reach the cluster, e.g. the address of the host running the suites."
  type        = list(string)
  default     = []
}

variable "allowed_security_group_ids" {
  description = "Security groups allowed to reach the cluster."
  type        = list(string)
  default     = []
}

variable "instance_class" {
  description = "Instance class for the writer."
  type        = string
  default     = "db.t4g.medium"
}

variable "max_connections" {
  description = "Connection ceiling. See docs/aurora-postgresql-suite.adoc for the pool maths."
  type        = number
  default     = 400
}

variable "publicly_accessible" {
  description = "Dev only: expose the writer so a CI runner outside the VPC can drive the suites."
  type        = bool
  default     = false
}
