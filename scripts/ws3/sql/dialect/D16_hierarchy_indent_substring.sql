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
-- name: D16_hierarchy_indent_substring
-- source: fineract-accounting/src/main/java/org/apache/fineract/accounting/glaccount/service/GLAccountReadPlatformServiceImpl.java:47
-- description: The GL account and office display indentation expression is
--   portable for ASCII hierarchy values. MySQL LENGTH counts bytes and
--   PostgreSQL LENGTH counts characters, but these hierarchy values are ASCII.
-- expect: mysql=pass postgres=pass
--
WITH hierarchy_values(h) AS (
    SELECT '.'
    UNION ALL SELECT '.1.'
    UNION ALL SELECT '.1.2.'
)
SELECT h,
       concat(substring('........................................', 1,
           ((LENGTH(h) - LENGTH(REPLACE(h, '.', '')) - 1) * 4)), 'Name') AS label
  FROM hierarchy_values
 ORDER BY h
