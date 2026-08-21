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
-- name: D01_if_function_mysql_only
-- source: fineract-provider/src/main/java/org/apache/fineract/portfolio/account/service/AccountTransfersReadPlatformServiceImpl.java:421 (as of 8c187f9d1)
-- description: IF(expr, a, b) is a MySQL/MariaDB function. PostgreSQL has no
--   IF() scalar function, so the statement is rejected before execution. The
--   call path (AccountTransfersReadPlatformService.getTotalTransactionAmount)
--   is used when validating transfer amounts, so on PostgreSQL it raises
--   instead of returning a total. Fixed by the CASE rewrite; see
--   sql/dialect/D01_fixed_case_portable.sql.
-- expect: mysql=pass postgres=fail
--
SELECT SUM(trans.amount) AS totalTransactionAmount
  FROM m_account_transfer_details AS det
 INNER JOIN m_account_transfer_transaction AS trans
    ON det.id = trans.account_transfer_details_id
 WHERE trans.is_reversed = false
   AND trans.transaction_date = DATE '2023-03-01'
   AND IF(1 = 1, det.from_loan_account_id = 1, det.from_savings_account_id = 1)
