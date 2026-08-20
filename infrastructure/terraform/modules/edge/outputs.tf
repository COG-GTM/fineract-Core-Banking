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

output "target_group_arn" {
  description = "WS4 contract: the ARN the fineract-api ECS service registers with in its load_balancer block."
  value       = aws_lb_target_group.app.arn
}

output "target_group_name" {
  description = "Target group name."
  value       = aws_lb_target_group.app.name
}

output "target_group_health_check_path" {
  description = "Health check path WS4 must keep reachable without authentication."
  value       = var.health_check_path
}

output "alb_arn" {
  description = "ALB ARN."
  value       = aws_lb.this.arn
}

output "alb_arn_suffix" {
  description = "ALB ARN suffix, for CloudWatch alarms and request-count-based autoscaling in WS4 and WS7."
  value       = aws_lb.this.arn_suffix
}

output "alb_dns_name" {
  description = "ALB DNS name."
  value       = aws_lb.this.dns_name
}

output "alb_zone_id" {
  description = "ALB hosted zone id, for alias records outside this module."
  value       = aws_lb.this.zone_id
}

output "https_listener_arn" {
  description = "HTTPS listener ARN, for additional listener rules."
  value       = aws_lb_listener.https.arn
}

output "web_acl_arn" {
  description = "WAF web ACL ARN attached to the ALB."
  value       = aws_wafv2_web_acl.this.arn
}

output "endpoint_url" {
  description = "Base URL clients use to reach the API once WS4 registers tasks."
  value       = "https://${var.record_name}"
}
