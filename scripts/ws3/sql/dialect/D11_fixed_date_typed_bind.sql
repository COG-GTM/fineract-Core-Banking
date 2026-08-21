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
-- name: D11_fixed_date_typed_bind
-- description: The portable form of D11: bind the value as a date rather than
--   as a formatted string (the Java fix passes the LocalDate itself). Accepted
--   by both engines with the same result.
-- expect: mysql=pass postgres=pass
--
SELECT coalesce(sum(trans.amount), 0) AS total
  FROM m_account_transfer_transaction trans
 WHERE trans.transaction_date = cast('2024-02-15' AS date)
