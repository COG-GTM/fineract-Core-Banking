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
-- name: savings_balances
-- description: Savings account balance recomputed from non-reversed
--   transactions and compared against the stored account_balance_derived, the
--   value SavingsAccountReadPlatformServiceImpl projects as accountBalance.
--   Deposit (1) and interest posting (4) credit the account, withdrawal (2)
--   debits it.
-- class: financial-balance
-- expect: identical
--
SELECT sa.id AS savings_account_id,
       sa.account_no,
       sa.currency_code,
       sa.account_balance_derived,
       COALESCE(SUM(CASE WHEN tr.transaction_type_enum IN (1, 4) THEN tr.amount
                         WHEN tr.transaction_type_enum = 2 THEN -tr.amount
                         ELSE 0 END), 0) AS recomputed_balance,
       COUNT(tr.id) AS transaction_count
  FROM m_savings_account sa
  LEFT JOIN m_savings_account_transaction tr
    ON tr.savings_account_id = sa.id AND tr.is_reversed = false
 GROUP BY sa.id, sa.account_no, sa.currency_code, sa.account_balance_derived
 ORDER BY sa.id
