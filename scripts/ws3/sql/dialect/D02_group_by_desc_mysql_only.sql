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
-- name: D02_group_by_desc_mysql_only
-- source: fineract-accounting/src/main/java/org/apache/fineract/accounting/glaccount/service/GLAccountReadPlatformServiceImpl.java:128-129 (as of 8c187f9d1)
-- description: "GROUP BY <column> DESC" is a MySQL/MariaDB extension that sorts
--   the grouped output; standard SQL and PostgreSQL accept no sort direction in
--   GROUP BY and reject the statement with a syntax error. The clause is
--   semantically irrelevant here because the result feeds an IN (...) subquery.
-- expect: mysql=pass postgres=fail
--
SELECT gl_j.id
  FROM acc_gl_journal_entry gl_j
 WHERE gl_j.id IN (SELECT t1.id
                     FROM (SELECT t2.account_id, MAX(t2.id) AS id
                             FROM (SELECT id, MAX(entry_date) AS entry_date, account_id
                                     FROM acc_gl_journal_entry
                                    WHERE is_running_balance_calculated = true
                                    GROUP BY account_id DESC, id) t3
                             INNER JOIN acc_gl_journal_entry t2
                                ON t2.account_id = t3.account_id AND t2.entry_date = t3.entry_date
                            GROUP BY t2.account_id DESC) t1)
