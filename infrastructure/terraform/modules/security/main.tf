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

data "aws_region" "current" {}

data "aws_partition" "current" {}

data "aws_caller_identity" "current" {
  count = var.account_id == null ? 1 : 0
}

locals {
  account_id = var.account_id != null ? var.account_id : one(data.aws_caller_identity.current[*].account_id)
  partition  = data.aws_partition.current.partition
  region     = data.aws_region.current.name

  tags = merge(var.tags, {
    Environment = var.environment
    Workstream  = "WS1"
  })

  # One CMK per data domain so that a key compromise or a key policy change is
  # scoped to one class of data: tenant databases (WS3), documents and report
  # exports (WS5), logs (WS7).
  kms_keys = {
    database = "Aurora PostgreSQL storage, snapshots and the Secrets Manager secrets holding database credentials"
    s3       = "Document store and report export buckets"
    logs     = "CloudWatch log groups and the CloudTrail trail"
  }
}

data "aws_iam_policy_document" "kms_key" {
  for_each = local.kms_keys

  statement {
    sid       = "EnableIAMUserPermissions"
    effect    = "Allow"
    actions   = ["kms:*"]
    resources = ["*"]

    principals {
      type        = "AWS"
      identifiers = ["arn:${local.partition}:iam::${local.account_id}:root"]
    }
  }

  dynamic "statement" {
    for_each = each.key == "logs" ? [1] : []

    content {
      sid    = "AllowCloudWatchLogs"
      effect = "Allow"

      actions = [
        "kms:Encrypt*",
        "kms:Decrypt*",
        "kms:ReEncrypt*",
        "kms:GenerateDataKey*",
        "kms:Describe*",
      ]

      resources = ["*"]

      principals {
        type        = "Service"
        identifiers = ["logs.${local.region}.amazonaws.com"]
      }

      condition {
        test     = "ArnLike"
        variable = "kms:EncryptionContext:aws:logs:arn"
        values   = ["arn:${local.partition}:logs:${local.region}:${local.account_id}:log-group:*"]
      }
    }
  }

  dynamic "statement" {
    for_each = each.key == "logs" && var.enable_cloudtrail ? [1] : []

    content {
      sid       = "AllowCloudTrail"
      effect    = "Allow"
      actions   = ["kms:GenerateDataKey*", "kms:DescribeKey"]
      resources = ["*"]

      principals {
        type        = "Service"
        identifiers = ["cloudtrail.amazonaws.com"]
      }
    }
  }
}

resource "aws_kms_key" "this" {
  for_each = local.kms_keys

  description             = "${var.name_prefix} ${each.key}: ${each.value}"
  deletion_window_in_days = var.kms_deletion_window_in_days
  enable_key_rotation     = true
  policy                  = data.aws_iam_policy_document.kms_key[each.key].json

  tags = merge(local.tags, {
    Name       = "${var.name_prefix}-${each.key}"
    DataDomain = each.key
  })
}

resource "aws_kms_alias" "this" {
  for_each = local.kms_keys

  name          = "alias/${var.name_prefix}-${each.key}"
  target_key_id = aws_kms_key.this[each.key].key_id
}

# ---------------------------------------------------------------------------
# Security groups: ALB -> tasks -> data, and nothing else.
# ---------------------------------------------------------------------------

resource "aws_security_group" "alb" {
  name        = "${var.name_prefix}-alb"
  description = "Public entry point. HTTPS only."
  vpc_id      = var.vpc_id

  tags = merge(local.tags, { Name = "${var.name_prefix}-alb" })
}

