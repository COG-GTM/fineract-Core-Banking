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

# CloudTrail and Config are enablement stubs. In an Organizations/Control Tower
# landing zone they are delivered by the organisation trail and the
# organisation conformance packs, so both are guarded by a variable and default
# to off: a dev account without Organizations access must still be able to run
# terraform plan. Q-SEC-7 decides whether an account-local trail is required in
# addition to the organisation trail.

locals {
  trail_bucket_name = "${var.name_prefix}-cloudtrail-${local.account_id}"
}

resource "aws_s3_bucket" "trail" {
  count = var.enable_cloudtrail ? 1 : 0

  bucket        = local.trail_bucket_name
  force_destroy = false

  tags = merge(local.tags, { Name = local.trail_bucket_name })
}

resource "aws_s3_bucket_public_access_block" "trail" {
  count = var.enable_cloudtrail ? 1 : 0

  bucket                  = aws_s3_bucket.trail[0].id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "trail" {
  count = var.enable_cloudtrail ? 1 : 0

  bucket = aws_s3_bucket.trail[0].id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "trail" {
  count = var.enable_cloudtrail ? 1 : 0

  bucket = aws_s3_bucket.trail[0].id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = aws_kms_key.this["s3"].arn
    }

    bucket_key_enabled = true
  }
}

data "aws_iam_policy_document" "trail_bucket" {
  count = var.enable_cloudtrail ? 1 : 0

  statement {
    sid       = "AWSCloudTrailAclCheck"
    effect    = "Allow"
    actions   = ["s3:GetBucketAcl"]
    resources = [aws_s3_bucket.trail[0].arn]

    principals {
      type        = "Service"
      identifiers = ["cloudtrail.amazonaws.com"]
    }
  }

  statement {
    sid       = "AWSCloudTrailWrite"
    effect    = "Allow"
    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.trail[0].arn}/AWSLogs/${local.account_id}/*"]

    principals {
      type        = "Service"
      identifiers = ["cloudtrail.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "s3:x-amz-acl"
      values   = ["bucket-owner-full-control"]
    }
  }
}

resource "aws_s3_bucket_policy" "trail" {
  count = var.enable_cloudtrail ? 1 : 0

  bucket = aws_s3_bucket.trail[0].id
  policy = data.aws_iam_policy_document.trail_bucket[0].json
}

resource "aws_cloudwatch_log_group" "trail" {
  count = var.enable_cloudtrail ? 1 : 0

  name              = "/aws/cloudtrail/${var.name_prefix}"
  retention_in_days = var.log_retention_days
  kms_key_id        = aws_kms_key.this["logs"].arn

  tags = local.tags
}

data "aws_iam_policy_document" "trail_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["cloudtrail.amazonaws.com"]
    }
  }
}

data "aws_iam_policy_document" "trail_logs" {
  count = var.enable_cloudtrail ? 1 : 0

  statement {
    effect    = "Allow"
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${aws_cloudwatch_log_group.trail[0].arn}:*"]
  }
}

resource "aws_iam_role" "trail" {
  count = var.enable_cloudtrail ? 1 : 0

  name               = "${var.name_prefix}-cloudtrail"
  assume_role_policy = data.aws_iam_policy_document.trail_assume.json

  tags = local.tags
}

resource "aws_iam_role_policy" "trail" {
  count = var.enable_cloudtrail ? 1 : 0

  name   = "${var.name_prefix}-cloudtrail"
  role   = aws_iam_role.trail[0].id
  policy = data.aws_iam_policy_document.trail_logs[0].json
}

resource "aws_cloudtrail" "this" {
  count = var.enable_cloudtrail ? 1 : 0

  name                          = var.name_prefix
  s3_bucket_name                = aws_s3_bucket.trail[0].id
  kms_key_id                    = aws_kms_key.this["logs"].arn
  include_global_service_events = true
  is_multi_region_trail         = true
  enable_log_file_validation    = true
  cloud_watch_logs_group_arn    = "${aws_cloudwatch_log_group.trail[0].arn}:*"
  cloud_watch_logs_role_arn     = aws_iam_role.trail[0].arn

  tags = local.tags

  depends_on = [aws_s3_bucket_policy.trail]
}

data "aws_iam_policy_document" "config_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["config.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "config" {
  count = var.enable_config ? 1 : 0

  name               = "${var.name_prefix}-config"
  assume_role_policy = data.aws_iam_policy_document.config_assume.json

  tags = local.tags
}

resource "aws_iam_role_policy_attachment" "config" {
  count = var.enable_config ? 1 : 0

  role       = aws_iam_role.config[0].name
  policy_arn = "arn:${local.partition}:iam::aws:policy/service-role/AWS_ConfigRole"
}

resource "aws_s3_bucket" "config" {
  count = var.enable_config ? 1 : 0

  bucket        = "${var.name_prefix}-config-${local.account_id}"
  force_destroy = false

  tags = merge(local.tags, { Name = "${var.name_prefix}-config" })
}

resource "aws_s3_bucket_public_access_block" "config" {
  count = var.enable_config ? 1 : 0

  bucket                  = aws_s3_bucket.config[0].id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "config" {
  count = var.enable_config ? 1 : 0

  bucket = aws_s3_bucket.config[0].id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "config" {
  count = var.enable_config ? 1 : 0

  bucket = aws_s3_bucket.config[0].id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = aws_kms_key.this["s3"].arn
    }

    bucket_key_enabled = true
  }
}

data "aws_iam_policy_document" "config_bucket" {
  count = var.enable_config ? 1 : 0

  statement {
    sid       = "AWSConfigBucketPermissionsCheck"
    effect    = "Allow"
    actions   = ["s3:GetBucketAcl", "s3:ListBucket"]
    resources = [aws_s3_bucket.config[0].arn]

    principals {
      type        = "Service"
      identifiers = ["config.amazonaws.com"]
    }
  }

  statement {
    sid       = "AWSConfigBucketDelivery"
    effect    = "Allow"
    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.config[0].arn}/AWSLogs/${local.account_id}/Config/*"]

    principals {
      type        = "Service"
      identifiers = ["config.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "s3:x-amz-acl"
      values   = ["bucket-owner-full-control"]
    }
  }
}

resource "aws_s3_bucket_policy" "config" {
  count = var.enable_config ? 1 : 0

  bucket = aws_s3_bucket.config[0].id
  policy = data.aws_iam_policy_document.config_bucket[0].json
}

resource "aws_config_configuration_recorder" "this" {
  count = var.enable_config ? 1 : 0

  name     = var.name_prefix
  role_arn = aws_iam_role.config[0].arn

  recording_group {
    all_supported                 = true
    include_global_resource_types = true
  }
}

resource "aws_config_delivery_channel" "this" {
  count = var.enable_config ? 1 : 0

  name           = var.name_prefix
  s3_bucket_name = aws_s3_bucket.config[0].id

  depends_on = [aws_config_configuration_recorder.this]
}

resource "aws_config_configuration_recorder_status" "this" {
  count = var.enable_config ? 1 : 0

  name       = aws_config_configuration_recorder.this[0].name
  is_enabled = true

  depends_on = [aws_config_delivery_channel.this]
}
