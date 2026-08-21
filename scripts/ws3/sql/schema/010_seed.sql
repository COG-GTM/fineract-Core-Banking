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
-- WS3 cross-engine equivalence harness: deterministic seed data.
--
-- Identical literals are loaded into both engines. The data is chosen to
-- exercise the divergence classes the audit cares about: NULLs feeding
-- COALESCE arithmetic, reversed rows behind boolean predicates, ties in
-- MAX()/GROUP BY, mixed decimal scales, and rows that must be excluded by a
-- date boundary.
--

INSERT INTO m_currency (code, decimal_places, currency_multiplesof, display_symbol, name, internationalized_name_code) VALUES
    ('USD', 2, NULL, '$', 'US Dollar', 'currency.USD'),
    ('EUR', 2, NULL, 'E', 'Euro', 'currency.EUR');

INSERT INTO m_office (id, parent_id, hierarchy, name, opening_date) VALUES
    (1, NULL, '.', 'Head Office', DATE '2009-01-01'),
    (2, 1, '.2.', 'Branch North', DATE '2010-06-01');

INSERT INTO m_client (id, office_id, account_no, status_enum, display_name, firstname, middlename, lastname, date_of_birth) VALUES
    (1, 1, '000000001', 300, 'Alice Smith', 'Alice', NULL, 'Smith', DATE '1985-03-14'),
    (2, 2, '000000002', 300, 'Bob Jones', 'Bob', 'B', 'Jones', DATE '1979-11-02'),
    (3, 2, '000000003', 300, 'Carol Ray', 'Carol', NULL, 'Ray', NULL);

INSERT INTO m_address (id, street, address_line_1, address_line_2, city, postal_code) VALUES
    (1, 'Main St', '12 Main St', NULL, 'Springfield', '11111'),
    (2, 'Elm St', '4 Elm St', 'Flat 2', 'Shelbyville', '22222');

INSERT INTO m_charge (id, name, currency_code, charge_time_enum, is_active) VALUES
    (1, 'Monthly Fee', 'USD', 3, true),
    (2, 'Inactive Fee', 'USD', 3, false);

-- Loans. Loan 3 is EUR, loan 4 is closed (status 600) and must be excluded by
-- the active-portfolio predicate. NULL derived columns exercise COALESCE.
INSERT INTO m_loan (id, account_no, client_id, currency_code, currency_digits, currency_multiplesof, loan_status_id,
    principal_amount, principal_disbursed_derived, principal_repaid_derived, principal_writtenoff_derived, principal_outstanding_derived,
    interest_charged_derived, interest_repaid_derived, interest_waived_derived, interest_writtenoff_derived, interest_outstanding_derived,
    fee_charges_charged_derived, fee_charges_repaid_derived, fee_charges_outstanding_derived,
    penalty_charges_charged_derived, penalty_charges_repaid_derived, penalty_charges_outstanding_derived,
    total_expected_repayment_derived, total_repayment_derived, total_outstanding_derived, total_overpaid_derived,
    net_disbursal_amount, is_npa, disbursedon_date, closedon_date) VALUES
    (1, 'L000000001', 1, 'USD', 2, NULL, 300,
        10000.000000, 10000.000000, 2500.000000, NULL, 7500.000000,
        1200.000000, 300.000000, NULL, NULL, 900.000000,
        150.000000, 50.000000, 100.000000,
        25.000000, NULL, 25.000000,
        11375.000000, 2850.000000, 8525.000000, NULL,
        10000.000000, false, DATE '2023-02-01', NULL),
    (2, 'L000000002', 2, 'USD', 2, NULL, 300,
        5000.500000, 5000.500000, NULL, NULL, 5000.500000,
        450.250000, NULL, NULL, NULL, 450.250000,
        NULL, NULL, NULL,
        NULL, NULL, NULL,
        5450.750000, NULL, 5450.750000, NULL,
        5000.500000, true, DATE '2023-05-15', NULL),
    (3, 'L000000003', 3, 'EUR', 2, NULL, 300,
        2000.000000, 2000.000000, 2000.000000, NULL, 0.000000,
        100.000000, 100.000000, NULL, NULL, 0.000000,
        NULL, NULL, NULL,
        NULL, NULL, NULL,
        2100.000000, 2100.000000, 0.000000, NULL,
        2000.000000, false, DATE '2023-01-10', NULL),
    (4, 'L000000004', 2, 'USD', 2, NULL, 600,
        900.000000, 900.000000, 900.000000, NULL, 0.000000,
        0.000000, 0.000000, NULL, NULL, 0.000000,
        NULL, NULL, NULL,
        NULL, NULL, NULL,
        900.000000, 900.000000, 0.000000, 10.000000,
        900.000000, false, DATE '2022-01-01', DATE '2022-12-31'),
    -- Loan 5 is active and has a repayment schedule but no loan transactions at
    -- all, so the LEFT JOIN in the installment query yields a NULL date. This is
    -- the row that exposes the GREATEST(NULL, date) divergence between the two
    -- engines (see docs/migration/ws3-dialect-audit.md, finding D-05).
    (5, 'L000000005', 3, 'USD', 2, NULL, 300,
        3000.000000, 3000.000000, NULL, NULL, 3000.000000,
        240.000000, NULL, NULL, NULL, 240.000000,
        NULL, NULL, NULL,
        NULL, NULL, NULL,
        3240.000000, NULL, 3240.000000, NULL,
        3000.000000, false, DATE '2023-07-01', NULL);

