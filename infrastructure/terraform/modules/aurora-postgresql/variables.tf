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

variable "name_prefix" {
  description = "Prefix for every resource name, conventionally <programme>-<environment>."
  type        = string
}

variable "vpc_id" {
  description = "VPC the cluster lives in. Supplied by the WS1 network module once it exists."
  type        = string
}

variable "subnet_ids" {
  description = "At least two subnets in different availability zones for the DB subnet group."
  type        = list(string)

  validation {
    condition     = length(var.subnet_ids) >= 2
    error_message = "Aurora requires subnets in at least two availability zones."
  }
}

variable "allowed_cidr_blocks" {
  description = "CIDR blocks allowed to reach the cluster port."
  type        = list(string)
  default     = []
}

variable "allowed_security_group_ids" {
  description = "Security groups allowed to reach the cluster port, e.g. the ECS task security group."
  type        = list(string)
  default     = []
}

variable "engine_version" {
  description = "Aurora PostgreSQL engine version."
  type        = string
  default     = "16.14"
}

variable "parameter_group_family" {
  description = "Cluster parameter group family, must match the engine version."
  type        = string
  default     = "aurora-postgresql16"
}

variable "initial_database_name" {
  description = "Database created with the cluster. The tenant registry Liquibase task expects fineract_tenants."
  type        = string
  default     = "fineract_tenants"
}

variable "master_username" {
  description = "Master username. Fineract's own tenant credentials are separate and live in the tenant registry."
  type        = string
  default     = "fineract"
}

variable "port" {
  description = "Cluster port."
  type        = number
  default     = 5432
}

variable "instance_count" {
  description = "Number of cluster instances. One writer in dev; a writer plus at least one reader elsewhere."
  type        = number
  default     = 1
}

variable "instance_class" {
  description = "Instance class for every cluster instance."
  type        = string
  default     = "db.t4g.medium"
}

variable "max_connections" {
  description = <<-EOT
    Cluster-wide connection ceiling. Size it from the pool maths rather than the engine default:
    (tenants x pool size per tenant x tasks) + headroom for migrations and operator sessions.
  EOT
  type        = number
  default     = 400
}

variable "idle_in_transaction_session_timeout_ms" {
  description = "Server-side reaping of connections abandoned inside a transaction."
  type        = number
  default     = 300000
}

variable "log_min_duration_statement_ms" {
  description = "Statement duration above which the statement is logged. -1 disables."
  type        = number
  default     = 1000
}

variable "publicly_accessible" {
  description = "Whether cluster instances get a public address. Dev only; never true outside dev."
  type        = bool
  default     = false
}

variable "performance_insights_enabled" {
  description = "Enable Performance Insights on cluster instances."
  type        = bool
  default     = true
}

variable "monitoring_interval" {
  description = "Enhanced monitoring interval in seconds. 0 disables."
  type        = number
  default     = 0
}

variable "backup_retention_period" {
  description = "Automated backup retention in days."
  type        = number
  default     = 7
}

variable "preferred_backup_window" {
  description = "Daily backup window in UTC."
  type        = string
  default     = "02:00-03:00"
}

variable "deletion_protection" {
  description = "Block cluster deletion. False in dev so the environment can be recycled."
  type        = bool
  default     = true
}

variable "skip_final_snapshot" {
  description = "Skip the final snapshot on destroy. True only in dev."
  type        = bool
  default     = false
}

variable "apply_immediately" {
  description = "Apply modifications immediately instead of in the maintenance window."
  type        = bool
  default     = false
}

variable "kms_key_id" {
  description = "Customer managed KMS key for storage encryption. Null uses the AWS managed RDS key."
  type        = string
  default     = null
}

variable "secret_recovery_window_in_days" {
  description = "Secrets Manager recovery window for the master credential secret."
  type        = number
  default     = 7
}

variable "tags" {
  description = "Tags applied to every resource."
  type        = map(string)
  default     = {}
}
