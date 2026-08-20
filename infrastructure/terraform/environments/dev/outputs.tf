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

output "service_url" {
  description = "Public HTTPS URL of the dev Fineract API."
  value       = module.edge.service_url
}

output "alb_dns_name" {
  description = "DNS name of the dev ALB."
  value       = module.edge.alb_dns_name
}

output "alb_security_group_id" {
  description = "Security group the ECS task security group must accept traffic from."
  value       = module.edge.alb_security_group_id
}

output "target_group_arn" {
  description = "Target group the dev ECS service registers with."
  value       = module.edge.target_group_arn
}

output "hosted_zone_name_servers" {
  description = "Name servers to delegate the dev zone to."
  value       = module.edge.hosted_zone_name_servers
}
