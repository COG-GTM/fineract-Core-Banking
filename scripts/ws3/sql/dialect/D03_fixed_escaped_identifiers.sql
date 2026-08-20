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
-- name: D03_fixed_escaped_identifiers
-- source: fineract-provider/src/main/java/org/apache/fineract/interoperation/service/InteropServiceImpl.java:158 (after fix)
-- description: The same projection with the identifiers routed through
--   DatabaseSpecificSQLGenerator.escape(); rendered here for PostgreSQL as
--   double quotes and for MySQL as backticks by the harness placeholder
--   {q}...{q}. Runs on both engines.
-- expect: mysql=pass postgres=pass
--
SELECT a.{q}address_line_1{q}, a.{q}address_line_2{q}
  FROM m_address a
 ORDER BY a.id
