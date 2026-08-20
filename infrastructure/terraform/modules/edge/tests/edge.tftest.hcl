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

# Plan-only assertions against a mocked AWS provider: no credentials, no API
# calls, nothing created.

mock_provider "aws" {
  mock_resource "aws_lb" {
    defaults = {
      arn      = "arn:aws:elasticloadbalancing:eu-west-1:000000000000:loadbalancer/app/fineract-dev-alb/0123456789abcdef"
      dns_name = "fineract-dev-alb-0123456789.eu-west-1.elb.amazonaws.com"
      zone_id  = "Z32O12XQLNTSW2"
    }
  }

  mock_resource "aws_lb_target_group" {
    defaults = {
      arn = "arn:aws:elasticloadbalancing:eu-west-1:000000000000:targetgroup/fineract-dev-app/0123456789abcdef"
    }
  }

  mock_resource "aws_acm_certificate" {
    defaults = {
      arn = "arn:aws:acm:eu-west-1:000000000000:certificate/00000000-0000-0000-0000-000000000000"

      domain_validation_options = [{
        domain_name           = "api.dev.fineract.example.com"
        resource_record_name  = "_acme.api.dev.fineract.example.com."
        resource_record_type  = "CNAME"
        resource_record_value = "_validation.acm-validations.aws."
      }]
    }
  }

  mock_resource "aws_acm_certificate_validation" {
    defaults = {
      certificate_arn = "arn:aws:acm:eu-west-1:000000000000:certificate/00000000-0000-0000-0000-000000000000"
    }
  }

  mock_resource "aws_wafv2_web_acl" {
    defaults = {
      arn = "arn:aws:wafv2:eu-west-1:000000000000:regional/webacl/fineract-dev-edge/00000000-0000-0000-0000-000000000000"
    }
  }

  mock_resource "aws_route53_zone" {
    defaults = {
      zone_id      = "Z0000000000000000000"
      name_servers = ["ns-1.awsdns-00.org", "ns-2.awsdns-00.com"]
    }
  }
}

variables {
  name_prefix       = "fineract-dev"
  vpc_id            = "vpc-00000000000000000"
  public_subnet_ids = ["subnet-0000000000000000a", "subnet-0000000000000000b", "subnet-0000000000000000c"]
  domain_name       = "api.dev.fineract.example.com"
  hosted_zone_name  = "dev.fineract.example.com"
}

run "alb_is_internet_facing_in_the_public_subnets" {
  command = plan

  assert {
    condition     = aws_lb.this.internal == false
    error_message = "The edge ALB must be internet-facing."
  }

  assert {
    condition     = aws_lb.this.load_balancer_type == "application"
    error_message = "The edge load balancer must be an ALB."
  }

  assert {
    condition     = length(aws_lb.this.subnets) >= 2
    error_message = "The ALB must span at least two public subnets."
  }

  assert {
    condition     = aws_lb.this.drop_invalid_header_fields == true
    error_message = "The ALB must drop invalid header fields."
  }
}

run "https_is_the_only_listener_serving_traffic" {
  command = plan

  assert {
    condition     = aws_lb_listener.https.port == 443 && aws_lb_listener.https.protocol == "HTTPS"
    error_message = "Traffic must be served on an HTTPS listener on port 443."
  }

  assert {
    condition     = aws_lb_listener.https.default_action[0].type == "forward"
    error_message = "The HTTPS listener must forward to the Fineract target group."
  }

  assert {
    condition     = aws_lb_listener.http_redirect.default_action[0].type == "redirect"
    error_message = "The port 80 listener must not serve traffic; it must redirect."
  }
}

run "plain_http_redirects_permanently_to_https" {
  command = plan

  assert {
    condition     = aws_lb_listener.http_redirect.port == 80 && aws_lb_listener.http_redirect.protocol == "HTTP"
    error_message = "The redirect listener must sit on port 80."
  }

  assert {
    condition     = aws_lb_listener.http_redirect.default_action[0].redirect[0].protocol == "HTTPS"
    error_message = "Port 80 must redirect to HTTPS."
  }

  assert {
    condition     = aws_lb_listener.http_redirect.default_action[0].redirect[0].port == "443"
    error_message = "Port 80 must redirect to port 443."
  }

  assert {
    condition     = aws_lb_listener.http_redirect.default_action[0].redirect[0].status_code == "HTTP_301"
    error_message = "The HTTP to HTTPS redirect must be permanent (301)."
  }
}

# Mocked apply: the wiring between resources is only resolvable once the
# computed ARNs exist. Nothing is created — the provider is mocked.
run "tls_policy_is_modern_and_certificate_is_from_acm" {
  command = apply

  assert {
    condition     = startswith(aws_lb_listener.https.ssl_policy, "ELBSecurityPolicy-TLS13-1-2")
    error_message = "The HTTPS listener must use a TLS 1.3 capable, TLS 1.2 minimum policy."
  }

  assert {
    condition     = aws_acm_certificate.this.validation_method == "DNS"
    error_message = "The certificate must be DNS validated so renewal is unattended."
  }

  assert {
    condition     = aws_acm_certificate.this.domain_name == var.domain_name
    error_message = "The certificate must cover the served domain name."
  }

  assert {
    condition     = aws_lb_listener.https.certificate_arn == aws_acm_certificate_validation.this.certificate_arn
    error_message = "The listener must use the validated certificate, not the unvalidated one."
  }
}

run "waf_is_associated_with_the_alb" {
  command = apply

  assert {
    condition     = aws_wafv2_web_acl_association.this.resource_arn == aws_lb.this.arn
    error_message = "The web ACL must be associated with the edge ALB."
  }

  assert {
    condition     = aws_wafv2_web_acl_association.this.web_acl_arn == aws_wafv2_web_acl.this.arn
    error_message = "The association must reference the web ACL created by this module."
  }

  assert {
    condition     = aws_wafv2_web_acl.this.scope == "REGIONAL"
    error_message = "A web ACL fronting an ALB must be REGIONAL."
  }

  assert {
    condition     = length(aws_wafv2_web_acl.this.rule) == length(var.waf_managed_rule_groups) + 1
    error_message = "The web ACL must carry every managed rule group plus the rate-based rule."
  }
}

run "dns_record_points_at_the_alb" {
  command = apply

  assert {
    condition     = aws_route53_record.alias.name == var.domain_name
    error_message = "The alias record must be created for the served domain name."
  }

  assert {
    condition     = aws_route53_record.alias.alias[0].name == aws_lb.this.dns_name
    error_message = "The alias record must target the ALB."
  }
}

run "weak_tls_policy_is_rejected" {
  command = plan

  variables {
    ssl_policy = "ELBSecurityPolicy-2016-08"
  }

  expect_failures = [var.ssl_policy]
}

run "single_subnet_is_rejected" {
  command = plan

  variables {
    public_subnet_ids = ["subnet-0000000000000000a"]
  }

  expect_failures = [var.public_subnet_ids]
}
