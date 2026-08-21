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

locals {
  service_name   = "${var.name_prefix}-api"
  migration_name = "${var.name_prefix}-migrate"

  # The service never runs Liquibase: the tenant upgrade is single-threaded, so startup
  # time grows with tenant count and every scale-out event would serialise behind it.
  # Schema changes go through the migration task definition below, gated by the pipeline.
  service_environment = merge(
    {
      FINERACT_SERVER_SSL_ENABLED = "true"
      FINERACT_SERVER_PORT        = tostring(var.container_port)
      JAVA_TOOL_OPTIONS           = "-XX:+UseContainerSupport -XX:MaxRAMPercentage=${var.jvm_max_ram_percentage} -XX:+UseStringDeduplication"
    },
    var.environment_variables,
    { FINERACT_LIQUIBASE_ENABLED = "false" },
  )

  migration_environment = merge(
    local.service_environment,
    {
      FINERACT_LIQUIBASE_ENABLED = "true"
      # Migration-only run: apply the changelog, then exit rather than serve traffic.
      SPRING_MAIN_WEB_APPLICATION_TYPE = "none"
    },
  )

  container_secrets = [
    for name, arn in var.secret_arns : {
      name      = name
      valueFrom = arn
    }
  ]
}

resource "aws_ecs_cluster" "this" {
  name = var.name_prefix

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  configuration {
    execute_command_configuration {
      kms_key_id = var.kms_key_arn
      logging    = "DEFAULT"
    }
  }
}

resource "aws_ecs_cluster_capacity_providers" "this" {
  cluster_name       = aws_ecs_cluster.this.name
  capacity_providers = ["FARGATE"]

  default_capacity_provider_strategy {
    capacity_provider = "FARGATE"
    weight            = 1
  }
}

resource "aws_cloudwatch_log_group" "service" {
  name              = "/aws/ecs/${local.service_name}"
  retention_in_days = var.log_retention_days
  kms_key_id        = var.kms_key_arn
}

resource "aws_cloudwatch_log_group" "migration" {
  name              = "/aws/ecs/${local.migration_name}"
  retention_in_days = var.log_retention_days
  kms_key_id        = var.kms_key_arn
}

data "aws_iam_policy_document" "task_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

# Execution role: used by the ECS agent to pull the image, write logs and resolve secrets.
# It is deliberately separate from the task role so application code cannot read secret ARNs
# it was not granted.
resource "aws_iam_role" "execution" {
  name               = "${local.service_name}-execution"
  assume_role_policy = data.aws_iam_policy_document.task_assume.json
}

resource "aws_iam_role_policy_attachment" "execution_managed" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "execution" {
  statement {
    sid    = "WriteTaskLogs"
    effect = "Allow"

    actions = [
      "logs:CreateLogStream",
      "logs:PutLogEvents",
    ]

    resources = [
      "${aws_cloudwatch_log_group.service.arn}:*",
      "${aws_cloudwatch_log_group.migration.arn}:*",
    ]
  }

  dynamic "statement" {
    for_each = length(var.secret_arns) > 0 ? [1] : []

    content {
      sid       = "ReadInjectedSecrets"
      effect    = "Allow"
      actions   = ["secretsmanager:GetSecretValue", "ssm:GetParameters"]
      resources = values(var.secret_arns)
    }
  }

  statement {
    sid       = "DecryptWithPlatformKey"
    effect    = "Allow"
    actions   = ["kms:Decrypt"]
    resources = [var.kms_key_arn]
  }
}

resource "aws_iam_role_policy" "execution" {
  name   = "${local.service_name}-execution"
  role   = aws_iam_role.execution.id
  policy = data.aws_iam_policy_document.execution.json
}

# Task role: the identity the application itself assumes. Credentials are vended by the
# task metadata endpoint and rotate automatically, so no long-lived keys exist anywhere.
resource "aws_iam_role" "task" {
  name               = "${local.service_name}-task"
  assume_role_policy = data.aws_iam_policy_document.task_assume.json
}

data "aws_iam_policy_document" "task" {
  dynamic "statement" {
    for_each = var.content_bucket_arn == "" ? [] : [var.content_bucket_arn]

    content {
      sid    = "DocumentStorage"
      effect = "Allow"

      actions = [
        "s3:GetObject",
        "s3:PutObject",
        "s3:DeleteObject",
      ]

      resources = ["${statement.value}/*"]
    }
  }

  dynamic "statement" {
    for_each = var.content_bucket_arn == "" ? [] : [var.content_bucket_arn]

    content {
      sid       = "DocumentStorageList"
      effect    = "Allow"
      actions   = ["s3:ListBucket"]
      resources = [statement.value]
    }
  }

  dynamic "statement" {
    for_each = var.enable_execute_command ? [1] : []

    content {
      sid    = "EcsExec"
      effect = "Allow"

      actions = [
        "ssmmessages:CreateControlChannel",
        "ssmmessages:CreateDataChannel",
        "ssmmessages:OpenControlChannel",
        "ssmmessages:OpenDataChannel",
      ]

      resources = ["*"]
    }
  }
}

