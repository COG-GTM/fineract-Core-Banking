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
-- name: journal_entry_running_balance
-- description: The latest running-balance journal entry per GL account. This is
--   the dialect-neutral rewrite of the correlated subquery in
--   fineract-accounting/src/main/java/org/apache/fineract/accounting/glaccount/service/GLAccountReadPlatformServiceImpl.java:128-129,
--   which in the shipped form uses the MySQL-only "GROUP BY <col> DESC" syntax
--   (see sql/dialect/D02_group_by_desc_mysql_only.sql).
-- class: financial-balance
-- expect: identical
--
SELECT je.account_id,
       je.id AS journal_entry_id,
       je.entry_date,
       je.office_running_balance,
       je.organization_running_balance
  FROM acc_gl_journal_entry je
 WHERE je.is_running_balance_calculated = true
   AND je.id IN (SELECT MAX(t2.id)
                   FROM acc_gl_journal_entry t2
                   JOIN (SELECT account_id, MAX(entry_date) AS entry_date
                           FROM acc_gl_journal_entry
                          WHERE is_running_balance_calculated = true
                          GROUP BY account_id) t3
                     ON t3.account_id = t2.account_id AND t3.entry_date = t2.entry_date
                  GROUP BY t2.account_id)
 ORDER BY je.account_id
