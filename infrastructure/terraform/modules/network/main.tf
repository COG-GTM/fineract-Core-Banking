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

locals {
  az_count = length(var.availability_zones)

  # dev runs a single NAT Gateway; prod runs one per AZ so that a zone failure
  # cannot take outbound reachability to the SMS gateway and the SMTP relay with
  # it (Q-PLT-8).
  nat_gateway_count = var.single_nat_gateway ? 1 : local.az_count

  tags = merge(var.tags, {
    Environment = var.environment
    Workstream  = "WS1"
  })
}

resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = merge(local.tags, { Name = var.name_prefix })
}

# The default security group cannot be deleted, so it is emptied instead: any
# ENI that lands on it by mistake has no connectivity.
resource "aws_default_security_group" "this" {
  vpc_id = aws_vpc.this.id

  tags = merge(local.tags, { Name = "${var.name_prefix}-default-do-not-use" })
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = merge(local.tags, { Name = "${var.name_prefix}-igw" })
}

resource "aws_subnet" "public" {
  count = local.az_count

  vpc_id                  = aws_vpc.this.id
  cidr_block              = var.public_subnet_cidrs[count.index]
  availability_zone       = var.availability_zones[count.index]
  map_public_ip_on_launch = false

  tags = merge(local.tags, {
    Name = "${var.name_prefix}-public-${var.availability_zones[count.index]}"
    Tier = "public"
  })
}

resource "aws_subnet" "private_app" {
  count = local.az_count

  vpc_id            = aws_vpc.this.id
  cidr_block        = var.private_app_subnet_cidrs[count.index]
  availability_zone = var.availability_zones[count.index]

  tags = merge(local.tags, {
    Name = "${var.name_prefix}-private-app-${var.availability_zones[count.index]}"
    Tier = "private-app"
  })
}

resource "aws_subnet" "private_data" {
  count = local.az_count

  vpc_id            = aws_vpc.this.id
  cidr_block        = var.private_data_subnet_cidrs[count.index]
  availability_zone = var.availability_zones[count.index]

  tags = merge(local.tags, {
    Name = "${var.name_prefix}-private-data-${var.availability_zones[count.index]}"
    Tier = "private-data"
  })
}

resource "aws_eip" "nat" {
  count = local.nat_gateway_count

  domain = "vpc"

  tags = merge(local.tags, { Name = "${var.name_prefix}-nat-${count.index}" })
}

resource "aws_nat_gateway" "this" {
  count = local.nat_gateway_count

  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = aws_subnet.public[count.index].id

  tags = merge(local.tags, { Name = "${var.name_prefix}-nat-${count.index}" })

  depends_on = [aws_internet_gateway.this]
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  tags = merge(local.tags, { Name = "${var.name_prefix}-public" })
}

resource "aws_route" "public_default" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.this.id
}

resource "aws_route_table_association" "public" {
  count = local.az_count

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table" "private_app" {
  count = local.az_count

  vpc_id = aws_vpc.this.id

  tags = merge(local.tags, { Name = "${var.name_prefix}-private-app-${var.availability_zones[count.index]}" })
}

resource "aws_route" "private_app_default" {
  count = local.az_count

  route_table_id         = aws_route_table.private_app[count.index].id
  destination_cidr_block = "0.0.0.0/0"
  nat_gateway_id         = aws_nat_gateway.this[var.single_nat_gateway ? 0 : count.index].id
}

resource "aws_route_table_association" "private_app" {
  count = local.az_count

  subnet_id      = aws_subnet.private_app[count.index].id
  route_table_id = aws_route_table.private_app[count.index].id
}

# The data tier has no default route: Aurora and MSK need no outbound internet
# access, and denying it removes an exfiltration path from the tier that holds
# the tenant databases.
resource "aws_route_table" "private_data" {
  count = local.az_count

  vpc_id = aws_vpc.this.id

  tags = merge(local.tags, { Name = "${var.name_prefix}-private-data-${var.availability_zones[count.index]}" })
}

resource "aws_route_table_association" "private_data" {
  count = local.az_count

  subnet_id      = aws_subnet.private_data[count.index].id
  route_table_id = aws_route_table.private_data[count.index].id
}

resource "aws_security_group" "vpc_endpoints" {
  name        = "${var.name_prefix}-vpc-endpoints"
  description = "HTTPS from inside the VPC to the interface endpoints"
  vpc_id      = aws_vpc.this.id

  tags = merge(local.tags, { Name = "${var.name_prefix}-vpc-endpoints" })
}

resource "aws_vpc_security_group_ingress_rule" "vpc_endpoints_https" {
  security_group_id = aws_security_group.vpc_endpoints.id
  description       = "HTTPS from the VPC CIDR"
  cidr_ipv4         = var.vpc_cidr
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
}

resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.this.id
  service_name      = "com.amazonaws.${data.aws_region.current.name}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = concat(aws_route_table.private_app[*].id, aws_route_table.private_data[*].id)

  tags = merge(local.tags, { Name = "${var.name_prefix}-s3" })
}

resource "aws_vpc_endpoint" "interface" {
  for_each = var.enable_interface_endpoints ? toset(var.interface_endpoint_services) : toset([])

  vpc_id              = aws_vpc.this.id
  service_name        = "com.amazonaws.${data.aws_region.current.name}.${each.value}"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = aws_subnet.private_app[*].id
  security_group_ids  = [aws_security_group.vpc_endpoints.id]
  private_dns_enabled = true

  tags = merge(local.tags, { Name = "${var.name_prefix}-${replace(each.value, ".", "-")}" })
}