# An empty policy document is not a valid inline policy, so only attach one when the task
# actually needs permissions.
resource "aws_iam_role_policy" "task" {
  count = var.content_bucket_arn == "" && !var.enable_execute_command ? 0 : 1

  name   = "${local.service_name}-task"
  role   = aws_iam_role.task.id
  policy = data.aws_iam_policy_document.task.json
}

resource "aws_ecs_task_definition" "api" {
  family                   = local.service_name
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.task_cpu
  memory                   = var.task_memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    cpu_architecture        = "X86_64"
    operating_system_family = "LINUX"
  }

  container_definitions = jsonencode([
    {
      name                   = "fineract"
      image                  = var.image_uri
      essential              = true
      readonlyRootFilesystem = false
      stopTimeout            = var.stop_timeout_seconds

      portMappings = [
        {
          containerPort = var.container_port
          protocol      = "tcp"
        }
      ]

      environment = [for name, value in local.service_environment : { name = name, value = value }]
      secrets     = local.container_secrets

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.service.name
          "awslogs-region"        = var.region
          "awslogs-stream-prefix" = "fineract"
        }
      }
    }
  ])
}

# One-off task definition run by the pipeline before a new image is rolled out. It shares
# the image and configuration of the service so the changelog applied is exactly the one
# shipped in that image.
resource "aws_ecs_task_definition" "migration" {
  family                   = local.migration_name
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.task_cpu
  memory                   = var.task_memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    cpu_architecture        = "X86_64"
    operating_system_family = "LINUX"
  }

  container_definitions = jsonencode([
    {
      name        = "liquibase"
      image       = var.image_uri
      essential   = true
      stopTimeout = 120

      environment = [for name, value in local.migration_environment : { name = name, value = value }]
      secrets     = local.container_secrets

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.migration.name
          "awslogs-region"        = var.region
          "awslogs-stream-prefix" = "liquibase"
        }
      }
    }
  ])
}

resource "aws_ecs_service" "api" {
  name            = local.service_name
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.api.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"
  propagate_tags  = "SERVICE"

  enable_execute_command             = var.enable_execute_command
  health_check_grace_period_seconds  = var.health_check_grace_period_seconds
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200
  wait_for_steady_state              = true

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  network_configuration {
    subnets          = var.private_app_subnet_ids
    security_groups  = [var.tasks_security_group_id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = var.target_group_arn
    container_name   = "fineract"
    container_port   = var.container_port
  }

  lifecycle {
    # Desired count is owned by application autoscaling once the service exists.
    ignore_changes = [desired_count]
  }

  depends_on = [aws_ecs_cluster_capacity_providers.this]
}

resource "aws_appautoscaling_target" "api" {
  service_namespace  = "ecs"
  resource_id        = "service/${aws_ecs_cluster.this.name}/${aws_ecs_service.api.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  min_capacity       = var.min_capacity
  max_capacity       = var.max_capacity
}

resource "aws_appautoscaling_policy" "requests" {
  name               = "${local.service_name}-requests"
  policy_type        = "TargetTrackingScaling"
  service_namespace  = aws_appautoscaling_target.api.service_namespace
  resource_id        = aws_appautoscaling_target.api.resource_id
  scalable_dimension = aws_appautoscaling_target.api.scalable_dimension

  target_tracking_scaling_policy_configuration {
    target_value       = var.requests_per_target
    scale_in_cooldown  = var.scale_in_cooldown
    scale_out_cooldown = var.scale_out_cooldown

    predefined_metric_specification {
      predefined_metric_type = "ALBRequestCountPerTarget"
      resource_label         = "${var.alb_arn_suffix}/${var.target_group_arn_suffix}"
    }
  }
}

resource "aws_appautoscaling_policy" "cpu" {
  name               = "${local.service_name}-cpu"
  policy_type        = "TargetTrackingScaling"
  service_namespace  = aws_appautoscaling_target.api.service_namespace
  resource_id        = aws_appautoscaling_target.api.resource_id
  scalable_dimension = aws_appautoscaling_target.api.scalable_dimension

  target_tracking_scaling_policy_configuration {
    target_value       = var.cpu_target_utilization
    scale_in_cooldown  = var.scale_in_cooldown
    scale_out_cooldown = var.scale_out_cooldown

    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
  }
}
