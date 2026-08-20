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

output "kms_key_arns" {
  description = "CMK ARNs keyed by data domain (database, s3, logs)."
  value       = { for k, v in aws_kms_key.this : k => v.arn }
}

output "kms_key_ids" {
  description = "CMK ids keyed by data domain."
  value       = { for k, v in aws_kms_key.this : k => v.key_id }
}

output "alb_security_group_id" {
  description = "Security group for the ALB. Consumed by the edge module."
  value       = aws_security_group.alb.id
}

output "tasks_security_group_id" {
  description = "Security group for the Fineract ECS tasks. WS4 attaches this to the service network configuration."
  value       = aws_security_group.tasks.id
}

output "data_security_group_id" {
  description = "Security group for Aurora (WS3) and MSK (WS6)."
  value       = aws_security_group.data.id
}

output "flow_log_group_name" {
  description = "CloudWatch log group receiving VPC flow logs."
  value       = try(aws_cloudwatch_log_group.flow_logs[0].name, null)
}

output "cloudtrail_arn" {
  description = "CloudTrail trail ARN when enable_cloudtrail is true."
  value       = try(aws_cloudtrail.this[0].arn, null)
}