resource "aws_vpc_security_group_ingress_rule" "alb_https" {
  for_each = toset(var.alb_ingress_cidrs)

  security_group_id = aws_security_group.alb.id
  description       = "HTTPS from ${each.value}"
  cidr_ipv4         = each.value
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "alb_to_tasks" {
  security_group_id            = aws_security_group.alb.id
  description                  = "Health checks and forwarded traffic to the Fineract tasks"
  referenced_security_group_id = aws_security_group.tasks.id
  from_port                    = var.task_port
  to_port                      = var.task_port
  ip_protocol                  = "tcp"
}

resource "aws_security_group" "tasks" {
  name        = "${var.name_prefix}-tasks"
  description = "Fineract ECS tasks (WS4). Reachable only from the ALB."
  vpc_id      = var.vpc_id

  tags = merge(local.tags, { Name = "${var.name_prefix}-tasks" })
}

resource "aws_vpc_security_group_ingress_rule" "tasks_from_alb" {
  security_group_id            = aws_security_group.tasks.id
  description                  = "Application traffic from the ALB"
  referenced_security_group_id = aws_security_group.alb.id
  from_port                    = var.task_port
  to_port                      = var.task_port
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "tasks_to_data" {
  security_group_id            = aws_security_group.tasks.id
  description                  = "Aurora PostgreSQL"
  referenced_security_group_id = aws_security_group.data.id
  from_port                    = var.database_port
  to_port                      = var.database_port
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "tasks_to_msk" {
  for_each = toset([for p in var.msk_ports : tostring(p)])

  security_group_id            = aws_security_group.tasks.id
  description                  = "MSK broker port ${each.value}"
  referenced_security_group_id = aws_security_group.data.id
  from_port                    = tonumber(each.value)
  to_port                      = tonumber(each.value)
  ip_protocol                  = "tcp"
}

# Outbound 443 is the SMS gateway (SmsMessageScheduledJobServiceImpl.java), the
# AWS APIs that have no interface endpoint, and ECR image pulls. It leaves
# through the NAT Gateway; Q-PLT-8 may replace this with an on-premises proxy,
# in which case the destination becomes the proxy CIDR, not 0.0.0.0/0.
resource "aws_vpc_security_group_egress_rule" "tasks_https" {
  security_group_id = aws_security_group.tasks.id
  description       = "HTTPS egress for the SMS gateway and AWS APIs"
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "tasks_smtp" {
  for_each = toset([for p in var.smtp_egress_ports : tostring(p)])

  security_group_id = aws_security_group.tasks.id
  description       = "SMTP relay on port ${each.value} (ReportMailingJobEmailServiceImpl.java:94-96)"
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = tonumber(each.value)
  to_port           = tonumber(each.value)
  ip_protocol       = "tcp"
}

resource "aws_security_group" "data" {
  name        = "${var.name_prefix}-data"
  description = "Aurora PostgreSQL (WS3) and MSK (WS6). No egress."
  vpc_id      = var.vpc_id

  tags = merge(local.tags, { Name = "${var.name_prefix}-data" })
}

resource "aws_vpc_security_group_ingress_rule" "data_from_tasks" {
  security_group_id            = aws_security_group.data.id
  description                  = "PostgreSQL from the application tier"
  referenced_security_group_id = aws_security_group.tasks.id
  from_port                    = var.database_port
  to_port                      = var.database_port
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "data_msk_from_tasks" {
  for_each = toset([for p in var.msk_ports : tostring(p)])

  security_group_id            = aws_security_group.data.id
  description                  = "MSK broker port ${each.value} from the application tier"
  referenced_security_group_id = aws_security_group.tasks.id
  from_port                    = tonumber(each.value)
  to_port                      = tonumber(each.value)
  ip_protocol                  = "tcp"
}

# ---------------------------------------------------------------------------
# VPC flow logs
# ---------------------------------------------------------------------------

resource "aws_cloudwatch_log_group" "flow_logs" {
  count = var.enable_flow_logs ? 1 : 0

  name              = "/aws/vpc/${var.name_prefix}/flow-logs"
  retention_in_days = var.flow_logs_retention_days
  kms_key_id        = aws_kms_key.this["logs"].arn

  tags = local.tags
}

data "aws_iam_policy_document" "flow_logs_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["vpc-flow-logs.amazonaws.com"]
    }
  }
}

data "aws_iam_policy_document" "flow_logs" {
  count = var.enable_flow_logs ? 1 : 0

  statement {
    effect = "Allow"

    actions = [
      "logs:CreateLogStream",
      "logs:PutLogEvents",
      "logs:DescribeLogStreams",
    ]

    resources = ["${aws_cloudwatch_log_group.flow_logs[0].arn}:*"]
  }
}

resource "aws_iam_role" "flow_logs" {
  count = var.enable_flow_logs ? 1 : 0

  name               = "${var.name_prefix}-flow-logs"
  assume_role_policy = data.aws_iam_policy_document.flow_logs_assume.json

  tags = local.tags
}

resource "aws_iam_role_policy" "flow_logs" {
  count = var.enable_flow_logs ? 1 : 0

  name   = "${var.name_prefix}-flow-logs"
  role   = aws_iam_role.flow_logs[0].id
  policy = data.aws_iam_policy_document.flow_logs[0].json
}

resource "aws_flow_log" "this" {
  count = var.enable_flow_logs ? 1 : 0

  vpc_id                   = var.vpc_id
  traffic_type             = "ALL"
  log_destination_type     = "cloud-watch-logs"
  log_destination          = aws_cloudwatch_log_group.flow_logs[0].arn
  iam_role_arn             = aws_iam_role.flow_logs[0].arn
  max_aggregation_interval = 60

  tags = merge(local.tags, { Name = "${var.name_prefix}-flow-logs" })
}
