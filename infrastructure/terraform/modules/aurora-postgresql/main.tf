#
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
#

locals {
  cluster_identifier = "${var.name_prefix}-aurora-postgresql"

  # Aurora PostgreSQL caps connections at LEAST({DBInstanceClassMemory/9531392}, 5000) by default.
  # The Fineract estate opens `pool_size_per_tenant` connections per tenant datasource per task,
  # so the ceiling has to be stated explicitly rather than inherited from the instance class.
  max_connections = var.max_connections
}

resource "aws_db_subnet_group" "this" {
  name       = local.cluster_identifier
  subnet_ids = var.subnet_ids
  tags       = var.tags
}

resource "aws_security_group" "this" {
  name        = "${local.cluster_identifier}-db"
  description = "Fineract Aurora PostgreSQL access"
  vpc_id      = var.vpc_id
  tags        = var.tags
}

resource "aws_vpc_security_group_ingress_rule" "cidr" {
  for_each = toset(var.allowed_cidr_blocks)

  security_group_id = aws_security_group.this.id
  description       = "PostgreSQL from ${each.value}"
  cidr_ipv4         = each.value
  from_port         = var.port
  to_port           = var.port
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "security_group" {
  for_each = toset(var.allowed_security_group_ids)

  security_group_id            = aws_security_group.this.id
  description                  = "PostgreSQL from ${each.value}"
  referenced_security_group_id = each.value
  from_port                    = var.port
  to_port                      = var.port
  ip_protocol                  = "tcp"
}

resource "aws_rds_cluster_parameter_group" "this" {
  name        = "${local.cluster_identifier}-cluster"
  family      = var.parameter_group_family
  description = "Fineract Aurora PostgreSQL cluster parameters"
  tags        = var.tags

  parameter {
    name         = "max_connections"
    value        = local.max_connections
    apply_method = "pending-reboot"
  }

  # Fineract holds pooled connections open across COB batches; an idle transaction left behind by a
  # failed batch otherwise pins a connection and a row version for the life of the pool.
  parameter {
    name         = "idle_in_transaction_session_timeout"
    value        = var.idle_in_transaction_session_timeout_ms
    apply_method = "immediate"
  }

  parameter {
    name         = "log_min_duration_statement"
    value        = var.log_min_duration_statement_ms
    apply_method = "immediate"
  }
}

resource "random_password" "master" {
  length  = 32
  special = false
}

resource "aws_rds_cluster" "this" {
  cluster_identifier              = local.cluster_identifier
  engine                          = "aurora-postgresql"
  engine_version                  = var.engine_version
  database_name                   = var.initial_database_name
  master_username                 = var.master_username
  master_password                 = random_password.master.result
  port                            = var.port
  db_subnet_group_name            = aws_db_subnet_group.this.name
  db_cluster_parameter_group_name = aws_rds_cluster_parameter_group.this.name
  vpc_security_group_ids          = [aws_security_group.this.id]
  storage_encrypted               = true
  kms_key_id                      = var.kms_key_id
  backup_retention_period         = var.backup_retention_period
  preferred_backup_window         = var.preferred_backup_window
  deletion_protection             = var.deletion_protection
  skip_final_snapshot             = var.skip_final_snapshot
  final_snapshot_identifier       = var.skip_final_snapshot ? null : "${local.cluster_identifier}-final"
  copy_tags_to_snapshot           = true
  apply_immediately               = var.apply_immediately
  enabled_cloudwatch_logs_exports = ["postgresql"]
  tags                            = var.tags
}

resource "aws_rds_cluster_instance" "this" {
  count = var.instance_count

  identifier                   = "${local.cluster_identifier}-${count.index + 1}"
  cluster_identifier           = aws_rds_cluster.this.id
  instance_class               = var.instance_class
  engine                       = aws_rds_cluster.this.engine
  engine_version               = aws_rds_cluster.this.engine_version
  db_subnet_group_name         = aws_db_subnet_group.this.name
  publicly_accessible          = var.publicly_accessible
  performance_insights_enabled = var.performance_insights_enabled
  monitoring_interval          = var.monitoring_interval
  auto_minor_version_upgrade   = true
  apply_immediately            = var.apply_immediately
  tags                         = var.tags
}

resource "aws_secretsmanager_secret" "master" {
  name                    = "${local.cluster_identifier}/master"
  description             = "Fineract Aurora PostgreSQL master credentials"
  recovery_window_in_days = var.secret_recovery_window_in_days
  tags                    = var.tags
}

resource "aws_secretsmanager_secret_version" "master" {
  secret_id = aws_secretsmanager_secret.master.id
  secret_string = jsonencode({
    engine   = "postgres"
    host     = aws_rds_cluster.this.endpoint
    port     = aws_rds_cluster.this.port
    username = aws_rds_cluster.this.master_username
    password = random_password.master.result
    dbname   = var.initial_database_name
  })
}
