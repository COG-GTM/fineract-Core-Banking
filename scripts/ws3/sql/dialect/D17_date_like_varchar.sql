--
-- Licensed to the Apache Software Foundation (ASF) under one
-- or more contributor license agreements. See the NOTICE file
-- distributed with this work for additional information
-- regarding copyright ownership. The ASF licenses this file
-- to you under the Apache License, Version 2.0 (the
-- "License"); you may not use this file except in compliance
-- with the License. You may obtain a copy of the License at
--
-- http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing,
-- software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
-- KIND, either express or implied. See the License for the
-- specific language governing permissions and limitations
-- under the License.
--
-- name: D17_date_like_varchar
-- source: fineract-accounting/src/main/java/org/apache/fineract/accounting/provisioning/service/ProvisioningEntriesReadPlatformServiceImpl.java:248
-- description: The dead String overload binds a date pattern as text and
--   applies LIKE to a DATE column. MariaDB coerces the value; PostgreSQL
--   rejects the date-to-varchar LIKE comparison.
-- expect: mysql=pass postgres=fail
--
SELECT COUNT(*)
  FROM (SELECT CAST('2024-01-15' AS DATE) AS created_date) t
 WHERE created_date LIKE '2024-01%'
