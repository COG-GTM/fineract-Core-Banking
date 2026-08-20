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

# Remote state is declared here but never bound to a real bucket in source
# control: the backend block is intentionally empty (partial configuration) and
# the values are supplied at init time.
#
#   terraform init -backend-config=backend.hcl
#
# See backend.hcl.example for the keys that must be supplied, and
# docs/migration/ws1-landing-zone.md for who owns the bucket and the lock table.

terraform {
  backend "s3" {}
}