INSERT INTO m_loan_repayment_schedule (id, loan_id, installment, fromdate, duedate, obligations_met_on_date, completed_derived,
    principal_amount, principal_completed_derived, principal_writtenoff_derived,
    interest_amount, interest_completed_derived, interest_writtenoff_derived, interest_waived_derived,
    fee_charges_amount, fee_charges_completed_derived, fee_charges_writtenoff_derived, fee_charges_waived_derived,
    penalty_charges_amount, penalty_charges_completed_derived, penalty_charges_writtenoff_derived, penalty_charges_waived_derived) VALUES
    (1, 1, 1, DATE '2023-02-01', DATE '2023-03-01', DATE '2023-03-01', true,
        2500.000000, 2500.000000, NULL, 400.000000, 300.000000, NULL, NULL,
        150.000000, 50.000000, NULL, NULL, NULL, NULL, NULL, NULL),
    (2, 1, 2, DATE '2023-03-01', DATE '2023-04-01', NULL, false,
        2500.000000, NULL, NULL, 400.000000, NULL, NULL, NULL,
        NULL, NULL, NULL, NULL, 25.000000, NULL, NULL, NULL),
    (3, 1, 3, DATE '2023-04-01', DATE '2023-05-01', NULL, false,
        5000.000000, NULL, NULL, 400.000000, NULL, NULL, 100.000000,
        NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL),
    (4, 2, 1, DATE '2023-05-15', DATE '2023-06-15', NULL, false,
        5000.500000, NULL, NULL, 450.250000, NULL, NULL, NULL,
        NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL),
    (5, 3, 1, DATE '2023-01-10', DATE '2023-02-10', DATE '2023-02-10', true,
        2000.000000, 2000.000000, NULL, 100.000000, 100.000000, NULL, NULL,
        NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL),
    (6, 5, 1, DATE '2023-07-01', DATE '2023-08-01', NULL, false,
        3000.000000, NULL, NULL, 240.000000, NULL, NULL, NULL,
        NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL);

-- Transaction 4 is reversed and must never contribute to a balance.
-- Transactions 2 and 3 share a transaction_date to create a MAX() tie.
INSERT INTO m_loan_transaction (id, loan_id, office_id, transaction_type_enum, transaction_date, is_reversed, amount,
    principal_portion_derived, interest_portion_derived, fee_charges_portion_derived, penalty_charges_portion_derived,
    outstanding_loan_balance_derived) VALUES
    (1, 1, 1, 1, DATE '2023-02-01', false, 10000.000000, 10000.000000, NULL, NULL, NULL, 10000.000000),
    (2, 1, 1, 2, DATE '2023-03-01', false, 2850.000000, 2500.000000, 300.000000, 50.000000, NULL, 7500.000000),
    (3, 1, 1, 2, DATE '2023-03-01', false, 0.000000, NULL, NULL, NULL, NULL, 7500.000000),
    (4, 1, 1, 2, DATE '2023-04-01', true, 500.000000, 500.000000, NULL, NULL, NULL, 7000.000000),
    (5, 2, 2, 1, DATE '2023-05-15', false, 5000.500000, 5000.500000, NULL, NULL, NULL, 5000.500000),
    (6, 3, 2, 1, DATE '2023-01-10', false, 2000.000000, 2000.000000, NULL, NULL, NULL, 2000.000000),
    (7, 3, 2, 2, DATE '2023-02-10', false, 2100.000000, 2000.000000, 100.000000, NULL, NULL, 0.000000);

INSERT INTO acc_gl_account (id, name, gl_code, classification_enum, account_usage, manual_entries_allowed) VALUES
    (1, 'Loan Portfolio', '10100', 1, 1, true),
    (2, 'Interest Income', '40100', 4, 1, true),
    (3, 'Savings Control', '20100', 2, 1, true);

