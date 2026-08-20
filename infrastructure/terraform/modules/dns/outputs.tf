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

output "hosted_zone_id" {
  description = "Hosted zone id. The edge module writes the ALB alias record into it."
  value       = local.zone_id
}

output "hosted_zone_name_servers" {
  description = "Name servers of the created zone. These are the records the parent domain must delegate to."
  value       = try(aws_route53_zone.this[0].name_servers, [])
}

output "certificate_arn" {
  description = "ACM certificate ARN for the ALB HTTPS listener."
  value       = var.validate_certificate ? one(aws_acm_certificate_validation.this[*].certificate_arn) : aws_acm_certificate.this.arn
}

output "domain_name" {
  description = "Certificate common name."
  value       = var.domain_name
}
