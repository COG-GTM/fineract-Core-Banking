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
-- name: loan_installment_due_null_safe
-- description: The null-safe rewrite of loan_installment_due. GREATEST() is
--   replaced by COALESCE(last transaction date, due date), which is defined
--   identically on both engines. Included to show that the divergence reported
--   by loan_installment_due is caused solely by GREATEST()'s NULL semantics and
--   not by the surrounding arithmetic.
-- class: financial-balance
-- expect: identical
--
SELECT l.id AS loan_id,
       ls.installment,
       COALESCE(loan_transaction.transaction_date, ls.duedate) AS transaction_date,
       COALESCE(ls.principal_amount, 0) - COALESCE(ls.principal_writtenoff_derived, 0) - COALESCE(ls.principal_completed_derived, 0) AS principal_due,
       COALESCE(ls.interest_amount, 0) - COALESCE(ls.interest_completed_derived, 0) - COALESCE(ls.interest_waived_derived, 0) - COALESCE(ls.interest_writtenoff_derived, 0) AS interest_due
  FROM m_loan l
  JOIN m_currency rc ON rc.code = l.currency_code
  JOIN m_loan_repayment_schedule ls ON ls.loan_id = l.id
  LEFT JOIN (SELECT tr.loan_id, MAX(tr.transaction_date) AS transaction_date
               FROM m_loan_transaction tr
              WHERE tr.transaction_type_enum IN (1, 2)
                AND tr.is_reversed = false
              GROUP BY tr.loan_id) loan_transaction ON loan_transaction.loan_id = l.id
 ORDER BY l.id, ls.installment