-- type_enum 1 = DEBIT, 2 = CREDIT. Entry 7 is reversed. Entries 3 and 4 share
-- an entry_date within the same account to make ordering ambiguous unless the
-- query is deterministic.
INSERT INTO acc_gl_journal_entry (id, account_id, office_id, currency_code, transaction_id, reversed, manual_entry, entry_date,
    type_enum, amount, is_running_balance_calculated, office_running_balance, organization_running_balance) VALUES
    (1, 1, 1, 'USD', 'L1', false, false, DATE '2023-02-01', 1, 10000.000000, true, 10000.000000, 10000.000000),
    (2, 3, 1, 'USD', 'L1', false, false, DATE '2023-02-01', 2, 10000.000000, true, -10000.000000, -10000.000000),
    (3, 1, 1, 'USD', 'L2', false, false, DATE '2023-03-01', 2, 2500.000000, true, 7500.000000, 7500.000000),
    (4, 2, 1, 'USD', 'L2', false, false, DATE '2023-03-01', 2, 300.000000, true, 300.000000, 300.000000),
    (5, 1, 2, 'USD', 'L3', false, false, DATE '2023-05-15', 1, 5000.500000, true, 5000.500000, 12500.500000),
    (6, 3, 2, 'USD', 'L3', false, false, DATE '2023-05-15', 2, 5000.500000, true, -5000.500000, -15000.500000),
    (7, 1, 2, 'USD', 'L4', true, true, DATE '2023-06-01', 1, 999.990000, false, NULL, NULL),
    (8, 2, 2, 'EUR', 'L5', false, false, DATE '2023-02-10', 2, 100.000000, true, 100.000000, 100.000000);

INSERT INTO m_savings_account (id, account_no, client_id, currency_code, currency_digits, status_enum, account_balance_derived,
    total_deposits_derived, total_withdrawals_derived, total_interest_posted_derived, total_fees_charge_derived,
    min_required_balance, activatedon_date) VALUES
    (1, 'S000000001', 1, 'USD', 2, 300, 1512.750000, 2000.000000, 500.000000, 12.750000, NULL, NULL, DATE '2023-01-05'),
    (2, 'S000000002', 2, 'USD', 2, 300, 0.000000, 250.000000, 250.000000, NULL, NULL, 0.000000, DATE '2023-04-01'),
    (3, 'S000000003', 3, 'EUR', 2, 100, 0.000000, NULL, NULL, NULL, NULL, NULL, NULL);

-- transaction_type_enum 1 = deposit, 2 = withdrawal, 4 = interest posting.
-- Transaction 5 is reversed.
INSERT INTO m_savings_account_transaction (id, savings_account_id, office_id, transaction_type_enum, transaction_date, is_reversed,
    is_manual, amount, running_balance_derived, balance_end_date_derived) VALUES
    (1, 1, 1, 1, DATE '2023-01-05', false, false, 1500.000000, 1500.000000, DATE '2023-02-05'),
    (2, 1, 1, 1, DATE '2023-02-05', false, false, 500.000000, 2000.000000, DATE '2023-03-05'),
    (3, 1, 1, 2, DATE '2023-03-05', false, false, 500.000000, 1500.000000, DATE '2023-03-31'),
    (4, 1, 1, 4, DATE '2023-03-31', false, false, 12.750000, 1512.750000, NULL),
    (5, 1, 1, 2, DATE '2023-04-01', true, true, 100.000000, 1412.750000, NULL),
    (6, 2, 2, 1, DATE '2023-04-01', false, false, 250.000000, 250.000000, DATE '2023-04-20'),
    (7, 2, 2, 2, DATE '2023-04-20', false, false, 250.000000, 0.000000, NULL);

INSERT INTO m_savings_account_charge (id, savings_account_id, charge_id, is_active, amount, amount_paid_derived, amount_outstanding_derived) VALUES
    (1, 1, 1, true, 10.000000, 4.000000, 6.000000),
    (2, 1, 1, true, 10.000000, NULL, 10.000000),
    (3, 2, 2, false, 10.000000, NULL, 10.000000);

INSERT INTO m_account_transfer_details (id, from_office_id, to_office_id, from_loan_account_id, to_loan_account_id,
    from_savings_account_id, to_savings_account_id, transfer_type) VALUES
    (1, 1, 1, NULL, 1, 1, NULL, 2),
    (2, 2, 2, 2, NULL, NULL, 2, 3),
    (3, 1, 2, NULL, 1, 1, NULL, 2);

INSERT INTO m_account_transfer_transaction (id, account_transfer_details_id, from_loan_transaction_id, to_loan_transaction_id,
    is_reversed, transaction_date, currency_code, amount) VALUES
    (1, 1, NULL, 2, false, DATE '2023-03-01', 'USD', 2850.000000),
    (2, 1, NULL, 2, false, DATE '2023-03-01', 'USD', 150.500000),
    (3, 2, 5, NULL, false, DATE '2023-05-15', 'USD', 500.000000),
    (4, 3, NULL, 2, true, DATE '2023-03-01', 'USD', 777.000000),
    (5, 3, NULL, 2, false, DATE '2023-04-01', 'USD', 60.000000);
