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
  description = "VPC the target group belongs to."
  type        = string
}

variable "public_subnet_ids" {
  description = "Public subnets the ALB is placed in. One per AZ."
  type        = list(string)
}

variable "alb_security_group_id" {
  description = "Security group of the ALB, from the security module."
  type        = string
}

variable "certificate_arn" {
  description = "ACM certificate ARN for the HTTPS listener, from the dns module."
  type        = string
}

variable "hosted_zone_id" {
  description = "Hosted zone the alias record is written into, from the dns module."
  type        = string
}

variable "record_name" {
  description = "FQDN of the alias record pointing at the ALB."
  type        = string
}

variable "target_port" {
  description = "Port the Fineract task listens on. 8443 keeps in-task TLS (application.properties:386-391); 8080 if Q-PLT-4 terminates TLS at the ALB."
  type        = number
  default     = 8443
}

variable "target_protocol" {
  description = "Protocol the ALB speaks to the task. HTTPS re-encrypts inside the VPC."
  type        = string
  default     = "HTTPS"

  validation {
    condition     = contains(["HTTP", "HTTPS"], var.target_protocol)
    error_message = "target_protocol must be HTTP or HTTPS."
  }
}

variable "health_check_path" {
  description = "Target group health check path. Mirrors the readiness probe at kubernetes/fineract-server-deployment.yml:71-86."
  type        = string
  default     = "/fineract-provider/actuator/health/readiness"
}

variable "health_check_interval" {
  description = "Seconds between health checks."
  type        = number
  default     = 30
}

variable "health_check_timeout" {
  description = "Health check timeout in seconds."
  type        = number
  default     = 10
}

variable "healthy_threshold" {
  description = "Consecutive successes before a target is considered healthy."
  type        = number
  default     = 2
}

variable "unhealthy_threshold" {
  description = "Consecutive failures before a target is considered unhealthy."
  type        = number
  default     = 3
}

variable "deregistration_delay" {
  description = "Seconds the ALB waits before deregistering a target. Fineract requests are short; 60s is enough to drain without holding a deployment open."
  type        = number
  default     = 60
}

variable "ssl_policy" {
  description = "ALB listener security policy."
  type        = string
  default     = "ELBSecurityPolicy-TLS13-1-2-2021-06"
}

variable "enable_deletion_protection" {
  description = "Deletion protection on the ALB. True in prod."
  type        = bool
  default     = false
}

variable "enable_http_redirect" {
  description = "Add a port 80 listener that redirects to 443. The ALB security group does not open 80, so this is off by default."
  type        = bool
  default     = false
}

variable "waf_rate_limit" {
  description = "Requests per five minutes from a single IP before the rate-based rule blocks it."
  type        = number
  default     = 2000
}

variable "waf_managed_rule_groups" {
  description = "AWS managed rule groups attached to the web ACL, in evaluation order."
  type = list(object({
    name     = string
    priority = number
  }))

  default = [
    { name = "AWSManagedRulesCommonRuleSet", priority = 10 },
    { name = "AWSManagedRulesKnownBadInputsRuleSet", priority = 20 },
    { name = "AWSManagedRulesAmazonIpReputationList", priority = 30 },
    { name = "AWSManagedRulesSQLiRuleSet", priority = 40 },
  ]
}

variable "enable_waf_logging" {
  description = "Send WAF request logs to a CloudWatch log group. The group name must start with aws-waf-logs-."
  type        = bool
  default     = true
}

variable "waf_log_retention_days" {
  description = "Retention of the WAF log group."
  type        = number
  default     = 90
}

variable "waf_log_kms_key_arn" {
  description = "CMK for the WAF log group. Null uses the CloudWatch Logs default key."
  type        = string
  default     = null
}

variable "access_logs_bucket" {
  description = "S3 bucket for ALB access logs. Null disables access logging."
  type        = string
  default     = null
}

variable "access_logs_prefix" {
  description = "Key prefix for ALB access logs."
  type        = string
  default     = "alb"
}

variable "tags" {
  description = "Tags merged into every resource."
  type        = map(string)
  default     = {}
}
