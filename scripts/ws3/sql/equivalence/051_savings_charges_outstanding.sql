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
-- name: savings_charges_outstanding
-- description: Outstanding recurring savings charges per account, taken from
--   the LEFT JOIN aggregate in
--   fineract-provider/src/main/java/org/apache/fineract/portfolio/savings/service/DepositAccountReadPlatformServiceImpl.java:1599-1604.
--   Exercises "s.is_active = TRUE" (boolean vs tinyint) and a decimal literal
--   default inside COALESCE.
-- class: financial-balance
-- expect: identical
--
SELECT sa.id AS savings_account_id,
       COALESCE(sac.amount_outstanding_derived, 0.0) AS outstanding_charge_amount
  FROM m_savings_account sa
  LEFT JOIN (SELECT s.savings_account_id AS savings_account_id,
                    SUM(COALESCE(s.amount_outstanding_derived, 0.0)) AS amount_outstanding_derived
               FROM m_savings_account_charge s
               JOIN m_charge c ON c.id = s.charge_id AND c.charge_time_enum = 3
              WHERE s.is_active = TRUE
              GROUP BY s.savings_account_id) sac ON sac.savings_account_id = sa.id
 ORDER BY sa.id
