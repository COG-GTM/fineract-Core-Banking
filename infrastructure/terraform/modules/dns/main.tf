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
  tags = merge(var.tags, {
    Environment = var.environment
    Workstream  = "WS1"
  })

  zone_id = var.create_hosted_zone ? aws_route53_zone.this[0].zone_id : var.hosted_zone_id

  # Keyed on names known at plan time so that the validation records do not
  # force a two-phase apply.
  certificate_names = toset(concat([var.domain_name], var.subject_alternative_names))
}

resource "aws_route53_zone" "this" {
  count = var.create_hosted_zone ? 1 : 0

  name          = var.domain_name
  force_destroy = false

  tags = merge(local.tags, { Name = "${var.name_prefix}-${var.domain_name}" })
}

resource "aws_acm_certificate" "this" {
  domain_name               = var.domain_name
  subject_alternative_names = var.subject_alternative_names
  validation_method         = "DNS"

  tags = merge(local.tags, { Name = "${var.name_prefix}-${var.domain_name}" })

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_route53_record" "validation" {
  for_each = { for idx, name in tolist(local.certificate_names) : name => idx }

  zone_id         = local.zone_id
  name            = tolist(aws_acm_certificate.this.domain_validation_options)[each.value].resource_record_name
  type            = tolist(aws_acm_certificate.this.domain_validation_options)[each.value].resource_record_type
  records         = [tolist(aws_acm_certificate.this.domain_validation_options)[each.value].resource_record_value]
  ttl             = 60
  allow_overwrite = true
}

resource "aws_acm_certificate_validation" "this" {
  count = var.validate_certificate ? 1 : 0

  certificate_arn         = aws_acm_certificate.this.arn
  validation_record_fqdns = [for record in aws_route53_record.validation : record.fqdn]
}
