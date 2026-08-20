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
-- name: loan_outstanding_by_office
-- description: Active loan portfolio outstanding aggregated per office and
--   currency. Mirrors the derived-balance columns that
--   fineract-provider/src/main/java/org/apache/fineract/portfolio/loanaccount/service/LoanReadPlatformServiceImpl.java
--   projects into LoanAccountData, and the aggregation the accounting
--   reconciliation reports run over them.
-- class: financial-balance
--
SELECT o.id AS office_id,
       l.currency_code,
       COUNT(*) AS loan_count,
       SUM(COALESCE(l.principal_outstanding_derived, 0)) AS principal_outstanding,
       SUM(COALESCE(l.interest_outstanding_derived, 0)) AS interest_outstanding,
       SUM(COALESCE(l.fee_charges_outstanding_derived, 0)) AS fee_outstanding,
       SUM(COALESCE(l.penalty_charges_outstanding_derived, 0)) AS penalty_outstanding,
       SUM(COALESCE(l.total_outstanding_derived, 0)) AS total_outstanding,
       SUM(COALESCE(l.total_overpaid_derived, 0)) AS total_overpaid
  FROM m_loan l
  JOIN m_client c ON c.id = l.client_id
  JOIN m_office o ON o.id = c.office_id
 WHERE l.loan_status_id = 300
 GROUP BY o.id, l.currency_code
 ORDER BY o.id, l.currency_code
