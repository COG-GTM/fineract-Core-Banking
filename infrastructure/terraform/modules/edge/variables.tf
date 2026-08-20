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
  description = "Prefix applied to the name of every resource created by this module, e.g. fineract-dev."
  type        = string
}

variable "vpc_id" {
  description = "Identifier of the VPC created by the network module."
  type        = string
}

variable "public_subnet_ids" {
  description = "Public subnet identifiers, one per availability zone, in which the internet-facing ALB is placed."
  type        = list(string)

  validation {
    condition     = length(var.public_subnet_ids) >= 2
    error_message = "An internet-facing ALB requires public subnets in at least two availability zones."
  }
}

variable "domain_name" {
  description = "Fully qualified domain name served by the ALB, e.g. api.dev.fineract.example.com."
  type        = string
}

variable "hosted_zone_name" {
  description = "Route 53 hosted zone that owns domain_name, e.g. dev.fineract.example.com."
  type        = string
}

variable "create_hosted_zone" {
  description = "Create the Route 53 public hosted zone. Set to false when the zone is delegated and managed elsewhere."
  type        = bool
  default     = true
}

variable "subject_alternative_names" {
  description = "Additional names to include on the ACM certificate."
  type        = list(string)
  default     = []
}

variable "target_port" {
  description = "Port the Fineract tasks listen on behind the ALB."
  type        = number
  default     = 8443
}

variable "target_protocol" {
  description = "Protocol used between the ALB and the Fineract tasks."
  type        = string
  default     = "HTTPS"

  validation {
    condition     = contains(["HTTP", "HTTPS"], var.target_protocol)
    error_message = "target_protocol must be HTTP or HTTPS."
  }
}

variable "health_check_path" {
  description = "Target group health check path; matches the actuator probe the tasks already expose."
  type        = string
  default     = "/fineract-provider/actuator/health"
}

variable "ssl_policy" {
  description = "ELB security policy for the HTTPS listener. Only TLS 1.2+ policies are accepted."
  type        = string
  default     = "ELBSecurityPolicy-TLS13-1-2-2021-06"

  validation {
    condition     = can(regex("^ELBSecurityPolicy-(TLS13-1-2|TLS-1-2)", var.ssl_policy))
    error_message = "ssl_policy must be a TLS 1.2 or TLS 1.3 policy; older policies negotiate TLS 1.0/1.1."
  }
}

variable "ingress_cidr_blocks" {
  description = "Source CIDR blocks allowed to reach the ALB on 80 and 443."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "waf_rate_limit" {
  description = "Requests per five-minute window from a single IP before the WAF rate-based rule blocks it."
  type        = number
  default     = 2000
}

variable "waf_managed_rule_groups" {
  description = "AWS managed rule groups attached to the web ACL, in evaluation order."
  type        = list(string)
  default = [
    "AWSManagedRulesCommonRuleSet",
    "AWSManagedRulesKnownBadInputsRuleSet",
    "AWSManagedRulesSQLiRuleSet",
    "AWSManagedRulesAmazonIpReputationList",
  ]
}

variable "enable_deletion_protection" {
  description = "Enable ALB deletion protection. Should be true in production."
  type        = bool
  default     = false
}

variable "access_logs_bucket" {
  description = "S3 bucket for ALB access logs. Access logging is disabled when null."
  type        = string
  default     = null
}

variable "idle_timeout" {
  description = "ALB idle timeout in seconds. Long enough for report generation requests."
  type        = number
  default     = 120
}

variable "tags" {
  description = "Tags applied to every resource created by this module."
  type        = map(string)
  default     = {}
}
