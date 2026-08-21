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
-- name: D12_fixed_group_by_joined_pk
-- description: The portable form of D12: add the joined table's primary key to
--   the GROUP BY so the selected column is functionally dependent in both
--   engines (the harness fixture keys m_currency on code; the production
--   schema keys it on id, which is what the Java fix groups by). Accepted by
--   both engines, with identical results.
-- expect: mysql=pass postgres=pass
--
SELECT cl.id AS client_id, ln.id AS loan_id, rc.name AS currency_name,
       sum(ls.principal_amount) AS principal_due
  FROM m_loan ln
  JOIN m_client cl ON cl.id = ln.client_id
  LEFT JOIN m_currency rc ON rc.code = ln.currency_code
  JOIN m_loan_repayment_schedule ls ON ls.loan_id = ln.id
 GROUP BY cl.id, ln.id, rc.code
 ORDER BY cl.id, ln.id
