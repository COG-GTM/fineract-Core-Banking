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
-- name: loan_installment_due
-- description: Per-installment principal/interest/fee/penalty due, plus the
--   GREATEST(last transaction date, due date) expression and the
--   MAX(transaction_date) GROUP BY subquery taken from the prepay/foreclosure
--   template mapper at
--   fineract-provider/src/main/java/org/apache/fineract/portfolio/loanaccount/service/LoanReadPlatformServiceImpl.java:2127-2140.
--   Exercises COALESCE over NULL derived columns, GREATEST across two date
--   columns, a MAX() tie, and boolean predicate handling on is_reversed.
-- class: financial-balance
-- expect: divergent
--   Documented divergence D-05: for a loan with no non-reversed transactions
--   the LEFT JOIN yields NULL, and GREATEST(NULL, duedate) is NULL on
--   MySQL/MariaDB but duedate on PostgreSQL. The harness asserts that the
--   divergence is still reproducible, so that it cannot be silently "fixed" by
--   an unrelated change or silently carried into production unnoticed.
--
SELECT l.id AS loan_id,
       ls.installment,
       GREATEST(loan_transaction.transaction_date, ls.duedate) AS transaction_date,
       COALESCE(ls.principal_amount, 0) - COALESCE(ls.principal_writtenoff_derived, 0) - COALESCE(ls.principal_completed_derived, 0) AS principal_due,
       COALESCE(ls.interest_amount, 0) - COALESCE(ls.interest_completed_derived, 0) - COALESCE(ls.interest_waived_derived, 0) - COALESCE(ls.interest_writtenoff_derived, 0) AS interest_due,
       COALESCE(ls.fee_charges_amount, 0) - COALESCE(ls.fee_charges_completed_derived, 0) - COALESCE(ls.fee_charges_writtenoff_derived, 0) - COALESCE(ls.fee_charges_waived_derived, 0) AS fee_due,
       COALESCE(ls.penalty_charges_amount, 0) - COALESCE(ls.penalty_charges_completed_derived, 0) - COALESCE(ls.penalty_charges_writtenoff_derived, 0) - COALESCE(ls.penalty_charges_waived_derived, 0) AS penalty_due,
       l.currency_code,
       l.currency_digits,
       l.net_disbursal_amount,
       rc.name AS currency_name
  FROM m_loan l
  JOIN m_currency rc ON rc.code = l.currency_code
  JOIN m_loan_repayment_schedule ls ON ls.loan_id = l.id
  LEFT JOIN (SELECT tr.loan_id, MAX(tr.transaction_date) AS transaction_date
               FROM m_loan_transaction tr
              WHERE tr.transaction_type_enum IN (1, 2)
                AND tr.is_reversed = false
              GROUP BY tr.loan_id) loan_transaction ON loan_transaction.loan_id = l.id
 ORDER BY l.id, ls.installment
