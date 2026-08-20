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

output "vpc_id" {
  description = "VPC id."
  value       = module.network.vpc_id
}

output "private_app_subnet_ids" {
  description = "Subnets WS4 places the ECS tasks in."
  value       = module.network.private_app_subnet_ids
}

output "private_data_subnet_ids" {
  description = "Subnets WS3 (Aurora) and WS6 (MSK) place their subnet groups in."
  value       = module.network.private_data_subnet_ids
}

output "public_subnet_ids" {
  description = "Subnets carrying the ALB."
  value       = module.network.public_subnet_ids
}

output "nat_gateway_public_ips" {
  description = "Egress addresses to give the SMS and SMTP providers (Q-PLT-8)."
  value       = module.network.nat_gateway_public_ips
}

output "tasks_security_group_id" {
  description = "WS4 contract: security group for the Fineract ECS service."
  value       = module.security.tasks_security_group_id
}

output "data_security_group_id" {
  description = "WS3/WS6 contract: security group for Aurora and MSK."
  value       = module.security.data_security_group_id
}

output "alb_security_group_id" {
  description = "Security group attached to the ALB."
  value       = module.security.alb_security_group_id
}

output "kms_key_arns" {
  description = "CMK ARNs keyed by data domain (database, s3, logs)."
  value       = module.security.kms_key_arns
}

output "target_group_arn" {
  description = "WS4 contract: target group the fineract-api service registers with."
  value       = module.edge.target_group_arn
}

output "alb_dns_name" {
  description = "ALB DNS name."
  value       = module.edge.alb_dns_name
}

output "api_endpoint_url" {
  description = "Base URL of the API once WS4 registers tasks."
  value       = module.edge.endpoint_url
}

output "web_acl_arn" {
  description = "WAF web ACL in front of the ALB."
  value       = module.edge.web_acl_arn
}

output "hosted_zone_name_servers" {
  description = "Name servers the parent domain must delegate to."
  value       = module.dns.hosted_zone_name_servers
}
