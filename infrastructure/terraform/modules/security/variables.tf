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

variable "vpc_id" {
  description = "VPC the security groups and flow logs belong to."
  type        = string
}

variable "account_id" {
  description = "AWS account id used in KMS key policies. Leave null to look it up with the caller identity; set it to run terraform plan without credentials."
  type        = string
  default     = null
}

variable "alb_ingress_cidrs" {
  description = "Source CIDRs allowed to reach the ALB on 443. This is the only rule in this module permitted to be open to the internet."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "task_port" {
  description = "Port the Fineract container listens on. 8443 keeps the in-task TLS listener (application.properties:386-391); set 8080 if Q-PLT-4 decides TLS terminates at the ALB."
  type        = number
  default     = 8443
}

variable "database_port" {
  description = "Aurora PostgreSQL port (WS3)."
  type        = number
  default     = 5432
}

variable "msk_ports" {
  description = "MSK broker ports reachable from the application tier (WS6). 9098 is IAM SASL, 9198 is IAM SASL over the public/TLS listener on some cluster types."
  type        = list(number)
  default     = [9098]
}

variable "smtp_egress_ports" {
  description = "Ports the application tier may open outbound for the SMTP relay (ReportMailingJobEmailServiceImpl.java:94-96). Empty disables SMTP egress."
  type        = list(number)
  default     = [587]
}

variable "enable_flow_logs" {
  description = "Send VPC flow logs to CloudWatch Logs."
  type        = bool
  default     = true
}

variable "flow_logs_retention_days" {
  description = "Retention of the flow log group. Confirm against the mandated retention in Q-OPS-3."
  type        = number
  default     = 90
}

variable "log_retention_days" {
  description = "Retention of the CloudTrail CloudWatch log group when CloudTrail is enabled."
  type        = number
  default     = 365
}

variable "kms_deletion_window_in_days" {
  description = "Waiting period before a scheduled CMK deletion completes."
  type        = number
  default     = 30
}

variable "enable_cloudtrail" {
  description = "Create the account CloudTrail trail and its bucket. Off by default so that a dev account without Organizations access can still plan (Q-SEC-7)."
  type        = bool
  default     = false
}

variable "enable_config" {
  description = "Create the AWS Config recorder and delivery channel. Off by default for the same reason as enable_cloudtrail."
  type        = bool
  default     = false
}

variable "tags" {
  description = "Tags merged into every resource."
  type        = map(string)
  default     = {}
}
