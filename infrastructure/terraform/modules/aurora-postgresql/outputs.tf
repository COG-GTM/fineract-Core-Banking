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

output "cluster_identifier" {
  description = "Aurora cluster identifier."
  value       = aws_rds_cluster.this.cluster_identifier
}

output "writer_endpoint" {
  description = "Writer endpoint, used by FINERACT_DEFAULT_TENANTDB_HOSTNAME."
  value       = aws_rds_cluster.this.endpoint
}

output "reader_endpoint" {
  description = "Reader endpoint, used by FINERACT_DEFAULT_TENANTDB_RO_HOSTNAME."
  value       = aws_rds_cluster.this.reader_endpoint
}

output "port" {
  description = "Cluster port."
  value       = aws_rds_cluster.this.port
}

output "master_username" {
  description = "Master username."
  value       = aws_rds_cluster.this.master_username
}

output "master_secret_arn" {
  description = "Secrets Manager secret holding the master credentials."
  value       = aws_secretsmanager_secret.master.arn
}

output "security_group_id" {
  description = "Security group guarding the cluster port."
  value       = aws_security_group.this.id
}

output "max_connections" {
  description = "Connection ceiling the HikariCP pool maths must stay under."
  value       = local.max_connections
}
