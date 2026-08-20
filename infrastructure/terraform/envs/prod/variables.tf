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
  description = "AWS region for the prod landing zone."
  type        = string
  default     = "eu-west-1"
}

variable "environment" {
  description = "Environment identifier. Also the suffix of every resource name."
  type        = string
  default     = "prod"
}

variable "account_id" {
  description = "AWS account id, used in KMS key policies. Leave null to look it up from the caller identity; set it to plan without credentials."
  type        = string
  default     = null
}

variable "vpc_cidr" {
  description = "VPC CIDR."
  type        = string
  default     = "10.70.0.0/16"
}

variable "availability_zones" {
  description = "Three availability zones."
  type        = list(string)
  default     = ["eu-west-1a", "eu-west-1b", "eu-west-1c"]
}

variable "public_subnet_cidrs" {
  description = "Public subnets, one per AZ. ALB only."
  type        = list(string)
  default     = ["10.70.0.0/24", "10.70.1.0/24", "10.70.2.0/24"]
}

variable "private_app_subnet_cidrs" {
  description = "Application subnets, one per AZ. ECS tasks (WS4)."
  type        = list(string)
  default     = ["10.70.16.0/20", "10.70.32.0/20", "10.70.48.0/20"]
}

variable "private_data_subnet_cidrs" {
  description = "Data subnets, one per AZ. Aurora (WS3) and MSK (WS6)."
  type        = list(string)
  default     = ["10.70.64.0/22", "10.70.68.0/22", "10.70.72.0/22"]
}

variable "alb_ingress_cidrs" {
  description = "Source CIDRs allowed to reach the ALB on 443."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "task_port" {
  description = "Port the Fineract container listens on (Q-PLT-4)."
  type        = number
  default     = 8443
}

variable "domain_name" {
  description = "Hosted zone apex and certificate common name. Replace with the real name before the first apply."
  type        = string
  default     = "fineract.example.com"
}

variable "subject_alternative_names" {
  description = "Additional certificate names."
  type        = list(string)
  default     = []
}

variable "api_record_name" {
  description = "FQDN of the alias record pointing at the ALB."
  type        = string
  default     = "api.fineract.example.com"
}

variable "create_hosted_zone" {
  description = "Create the hosted zone in this account."
  type        = bool
  default     = true
}

variable "hosted_zone_id" {
  description = "Existing hosted zone id when create_hosted_zone is false."
  type        = string
  default     = null
}

variable "validate_certificate" {
  description = "Block the apply until ACM DNS validation completes."
  type        = bool
  default     = true
}

variable "enable_cloudtrail" {
  description = "Create an account-local CloudTrail trail. Off until Q-SEC-7 says whether the organisation trail is sufficient."
  type        = bool
  default     = true
}

variable "enable_config" {
  description = "Create the AWS Config recorder. Off until Q-SEC-7 says whether Control Tower already records this account."
  type        = bool
  default     = true
}
