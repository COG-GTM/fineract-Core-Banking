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

variable "name_prefix" {
  description = "Prefix applied to the Name tag of every resource, e.g. fineract-dev."
  type        = string
}

variable "environment" {
  description = "Environment identifier (dev, prod). Used for tagging only."
  type        = string
}

variable "domain_name" {
  description = "Apex domain of the hosted zone and the common name of the certificate, e.g. dev.fineract.example.com."
  type        = string
}

variable "subject_alternative_names" {
  description = "Additional names on the certificate. Every name must resolve in the zone this module manages, or validation will not complete."
  type        = list(string)
  default     = []
}

variable "create_hosted_zone" {
  description = "Create the hosted zone. Set false and pass hosted_zone_id when the zone is delegated from a zone this account does not own."
  type        = bool
  default     = true
}

variable "hosted_zone_id" {
  description = "Existing hosted zone id, required when create_hosted_zone is false."
  type        = string
  default     = null
}

variable "validate_certificate" {
  description = "Wait for DNS validation to complete. Set false when the validation records are published in a zone Terraform does not manage."
  type        = bool
  default     = true
}

variable "tags" {
  description = "Tags merged into every resource."
  type        = map(string)
  default     = {}
}
