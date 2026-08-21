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
  description = "Prefix for all resource names, e.g. fineract-dev."
  type        = string
}

variable "environment" {
  description = "Environment name (dev, prod)."
  type        = string
}

variable "region" {
  description = "AWS region the service runs in; used for the awslogs driver."
  type        = string
}

variable "image_uri" {
  description = "Fully qualified ECR image reference. WS2 publishes immutable digests, so this must be pinned by digest (repo@sha256:...), never by a mutable tag."
  type        = string

  validation {
    condition     = can(regex("@sha256:[0-9a-f]{64}$", var.image_uri))
    error_message = "image_uri must be pinned by digest (…@sha256:<64 hex chars>) so a task replacement can never pull a different image than the one the pipeline promoted."
  }
}

variable "private_app_subnet_ids" {
  description = "WS1 contract: private application subnets the tasks run in."
  type        = list(string)
}

variable "tasks_security_group_id" {
  description = "WS1 contract: security group applied to the ENI of every task."
  type        = string
}

variable "target_group_arn" {
  description = "WS1 contract: ALB target group the fineract-api service registers with."
  type        = string
}

variable "container_port" {
  description = "Port the container listens on. The image serves HTTPS on 8443 when FINERACT_SERVER_SSL_ENABLED is true."
  type        = number
  default     = 8443
}

variable "readiness_path" {
  description = "Readiness endpoint the ALB target group health-checks. Kept as a variable so the WS1 target group and this module cannot drift apart."
  type        = string
  default     = "/fineract-provider/actuator/health/readiness"
}

variable "task_cpu" {
  description = "Task CPU units. README.md documents 8 vCPU as the minimum; Q-PLT-7 reconciles that against observed load before prod."
  type        = number
  default     = 8192
}

variable "task_memory" {
  description = "Task memory in MiB. README.md documents 16 GB as the minimum. The kubernetes manifest's 2 GiB limit is a laptop-scale value and is not used here."
  type        = number
  default     = 16384
}

variable "jvm_max_ram_percentage" {
  description = "-XX:MaxRAMPercentage for the container-aware JVM. Leaves headroom for non-heap memory inside the task."
  type        = number
  default     = 75
}

variable "desired_count" {
  description = "Baseline task count. Two or more is required for replacement without request loss."
  type        = number
  default     = 2

  validation {
    condition     = var.desired_count >= 2
    error_message = "desired_count must be at least 2: a single task cannot be replaced without dropping requests."
  }
}

variable "min_capacity" {
  description = "Autoscaling floor."
  type        = number
  default     = 2
}

variable "max_capacity" {
  description = "Autoscaling ceiling. Bounded by the Aurora connection budget: max_capacity * FINERACT_HIKARI_MAXIMUM_POOL_SIZE connections."
  type        = number
  default     = 6
}

variable "alb_arn_suffix" {
  description = "WS1 contract: ALB ARN suffix, used to build the request-count-per-target autoscaling metric."
  type        = string
}

variable "target_group_arn_suffix" {
  description = "WS1 contract: target group ARN suffix, used to build the request-count-per-target autoscaling metric."
  type        = string
}

variable "requests_per_target" {
  description = "Target ALBRequestCountPerTarget value for the request-based scaling policy."
  type        = number
  default     = 600
}

variable "cpu_target_utilization" {
  description = "Target average CPU utilisation percentage for the CPU scaling policy."
  type        = number
  default     = 60
}

variable "scale_in_cooldown" {
  description = "Seconds to wait before a further scale-in. Deliberately long: Fineract task startup is expensive."
  type        = number
  default     = 300
}

variable "scale_out_cooldown" {
  description = "Seconds to wait before a further scale-out."
  type        = number
  default     = 60
}

variable "health_check_grace_period_seconds" {
  description = "Grace period before ECS starts honouring target group health. Must cover JVM start plus tenant context load."
  type        = number
  default     = 180
}

variable "stop_timeout_seconds" {
  description = "Time the container gets to drain in-flight requests after SIGTERM."
  type        = number
  default     = 60
}

variable "environment_variables" {
  description = "Non-secret FINERACT_* configuration injected into both the service and the migration task."
  type        = map(string)
  default     = {}
}

variable "secret_arns" {
  description = "Secrets Manager or SSM Parameter Store ARNs keyed by the environment variable name they populate (for example FINERACT_HIKARI_PASSWORD). No long-lived keys are ever placed in environment_variables."
  type        = map(string)
  default     = {}
}

variable "kms_key_arn" {
  description = "CMK used to encrypt the log group and to decrypt the injected secrets."
  type        = string
}

variable "log_retention_days" {
  description = "CloudWatch Logs retention for the service and migration log groups."
  type        = number
  default     = 30
}

variable "content_bucket_arn" {
  description = "Optional S3 bucket holding Fineract document storage. Empty string grants the task role no S3 access at all."
  type        = string
  default     = ""
}

variable "enable_execute_command" {
  description = "Whether ECS Exec is allowed. Off by default; a stateless runtime should not be shelled into."
  type        = bool
  default     = false
}
