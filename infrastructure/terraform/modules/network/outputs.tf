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

output "vpc_id" {
  description = "VPC id. Consumed by the security, edge and (WS4) service modules."
  value       = aws_vpc.this.id
}

output "vpc_cidr_block" {
  description = "VPC CIDR block."
  value       = aws_vpc.this.cidr_block
}

output "availability_zones" {
  description = "Availability zones the VPC spans."
  value       = var.availability_zones
}

output "public_subnet_ids" {
  description = "Public subnets. The ALB is the only workload permitted here."
  value       = aws_subnet.public[*].id
}

output "private_app_subnet_ids" {
  description = "Private application subnets. WS4 places ECS tasks here."
  value       = aws_subnet.private_app[*].id
}

output "private_data_subnet_ids" {
  description = "Private data subnets. WS3 (Aurora) and WS6 (MSK) place their subnet groups here."
  value       = aws_subnet.private_data[*].id
}

output "nat_gateway_ids" {
  description = "NAT Gateway ids providing egress for the SMS gateway and SMTP relay (Q-PLT-8)."
  value       = aws_nat_gateway.this[*].id
}

output "nat_gateway_public_ips" {
  description = "Elastic IPs of the NAT Gateways. These are the source addresses an SMS or SMTP provider allow-lists."
  value       = aws_eip.nat[*].public_ip
}

output "private_app_route_table_ids" {
  description = "Route tables of the application tier."
  value       = aws_route_table.private_app[*].id
}

output "private_data_route_table_ids" {
  description = "Route tables of the data tier."
  value       = aws_route_table.private_data[*].id
}

output "vpc_endpoint_security_group_id" {
  description = "Security group in front of the interface endpoints."
  value       = aws_security_group.vpc_endpoints.id
}

output "interface_endpoint_ids" {
  description = "Interface endpoint ids keyed by service short name."
  value       = { for k, v in aws_vpc_endpoint.interface : k => v.id }
}

output "s3_gateway_endpoint_id" {
  description = "S3 gateway endpoint id."
  value       = aws_vpc_endpoint.s3.id
}
