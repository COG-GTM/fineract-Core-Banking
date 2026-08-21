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
-- name: journal_entry_totals
-- description: Trial-balance shaped aggregation of acc_gl_journal_entry:
--   debits and credits per office, GL account and currency, excluding reversed
--   entries. This is the read path the accounting reconciliation in WS9 will
--   compare between the incumbent database and Aurora.
-- class: financial-balance
-- expect: identical
--
SELECT je.office_id,
       je.account_id,
       je.currency_code,
       SUM(CASE WHEN je.type_enum = 1 THEN je.amount ELSE 0 END) AS total_debits,
       SUM(CASE WHEN je.type_enum = 2 THEN je.amount ELSE 0 END) AS total_credits,
       SUM(CASE WHEN je.type_enum = 1 THEN je.amount ELSE -je.amount END) AS net_movement,
       COUNT(*) AS entry_count,
       MAX(je.entry_date) AS last_entry_date
  FROM acc_gl_journal_entry je
 WHERE je.reversed = false
 GROUP BY je.office_id, je.account_id, je.currency_code
 ORDER BY je.office_id, je.account_id, je.currency_code
