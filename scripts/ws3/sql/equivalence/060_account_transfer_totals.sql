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
-- name: account_transfer_totals
-- description: Total transferred amount for a loan account on a given date.
--   This is the dialect-neutral CASE form of the query at
--   fineract-provider/src/main/java/org/apache/fineract/portfolio/account/service/AccountTransfersReadPlatformServiceImpl.java:414-424,
--   which in the shipped form used the MySQL-only IF() function (see
--   sql/dialect/D01_if_function_mysql_only.sql). Bound parameters are inlined
--   here: accountType = 1 (loan), accountId = 1, transactionDate = 2023-03-01.
-- class: financial-balance
-- expect: identical
--
SELECT SUM(trans.amount) AS total_transaction_amount
  FROM m_account_transfer_details det
 INNER JOIN m_account_transfer_transaction trans
    ON det.id = trans.account_transfer_details_id
 WHERE trans.is_reversed = false
   AND trans.transaction_date = DATE '2023-03-01'
   AND CASE WHEN 1 = 1 THEN det.from_loan_account_id ELSE det.from_savings_account_id END = 1
