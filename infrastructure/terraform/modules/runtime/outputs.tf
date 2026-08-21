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

output "cluster_name" {
  description = "ECS cluster carrying the fineract-api service."
  value       = aws_ecs_cluster.this.name
}

output "cluster_arn" {
  description = "ECS cluster ARN."
  value       = aws_ecs_cluster.this.arn
}

output "service_name" {
  description = "Name of the fineract-api service."
  value       = aws_ecs_service.api.name
}

output "task_definition_arn" {
  description = "Revision of the API task definition currently deployed."
  value       = aws_ecs_task_definition.api.arn
}

output "migration_task_definition_arn" {
  description = "Task definition the pipeline runs as a one-off Liquibase job before rolling out a new image."
  value       = aws_ecs_task_definition.migration.arn
}

output "migration_task_definition_family" {
  description = "Family name to pass to `aws ecs run-task --task-definition`."
  value       = aws_ecs_task_definition.migration.family
}

output "task_role_arn" {
  description = "Role the application assumes; credentials rotate through the task metadata endpoint."
  value       = aws_iam_role.task.arn
}

output "execution_role_arn" {
  description = "Role the ECS agent uses to pull images, write logs and resolve secrets."
  value       = aws_iam_role.execution.arn
}

output "log_group_name" {
  description = "CloudWatch log group receiving service logs."
  value       = aws_cloudwatch_log_group.service.name
}

output "migration_log_group_name" {
  description = "CloudWatch log group receiving Liquibase output."
  value       = aws_cloudwatch_log_group.migration.name
}

output "readiness_path" {
  description = "Readiness endpoint the ALB target group must health-check for tasks to be replaceable without request loss."
  value       = var.readiness_path
}
