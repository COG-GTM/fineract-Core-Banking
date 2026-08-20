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

output "alb_arn" {
  description = "ARN of the internet-facing ALB."
  value       = aws_lb.this.arn
}

output "alb_dns_name" {
  description = "DNS name of the ALB; the Route 53 alias points here."
  value       = aws_lb.this.dns_name
}

output "alb_security_group_id" {
  description = "Security group of the ALB; the task security group should allow ingress from it."
  value       = aws_security_group.alb.id
}

output "target_group_arn" {
  description = "Target group the ECS service registers with."
  value       = aws_lb_target_group.app.arn
}

output "certificate_arn" {
  description = "ARN of the validated ACM certificate."
  value       = aws_acm_certificate_validation.this.certificate_arn
}

output "hosted_zone_id" {
  description = "Route 53 zone holding the service record."
  value       = local.zone_id
}

output "hosted_zone_name_servers" {
  description = "Name servers to delegate to when this module creates the zone."
  value       = var.create_hosted_zone ? aws_route53_zone.this[0].name_servers : []
}

output "service_url" {
  description = "Public HTTPS URL of the Fineract API."
  value       = "https://${var.domain_name}"
}

output "web_acl_arn" {
  description = "ARN of the web ACL associated with the ALB."
  value       = aws_wafv2_web_acl.this.arn
}
