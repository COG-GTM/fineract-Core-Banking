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
-- name: loan_transaction_totals
-- description: Non-reversed loan transaction money movement per loan and
--   transaction type, with the portion breakdown that
--   fineract-provider/src/main/java/org/apache/fineract/portfolio/loanaccount/service/LoanReadPlatformServiceImpl.java
--   exposes on LoanTransactionData. Exercises boolean-vs-tinyint predicate
--   handling (is_reversed = false) and SUM over NULL portions.
-- class: financial-balance
-- expect: identical
--
SELECT tr.loan_id,
       tr.transaction_type_enum,
       COUNT(*) AS transaction_count,
       SUM(tr.amount) AS total_amount,
       SUM(COALESCE(tr.principal_portion_derived, 0)) AS principal_portion,
       SUM(COALESCE(tr.interest_portion_derived, 0)) AS interest_portion,
       SUM(COALESCE(tr.fee_charges_portion_derived, 0)) AS fee_portion,
       SUM(COALESCE(tr.penalty_charges_portion_derived, 0)) AS penalty_portion,
       MIN(tr.transaction_date) AS first_transaction_date,
       MAX(tr.transaction_date) AS last_transaction_date
  FROM m_loan_transaction tr
 WHERE tr.is_reversed = false
 GROUP BY tr.loan_id, tr.transaction_type_enum
 ORDER BY tr.loan_id, tr.transaction_type_enum
