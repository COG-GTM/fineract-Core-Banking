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
    "fineract:module" = "edge"
  })

  zone_id = var.create_hosted_zone ? aws_route53_zone.this[0].zone_id : data.aws_route53_zone.this[0].zone_id

  # Keyed statically so the validation records plan cleanly before the
  # certificate exists; only the record values are known after apply.
  certificate_domains = toset(concat([var.domain_name], var.subject_alternative_names))

  domain_validation_options = {
    for option in aws_acm_certificate.this.domain_validation_options : option.domain_name => option
  }
}

# --------------------------------------------------------------------------
# Route 53
# --------------------------------------------------------------------------

resource "aws_route53_zone" "this" {
  count = var.create_hosted_zone ? 1 : 0

  name = var.hosted_zone_name
  tags = local.tags
}

data "aws_route53_zone" "this" {
  count = var.create_hosted_zone ? 0 : 1

  name         = var.hosted_zone_name
  private_zone = false
}

# --------------------------------------------------------------------------
# ACM certificate, DNS validated
# --------------------------------------------------------------------------

resource "aws_acm_certificate" "this" {
  domain_name               = var.domain_name
  subject_alternative_names = var.subject_alternative_names
  validation_method         = "DNS"
  key_algorithm             = "RSA_2048"

  tags = local.tags

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_route53_record" "certificate_validation" {
  for_each = local.certificate_domains

  zone_id         = local.zone_id
  name            = local.domain_validation_options[each.key].resource_record_name
  type            = local.domain_validation_options[each.key].resource_record_type
  records         = [local.domain_validation_options[each.key].resource_record_value]
  ttl             = 60
  allow_overwrite = true
}

resource "aws_acm_certificate_validation" "this" {
  certificate_arn         = aws_acm_certificate.this.arn
  validation_record_fqdns = [for record in aws_route53_record.certificate_validation : record.fqdn]
}

# --------------------------------------------------------------------------
# Security group
# --------------------------------------------------------------------------

resource "aws_security_group" "alb" {
  name        = "${var.name_prefix}-alb"
  description = "Internet-facing ALB for Fineract; 80 redirects to 443."
  vpc_id      = var.vpc_id

  tags = merge(local.tags, { Name = "${var.name_prefix}-alb" })

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_vpc_security_group_ingress_rule" "https" {
  for_each = toset(var.ingress_cidr_blocks)

  security_group_id = aws_security_group.alb.id
  description       = "HTTPS from the internet"
  cidr_ipv4         = each.value
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "http_redirect" {
  for_each = toset(var.ingress_cidr_blocks)

  security_group_id = aws_security_group.alb.id
  description       = "HTTP, answered with a 301 to HTTPS"
  cidr_ipv4         = each.value
  from_port         = 80
  to_port           = 80
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "to_targets" {
  security_group_id = aws_security_group.alb.id
  description       = "To the Fineract tasks in the private subnets"
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = var.target_port
  to_port           = var.target_port
  ip_protocol       = "tcp"
}

# --------------------------------------------------------------------------
# Application Load Balancer
# --------------------------------------------------------------------------

resource "aws_lb" "this" {
  name               = "${var.name_prefix}-alb"
  internal           = false
  load_balancer_type = "application"
  subnets            = var.public_subnet_ids
  security_groups    = [aws_security_group.alb.id]

  drop_invalid_header_fields = true
  enable_deletion_protection = var.enable_deletion_protection
  enable_http2               = true
  idle_timeout               = var.idle_timeout

  dynamic "access_logs" {
    for_each = var.access_logs_bucket == null ? [] : [var.access_logs_bucket]

    content {
      bucket  = access_logs.value
      prefix  = var.name_prefix
      enabled = true
    }
  }

  tags = local.tags
}

resource "aws_lb_target_group" "app" {
  name        = "${var.name_prefix}-app"
  port        = var.target_port
  protocol    = var.target_protocol
  target_type = "ip"
  vpc_id      = var.vpc_id

  deregistration_delay = 30

  health_check {
    path                = var.health_check_path
    protocol            = var.target_protocol
    matcher             = "200"
    interval            = 30
    timeout             = 10
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  tags = local.tags

  lifecycle {
    create_before_destroy = true
  }
}

# TLS terminates here, on an ACM certificate, replacing the keystore committed
# at fineract-provider/src/main/resources/keystore.jks.
resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.this.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = var.ssl_policy
  certificate_arn   = aws_acm_certificate_validation.this.certificate_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }

  tags = local.tags
}

resource "aws_lb_listener" "http_redirect" {
  load_balancer_arn = aws_lb.this.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "redirect"

    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }

  tags = local.tags
}

resource "aws_route53_record" "alias" {
  zone_id = local.zone_id
  name    = var.domain_name
  type    = "A"

  alias {
    name                   = aws_lb.this.dns_name
    zone_id                = aws_lb.this.zone_id
    evaluate_target_health = true
  }
}

# --------------------------------------------------------------------------
# WAF
# --------------------------------------------------------------------------

resource "aws_wafv2_web_acl" "this" {
  name        = "${var.name_prefix}-edge"
  description = "Web ACL in front of the Fineract ALB."
  scope       = "REGIONAL"

  default_action {
    allow {}
  }

  dynamic "rule" {
    for_each = { for index, group in var.waf_managed_rule_groups : group => index }

    content {
      name     = rule.key
      priority = rule.value

      override_action {
        none {}
      }

      statement {
        managed_rule_group_statement {
          name        = rule.key
          vendor_name = "AWS"
        }
      }

      visibility_config {
        cloudwatch_metrics_enabled = true
        metric_name                = rule.key
        sampled_requests_enabled   = true
      }
    }
  }

  rule {
    name     = "RateLimitPerIp"
    priority = length(var.waf_managed_rule_groups)

    action {
      block {}
    }

    statement {
      rate_based_statement {
        limit              = var.waf_rate_limit
        aggregate_key_type = "IP"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "RateLimitPerIp"
      sampled_requests_enabled   = true
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "${var.name_prefix}-edge"
    sampled_requests_enabled   = true
  }

  tags = local.tags
}

resource "aws_wafv2_web_acl_association" "this" {
  resource_arn = aws_lb.this.arn
  web_acl_arn  = aws_wafv2_web_acl.this.arn
}
