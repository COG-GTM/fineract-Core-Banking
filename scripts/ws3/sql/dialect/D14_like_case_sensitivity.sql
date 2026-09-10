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
-- name: D14_like_case_sensitivity
-- source: fineract-provider/src/main/java/org/apache/fineract/portfolio/search/service/SearchReadPlatformServiceImpl.java:86-113
-- description: Bare LIKE is case-insensitive under the harness MariaDB
--   utf8mb4_unicode_ci collation but case-sensitive under PostgreSQL.
-- expect: mysql=pass postgres=pass
-- known-divergence: yes
--
WITH names(display_name) AS (
    SELECT 'John Smith'
    UNION ALL SELECT 'SMITHERS'
    UNION ALL SELECT 'smyth'
)
SELECT COUNT(*) AS matches
  FROM names
 WHERE display_name LIKE '%smith%'
